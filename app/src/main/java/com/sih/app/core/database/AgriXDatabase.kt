package com.sih.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FarmEntity::class, DiagnosisEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(SyncStatusConverters::class)
abstract class AgriXDatabase : RoomDatabase() {
    abstract fun farmDao(): FarmDao
    abstract fun diagnosisDao(): DiagnosisDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farm_profile ADD COLUMN latitude REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE farm_profile ADD COLUMN longitude REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE farm_profile ADD COLUMN locationAccuracyMeters REAL DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `diagnosis_records` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `cropId` TEXT NOT NULL,
                        `cropName` TEXT NOT NULL,
                        `diseaseId` INTEGER NOT NULL,
                        `diseaseName` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `diagnosticStatus` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `imageId` TEXT,
                        `syncStatus` TEXT NOT NULL,
                        `backendDiagnosisId` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `syncedAt` INTEGER,
                        `lastSyncError` TEXT,
                        `retryCount` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
