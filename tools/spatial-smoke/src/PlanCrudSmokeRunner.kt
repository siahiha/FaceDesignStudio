package com.example.facedesignstudio

import android.app.Instrumentation
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.facedesignstudio.data.PlannerRepository
import com.example.facedesignstudio.data.local.*
import com.example.facedesignstudio.domain.*
import com.example.facedesignstudio.ui.PlannerViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Only synthetic patients in an isolated in-memory Room database. */
class PlanCrudSmokeRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report=StringBuilder()
        try {
            runBlocking {
                val db=Room.inMemoryDatabaseBuilder(targetContext,PlannerDatabase::class.java).build()
                val repo=PlannerRepository(db,db.patientDao(),db.photoAssetDao(),db.hairlinePlanDao(),db.appSettingsDao())
                var vm:PlannerViewModel?=null
                try {
                    val patient=PatientEntity(patientCode="TEST",firstName="Test",lastName="Fixture",age=null,sex="",phone="",notes="",createdAt=0,updatedAt=0)
                    repo.savePatient(patient)
                    val other=patient.copy(id=UUID.randomUUID().toString(),patientCode="OTHER")
                    repo.savePatient(other)
                    // Valid JPEG plus trailing padding exercises the old CursorWindow failure.
                    val jpeg=targetContext.assets.open("grace_hopper.jpg").use { it.readBytes() }
                    val bytes=jpeg + ByteArray(4*1024*1024+37) { (it%251).toByte() }
                    val nodes=listOf(BezierNode(.3f,.3f,.3f,.3f,.4f,.2f),BezierNode(.5f,.2f,.45f,.2f,.55f,.2f),BezierNode(.7f,.3f,.6f,.2f,.7f,.3f))
                    val landmarks=listOf(FacialLandmark("LEFT_PUPIL",.4f,.45f),FacialLandmark("RIGHT_PUPIL",.6f,.45f),FacialLandmark("CHIN",.5f,.8f))
                    suspend fun save(patientId:String)=repo.savePhotoAndPlan(
                        patientId=patientId,source="TEST",mimeType="image/jpeg",width=512,height=600,exifRotation=0,imageBytes=bytes,
                        faceConfidence=.99f,thresholdPercent=90,frontalScore=1f,landmarksJson=HairlineGeometry.landmarksToJson(landmarks),
                        nodesJson=HairlineGeometry.nodesToJson(nodes),transformJson=HairlineGeometry.transformToJson(EditorTransform(),HairlineAppearance()),
                        modelVersion="test",algorithmVersion="test",referenceMethod="FACIAL_PROPORTION",hairlineProfile="CURRENT",
                        balancedHandles=false,symmetryEnabled=false,customRiseRatio=1.05f,planId=null)
                    val original=save(patient.id)
                    val unrelated=save(other.id)
                    check(repo.getPhoto(original.photoId)!!.originalBytes.contentEquals(bytes))
                    report.appendLine("Large saved photo (>4 MiB), byte-exact chunked read: PASS")
                    val model=withContext(Dispatchers.Main) { PlannerViewModel(repo,FaceDetectionEngine(targetContext)) }
                    vm=model
                    val opened=CompletableDeferred<Unit>()
                    withContext(Dispatchers.Main) { model.loadPlan(original.snapshotId) { opened.complete(Unit) } }
                    withTimeout(10000) { opened.await();model.state.first { it.plans.any { p -> p.snapshotId==original.snapshotId } } }
                    check(model.state.value.pendingPhoto!!.originalBytes.contentEquals(bytes))
                    check(model.state.value.nodes==nodes)
                    withContext(Dispatchers.Main) { model.beginEditorChange(); model.moveNode(1,.5f,.28f) }
                    check(!model.state.value.saveComplete)
                    withContext(Dispatchers.Main) { model.savePlan() }
                    withTimeout(10000) { model.state.first { !it.busy && it.saveComplete && it.savedPlanVersion==2 } }
                    val versions=repo.observePlans(patient.id).first { it.size==2 }
                    val edited=versions.first { it.version==2 }
                    check(edited.nodesJson!=original.nodesJson)
                    check(repo.getPlanSnapshot(original.snapshotId)!!.nodesJson==original.nodesJson)
                    report.appendLine("ViewModel open, edit and save next version; original retained: PASS")
                    val shared=edited.copy(snapshotId=UUID.randomUUID().toString(),version=3)
                    db.hairlinePlanDao().insertSnapshot(shared)
                    withTimeout(10000) { model.state.first { it.plans.any { p -> p.snapshotId==edited.snapshotId } } }
                    withContext(Dispatchers.Main) { model.deletePlan(edited.snapshotId) }
                    withTimeout(10000) { model.state.first { !it.busy && it.messageKey=="plan_deleted" } }
                    check(repo.getPlanSnapshot(edited.snapshotId)==null)
                    check(model.state.value.pendingPhoto==null)
                    check(repo.getPhoto(edited.photoId)!=null) // Another snapshot still owns it.
                    check(repo.deletePlanSnapshot(shared.snapshotId))
                    check(repo.getPhoto(edited.photoId)==null)
                    check(!repo.deletePlanSnapshot(shared.snapshotId))
                    check(repo.getPlanSnapshot(original.snapshotId)!=null)
                    check(repo.getPhoto(original.photoId)!=null)
                    check(repo.getPlanSnapshot(unrelated.snapshotId)!=null)
                    check(repo.getPhoto(unrelated.photoId)!=null)
                    report.appendLine("Delete selected version, clear editor, keep shared photos and other plans; orphan cleanup: PASS")
                    val failedNavigation=CompletableDeferred<Unit>()
                    withContext(Dispatchers.Main) { model.loadPlan("missing") { failedNavigation.complete(Unit) } }
                    withTimeout(10000) { model.state.first { !it.busy && it.messageKey=="plan_load_failed" } }
                    check(!failedNavigation.isCompleted)
                    report.appendLine("Failed load stays out of editor: PASS")
                } finally {
                    withContext(Dispatchers.Main) { vm?.viewModelScope?.cancel() }
                    db.close()
                }
            }
            finish(-1,Bundle().apply { putString("report",report.toString()) })
        } catch(error:Throwable) {
            finish(0,Bundle().apply { putString("report",report.toString());putString("failure",error.stackTraceToString()) })
        }
    }
}
