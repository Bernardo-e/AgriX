package com.sih.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
}

@Entity(tableName = "diagnosis_records")
data class DiagnosisEntity(
    @PrimaryKey
    val id: String,
    val cropId: String,
    val cropName: String,
    val diseaseId: Int,
    val diseaseName: String,
    val confidence: Float,
    val diagnosticStatus: String,
    val source: String = "on_device_tflite",
    val imageId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val backendDiagnosisId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
    val lastSyncError: String? = null,
    val retryCount: Int = 0,
)
