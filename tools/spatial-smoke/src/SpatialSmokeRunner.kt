package com.example.facedesignstudio

import android.app.Instrumentation
import android.graphics.*
import android.os.Bundle
import android.os.SystemClock
import com.example.facedesignstudio.domain.*
import kotlin.math.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Public photo fixture only. No access to the main application's patient database. */
class SpatialSmokeRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = StringBuilder()
        try {
            val face = targetContext.assets.open("grace_hopper.jpg").use { BitmapFactory.decodeStream(it)!! }
            val still = FaceDetectionEngine(targetContext).analyze(face,.5f)
            check(still is DetectionOutcome.Accepted) { "Still-photo analysis failed" }
            val aspect=face.width.toFloat()/face.height
            val proposal=HairlineGeometry.initialProposal(still.analysis,imageAspectRatio=aspect)
            val source=LiveFaceTracker(targetContext,referenceMode=true).use { it.detect(face,0) }
            check(source.issue==LiveTrackingIssue.NONE) { "Reference mesh: ${source.issue}" }
            // Public fixture diagnostics only; never exports a camera frame or customer data.
            File(targetContext.cacheDir,"spatial-fixture.json").writeText(JSONObject().apply {
                put("width",face.width);put("height",face.height)
                put("mesh",JSONArray().apply { source.mesh.forEach { put(JSONArray(listOf(it.x,it.y,it.z))) } })
                put("nodes",JSONArray(HairlineGeometry.nodesToJson(proposal.nodes)))
            }.toString())
            val ref=SpatialFaceAlignment.prepare(source.mesh,face.width,face.height,proposal.nodes,still.analysis.landmarks) {
                report.appendLine("Reference preparation: $it")
            }
            check(ref!=null) { "Forehead preparation failed" }
            report.appendLine("Saved 2D plan -> 3D reference: PASS")
            LiveFaceTracker(targetContext).use { tracker ->
                val gate=LiveCalibrationGate()
                repeat(10) { i ->
                    val start=SystemClock.elapsedRealtimeNanos()
                    val live=tracker.detect(face,1000L+i*100)
                    val inferred=SystemClock.elapsedRealtimeNanos()
                    val result=SpatialFaceAlignment.align(ref,live.mesh,face.width,face.height)
                    val end=SystemClock.elapsedRealtimeNanos()
                    check(!face.isRecycled)
                    report.appendLine("Frame $i: ${result.issue}, rms=${result.residualEyeRatio}, inferenceMs=${(inferred-start)/1e6}, geometryMs=${(end-inferred)/1e6}")
                    check(result.issue==LiveTrackingIssue.NONE)
                    gate.accept(result,1000L+i*100,1010L+i*100)
                }
                check(gate.confirm(1920))
                val blank=Bitmap.createBitmap(face.width,face.height,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
                val lost=tracker.detect(blank,2100)
                check(lost.issue==LiveTrackingIssue.NO_FACE)
                gate.accept(LiveAlignmentResult(lost.issue),2100,2110)
                check(!gate.calibrated)
                blank.recycle()
                report.appendLine("Confirmation + loss: PASS")
            }
            // Known 3D rotations of model output test the projection, not real camera accuracy.
            for (degrees in listOf(-20.0,20.0)) {
                val r=Math.toRadians(degrees)
                val center=MeshPoint(source.mesh[168].x*aspect,source.mesh[168].y,source.mesh[168].z*aspect)
                val turned=source.mesh.map { p ->
                    val v=MeshPoint(p.x*aspect,p.y,p.z*aspect)-center
                    val q=MeshPoint((cos(r)*v.x+sin(r)*v.z).toFloat(),v.y,(-sin(r)*v.x+cos(r)*v.z).toFloat())+center
                    MeshPoint(q.x/aspect,q.y,q.z/aspect)
                }
                val result=SpatialFaceAlignment.align(ref,turned,face.width,face.height)
                check(result.issue==LiveTrackingIssue.NONE) { "Synthetic yaw $degrees: ${result.issue}" }
                report.appendLine("Synthetic yaw $degrees: PASS")
            }
            val pair=Bitmap.createBitmap(face.width*2,face.height,Bitmap.Config.ARGB_8888)
            Canvas(pair).apply { drawBitmap(face,0f,0f,null);drawBitmap(face,face.width.toFloat(),0f,null) }
            LiveFaceTracker(targetContext).use { check(it.detect(pair,1000).issue==LiveTrackingIssue.MULTIPLE_FACES) }
            pair.recycle();face.recycle()
            report.appendLine("Two faces: PASS")
            finish(-1,Bundle().apply { putString("report",report.toString()) })
        } catch(error:Throwable) {
            finish(0,Bundle().apply { putString("report",report.toString());putString("failure",error.stackTraceToString()) })
        }
    }
}
