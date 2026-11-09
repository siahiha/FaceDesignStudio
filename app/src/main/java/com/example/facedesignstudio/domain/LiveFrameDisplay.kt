package com.example.facedesignstudio.domain

/** Image continuity is independent of the stricter freshness rule for the guide. */
enum class LiveFrameDisplay { EMPTY, CURRENT, WAITING }

fun liveFrameDisplay(running: Boolean, capturedAt: Long?, nowMs: Long): LiveFrameDisplay = when {
    !running || capturedAt == null -> LiveFrameDisplay.EMPTY
    nowMs - capturedAt in 0..LiveCalibrationGate.MAX_FRAME_AGE_MS -> LiveFrameDisplay.CURRENT
    else -> LiveFrameDisplay.WAITING // Retain the image, label the delay, hide its guide.
}
