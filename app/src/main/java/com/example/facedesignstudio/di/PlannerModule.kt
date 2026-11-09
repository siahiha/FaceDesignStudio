package com.example.facedesignstudio.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.facedesignstudio.data.local.AppSettingsDao
import com.example.facedesignstudio.data.local.HairlinePlanDao
import com.example.facedesignstudio.data.local.PatientDao
import com.example.facedesignstudio.data.local.PhotoAssetDao
import com.example.facedesignstudio.data.local.PlannerDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlannerModule {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE hairline_plan_versions ADD COLUMN referenceMethod TEXT NOT NULL DEFAULT 'FACIAL_PROPORTION'")
            database.execSQL("ALTER TABLE hairline_plan_versions ADD COLUMN hairlineProfile TEXT NOT NULL DEFAULT 'MODERATE'")
            database.execSQL("ALTER TABLE hairline_plan_versions ADD COLUMN balancedHandles INTEGER NOT NULL DEFAULT 1")
            database.execSQL("ALTER TABLE hairline_plan_versions ADD COLUMN symmetryEnabled INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE hairline_plan_versions ADD COLUMN customRiseRatio REAL NOT NULL DEFAULT 1.05")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlannerDatabase =
        Room.databaseBuilder(context, PlannerDatabase::class.java, "facedesign_studio.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun providePatientDao(database: PlannerDatabase): PatientDao = database.patientDao()
    @Provides fun providePhotoAssetDao(database: PlannerDatabase): PhotoAssetDao = database.photoAssetDao()
    @Provides fun provideHairlinePlanDao(database: PlannerDatabase): HairlinePlanDao = database.hairlinePlanDao()
    @Provides fun provideAppSettingsDao(database: PlannerDatabase): AppSettingsDao = database.appSettingsDao()
}
