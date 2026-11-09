package com.example.facedesignstudio.domain

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class SpatialFaceAlignmentTest {
    private val mesh: List<MeshPoint> = javaClass.getResourceAsStream("/canonical_face_points.txt")!!.bufferedReader().useLines { lines ->
        lines.map { line -> line.split(" ").map(String::toFloat).let { MeshPoint(.5f+it[0]*.025f,.45f-it[1]*.025f,-it[2]*.025f) } }.toList()
    }.let { points -> points + List(10) { i ->
        val a=points[if(i<5) 33 else 263];val b=points[if(i<5) 133 else 362]
        (a+b)*.5f
    } }
    private val nodes = listOf(
        BezierNode(.34f,.30f,.34f,.30f,.37f,.25f),
        BezierNode(.5f,.25f,.43f,.25f,.57f,.25f),
        BezierNode(.66f,.30f,.63f,.25f,.66f,.30f),
    )
    private val saved = SpatialFaceAlignment.markerIndices.mapIndexed { i,k -> FacialLandmark(LiveAlignment.anchorNames[i],mesh[k].x,mesh[k].y) }
    private fun reference() = SpatialFaceAlignment.prepare(mesh,800,800,nodes,saved)!!
    private fun rotated(p:MeshPoint,yaw:Float,pitch:Float,roll:Float=0f,scale:Float=1f):MeshPoint {
        val y=Math.toRadians(yaw.toDouble());val x=Math.toRadians(pitch.toDouble());val r=Math.toRadians(roll.toDouble())
        val v=p-MeshPoint(.5f,.45f,0f)
        val a=MeshPoint((cos(y)*v.x+sin(y)*v.z).toFloat(),v.y,(-sin(y)*v.x+cos(y)*v.z).toFloat())
        val b=MeshPoint(a.x,(cos(x)*a.y-sin(x)*a.z).toFloat(),(sin(x)*a.y+cos(x)*a.z).toFloat())
        return MeshPoint((cos(r)*b.x-sin(r)*b.y).toFloat(),(sin(r)*b.x+cos(r)*b.y).toFloat(),b.z)*scale + MeshPoint(.5f,.45f,.02f)
    }

    @Test fun identityKeepsTheSavedCurveAndEnablesPreview() {
        val result=SpatialFaceAlignment.align(reference(),mesh,800,800)
        assertEquals(LiveTrackingIssue.NONE,result.issue)
        assertEquals(49,result.curve.size)
        assertEquals(nodes.first().x,result.curve.first().x,1e-5f)
        assertEquals(nodes.last().y,result.curve.last().y,1e-5f)
        assertEquals(nodes[1].x,result.curve[24].x,1e-5f)
        assertEquals(LiveOverlayMode.PREVIEW,result.overlayMode(1000,1100,false))
    }

    @Test fun yawPitchScaleAndRollFollowThe3dSurfaceAcrossAspectRatios() {
        val ref=reference()
        for(yaw in listOf(-25f,0f,25f)) for(pitch in listOf(-15f,0f,15f)) for(aspect in listOf(.75f,1f,1.5f)) {
            fun transform(p:MeshPoint):MeshPoint {
                val v=rotated(p,yaw,pitch,8f,.9f)
                return v.copy(x=v.x+(aspect-1f)*.5f)
            }
            val current=mesh.map { transform(it).let { p -> MeshPoint(p.x/aspect,p.y,p.z/aspect) } }
            val result=SpatialFaceAlignment.align(ref,current,(800*aspect).toInt(),800)
            assertEquals("yaw=$yaw pitch=$pitch aspect=$aspect",LiveTrackingIssue.NONE,result.issue)
            ref.curve.zip(result.curve).forEach { (p,q) ->
                val expected=transform(p)
                assertEquals(expected.x/aspect,q.x,1e-4f);assertEquals(expected.y,q.y,1e-4f)
            }
        }
    }

    @Test fun aTurnedReferenceCanRegisterWithADifferentHeadAngle() {
        val source=mesh.map { rotated(it,20f,10f) }
        val shiftedNodes=nodes.map { it.copy(x=it.x-.03f,inX=it.inX-.03f,outX=it.outX-.03f) }
        val landmarks=SpatialFaceAlignment.markerIndices.mapIndexed { i,k -> FacialLandmark(LiveAlignment.anchorNames[i],source[k].x,source[k].y) }
        val ref=SpatialFaceAlignment.prepare(source,800,800,shiftedNodes,landmarks)
        assertNotNull(ref)
        assertEquals(LiveTrackingIssue.NONE,SpatialFaceAlignment.align(ref!!,mesh,800,800).issue)
    }

    @Test fun rotationLimitsAndMirroringHideTheGuide() {
        for(current in listOf(mesh.map { rotated(it,60f,0f) },mesh.map { rotated(it,0f,50f) },mesh.map { it.copy(x=1f-it.x) })) {
            val result=SpatialFaceAlignment.align(reference(),current,800,800)
            assertTrue(result.issue in listOf(LiveTrackingIssue.POSE_OUT_OF_RANGE,LiveTrackingIssue.FACE_TOO_SMALL))
            assertEquals(LiveOverlayMode.HIDDEN,result.overlayMode(1000,1100,true))
        }
    }

    @Test fun tiltedStillReferenceCanBeReconstructedAndFollowANeutralFace() {
        val original=reference()
        for (pitch in listOf(-32f,32f)) {
            val source=mesh.map { rotated(it,0f,pitch) }
            val curve=listOf(0,24,48).map { index ->
                val p=rotated(original.curve[index],0f,pitch)
                BezierNode(p.x,p.y,p.x,p.y,p.x,p.y)
            }
            val markers=SpatialFaceAlignment.markerIndices.mapIndexed { i,k ->
                FacialLandmark(LiveAlignment.anchorNames[i],source[k].x,source[k].y)
            }
            var reason=""
            val ref=SpatialFaceAlignment.prepare(source,800,800,curve,markers) { reason=it }
            assertNotNull("Still pitch $pitch rejected: $reason",ref)
            val neutral=SpatialFaceAlignment.align(ref!!,mesh,800,800)
            assertEquals(LiveTrackingIssue.NONE,neutral.issue)
            assertEquals(LiveOverlayMode.PREVIEW,neutral.overlayMode(1000,1100,false))
            // Reconstruction accepts this still, but the live tracking limit stays narrower.
            assertEquals(LiveTrackingIssue.POSE_OUT_OF_RANGE,SpatialFaceAlignment.align(ref,source,800,800).issue)
        }
    }

    @Test fun extremeReferenceTiltRetainsSpecificFailureWithoutCreatingAGuide() {
        val source=mesh.map { rotated(it,0f,55f) }
        var reason=""
        assertNull(SpatialFaceAlignment.prepare(source,800,800,nodes,saved) { reason=it })
        assertEquals(ReferencePreparationIssue.POSE_OUT_OF_RANGE,ReferencePreparationIssue.fromRejection(reason))
        assertEquals(ReferencePreparationIssue.LANDMARK_MISMATCH,ReferencePreparationIssue.fromRejection("reference_mismatch"))
        assertEquals(ReferencePreparationIssue.CURVE_OUTSIDE_FOREHEAD,ReferencePreparationIssue.fromRejection("curve_outside_forehead"))
    }

    @Test fun malformedMeshAndTinyFaceCannotProduceARegistration() {
        for(current in listOf(emptyList(),mesh.take(100),mesh.mapIndexed { i,p -> if(i==10) p.copy(z=Float.NaN) else p }))
            assertEquals(LiveTrackingIssue.INVALID_LANDMARKS,SpatialFaceAlignment.align(reference(),current,800,800).issue)
        val tiny=mesh.map { rotated(it,0f,0f,scale=.1f) }
        assertEquals(LiveTrackingIssue.FACE_TOO_SMALL,SpatialFaceAlignment.align(reference(),tiny,800,800).issue)
    }

    @Test fun missingOrDifferentSavedReferenceDoesNotAttachTheWrongPlan() {
        assertNull(SpatialFaceAlignment.prepare(mesh,800,800,nodes,emptyList()))
        assertNull(SpatialFaceAlignment.prepare(mesh,800,800,nodes,saved.map { it.copy(x=it.x+.2f) }))
        assertNull(SpatialFaceAlignment.prepare(mesh,0,800,nodes,saved))
        assertNull(SpatialFaceAlignment.prepare(mesh,800,800,nodes.take(1),saved))
    }

    @Test fun aLineFarOutsideTheForeheadIsNotExtrapolated() {
        assertNull(SpatialFaceAlignment.prepare(mesh,800,800,nodes.map { it.copy(y=.01f,inY=.01f,outY=.01f) },saved))
        assertNull(SpatialFaceAlignment.prepare(mesh,800,800,nodes.map { it.copy(outX=Float.NaN) },saved))
    }

    @Test fun clippedProjectionIsHidden() {
        val highNodes=nodes.map { it.copy(y=it.y-.06f,inY=it.inY-.06f,outY=it.outY-.06f) }
        val highReference=SpatialFaceAlignment.prepare(mesh,800,800,highNodes,saved)!!
        val moved=mesh.map { it.copy(y=it.y-.21f) }
        assertEquals(LiveTrackingIssue.OUT_OF_FRAME,SpatialFaceAlignment.align(highReference,moved,800,800).issue)
    }

    @Test fun confirmationSurvivesSmoothHeadRotationButNotLossOrStaleData() {
        val ref=reference();val gate=LiveCalibrationGate()
        for(i in 0..7) gate.accept(SpatialFaceAlignment.align(ref,mesh,800,800),1000L+i*100,1010L+i*100)
        assertTrue(gate.confirm(1720))
        for(i in 1..10) {
            val result=SpatialFaceAlignment.align(ref,mesh.map { rotated(it,i.toFloat(),0f) },800,800)
            assertTrue(gate.accept(result,1700L+i*100,1710L+i*100))
            assertEquals(LiveOverlayMode.CONFIRMED,result.overlayMode(1700L+i*100,1710L+i*100,gate.calibrated))
            assertEquals(LiveOverlayMode.HIDDEN,result.overlayMode(1000,2000,true))
        }
        gate.accept(LiveAlignmentResult(LiveTrackingIssue.NO_FACE),2800,2810)
        assertFalse(gate.calibrated)
    }

    @Test fun nonRigidDistortionCannotBeConfirmed() {
        val bad=mesh.mapIndexed { i,p -> if(i in listOf(93,103,234)) p.copy(x=p.x+.1f) else p }
        val result=SpatialFaceAlignment.align(reference(),bad,800,800)
        assertEquals(LiveTrackingIssue.POOR_FIT,result.issue)
        val gate=LiveCalibrationGate()
        repeat(10) { gate.accept(result,1000L+it*100,1010L+it*100) }
        assertFalse(gate.confirm(1920))
    }

    @Test fun publicPortraitHairlineUsesBoundedTangentExtensionAboveTheMesh() {
        val rows=javaClass.getResourceAsStream("/public_portrait_mesh.txt")!!.bufferedReader().readLines()
        val (width,height,count)=rows.first().split(" ").map(String::toInt)
        val points=rows.drop(1).take(count).map { line -> line.split(" ").map(String::toFloat).let { MeshPoint(it[0],it[1],it[2]) } }
        val curve=rows.drop(1+count).map { line -> line.split(" ").map(String::toFloat).let { BezierNode(it[0],it[1],it[2],it[3],it[4],it[5]) } }
        val markers=SpatialFaceAlignment.markerIndices.mapIndexed { i,k -> FacialLandmark(LiveAlignment.anchorNames[i],points[k].x,points[k].y) }
        val ref=SpatialFaceAlignment.prepare(points,width,height,curve,markers)
        assertNotNull("A valid curve above the mesh must not acquire unbounded quadratic depth",ref)
        val result=SpatialFaceAlignment.align(ref!!,points,width,height)
        assertEquals(LiveTrackingIssue.NONE,result.issue)
        assertEquals(curve.first().x,result.curve.first().x,1e-5f)
        assertEquals(curve.last().y,result.curve.last().y,1e-5f)
    }
}
