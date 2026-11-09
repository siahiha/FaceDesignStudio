package com.example.facedesignstudio.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID

@Entity(tableName = "patients", indices = [Index(value = ["patientCode"], unique = true)])
data class PatientEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientCode: String,
    val firstName: String,
    val lastName: String,
    val age: Int?,
    val sex: String,
    val phone: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "photo_assets",
    indices = [Index("patientId")],
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PhotoAssetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val patientId: String,
    val source: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val exifRotation: Int,
    val capturedAt: Long,
    val originalBytes: ByteArray,
)

@Entity(
    tableName = "hairline_plan_versions",
    indices = [Index(value = ["planId", "version"], unique = true), Index("patientId"), Index("photoId")],
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["id"],
            childColumns = ["patientId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PhotoAssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HairlinePlanVersionEntity(
    @PrimaryKey val snapshotId: String = UUID.randomUUID().toString(),
    val planId: String,
    val patientId: String,
    val photoId: String,
    val version: Int,
    val faceConfidence: Float,
    val thresholdPercent: Int,
    val frontalScore: Float,
    val landmarksJson: String,
    val nodesJson: String,
    val transformJson: String,
    val modelVersion: String,
    val algorithmVersion: String,
    @ColumnInfo(defaultValue = "'FACIAL_PROPORTION'") val referenceMethod: String = "FACIAL_PROPORTION",
    @ColumnInfo(defaultValue = "'MODERATE'") val hairlineProfile: String = "MODERATE",
    @ColumnInfo(defaultValue = "1") val balancedHandles: Boolean = true,
    @ColumnInfo(defaultValue = "0") val symmetryEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "1.05") val customRiseRatio: Float = 1.05f,
    val savedAt: Long,
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val minimumFaceConfidencePercent: Int?,
)
