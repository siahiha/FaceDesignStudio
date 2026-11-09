package com.example.facedesignstudio

import android.app.Instrumentation
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import com.example.facedesignstudio.domain.*
import kotlin.math.*

/** Explicit opt-in device check. Read-only, on-device; never exports photos, names or coordinates. */
class StoredReferenceSmokeRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report=StringBuilder()
        try {
            val path=targetContext.getDatabasePath("facedesign_studio.db")
            check(path.exists()) { "Main database unavailable" }
            SQLiteDatabase.openDatabase(path.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT p.id,length(p.originalBytes),v.landmarksJson,v.nodesJson FROM hairline_plan_versions v JOIN photo_assets p ON p.id=v.photoId ORDER BY v.savedAt DESC LIMIT 3",null).use { rows ->
                    var count=0
                    var failures=0
                    while(rows.moveToNext()) {
                        count++
                        val photoId=rows.getString(0)
                        val length=rows.getInt(1)
                        check(length>0) { "Reference $count has no photo bytes" }
                        // Read large photos without overflowing Android's CursorWindow.
                        val bytes=ByteArray(length)
                        var offset=0
                        while(offset<length) {
                            db.rawQuery("SELECT substr(originalBytes,?,?) FROM photo_assets WHERE id=?",
                                arrayOf((offset+1).toString(),minOf(131072,length-offset).toString(),photoId)).use { part ->
                                check(part.moveToFirst())
                                val chunk=part.getBlob(0) ?: error("Reference $count photo chunk unavailable")
                                check(chunk.isNotEmpty())
                                chunk.copyInto(bytes,offset);offset+=chunk.size
                            }
                        }
                        val image=ImageDecoder.decode(bytes)?.bitmap ?: error("Saved image cannot be decoded")
                        try {
                            val landmarks=HairlineGeometry.landmarksFromJson(rows.getString(2))
                            val nodes=HairlineGeometry.nodesFromJson(rows.getString(3))
                            val face=LiveFaceTracker(targetContext,referenceMode=true).use { it.detect(image,0) }
                            if (face.issue!=LiveTrackingIssue.NONE) {
                                report.appendLine("Reference $count mesh: ${face.issue}"); failures++; continue
                            }
                            val aspect=image.width.toFloat()/image.height
                            val points=face.mesh.map { MeshPoint(it.x*aspect,it.y,it.z*aspect) }
                            val right=points[263]-points[33]; val down=points[152]-points[10]
                            val normal=MeshPoint(right.y*down.z-right.z*down.y,right.z*down.x-right.x*down.z,right.x*down.y-right.y*down.x)
                            report.appendLine("Reference $count estimated yaw/pitch: ${Math.toDegrees(atan2(normal.x.toDouble(),normal.z.toDouble())).roundToInt()}/${Math.toDegrees(atan2(normal.y.toDouble(),normal.z.toDouble())).roundToInt()} degrees")
                            val reference=SpatialFaceAlignment.prepare(face.mesh,image.width,image.height,nodes,landmarks) {
                                report.appendLine("Reference $count preparation: $it")
                            }
                            if (reference==null) { failures++; continue }
                            val result=SpatialFaceAlignment.align(reference,face.mesh,image.width,image.height)
                            if (result.issue!=LiveTrackingIssue.NONE && result.issue!=LiveTrackingIssue.POSE_OUT_OF_RANGE) {
                                report.appendLine("Reference $count identity: ${result.issue}"); failures++; continue
                            }
                            // Turn the on-device mesh to neutral with a known rigid basis.
                            // A tilted still can exceed the narrower live pose limit; it
                            // must nevertheless transfer to a neutral view successfully.
                            val horizontal=right*(1f/right.length())
                            val facing=normal*(1f/normal.length())
                            val vertical=MeshPoint(facing.y*horizontal.z-facing.z*horizontal.y,
                                facing.z*horizontal.x-facing.x*horizontal.z,facing.x*horizontal.y-facing.y*horizontal.x)
                            val center=(points[473]+points[468])*.5f
                            val neutral=points.map { p ->
                                val v=p-center
                                MeshPoint((v.dot(horizontal)+aspect*.5f)/aspect,v.dot(vertical)+.5f,v.dot(facing)/aspect)
                            }
                            val neutralResult=SpatialFaceAlignment.align(reference,neutral,image.width,image.height)
                            if (neutralResult.issue!=LiveTrackingIssue.NONE) {
                                report.appendLine("Reference $count neutral projection: ${neutralResult.issue}"); failures++; continue
                            }
                            report.appendLine("Reference $count: 3D preparation + synthetic neutral projection PASS (identity: ${result.issue})")
                        } finally { image.recycle() }
                    }
                    check(count>0) { "No saved plans to check" }
                    check(failures==0) { "$failures of $count references unsupported" }
                }
            }
            finish(-1,Bundle().apply { putString("report",report.toString()) })
        } catch(error:Throwable) {
            finish(0,Bundle().apply { putString("report",report.toString());putString("failure",error.message) })
        }
    }
}
