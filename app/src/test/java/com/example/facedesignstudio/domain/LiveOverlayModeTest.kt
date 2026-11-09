package com.example.facedesignstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveOverlayModeTest {
    private fun misaligned(nose: Boolean): LiveAlignmentResult {
        val source = LiveTestFace.reference
        val current = source.map {
            if (nose && it.name == "NOSE_TIP") it.copy(x = .55f)
            else if (!nose && it.name == "CHIN") it.copy(x = .65f) else it
        }
        return LiveAlignment.align(source, 600, 800, current, 600, 800, LiveTestFace.nodes)
    }

    @Test fun moderatePoseOrFitMismatchShowsOnlyFaintCurrentFrameGuidance() {
        for (nose in listOf(true, false)) {
            val result = misaligned(nose)
            assertEquals(if (nose) LiveTrackingIssue.POSE_MISMATCH else LiveTrackingIssue.POOR_FIT, result.issue)
            assertTrue(result.guidanceAvailable)
            assertEquals(7, result.expectedMarkers.size)
            assertEquals(7, result.actualMarkers.size)
            assertEquals(LiveTestFace.nodes.size, result.nodes.size)
            for (confirmed in listOf(false, true)) {
                assertEquals(LiveOverlayMode.ALIGNING, result.overlayMode(1000, 1100, confirmed))
                assertEquals(LiveOverlayMode.HIDDEN, result.overlayMode(1000, 1351, confirmed))
                assertEquals(LiveOverlayMode.HIDDEN, result.overlayMode(1000, 999, confirmed))
            }
        }
    }

    @Test fun extremeMismatchDoesNotProduceAnApproximateHairline() {
        val source = LiveTestFace.reference
        val result = LiveAlignment.align(source, 600, 800, source.map { it.copy(x = 1f - it.x) }, 600, 800, LiveTestFace.nodes)
        assertFalse(result.guidanceAvailable)
        assertEquals(LiveOverlayMode.HIDDEN, result.overlayMode(1000, 1100, true))
    }

    @Test fun faintGuidanceResetsConfirmationAndNeverCountsAsStable() {
        val gate = LiveCalibrationGate()
        for (i in 0..7) gate.accept(LiveTestFace.result(), 1000L + i * 100, 1000L + i * 100)
        assertTrue(gate.confirm(1700))
        for (i in 0..9) {
            assertFalse(gate.accept(misaligned(true), 1800L + i * 100, 1800L + i * 100))
            assertFalse(gate.ready)
            assertFalse(gate.confirm(1800L + i * 100))
            assertEquals(0, gate.stableFrames)
        }
    }

    @Test fun regainingAlignmentAutomaticallyRestoresTheBrightPreview() {
        assertEquals(LiveOverlayMode.ALIGNING, misaligned(true).overlayMode(1000, 1100, false))
        assertEquals(LiveOverlayMode.PREVIEW, LiveTestFace.result().overlayMode(1200, 1300, false))
        assertEquals(LiveOverlayMode.CONFIRMED, LiveTestFace.result().overlayMode(1400, 1500, true))
        assertEquals(LiveOverlayMode.ALIGNING, misaligned(false).overlayMode(1600, 1700, true))
    }

    @Test fun validUnconfirmedRegistrationShowsAPreviewWithoutWaitingForCalibration() {
        val result = LiveTestFace.result()
        assertEquals(LiveOverlayMode.PREVIEW, result.overlayMode(1000, 1240, false))
        assertEquals(LiveOverlayMode.CONFIRMED, result.overlayMode(1000, 1240, true))
    }

    @Test fun badRegistrationNeverShowsALineEvenAfterConfirmation() {
        for (issue in LiveTrackingIssue.entries.filter { it != LiveTrackingIssue.NONE }) {
            val result = LiveTestFace.result().copy(issue = issue)
            assertEquals(LiveOverlayMode.HIDDEN, result.overlayMode(1000, 1240, false))
            assertEquals(LiveOverlayMode.HIDDEN, result.overlayMode(1000, 1240, true))
        }
        assertEquals(LiveOverlayMode.HIDDEN, LiveTestFace.result().copy(nodes = emptyList()).overlayMode(1000, 1240, true))
    }

    @Test fun staleOrFutureFramesCannotShowPreviewOrConfirmedLine() {
        val result = LiveTestFace.result()
        for (confirmed in listOf(false, true)) {
            assertEquals(LiveOverlayMode.HIDDEN, result.overlayMode(1000, 1351, confirmed))
            assertEquals(LiveOverlayMode.HIDDEN, result.overlayMode(1000, 999, confirmed))
        }
    }
}
