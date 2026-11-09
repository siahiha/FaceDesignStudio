package com.example.facedesignstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveFrameDisplayTest {
    @Test fun slowInferenceKeepsTheImageButNeverAnExpiredGuide() {
        val result = LiveTestFace.result()
        assertEquals(LiveFrameDisplay.CURRENT, liveFrameDisplay(true, 1000, 1240))
        assertEquals(LiveOverlayMode.PREVIEW, result.overlayMode(1000, 1240, false))
        for (now in listOf(1351L, 1480L, 5000L)) {
            assertEquals(LiveFrameDisplay.WAITING, liveFrameDisplay(true, 1000, now))
            assertEquals(LiveOverlayMode.HIDDEN, result.overlayMode(1000, now, true))
        }
        // Arrival of the next analyzed frame swaps the image without an empty state.
        assertEquals(LiveFrameDisplay.CURRENT, liveFrameDisplay(true, 1240, 1480))
    }

    @Test fun leavingTheCameraOrStartingWithoutAFrameShowsNoOldImage() {
        assertEquals(LiveFrameDisplay.EMPTY, liveFrameDisplay(false, 1000, 1100))
        assertEquals(LiveFrameDisplay.EMPTY, liveFrameDisplay(true, null, 1100))
        assertEquals(LiveFrameDisplay.EMPTY, liveFrameDisplay(false, null, 1100))
    }

    @Test fun freshnessBoundaryAndFutureTimestampDoNotEnableAnOldGuide() {
        assertEquals(LiveFrameDisplay.CURRENT, liveFrameDisplay(true, 1000, 1350))
        assertEquals(LiveFrameDisplay.WAITING, liveFrameDisplay(true, 1000, 1351))
        assertEquals(LiveFrameDisplay.WAITING, liveFrameDisplay(true, 1000, 999))
        assertEquals(LiveOverlayMode.HIDDEN, LiveTestFace.result().overlayMode(1000, 999, true))
    }
}
