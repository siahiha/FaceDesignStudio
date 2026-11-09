package com.example.facedesignstudio.domain

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker

data class LiveFaceResult(val issue: LiveTrackingIssue, val landmarks: List<FacialLandmark> = emptyList())

/** Owned and used by one camera analyzer thread, separate from the still-photo engine. */
class LiveFaceTracker(context: Context) : AutoCloseable {
    private val tracker = FaceLandmarker.createFromOptions(context, FaceLandmarker.FaceLandmarkerOptions.builder()
        .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
        .setRunningMode(RunningMode.VIDEO)
        .setNumFaces(2)
        .setMinFaceDetectionConfidence(0.7f)
        .setMinFacePresenceConfidence(0.7f)
        .setMinTrackingConfidence(0.7f)
        .build())
    private var previousTimestamp = -1L

    fun detect(bitmap: Bitmap, timestampMs: Long): LiveFaceResult {
        if (bitmap.isRecycled) return LiveFaceResult(LiveTrackingIssue.MODEL_ERROR)
        // MPImage.close() recycles its backing Bitmap. The camera frame is also rendered by
        // Compose, so inference must own a separate copy rather than destroy the displayed frame.
        val inferenceBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            ?: return LiveFaceResult(LiveTrackingIssue.MODEL_ERROR)
        val image = BitmapImageBuilder(inferenceBitmap).build()
        return try {
            val timestamp = maxOf(timestampMs, previousTimestamp + 1)
            previousTimestamp = timestamp
            val faces = tracker.detectForVideo(image, timestamp).faceLandmarks()
            when {
                faces.isEmpty() -> LiveFaceResult(LiveTrackingIssue.NO_FACE)
                faces.size != 1 -> LiveFaceResult(LiveTrackingIssue.MULTIPLE_FACES)
                else -> {
                    val mesh = faces.single()
                    val points = listOf("LEFT_PUPIL" to 473, "RIGHT_PUPIL" to 468, "GLABELLA" to 168,
                        "NOSE_TIP" to 1, "LEFT_TEMPLE" to 127, "RIGHT_TEMPLE" to 356, "CHIN" to 152).map { (name, index) ->
                        val p = mesh.getOrNull(index) ?: return LiveFaceResult(LiveTrackingIssue.INVALID_LANDMARKS)
                        if (!p.x().isFinite() || !p.y().isFinite() || p.x() !in 0f..1f || p.y() !in 0f..1f) {
                            return LiveFaceResult(LiveTrackingIssue.INVALID_LANDMARKS)
                        }
                        FacialLandmark(name, p.x(), p.y())
                    }
                    LiveFaceResult(LiveTrackingIssue.NONE, points)
                }
            }
        } finally { image.close() }
    }

    override fun close() { tracker.close() }
}
