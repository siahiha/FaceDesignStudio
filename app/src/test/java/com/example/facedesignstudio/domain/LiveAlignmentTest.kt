package com.example.facedesignstudio.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

internal object LiveTestFace {
    val reference = listOf(
        FacialLandmark("LEFT_PUPIL", .65f, .46f), FacialLandmark("RIGHT_PUPIL", .35f, .46f),
        FacialLandmark("GLABELLA", .5f, .43f), FacialLandmark("NOSE_TIP", .5f, .58f),
        FacialLandmark("LEFT_TEMPLE", .78f, .49f), FacialLandmark("RIGHT_TEMPLE", .22f, .49f),
        FacialLandmark("CHIN", .5f, .85f),
    )
    val nodes = listOf(
        BezierNode(.22f, .27f, .22f, .27f, .22f, .18f),
        BezierNode(.42f, .24f, .32f, .22f, .47f, .28f),
        BezierNode(.58f, .24f, .53f, .28f, .68f, .22f),
        BezierNode(.78f, .27f, .78f, .18f, .78f, .27f),
    )
    fun result(dx: Float = 0f): LiveAlignmentResult = LiveAlignment.align(reference, 600, 800,
        reference.map { it.copy(x = it.x + dx) }, 600, 800, nodes)
}

class LiveAlignmentTest {
    private val reference = LiveTestFace.reference
    private val nodes = LiveTestFace.nodes

    @Test fun identityPreservesEveryAnchorAndHandle() {
        val result = LiveTestFace.result()
        assertEquals(LiveTrackingIssue.NONE, result.issue)
        assertEquals(0f, result.residualEyeRatio, 1e-5f)
        nodes.zip(result.nodes).forEach { (expected, actual) -> assertNode(expected, actual) }
    }

    @Test fun translationScaleAndRollWorkAcrossPortraitAndLandscapeAspectRatios() {
        for (degrees in listOf(-18f, 0f, 18f)) for (scale in listOf(.8f, 1f, 1.1f)) for (size in listOf(600 to 800, 1280 to 720)) {
            val radians = Math.toRadians(degrees.toDouble()).toFloat()
            val a = cos(radians) * scale; val b = sin(radians) * scale
            val aspect = size.first.toFloat() / size.second
            fun expected(p: FacePoint): FacePoint {
                val x = p.x * .75f - .375f; val y = p.y - .5f
                return FacePoint((a*x - b*y + aspect/2f + .02f)/aspect, b*x + a*y + .52f)
            }
            val live = reference.map { p -> expected(FacePoint(p.x,p.y)).let { p.copy(x=it.x,y=it.y) } }
            val result = LiveAlignment.align(reference,600,800,live,size.first,size.second,nodes)
            assertEquals("rotation=$degrees scale=$scale size=$size", LiveTrackingIssue.NONE,result.issue)
            nodes.zip(result.nodes).forEach { (source, actual) ->
                assertPoint(expected(FacePoint(source.x,source.y)),FacePoint(actual.x,actual.y))
                assertPoint(expected(FacePoint(source.inX,source.inY)),FacePoint(actual.inX,actual.inY))
                assertPoint(expected(FacePoint(source.outX,source.outY)),FacePoint(actual.outX,actual.outY))
            }
        }
    }

    @Test fun mappingControlsPreservesTheWholeCubicNotJustNodes() {
        val result = LiveTestFace.result(.03f)
        for (segment in 0 until nodes.lastIndex) for (step in 0..20) {
            val point = cubic(nodes[segment],nodes[segment+1],step/20f)
            assertPoint(FacePoint(point.x+.03f,point.y),cubic(result.nodes[segment],result.nodes[segment+1],step/20f))
        }
        assertEquals(.22f,nodes.first().x,0f)
    }

    @Test fun missingNonfiniteAndOutOfRangeLandmarksNeverProduceAnOverlay() {
        for (bad in listOf(reference.dropLast(1),reference.map { if(it.name=="CHIN") it.copy(y=Float.NaN) else it },
            reference.map { if(it.name=="CHIN") it.copy(y=1.2f) else it })) {
            assertEquals(LiveTrackingIssue.INVALID_LANDMARKS,LiveAlignment.align(reference,600,800,bad,600,800,nodes).issue)
            assertEquals(LiveTrackingIssue.INVALID_REFERENCE,LiveAlignment.align(bad,600,800,reference,600,800,nodes).issue)
        }
    }

    @Test fun invalidSizesAndNodesAreRejected() {
        assertEquals(LiveTrackingIssue.INVALID_REFERENCE,LiveAlignment.align(reference,0,800,reference,600,800,nodes).issue)
        assertEquals(LiveTrackingIssue.INVALID_LANDMARKS,LiveAlignment.align(reference,600,800,reference,600,0,nodes).issue)
        assertEquals(LiveTrackingIssue.INVALID_REFERENCE,LiveAlignment.align(reference,600,800,reference,600,800,nodes.take(1)).issue)
        val invalid = nodes.toMutableList().apply { this[0]=this[0].copy(outX=Float.POSITIVE_INFINITY) }
        assertEquals(LiveTrackingIssue.INVALID_REFERENCE,LiveAlignment.align(reference,600,800,reference,600,800,invalid).issue)
    }

    @Test fun degenerateEyesAndTinyFacesAreRejected() {
        val collapsed = reference.map { it.copy(x=.5f,y=.5f) }
        assertEquals(LiveTrackingIssue.INVALID_REFERENCE,LiveAlignment.align(collapsed,600,800,reference,600,800,nodes).issue)
        val tiny = reference.map { it.copy(x=.5f+(it.x-.5f)*.1f,y=.5f+(it.y-.5f)*.1f) }
        assertEquals(LiveTrackingIssue.FACE_TOO_SMALL,LiveAlignment.align(reference,600,800,tiny,600,800,nodes).issue)
    }

    @Test fun changedNosePoseIsRejectedInsteadOfWarpingTheHairline() {
        val turned = reference.map { if(it.name=="NOSE_TIP") it.copy(x=.64f) else it }
        assertEquals(LiveTrackingIssue.POSE_MISMATCH,LiveAlignment.align(reference,600,800,turned,600,800,nodes).issue)
        assertEquals(LiveTrackingIssue.REFERENCE_NOT_FRONTAL,LiveAlignment.align(turned,600,800,reference,600,800,nodes).issue)
        val pitched = reference.map { if(it.name=="NOSE_TIP") it.copy(y=.64f) else it }
        assertEquals(LiveTrackingIssue.POSE_MISMATCH,LiveAlignment.align(reference,600,800,pitched,600,800,nodes).issue)
    }

    @Test fun matchingNoseProportionsAreNotMistakenForAnInvalidReference() {
        // Synthetic proportions, including the 0.93 ratio that exceeded the old 0.85 cap.
        // A fixed eye-to-nose ratio cannot distinguish anatomy from head pitch.
        for (ratio in listOf(.1f, .53f, .93f, 1.1f)) {
            val source = reference.map { if (it.name == "NOSE_TIP") it.copy(y = .46f + .225f * ratio) else it }
            val live = source.map { it.copy(x = it.x + .03f) }
            val result = LiveAlignment.align(source, 600, 800, live, 600, 800, nodes)
            assertEquals("nose ratio=$ratio", LiveTrackingIssue.NONE, result.issue)
            nodes.zip(result.nodes).forEach { (expected, actual) ->
                assertNode(expected.copy(x = expected.x + .03f, inX = expected.inX + .03f, outX = expected.outX + .03f), actual)
            }
        }
    }

    @Test fun changedPitchIsStillRejectedForAReferenceWithLargerNoseRatio() {
        val source = reference.map { if (it.name == "NOSE_TIP") it.copy(y = .46f + .225f * .93f) else it }
        for (delta in listOf(-.04f, .04f)) {
            val pitched = source.map { if (it.name == "NOSE_TIP") it.copy(y = it.y + delta) else it }
            assertEquals(LiveTrackingIssue.POSE_MISMATCH,
                LiveAlignment.align(source, 600, 800, pitched, 600, 800, nodes).issue)
        }
    }

    @Test fun poorFitAndMirroredCorrespondencesAreRejected() {
        val wrongChin = reference.map { if(it.name=="CHIN") it.copy(x=.65f) else it }
        assertEquals(LiveTrackingIssue.POOR_FIT,LiveAlignment.align(reference,600,800,wrongChin,600,800,nodes).issue)
        val mirrored = reference.map { it.copy(x=1f-it.x) }
        assertEquals(LiveTrackingIssue.POOR_FIT,LiveAlignment.align(reference,600,800,mirrored,600,800,nodes).issue)
    }

    @Test fun clippedHairlineIsHiddenEvenWithGoodLandmarkFit() {
        val moved = reference.map { it.copy(y=it.y-.2f) }
        val result=LiveAlignment.align(reference,600,800,moved,600,800,nodes)
        assertEquals(LiveTrackingIssue.OUT_OF_FRAME,result.issue)
        assertTrue(result.nodes.isEmpty())
    }

    private fun assertNode(a:BezierNode,b:BezierNode) {
        assertPoint(FacePoint(a.x,a.y),FacePoint(b.x,b.y))
        assertPoint(FacePoint(a.inX,a.inY),FacePoint(b.inX,b.inY))
        assertPoint(FacePoint(a.outX,a.outY),FacePoint(b.outX,b.outY))
    }
    private fun assertPoint(a:FacePoint,b:FacePoint) { assertEquals(a.x,b.x,1e-5f);assertEquals(a.y,b.y,1e-5f) }
    private fun cubic(a:BezierNode,b:BezierNode,t:Float):FacePoint {
        val u=1f-t
        fun value(p:Float,c:Float,d:Float,q:Float)=u*u*u*p+3*u*u*t*c+3*u*t*t*d+t*t*t*q
        return FacePoint(value(a.x,a.outX,b.inX,b.x),value(a.y,a.outY,b.inY,b.y))
    }
}
