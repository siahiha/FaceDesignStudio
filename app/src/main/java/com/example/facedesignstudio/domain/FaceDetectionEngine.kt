package com.example.facedesignstudio.domain

import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class ImageInfo(
    val bitmap: Bitmap,
    val rotationDegrees: Int,
)

data class FacePoint(
    val x: Float,
    val y: Float,
)

data class FacialLandmark(
    val name: String,
    val x: Float,
    val y: Float,
    // The currently selected face mesh model does not provide individual point scores.
    val confidence: Float? = null,
)

data class DetectionAnalysis(
    val faceConfidence: Float,
    val landmarks: List<FacialLandmark>,
    val frontalScore: Float,
    val eyeRotationDegrees: Float,
    val midlineX: Float,
    val detectorFaceCount: Int,
)

sealed interface DetectionOutcome {
    data class Accepted(val analysis: DetectionAnalysis) : DetectionOutcome
    data class Rejected(val reason: DetectionRejection, val faceConfidence: Float? = null) : DetectionOutcome
}

enum class DetectionRejection {
    NO_FACE,
    REQUIRED_LANDMARK_MISSING,
    LOW_FACE_CONFIDENCE,
    MODEL_ERROR,
}

@Singleton
class FaceDetectionEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val MODEL_VERSION = "MediaPipe Tasks Vision 0.10.35 / BlazeFace short-range + Face Landmarker float16"
        private const val DETECTOR_MODEL = "blaze_face_short_range.tflite"
        private const val LANDMARKER_MODEL = "face_landmarker.task"
        private const val FACE_DETECTION_INTERNAL_FLOOR = 0.01f
        private val REQUIRED_POINT_INDICES = listOf(
            "LEFT_EYE_LOWER" to 145,
            "RIGHT_EYE_LOWER" to 374,
            "LEFT_PUPIL" to 473,
            "RIGHT_PUPIL" to 468,
            "LEFT_IRIS_BOTTOM" to 477,
            "RIGHT_IRIS_BOTTOM" to 472,
            "GLABELLA" to 168,
            "NOSE_TIP" to 1,
            "CHIN" to 152,
            "LEFT_TEMPLE" to 127,
            "RIGHT_TEMPLE" to 356,
        )
    }

    private val faceDetector: FaceDetector by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val baseOptions = BaseOptions.builder().setModelAssetPath(DETECTOR_MODEL).build()
        val options = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setMinDetectionConfidence(FACE_DETECTION_INTERNAL_FLOOR)
            .build()
        FaceDetector.createFromOptions(context, options)
    }

    private val faceLandmarker: FaceLandmarker by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val baseOptions = BaseOptions.builder().setModelAssetPath(LANDMARKER_MODEL).build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0f)
            .setMinFacePresenceConfidence(0f)
            .build()
        FaceLandmarker.createFromOptions(context, options)
    }

    fun analyze(bitmap: Bitmap, minimumConfidence: Float): DetectionOutcome {
        return try {
        val image = BitmapImageBuilder(bitmap).build()
        val faceResult: FaceDetectorResult = faceDetector.detect(image)
        val detections = faceResult.detections()
        if (detections.isEmpty()) return DetectionOutcome.Rejected(DetectionRejection.NO_FACE)
        val scoredDetections = detections.mapNotNull { detection ->
            val score = detection.categories().maxOfOrNull { it.score() }
                ?.takeIf { it.isFinite() && it in 0f..1f }
            score?.let { detection to it }
        }
        if (scoredDetections.isEmpty()) return DetectionOutcome.Rejected(DetectionRejection.NO_FACE)

        // A group photo is usable: choose the strongest detector result, then let the
        // one-face landmarker locate the most prominent face in the same image.
        val (selectedDetection, faceConfidence) = scoredDetections.maxByOrNull { it.second }
            ?: return DetectionOutcome.Rejected(DetectionRejection.NO_FACE)
        if (faceConfidence < minimumConfidence) {
            return DetectionOutcome.Rejected(DetectionRejection.LOW_FACE_CONFIDENCE, faceConfidence)
        }

        val selectedFaceCrop = cropSelectedFace(bitmap, selectedDetection.boundingBox())
            ?: return DetectionOutcome.Rejected(DetectionRejection.REQUIRED_LANDMARK_MISSING, faceConfidence)
        val faceResultLandmarks = try {
            faceLandmarker.detect(BitmapImageBuilder(selectedFaceCrop.bitmap).build()).faceLandmarks().firstOrNull()
        } finally {
            selectedFaceCrop.bitmap.recycle()
        } ?: return DetectionOutcome.Rejected(DetectionRejection.REQUIRED_LANDMARK_MISSING, faceConfidence)

        val landmarks = REQUIRED_POINT_INDICES.mapNotNull { (name, index) ->
            val point = faceResultLandmarks.getOrNull(index) ?: return@mapNotNull null
            if (!point.x().isFinite() || !point.y().isFinite()) return@mapNotNull null
            if (point.x() !in 0f..1f || point.y() !in 0f..1f) return@mapNotNull null
            val x = (selectedFaceCrop.left + point.x() * selectedFaceCrop.width) / bitmap.width
            val y = (selectedFaceCrop.top + point.y() * selectedFaceCrop.height) / bitmap.height
            if (x !in 0f..1f || y !in 0f..1f) return@mapNotNull null
            FacialLandmark(name, x, y, point.confidenceOrNull())
        }
        if (landmarks.size != REQUIRED_POINT_INDICES.size) {
            return DetectionOutcome.Rejected(DetectionRejection.REQUIRED_LANDMARK_MISSING, faceConfidence)
        }
        val points = landmarks.associateBy { it.name }
        val leftEye = points.getValue("LEFT_IRIS_BOTTOM")
        val rightEye = points.getValue("RIGHT_IRIS_BOTTOM")
        val nose = points.getValue("NOSE_TIP")
        val leftDistance = abs(nose.x - leftEye.x)
        val rightDistance = abs(rightEye.x - nose.x)
        val bilateralSymmetry = if (leftDistance + rightDistance == 0f) 0f else {
            1f - abs(leftDistance - rightDistance) / (leftDistance + rightDistance)
        }
        val eyeLevelScore = (1f - abs(leftEye.y - rightEye.y) / 0.12f).coerceIn(0f, 1f)
        val frontalScore = (bilateralSymmetry * 0.75f + eyeLevelScore * 0.25f).coerceIn(0f, 1f)
        val visualEyePoints = listOf(leftEye, rightEye).sortedBy { it.x }
        val eyeRotation = Math.toDegrees(
            atan2(
                ((visualEyePoints[1].y - visualEyePoints[0].y) * bitmap.height).toDouble(),
                ((visualEyePoints[1].x - visualEyePoints[0].x) * bitmap.width).toDouble(),
            ),
        )
            .toFloat()
        val eyeMidpoint = (points.getValue("LEFT_PUPIL").x + points.getValue("RIGHT_PUPIL").x) / 2f
        val midlineX = eyeMidpoint.coerceIn(0f, 1f)

        DetectionOutcome.Accepted(
            DetectionAnalysis(
                faceConfidence = faceConfidence,
                landmarks = landmarks,
                frontalScore = frontalScore,
                eyeRotationDegrees = eyeRotation,
                midlineX = midlineX,
                detectorFaceCount = detections.size,
            ),
        )
        } catch (_: Exception) {
            DetectionOutcome.Rejected(DetectionRejection.MODEL_ERROR)
        }
    }

    private fun NormalizedLandmark.confidenceOrNull(): Float? =
        if (presence().isPresent && presence().get().isFinite()) presence().get().coerceIn(0f, 1f) else null

    private data class SelectedFaceCrop(val bitmap: Bitmap, val left: Int, val top: Int, val width: Int, val height: Int)

    private fun cropSelectedFace(bitmap: Bitmap, bounds: android.graphics.RectF): SelectedFaceCrop? {
        if (!bounds.left.isFinite() || !bounds.top.isFinite() || !bounds.right.isFinite() || !bounds.bottom.isFinite()) return null
        val faceWidth = bounds.width()
        val faceHeight = bounds.height()
        if (faceWidth < 2f || faceHeight < 2f) return null

        val left = floor(bounds.left - faceWidth * 0.28f).toInt().coerceIn(0, bitmap.width - 1)
        val top = floor(bounds.top - faceHeight * 0.35f).toInt().coerceIn(0, bitmap.height - 1)
        val right = ceil(bounds.right + faceWidth * 0.28f).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = ceil(bounds.bottom + faceHeight * 0.22f).toInt().coerceIn(top + 1, bitmap.height)
        if (right - left < 2 || bottom - top < 2) return null
        val cropWidth = right - left
        val cropHeight = bottom - top
        return SelectedFaceCrop(Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight), left, top, cropWidth, cropHeight)
    }
}

object ImageDecoder {
    fun decode(bytes: ByteArray): ImageInfo? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longestSide = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longestSide / sample > 1600) sample *= 2
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val rotation = try {
            ExifInterface(ByteArrayInputStream(bytes)).rotationDegrees
        } catch (_: Exception) {
            0
        }
        if (rotation == 0) return ImageInfo(decoded, rotation)
        val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return ImageInfo(rotated, rotation)
    }
}

fun List<FacialLandmark>.byName(name: String): FacialLandmark = first { it.name == name }

fun approximateFrontalityMessageScore(score: Float): Boolean = score < 0.65f

fun normalizedDistance(a: FacePoint, b: FacePoint): Float = hypot(a.x - b.x, a.y - b.y)

fun stableCoordinate(value: Float): Float = min(1f, max(0f, value))
