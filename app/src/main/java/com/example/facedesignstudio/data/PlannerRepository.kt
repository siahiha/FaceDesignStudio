package com.example.facedesignstudio.data

import androidx.room.withTransaction
import com.example.facedesignstudio.data.local.AppSettingsDao
import com.example.facedesignstudio.data.local.AppSettingsEntity
import com.example.facedesignstudio.data.local.HairlinePlanDao
import com.example.facedesignstudio.data.local.HairlinePlanVersionEntity
import com.example.facedesignstudio.data.local.PatientDao
import com.example.facedesignstudio.data.local.PatientEntity
import com.example.facedesignstudio.data.local.PhotoAssetDao
import com.example.facedesignstudio.data.local.PhotoAssetEntity
import com.example.facedesignstudio.data.local.PlannerDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlannerRepository @Inject constructor(
    private val database: PlannerDatabase,
    private val patientDao: PatientDao,
    private val photoAssetDao: PhotoAssetDao,
    private val hairlinePlanDao: HairlinePlanDao,
    private val appSettingsDao: AppSettingsDao,
) {
    companion object {
        const val DEFAULT_THRESHOLD_PERCENT = 90
    }

    fun observePatients(): Flow<List<PatientEntity>> = patientDao.observeAll()
    fun observeSettings(): Flow<AppSettingsEntity?> = appSettingsDao.observe()
    fun observePlans(patientId: String): Flow<List<HairlinePlanVersionEntity>> =
        hairlinePlanDao.observeForPatient(patientId)

    suspend fun getPatient(patientId: String): PatientEntity? = patientDao.findById(patientId)

    suspend fun getPlanSnapshot(snapshotId: String): HairlinePlanVersionEntity? =
        hairlinePlanDao.findBySnapshotId(snapshotId)

    suspend fun getPhoto(photoId: String): PhotoAssetEntity? = database.withTransaction {
        val metadata = photoAssetDao.metadataById(photoId) ?: return@withTransaction null
        val length = photoAssetDao.byteCount(photoId) ?: error("Photo bytes missing")
        check(length > 0) { "Photo is empty" }
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val size = minOf(128 * 1024, length - offset)
            val chunk = photoAssetDao.readChunk(photoId, offset + 1, size) ?: error("Photo chunk missing")
            check(chunk.size == size) { "Photo chunk incomplete" }
            chunk.copyInto(bytes, offset)
            offset += size
        }
        metadata.copy(originalBytes = bytes)
    }

    suspend fun deletePlanSnapshot(snapshotId: String): Boolean = database.withTransaction {
        val snapshot = hairlinePlanDao.findBySnapshotId(snapshotId) ?: return@withTransaction false
        val deleted = hairlinePlanDao.deleteSnapshot(snapshotId) > 0
        if (deleted) photoAssetDao.deleteIfUnused(snapshot.photoId)
        deleted
    }

    suspend fun savePatient(patient: PatientEntity) {
        val existing = patientDao.findById(patient.id)
        if (existing == null) patientDao.insert(patient) else patientDao.update(patient)
    }

    suspend fun deletePatient(patientId: String) = patientDao.delete(patientId)

    suspend fun ensureDefaultThreshold() {
        val currentThreshold = appSettingsDao.get()?.minimumFaceConfidencePercent
        if (currentThreshold == null) {
            appSettingsDao.save(AppSettingsEntity(minimumFaceConfidencePercent = DEFAULT_THRESHOLD_PERCENT))
        }
    }

    suspend fun saveThreshold(percent: Int) {
        val safePercent = percent.coerceIn(1, 99)
        val updated = appSettingsDao.updateThreshold(safePercent)
        if (updated == 0) appSettingsDao.save(AppSettingsEntity(minimumFaceConfidencePercent = safePercent))
    }

    suspend fun thresholdPercent(): Int = appSettingsDao.get()?.minimumFaceConfidencePercent ?: DEFAULT_THRESHOLD_PERCENT

    suspend fun savePhotoAndPlan(
        patientId: String,
        source: String,
        mimeType: String,
        width: Int,
        height: Int,
        exifRotation: Int,
        imageBytes: ByteArray,
        faceConfidence: Float,
        thresholdPercent: Int,
        frontalScore: Float,
        landmarksJson: String,
        nodesJson: String,
        transformJson: String,
        modelVersion: String,
        algorithmVersion: String,
        referenceMethod: String,
        hairlineProfile: String,
        balancedHandles: Boolean,
        symmetryEnabled: Boolean,
        customRiseRatio: Float,
        planId: String?,
    ): HairlinePlanVersionEntity = database.withTransaction {
        val photo = PhotoAssetEntity(
            patientId = patientId,
            source = source,
            mimeType = mimeType,
            width = width,
            height = height,
            exifRotation = exifRotation,
            capturedAt = System.currentTimeMillis(),
            originalBytes = imageBytes,
        )
        photoAssetDao.insert(photo)
        val stablePlanId = planId ?: UUID.randomUUID().toString()
        val snapshot = HairlinePlanVersionEntity(
            planId = stablePlanId,
            patientId = patientId,
            photoId = photo.id,
            version = hairlinePlanDao.latestVersion(stablePlanId) + 1,
            faceConfidence = faceConfidence,
            thresholdPercent = thresholdPercent,
            frontalScore = frontalScore,
            landmarksJson = landmarksJson,
            nodesJson = nodesJson,
            transformJson = transformJson,
            modelVersion = modelVersion,
            algorithmVersion = algorithmVersion,
            referenceMethod = referenceMethod,
            hairlineProfile = hairlineProfile,
            balancedHandles = balancedHandles,
            symmetryEnabled = symmetryEnabled,
            customRiseRatio = customRiseRatio,
            savedAt = System.currentTimeMillis(),
        )
        hairlinePlanDao.insertSnapshot(snapshot)
        snapshot
    }
}
