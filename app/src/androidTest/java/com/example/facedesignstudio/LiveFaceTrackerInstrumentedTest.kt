package com.example.facedesignstudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facedesignstudio.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveFaceTrackerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun portrait(): Bitmap = instrumentation.context.assets.open("grace_hopper.jpg").use { BitmapFactory.decodeStream(it)!! }

    @Test fun blankFrameNeverProducesFaceLandmarks() {
        val blank = Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        try { LiveFaceTracker(context).use { tracker ->
            assertEquals(LiveTrackingIssue.NO_FACE,tracker.detect(blank,1000).issue)
            assertFalse("Inference must not recycle the displayed camera frame",blank.isRecycled)
        } } finally { blank.recycle() }
    }

    @Test fun twoFacesAreRejectedInsteadOfSelectingOne() {
        val face = portrait()
        val pair = Bitmap.createBitmap(face.width*2,face.height,Bitmap.Config.ARGB_8888)
        Canvas(pair).apply { drawBitmap(face,0f,0f,null);drawBitmap(face,face.width.toFloat(),0f,null) }
        try { LiveFaceTracker(context).use { tracker ->
            assertEquals(LiveTrackingIssue.MULTIPLE_FACES,tracker.detect(pair,1000).issue)
        } } finally { face.recycle();pair.recycle() }
    }

    @Test fun storedPhotoLandmarksRegisterAgainstVideoModeAndLossClearsTheGuide() {
        val face=portrait()
        val blank=Bitmap.createBitmap(face.width,face.height,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        try {
            val detection=FaceDetectionEngine(context).analyze(face,.5f)
            assertTrue("Still-photo reference detection failed: $detection",detection is DetectionOutcome.Accepted)
            val reference=(detection as DetectionOutcome.Accepted).analysis
            val proposal=HairlineGeometry.initialProposal(reference,imageAspectRatio=face.width.toFloat()/face.height)
            LiveFaceTracker(context).use { tracker ->
                val gate=LiveCalibrationGate()
                val timings=mutableListOf<Long>()
                repeat(10) { index ->
                    val start=SystemClock.uptimeMillis()
                    val live=tracker.detect(face,1000L+index*100)
                    assertFalse("Input bitmap must remain valid for rendering and reuse",face.isRecycled)
                    timings.add(SystemClock.uptimeMillis()-start)
                    assertEquals(LiveTrackingIssue.NONE,live.issue)
                    val alignment=LiveAlignment.align(reference.landmarks,face.width,face.height,live.landmarks,face.width,face.height,proposal.nodes)
                    assertEquals("Alignment error: $alignment",LiveTrackingIssue.NONE,alignment.issue)
                    gate.accept(alignment,1000L+index*100,1010L+index*100)
                }
                assertTrue(gate.confirm(1920))
                val lost=tracker.detect(blank,2100)
                assertEquals(LiveTrackingIssue.NO_FACE,lost.issue)
                assertFalse(gate.accept(LiveAlignmentResult(lost.issue),2100,2110))
                assertFalse(gate.calibrated)
                Log.i("LiveTrackingTest","Fixture inference milliseconds: $timings")
            }
        } finally { face.recycle();blank.recycle() }
    }
}
