package com.example.facedesignstudio.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PatientEntity::class,
        PhotoAssetEntity::class,
        HairlinePlanVersionEntity::class,
        AppSettingsEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun patientDao(): PatientDao
    abstract fun photoAssetDao(): PhotoAssetDao
    abstract fun hairlinePlanDao(): HairlinePlanDao
    abstract fun appSettingsDao(): AppSettingsDao
}
