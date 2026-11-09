package com.example.facedesignstudio.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Query("SELECT * FROM patients ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PatientEntity>>

    @Query("SELECT * FROM patients WHERE id = :patientId LIMIT 1")
    suspend fun findById(patientId: String): PatientEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(patient: PatientEntity)

    @Update
    suspend fun update(patient: PatientEntity)

    @Query("DELETE FROM patients WHERE id = :patientId")
    suspend fun delete(patientId: String)
}

@Dao
interface PhotoAssetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(photo: PhotoAssetEntity)

    @Query("SELECT * FROM photo_assets WHERE patientId = :patientId ORDER BY capturedAt DESC")
    fun observeForPatient(patientId: String): Flow<List<PhotoAssetEntity>>

    @Query("SELECT * FROM photo_assets WHERE id = :photoId LIMIT 1")
    suspend fun findById(photoId: String): PhotoAssetEntity?

    // Keep the full photo BLOB out of CursorWindow when opening a saved plan.
    @Query("SELECT id, patientId, source, mimeType, width, height, exifRotation, capturedAt, X'' AS originalBytes FROM photo_assets WHERE id = :photoId LIMIT 1")
    suspend fun metadataById(photoId: String): PhotoAssetEntity?

    @Query("SELECT length(originalBytes) FROM photo_assets WHERE id = :photoId")
    suspend fun byteCount(photoId: String): Int?

    @Query("SELECT substr(originalBytes, :start, :size) FROM photo_assets WHERE id = :photoId")
    suspend fun readChunk(photoId: String, start: Int, size: Int): ByteArray?

    @Query("DELETE FROM photo_assets WHERE id = :photoId AND NOT EXISTS (SELECT 1 FROM hairline_plan_versions WHERE photoId = :photoId)")
    suspend fun deleteIfUnused(photoId: String)
}

@Dao
interface HairlinePlanDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshot(plan: HairlinePlanVersionEntity)

    @Query("SELECT * FROM hairline_plan_versions WHERE patientId = :patientId ORDER BY savedAt DESC")
    fun observeForPatient(patientId: String): Flow<List<HairlinePlanVersionEntity>>

    @Query("SELECT COALESCE(MAX(version), 0) FROM hairline_plan_versions WHERE planId = :planId")
    suspend fun latestVersion(planId: String): Int

    @Query("SELECT * FROM hairline_plan_versions WHERE snapshotId = :snapshotId LIMIT 1")
    suspend fun findBySnapshotId(snapshotId: String): HairlinePlanVersionEntity?

    @Query("DELETE FROM hairline_plan_versions WHERE snapshotId = :snapshotId")
    suspend fun deleteSnapshot(snapshotId: String): Int
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun get(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(settings: AppSettingsEntity)

    @Query("UPDATE app_settings SET minimumFaceConfidencePercent = :percent WHERE id = 1")
    suspend fun updateThreshold(percent: Int): Int
}
