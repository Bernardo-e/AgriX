package com.sih.app.core.data

import com.sih.app.core.data.api.ApiException
import com.sih.app.core.data.api.BackendCropRef
import com.sih.app.core.data.api.BackendDiagnosisListResponse
import com.sih.app.core.data.api.BackendDiagnosisRequest
import com.sih.app.core.data.api.BackendDiagnosisResponse
import com.sih.app.core.data.api.BackendDiseaseRef
import com.sih.app.core.data.api.DiagnosisApiClient
import com.sih.app.core.database.DiagnosisDao
import com.sih.app.core.database.DiagnosisEntity
import com.sih.app.core.database.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DiagnosisSyncRepositoryTest {

    private lateinit var fakeDao: FakeDiagnosisDao
    private lateinit var fakeApiClient: FakeDiagnosisApiClient
    private lateinit var repository: DiagnosisRepository
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        fakeDao = FakeDiagnosisDao()
        fakeApiClient = FakeDiagnosisApiClient()
        repository = DiagnosisRepository(
            diagnosisDao = fakeDao,
            apiClient = fakeApiClient,
            externalScope = testScope,
        )
    }

    // A. Online diagnosis: local record created -> POST succeeds -> record becomes SYNCED
    @Test
    fun testOnlineDiagnosisSyncSuccess() = runBlocking {
        fakeApiClient.shouldSucceed = true
        fakeApiClient.mockResponseId = "diag_server_001"

        val localRecord = repository.recordLocalDiagnosis(
            cropId = "tomato",
            cropName = "Tomato",
            diseaseId = 53,
            diseaseName = "tomato bacterial leaf spot",
            confidence = 0.618f,
            diagnosticStatus = "MODERATE_CONFIDENCE",
            source = "on_device_tflite",
            imageId = "img_001.jpg",
        )

        // Local record is immediately created
        assertNotNull(localRecord)
        assertEquals("tomato", localRecord.cropId)
        assertEquals(53, localRecord.diseaseId)

        val updatedRecord = fakeDao.getDiagnosisById(localRecord.id)
        assertNotNull(updatedRecord)
        assertEquals(SyncStatus.SYNCED, updatedRecord?.syncStatus)
        assertEquals("diag_server_001", updatedRecord?.backendDiagnosisId)
        assertNotNull(updatedRecord?.syncedAt)
        assertNull(updatedRecord?.lastSyncError)
    }

    // B. Offline diagnosis: local record created -> network fails -> record remains intact
    @Test
    fun testOfflineDiagnosisPreservesLocalData() = runBlocking {
        fakeApiClient.shouldSucceed = false
        fakeApiClient.mockException = IOException("No internet connection")

        val localRecord = repository.recordLocalDiagnosis(
            cropId = "apple",
            cropName = "Apple",
            diseaseId = 0,
            diseaseName = "apple black rot",
            confidence = 0.92f,
            diagnosticStatus = "CONFIDENT",
        )

        val storedRecord = fakeDao.getDiagnosisById(localRecord.id)
        assertNotNull("Local record MUST be preserved despite network failure", storedRecord)
        assertEquals(SyncStatus.FAILED, storedRecord?.syncStatus)
        assertEquals("apple", storedRecord?.cropId)
        assertEquals(0, storedRecord?.diseaseId)
        assertEquals("No internet connection", storedRecord?.lastSyncError)
        assertEquals(1, storedRecord?.retryCount)
    }

    // C. Retry: PENDING/FAILED record -> network restored -> upload succeeds -> record becomes SYNCED
    @Test
    fun testRetryFailedDiagnosisSucceedsWhenNetworkRestored() = runBlocking {
        // First attempt fails (offline)
        fakeApiClient.shouldSucceed = false
        fakeApiClient.mockException = IOException("Network timeout")

        val localRecord = repository.recordLocalDiagnosis(
            cropId = "rice",
            cropName = "Rice",
            diseaseId = 43,
            diseaseName = "rice blast",
            confidence = 0.77f,
            diagnosticStatus = "CONFIDENT",
        )

        assertEquals(SyncStatus.FAILED, fakeDao.getDiagnosisById(localRecord.id)?.syncStatus)

        // Network restored
        fakeApiClient.shouldSucceed = true
        fakeApiClient.mockResponseId = "diag_server_rice_43"

        val retryResult = repository.retryDiagnosis(localRecord.id)
        assertTrue(retryResult.isSuccess)

        val syncedRecord = fakeDao.getDiagnosisById(localRecord.id)
        assertEquals(SyncStatus.SYNCED, syncedRecord?.syncStatus)
        assertEquals("diag_server_rice_43", syncedRecord?.backendDiagnosisId)
        assertNull(syncedRecord?.lastSyncError)
    }

    // D. Backend 500 failure: POST fails -> local diagnosis remains intact & retryable
    @Test
    fun testBackend500FailurePreservesLocalDiagnosis() = runBlocking {
        fakeApiClient.shouldSucceed = false
        fakeApiClient.mockException = ApiException(500, "Internal Server Error")

        val localRecord = repository.recordLocalDiagnosis(
            cropId = "wheat",
            cropName = "Wheat",
            diseaseId = 60,
            diseaseName = "wheat bacterial leaf streak (black chaff)",
            confidence = 0.82f,
            diagnosticStatus = "CONFIDENT",
        )

        val stored = fakeDao.getDiagnosisById(localRecord.id)
        assertNotNull(stored)
        assertEquals(SyncStatus.FAILED, stored?.syncStatus)
        assertTrue(stored?.lastSyncError?.contains("500") == true)
        assertEquals(1, stored?.retryCount)
    }

    // E. Duplicate protection: Already SYNCED records are not re-uploaded
    @Test
    fun testDuplicateProtectionSkipsAlreadySyncedRecord() = runBlocking {
        fakeApiClient.shouldSucceed = true
        fakeApiClient.mockResponseId = "diag_server_already_synced"

        val localRecord = repository.recordLocalDiagnosis(
            cropId = "corn",
            cropName = "Corn",
            diseaseId = 21,
            diseaseName = "corn gray leaf spot",
            confidence = 0.85f,
            diagnosticStatus = "CONFIDENT",
        )

        assertEquals(1, fakeApiClient.recordCallCount)

        // Calling sync again on already synced record
        val syncedRecord = fakeDao.getDiagnosisById(localRecord.id)!!
        val secondResult = repository.syncDiagnosis(syncedRecord)

        assertTrue(secondResult.isSuccess)
        // Call count MUST NOT increase
        assertEquals("Duplicate sync must not call network", 1, fakeApiClient.recordCallCount)
    }

    // F. Batch sync of multiple pending records
    @Test
    fun testSyncPendingDiagnosesInBatch() = runBlocking {
        fakeApiClient.shouldSucceed = false
        fakeApiClient.mockException = IOException("Offline")

        val diag1 = repository.recordLocalDiagnosis("tomato", "Tomato", 54, "tomato early blight", 0.70f, "MODERATE_CONFIDENCE")
        val diag2 = repository.recordLocalDiagnosis("potato", "Potato", 41, "potato early blight", 0.65f, "MODERATE_CONFIDENCE")

        assertEquals(2, fakeDao.getPendingDiagnoses().size)

        // Network restored
        fakeApiClient.shouldSucceed = true
        fakeApiClient.mockResponseId = "diag_batch_success"

        val count = repository.syncPendingDiagnoses()
        assertEquals(2, count)
        assertEquals(0, fakeDao.getPendingDiagnoses().size)
    }
}

// In-memory fake DAO for testing
class FakeDiagnosisDao : DiagnosisDao {
    private val storage = mutableMapOf<String, DiagnosisEntity>()
    private val flow = MutableStateFlow<List<DiagnosisEntity>>(emptyList())

    override suspend fun insert(diagnosis: DiagnosisEntity): Long {
        storage[diagnosis.id] = diagnosis
        updateFlow()
        return 1L
    }

    override suspend fun update(diagnosis: DiagnosisEntity): Int {
        storage[diagnosis.id] = diagnosis
        updateFlow()
        return 1
    }

    override suspend fun getDiagnosisById(id: String): DiagnosisEntity? {
        return storage[id]
    }

    override fun getAllDiagnosesFlow(): Flow<List<DiagnosisEntity>> {
        return flow.asStateFlow()
    }

    override suspend fun getPendingDiagnoses(): List<DiagnosisEntity> {
        return storage.values.filter { it.syncStatus == SyncStatus.PENDING || it.syncStatus == SyncStatus.FAILED }
    }

    override suspend fun updateSyncStatus(
        id: String,
        syncStatus: SyncStatus,
        backendId: String?,
        syncedAt: Long?,
        error: String?,
        retryCount: Int,
    ): Int {
        val existing = storage[id]
        if (existing != null) {
            storage[id] = existing.copy(
                syncStatus = syncStatus,
                backendDiagnosisId = backendId,
                syncedAt = syncedAt,
                lastSyncError = error,
                retryCount = retryCount,
            )
            updateFlow()
            return 1
        }
        return 0
    }

    override suspend fun deleteAll(): Int {
        val count = storage.size
        storage.clear()
        updateFlow()
        return count
    }

    private fun updateFlow() {
        flow.value = storage.values.sortedByDescending { it.createdAt }
    }
}

// In-memory fake API client for testing
class FakeDiagnosisApiClient : DiagnosisApiClient {
    var shouldSucceed = true
    var mockResponseId = "diag_test_id"
    var mockException: Exception = IOException("Connection refused")
    var recordCallCount = 0

    override suspend fun recordDiagnosis(request: BackendDiagnosisRequest): Result<BackendDiagnosisResponse> {
        recordCallCount++
        return if (shouldSucceed) {
            Result.success(
                BackendDiagnosisResponse(
                    id = mockResponseId,
                    status = "recorded",
                    crop = BackendCropRef(request.cropId, request.cropId.replaceFirstChar { it.uppercase() }),
                    disease = BackendDiseaseRef(request.diseaseId, "Mock Disease"),
                    confidence = request.confidence,
                    diagnosticStatus = request.diagnosticStatus,
                    source = request.source,
                    imageId = request.imageId,
                    createdAt = "2026-08-26T15:00:00Z",
                )
            )
        } else {
            Result.failure(mockException)
        }
    }

    override suspend fun listDiagnoses(cropId: String?, limit: Int): Result<BackendDiagnosisListResponse> {
        return Result.success(BackendDiagnosisListResponse(total = 0, diagnoses = emptyList()))
    }

    override suspend fun getDiagnosis(diagnosisId: String): Result<BackendDiagnosisResponse> {
        return Result.success(
            BackendDiagnosisResponse(
                id = diagnosisId,
                status = "recorded",
                crop = BackendCropRef("tomato", "Tomato"),
                disease = BackendDiseaseRef(53, "tomato bacterial leaf spot"),
                confidence = 0.618f,
                diagnosticStatus = "MODERATE_CONFIDENCE",
                source = "on_device_tflite",
                imageId = null,
                createdAt = "2026-08-26T15:00:00Z",
            )
        )
    }
}
