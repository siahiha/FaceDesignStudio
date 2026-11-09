package com.example.facedesignstudio.domain

import org.junit.Assert.*
import org.junit.Test

class LiveCalibrationGateTest {
    private val result = LiveTestFace.result()
    private fun readyGate():LiveCalibrationGate = LiveCalibrationGate().apply {
        for(i in 0..7) assertFalse(accept(result,1000L+i*100,1010L+i*100))
        assertTrue(ready)
    }

    @Test fun stableFramesRequireExplicitConfirmation() {
        val gate=readyGate()
        assertFalse(gate.calibrated)
        assertTrue(gate.confirm(1720))
        assertTrue(gate.accept(result,1800,1810))
    }

    @Test fun minimumDurationAndFrameCountAreBothRequired() {
        val gate=LiveCalibrationGate()
        for(i in 0..7) gate.accept(result,1000L+i*10,1000L+i*10)
        assertFalse(gate.ready)
        assertFalse(gate.confirm(1070))
        val fewer=LiveCalibrationGate()
        for(i in 0..3) fewer.accept(result,1000L+i*200,1000L+i*200)
        assertFalse(fewer.ready)
    }

    @Test fun lossMultipleFacesAndBadFitRequireNewCalibration() {
        for(issue in listOf(LiveTrackingIssue.NO_FACE,LiveTrackingIssue.MULTIPLE_FACES,LiveTrackingIssue.POSE_MISMATCH,LiveTrackingIssue.POOR_FIT,LiveTrackingIssue.MODEL_ERROR)) {
            val gate=readyGate(); gate.confirm(1720)
            assertFalse(gate.accept(LiveAlignmentResult(issue),1800,1810))
            assertFalse(gate.calibrated)
            assertFalse(gate.accept(result,1900,1910))
            assertFalse(gate.ready)
        }
    }

    @Test fun staleAndFutureFramesAreRejected() {
        for(now in listOf(1799L,2151L)) {
            val gate=readyGate();gate.confirm(1720)
            assertFalse(gate.accept(result,1800,now))
            assertFalse(gate.calibrated)
        }
        val gate=readyGate();gate.confirm(1720)
        assertTrue(gate.accept(result,1800,2150))
    }

    @Test fun duplicateTimestampAndLongGapsInvalidateConfirmation() {
        for(timestamp in listOf(1700L,1600L,2100L)) {
            val gate=readyGate();gate.confirm(1720)
            assertFalse(gate.accept(result,timestamp,timestamp))
            assertFalse(gate.calibrated)
        }
    }

    @Test fun confirmationCannotUseAnExpiredFrame() {
        val gate=readyGate()
        assertFalse(gate.confirm(2051))
        // The displayed frame expired, but the next inference may still be in progress.
        assertTrue(gate.ready)
        gate.refresh(2061)
        assertFalse(gate.ready)
    }

    @Test fun slowContinuousInferenceCanFinishCalibrationDespiteGapsInDisplayFreshness() {
        val gate = LiveCalibrationGate()
        repeat(8) { index ->
            val capturedAt = 1000L + index * 240
            val receivedAt = capturedAt + 240
            // Model the UI's 50 ms timer while each 240 ms inference runs.
            for (tick in capturedAt..receivedAt step 50) gate.refresh(tick)
            assertFalse(gate.accept(result, capturedAt, receivedAt))
        }
        assertEquals(8, gate.stableFrames)
        assertTrue(gate.ready)
        assertTrue(gate.confirm(2930))
    }

    @Test fun expiredDisplayedFrameHidesTheLineWithoutForgettingAnOngoingConfirmedStream() {
        val gate = readyGate()
        assertTrue(gate.confirm(1720))
        assertTrue(gate.accept(result, 1800, 2040))
        gate.refresh(2160)
        assertTrue(gate.calibrated)
        assertEquals(LiveOverlayMode.HIDDEN, result.overlayMode(1800, 2160, gate.calibrated))
        assertTrue(gate.accept(result, 2040, 2280))
        assertEquals(LiveOverlayMode.CONFIRMED, result.overlayMode(2040, 2280, gate.calibrated))
    }

    @Test fun stoppedResultStreamDiscardsCalibration() {
        val gate = readyGate()
        gate.confirm(1720)
        gate.refresh(2061)
        assertFalse(gate.calibrated)
        assertFalse(gate.ready)
        assertFalse(gate.accept(result, 2200, 2210))
    }

    @Test fun smoothMotionKeepsTrackingButAbruptFaceJumpRequiresConfirmation() {
        val gate=readyGate();gate.confirm(1720)
        assertTrue(gate.accept(LiveTestFace.result(.01f),1800,1810))
        assertFalse(gate.accept(LiveTestFace.result(.15f),1900,1910))
        assertFalse(gate.calibrated)
    }

    @Test fun motionDuringCalibrationRestartsTheStabilityWindow() {
        val gate=readyGate()
        gate.accept(LiveTestFace.result(.03f),1800,1810)
        assertFalse(gate.ready)
        assertEquals(1,gate.stableFrames)
        assertFalse(gate.confirm(1810))
    }

    @Test fun resetDiscardsAllSessionState() {
        val gate=readyGate();gate.confirm(1720);gate.reset()
        assertFalse(gate.calibrated);assertFalse(gate.ready);assertEquals(0,gate.stableFrames)
        assertFalse(gate.confirm(1720))
        assertFalse(gate.accept(result,1800,1810))
    }
}
