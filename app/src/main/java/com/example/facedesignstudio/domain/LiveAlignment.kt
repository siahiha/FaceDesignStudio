package com.example.facedesignstudio.domain

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** A 2D similarity in height-normalized pixel space, never an editor/view transform. */
data class FaceRegistration(
    val a: Float,
    val b: Float,
    val tx: Float,
    val ty: Float,
    val referenceAspect: Float,
    val frameAspect: Float,
) {
    fun map(point: FacePoint): FacePoint {
        val x = point.x * referenceAspect
        return FacePoint((a * x - b * point.y + tx) / frameAspect, b * x + a * point.y + ty)
    }

    fun map(node: BezierNode): BezierNode {
        val anchor = map(FacePoint(node.x, node.y))
        val input = map(FacePoint(node.inX, node.inY))
        val output = map(FacePoint(node.outX, node.outY))
        return node.copy(x = anchor.x, y = anchor.y, inX = input.x, inY = input.y, outX = output.x, outY = output.y)
    }
}

enum class LiveTrackingIssue {
    NONE, NO_FACE, MULTIPLE_FACES, INVALID_REFERENCE, REFERENCE_NOT_FRONTAL, INVALID_LANDMARKS,
    FACE_TOO_SMALL, POSE_MISMATCH, POOR_FIT, OUT_OF_FRAME, MODEL_ERROR, STALE_FRAME,
}

data class LiveAlignmentResult(
    val issue: LiveTrackingIssue,
    val registration: FaceRegistration? = null,
    val nodes: List<BezierNode> = emptyList(),
    val expectedMarkers: List<FacePoint> = emptyList(),
    val actualMarkers: List<FacePoint> = emptyList(),
    val residualEyeRatio: Float = 0f,
    val eyeDistance: Float = 0f,
    val frameAspect: Float = 1f,
    val guidanceAvailable: Boolean = false,
)

enum class LiveOverlayMode { HIDDEN, ALIGNING, PREVIEW, CONFIRMED }

/** A valid registration is visible for inspection even before the user confirms it. */
fun LiveAlignmentResult.overlayMode(capturedAt: Long, nowMs: Long, calibrated: Boolean): LiveOverlayMode {
    if (registration == null || nodes.size < 2 ||
        nowMs - capturedAt !in 0..LiveCalibrationGate.MAX_FRAME_AGE_MS
    ) return LiveOverlayMode.HIDDEN
    if (issue != LiveTrackingIssue.NONE) {
        return if (guidanceAvailable && issue in listOf(LiveTrackingIssue.POSE_MISMATCH, LiveTrackingIssue.POOR_FIT))
            LiveOverlayMode.ALIGNING else LiveOverlayMode.HIDDEN
    }
    return if (calibrated) LiveOverlayMode.CONFIRMED else LiveOverlayMode.PREVIEW
}

/** Rejects unsupported views instead of extrapolating a frontal plan onto a turned face. */
object LiveAlignment {
    val anchorNames = listOf("LEFT_PUPIL", "RIGHT_PUPIL", "GLABELLA", "NOSE_TIP", "LEFT_TEMPLE", "RIGHT_TEMPLE", "CHIN")
    const val MAX_RMS_EYE_RATIO = 0.045f
    const val MAX_POINT_EYE_RATIO = 0.09f
    // Broader bounds only for a faint positioning aid, never for confirmation.
    const val MAX_GUIDANCE_RMS_EYE_RATIO = 0.20f
    const val MAX_GUIDANCE_POINT_EYE_RATIO = 0.40f

    fun align(
        reference: List<FacialLandmark>, referenceWidth: Int, referenceHeight: Int,
        current: List<FacialLandmark>, frameWidth: Int, frameHeight: Int,
        nodes: List<BezierNode>,
    ): LiveAlignmentResult {
        fun reject(issue: LiveTrackingIssue) = LiveAlignmentResult(issue)
        if (referenceWidth <= 0 || referenceHeight <= 0 || nodes.size < 2 || nodes.any { node ->
                listOf(node.x, node.y, node.inX, node.inY, node.outX, node.outY).any { !it.isFinite() }
            }) return reject(LiveTrackingIssue.INVALID_REFERENCE)
        if (frameWidth <= 0 || frameHeight <= 0) return reject(LiveTrackingIssue.INVALID_LANDMARKS)
        val source = points(reference) ?: return reject(LiveTrackingIssue.INVALID_REFERENCE)
        val target = points(current) ?: return reject(LiveTrackingIssue.INVALID_LANDMARKS)
        val sourceAspect = referenceWidth.toFloat() / referenceHeight
        val targetAspect = frameWidth.toFloat() / frameHeight
        val p = source.map { FacePoint(it.x * sourceAspect, it.y) }
        val q = target.map { FacePoint(it.x * targetAspect, it.y) }
        val sourceEyes = distance(p[0], p[1])
        val targetEyes = distance(q[0], q[1])
        if (sourceEyes < 0.03f * minOf(sourceAspect, 1f)) return reject(LiveTrackingIssue.INVALID_REFERENCE)
        if (targetEyes < 0.12f * minOf(targetAspect, 1f)) return reject(LiveTrackingIssue.FACE_TOO_SMALL)
        val sourcePose = pose(p, sourceEyes)
        val targetPose = pose(q, targetEyes)
        if (abs(sourcePose.x) > 0.22f) return reject(LiveTrackingIssue.REFERENCE_NOT_FRONTAL)
        // Eye-to-nose height varies with anatomy. Compare it with this person's
        // reference, not a universal range that can reject even an identical frame.
        // Keep the relative pose limits and all seven-point residual checks.
        val poseMismatch = abs(targetPose.x) > 0.22f ||
            abs(sourcePose.x - targetPose.x) > 0.10f || abs(sourcePose.y - targetPose.y) > 0.12f
        val pc = centroid(p)
        val qc = centroid(q)
        var dot = 0f
        var cross = 0f
        var norm = 0f
        p.indices.forEach { i ->
            val px = p[i].x - pc.x; val py = p[i].y - pc.y
            val qx = q[i].x - qc.x; val qy = q[i].y - qc.y
            dot += px * qx + py * qy
            cross += px * qy - py * qx
            norm += px * px + py * py
        }
        if (norm < 1e-8f) return reject(LiveTrackingIssue.INVALID_REFERENCE)
        val a = dot / norm
        val b = cross / norm
        val fit = FaceRegistration(a, b, qc.x - a * pc.x + b * pc.y, qc.y - b * pc.x - a * pc.y, sourceAspect, targetAspect)
        val expected = source.map(fit::map)
        val errors = expected.zip(target) { e, t -> hypot((e.x - t.x) * targetAspect, e.y - t.y) / targetEyes }
        val rms = sqrt(errors.sumOf { (it * it).toDouble() } / errors.size).toFloat()
        val issue = when {
            poseMismatch -> LiveTrackingIssue.POSE_MISMATCH
            !rms.isFinite() || rms > MAX_RMS_EYE_RATIO || errors.any { it > MAX_POINT_EYE_RATIO } -> LiveTrackingIssue.POOR_FIT
            else -> LiveTrackingIssue.NONE
        }
        if (!rms.isFinite() || rms > MAX_GUIDANCE_RMS_EYE_RATIO || errors.any { it > MAX_GUIDANCE_POINT_EYE_RATIO }) return reject(issue)
        val mapped = nodes.map(fit::map)
        // A cubic remains in the convex hull of its controls; do not show a clipped guide.
        if (mapped.any { listOf(it.x, it.y, it.inX, it.inY, it.outX, it.outY).any { value -> value !in 0f..1f } }) {
            return reject(LiveTrackingIssue.OUT_OF_FRAME)
        }
        return LiveAlignmentResult(issue, fit, mapped, expected, target, rms, targetEyes, targetAspect,
            guidanceAvailable = issue != LiveTrackingIssue.NONE)
    }

    private fun points(landmarks: List<FacialLandmark>): List<FacePoint>? {
        val named = landmarks.associateBy { it.name }
        return anchorNames.map { name ->
            val p = named[name] ?: return null
            if (!p.x.isFinite() || !p.y.isFinite() || p.x !in 0f..1f || p.y !in 0f..1f) return null
            FacePoint(p.x, p.y)
        }
    }

    private fun centroid(points: List<FacePoint>) = FacePoint(points.sumOf { it.x.toDouble() }.toFloat() / points.size, points.sumOf { it.y.toDouble() }.toFloat() / points.size)
    private fun distance(a: FacePoint, b: FacePoint) = hypot(a.x - b.x, a.y - b.y)
    private fun pose(points: List<FacePoint>, eyes: Float): FacePoint {
        val dx = (points[1].x - points[0].x) / eyes
        val dy = (points[1].y - points[0].y) / eyes
        val nx = points[3].x - (points[0].x + points[1].x) / 2f
        val ny = points[3].y - (points[0].y + points[1].y) / 2f
        return FacePoint((nx * dx + ny * dy) / eyes, abs(-nx * dy + ny * dx) / eyes)
    }
}

/** Session-only confirmation. Display freshness and interruption of the stream are distinct. */
class LiveCalibrationGate {
    companion object {
        const val MAX_FRAME_AGE_MS = 350L
        const val REQUIRED_STABLE_FRAMES = 8
        const val MIN_STABLE_DURATION_MS = 600L
    }
    var calibrated = false; private set
    var stableFrames = 0; private set
    private var stableSince = 0L
    private var lastTimestamp: Long? = null
    private var lastReceivedAt: Long? = null
    private var lastResult: LiveAlignmentResult? = null
    var ready = false; private set

    fun reset() {
        calibrated = false; stableFrames = 0; ready = false
        lastTimestamp = null; lastReceivedAt = null; lastResult = null; stableSince = 0L
    }

    fun refresh(nowMs: Long) {
        val receivedAt = lastReceivedAt ?: return
        // A 240 ms inference leaves only 110 ms of display freshness. Resetting on capture
        // age would discard every calibration while the next valid frame is still processing.
        // The old overlay stays hidden, but continuity survives until arrivals actually stop.
        if (nowMs - receivedAt !in 0..MAX_FRAME_AGE_MS) reset()
    }

    fun accept(result: LiveAlignmentResult, timestampMs: Long, nowMs: Long): Boolean {
        val previousTime = lastTimestamp
        if (result.issue != LiveTrackingIssue.NONE || result.registration == null ||
            nowMs - timestampMs !in 0..MAX_FRAME_AGE_MS ||
            (previousTime != null && timestampMs <= previousTime)
        ) { reset(); return false }
        if (previousTime != null && timestampMs - previousTime > MAX_FRAME_AGE_MS) reset()
        val previous = lastResult
        val motion = if (previous == null || previous.actualMarkers.size != result.actualMarkers.size) Float.POSITIVE_INFINITY else {
            previous.actualMarkers.zip(result.actualMarkers) { p, q ->
                hypot((p.x - q.x) * result.frameAspect, p.y - q.y) / result.eyeDistance
            }.maxOrNull() ?: Float.POSITIVE_INFINITY
        }
        // Abrupt relocation can indicate a different person entering without an empty frame.
        if (calibrated && motion > 0.35f) reset()
        if (motion <= 0.04f) stableFrames++ else { stableFrames = 1; stableSince = timestampMs }
        lastTimestamp = timestampMs
        lastReceivedAt = nowMs
        lastResult = result
        ready = stableFrames >= REQUIRED_STABLE_FRAMES && timestampMs - stableSince >= MIN_STABLE_DURATION_MS
        return calibrated
    }

    fun confirm(nowMs: Long): Boolean {
        refresh(nowMs)
        val time = lastTimestamp ?: return false
        if (nowMs - time !in 0..MAX_FRAME_AGE_MS) return false
        if (ready) calibrated = true
        return calibrated
    }
}
