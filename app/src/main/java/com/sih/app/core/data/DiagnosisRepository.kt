package com.sih.app.core.data

import android.util.Log
import com.sih.app.core.data.api.BackendDiagnosisRequest
import com.sih.app.core.data.api.DiagnosisApiClient
import com.sih.app.core.database.DiagnosisDao
import com.sih.app.core.database.DiagnosisEntity
import com.sih.app.core.database.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class DiagnosisRepository(
    private val diagnosisDao: DiagnosisDao,
    private val apiClient: DiagnosisApiClient,
    private val externalScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    companion object {
        private const val TAG = "DiagnosisRepository"

        private fun logD(tag: String, msg: String) {
            try {
                Log.d(tag, msg)
            } catch (t: Throwable) {
                // JVM test fallback
            }
        }

        private fun logW(tag: String, msg: String) {
            try {
                Log.w(tag, msg)
            } catch (t: Throwable) {
                // JVM test fallback
            }
        }
    }

    /**
     * Expose reactive Flow of all local diagnoses for UI screens.
     */
    fun getAllDiagnosesFlow(): Flow<List<DiagnosisEntity>> {
        return diagnosisDao.getAllDiagnosesFlow()
    }

    fun getLatestDiagnosisFlow(): Flow<DiagnosisEntity?> {
        return diagnosisDao.getLatestDiagnosisFlow()
    }

    suspend fun getLatestDiagnosis(): DiagnosisEntity? = withContext(Dispatchers.IO) {
        diagnosisDao.getLatestDiagnosis()
    }

    /**
     * Retrieve a specific diagnosis by local ID.
     */
    suspend fun getDiagnosisById(id: String): DiagnosisEntity? = withContext(Dispatchers.IO) {
        diagnosisDao.getDiagnosisById(id)
    }

    /**
     * Record a newly completed on-device TFLite diagnosis locally first (offline-first),
     * and asynchronously trigger a non-blocking background sync attempt.
     */
    suspend fun recordLocalDiagnosis(
        cropId: String,
        cropName: String,
        diseaseId: Int,
        diseaseName: String,
        confidence: Float,
        diagnosticStatus: String,
        source: String = "on_device_tflite",
        imageId: String? = null,
    ): DiagnosisEntity = withContext(Dispatchers.IO) {
        val uniqueLocalId = "diag_local_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val nowEpoch = System.currentTimeMillis()

        val entity = DiagnosisEntity(
            id = uniqueLocalId,
            cropId = cropId.lowercase().trim(),
            cropName = cropName,
            diseaseId = diseaseId,
            diseaseName = diseaseName,
            confidence = confidence,
            diagnosticStatus = diagnosticStatus,
            source = source,
            imageId = imageId,
            syncStatus = SyncStatus.PENDING,
            backendDiagnosisId = null,
            createdAt = nowEpoch,
            syncedAt = null,
            lastSyncError = null,
            retryCount = 0,
        )

        // 1. Always persist locally first
        diagnosisDao.insert(entity)
        logD(TAG, "Local diagnosis recorded: $uniqueLocalId ($diseaseName) with status PENDING")

        // 2. Trigger asynchronous, non-blocking sync attempt
        externalScope.launch {
            trySyncSingleDiagnosis(entity)
        }

        entity
    }

    /**
     * Synchronize a specific diagnosis record to the backend companion.
     */
    suspend fun syncDiagnosis(diagnosis: DiagnosisEntity): Result<DiagnosisEntity> = withContext(Dispatchers.IO) {
        trySyncSingleDiagnosis(diagnosis)
    }

    /**
     * Retry synchronization for a specific diagnosis ID.
     */
    suspend fun retryDiagnosis(id: String): Result<DiagnosisEntity> = withContext(Dispatchers.IO) {
        val entity = diagnosisDao.getDiagnosisById(id)
            ?: return@withContext Result.failure(IllegalArgumentException("Diagnosis $id not found"))
        trySyncSingleDiagnosis(entity)
    }

    /**
     * Query all pending or failed local diagnosis records and attempt to upload them.
     */
    suspend fun syncPendingDiagnoses(): Int = withContext(Dispatchers.IO) {
        val pendingList = diagnosisDao.getPendingDiagnoses()
        if (pendingList.isEmpty()) {
            return@withContext 0
        }

        var successCount = 0
        for (diagnosis in pendingList) {
            val result = trySyncSingleDiagnosis(diagnosis)
            if (result.isSuccess) {
                successCount++
            }
        }
        successCount
    }

    private suspend fun trySyncSingleDiagnosis(diagnosis: DiagnosisEntity): Result<DiagnosisEntity> {
        // Duplicate Protection: If already synced with a backend ID, do not re-upload.
        if (diagnosis.syncStatus == SyncStatus.SYNCED && !diagnosis.backendDiagnosisId.isNullOrBlank()) {
            logD(TAG, "Diagnosis ${diagnosis.id} is already SYNCED (${diagnosis.backendDiagnosisId}). Skipping.")
            return Result.success(diagnosis)
        }

        // Mark as SYNCING
        diagnosisDao.updateSyncStatus(
            id = diagnosis.id,
            syncStatus = SyncStatus.SYNCING,
            backendId = diagnosis.backendDiagnosisId,
            syncedAt = diagnosis.syncedAt,
            error = null,
            retryCount = diagnosis.retryCount,
        )

        val isoTimestamp = formatIso8601(diagnosis.createdAt)

        val request = BackendDiagnosisRequest(
            cropId = diagnosis.cropId,
            diseaseId = diagnosis.diseaseId,
            confidence = diagnosis.confidence,
            diagnosticStatus = diagnosis.diagnosticStatus,
            source = diagnosis.source,
            imageId = diagnosis.imageId ?: diagnosis.id,
            createdAt = isoTimestamp,
        )

        val apiResult = apiClient.recordDiagnosis(request)

        return if (apiResult.isSuccess) {
            val response = apiResult.getOrThrow()
            val syncedEpoch = System.currentTimeMillis()

            diagnosisDao.updateSyncStatus(
                id = diagnosis.id,
                syncStatus = SyncStatus.SYNCED,
                backendId = response.id,
                syncedAt = syncedEpoch,
                error = null,
                retryCount = diagnosis.retryCount,
            )

            logD(TAG, "Diagnosis ${diagnosis.id} synced successfully -> Backend ID: ${response.id}")

            val updatedEntity = diagnosis.copy(
                syncStatus = SyncStatus.SYNCED,
                backendDiagnosisId = response.id,
                syncedAt = syncedEpoch,
                lastSyncError = null,
            )
            Result.success(updatedEntity)
        } else {
            val exception = apiResult.exceptionOrNull()
            val errorMessage = exception?.message ?: "Unknown sync error"
            val newRetryCount = diagnosis.retryCount + 1

            diagnosisDao.updateSyncStatus(
                id = diagnosis.id,
                syncStatus = SyncStatus.FAILED,
                backendId = null,
                syncedAt = null,
                error = errorMessage,
                retryCount = newRetryCount,
            )

            logW(TAG, "Diagnosis ${diagnosis.id} sync failed: $errorMessage (Attempt $newRetryCount)")

            val updatedEntity = diagnosis.copy(
                syncStatus = SyncStatus.FAILED,
                lastSyncError = errorMessage,
                retryCount = newRetryCount,
            )
            Result.failure(exception ?: Exception(errorMessage))
        }
    }

    private fun formatIso8601(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(epochMs))
    }
}
