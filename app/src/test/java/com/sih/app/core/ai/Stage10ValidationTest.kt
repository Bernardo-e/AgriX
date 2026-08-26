package com.sih.app.core.ai

import com.sih.app.core.data.DiagnosisRepository
import com.sih.app.core.data.FakeDiagnosisApiClient
import com.sih.app.core.data.FakeDiagnosisDao
import com.sih.app.core.database.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class Stage10ValidationTest {

    private lateinit var advisoryRepository: AdvisoryRepository
    private lateinit var fakeDao: FakeDiagnosisDao
    private lateinit var fakeApiClient: FakeDiagnosisApiClient
    private lateinit var diagnosisRepository: DiagnosisRepository
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        val assetFile = sequenceOf(
            File("src/main/assets/disease_advisories.json"),
            File("app/src/main/assets/disease_advisories.json"),
            File("D:/SIH/app/src/main/assets/disease_advisories.json"),
        ).firstOrNull { it.exists() } ?: throw IllegalStateException("disease_advisories.json asset not found")

        advisoryRepository = AdvisoryRepository(initialJsonString = assetFile.readText())

        fakeDao = FakeDiagnosisDao()
        fakeApiClient = FakeDiagnosisApiClient()
        diagnosisRepository = DiagnosisRepository(
            diagnosisDao = fakeDao,
            apiClient = fakeApiClient,
            externalScope = testScope,
        )
    }

    // 1. CROP-AWARE REASONING VALIDATION
    @Test
    fun testCropAwareReasoning_CompatibleDisease() {
        // Crop: Tomato, Disease: Tomato Early Blight (54)
        val diagResult = DiagnosticResult(
            primaryPrediction = DiseasePrediction(
                diseaseName = "tomato early blight",
                confidence = 0.82f,
                classId = 54,
                crop = "Tomato",
                rank = 1,
            ),
            topPredictions = listOf(
                DiseasePrediction("tomato early blight", 0.82f, 54, "Tomato", 1),
                DiseasePrediction("tomato late blight", 0.12f, 56, "Tomato", 2),
            ),
            cropCompatiblePredictions = listOf(
                DiseasePrediction("tomato early blight", 0.82f, 54, "Tomato", 1),
                DiseasePrediction("tomato late blight", 0.12f, 56, "Tomato", 2),
            ),
            status = DiagnosticStatus.CONFIDENT,
            message = "High confidence identification",
            selectedCrop = "Tomato",
            confidenceBand = ConfidenceBand.HIGH,
        )

        val advisory = advisoryRepository.getAdvisoryForDiagnosticResult(diagResult)
        assertTrue(advisory is AdvisoryResult.Available)
        val available = advisory as AdvisoryResult.Available
        assertEquals("tomato", available.presentation.cropId)
        assertEquals(54, available.presentation.diseaseId)
        assertEquals(AdvisoryConfidenceLevel.CONFIDENT, available.presentation.confidenceLevel)
    }

    @Test
    fun testCropAwareReasoning_MismatchedCropYieldsUncertainOrUnavailable() {
        // Selected Crop: Apple, but raw prediction is Tomato Bacterial Spot (53)
        // Crop-aware engine marks status as UNKNOWN_OR_UNCERTAIN or filters incompatible predictions
        val diagResult = DiagnosticResult(
            primaryPrediction = null,
            topPredictions = listOf(
                DiseasePrediction("tomato bacterial leaf spot", 0.65f, 53, "Tomato", 1),
            ),
            cropCompatiblePredictions = emptyList(), // Filtered out by crop-aware engine
            status = DiagnosticStatus.UNKNOWN_OR_UNCERTAIN,
            message = "No compatible diseases found for selected crop 'Apple'.",
            selectedCrop = "Apple",
            confidenceBand = ConfidenceBand.UNCERTAIN,
        )

        val advisory = advisoryRepository.getAdvisoryForDiagnosticResult(diagResult)
        assertTrue("Mismatched crop prediction MUST yield Uncertain guidance", advisory is AdvisoryResult.Uncertain)
        val uncertain = advisory as AdvisoryResult.Uncertain
        assertTrue(uncertain.message.contains("could not confidently identify"))
    }

    // 2. CONFIDENCE GATING VALIDATION (4 STATES)
    @Test
    fun testConfidenceGating_AllFourStates() {
        // A. CONFIDENT (>= 0.75)
        val confident = DiagnosticResult(
            primaryPrediction = DiseasePrediction("rice blast", 0.85f, 43, "Rice", 1),
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.CONFIDENT,
            message = "Confident",
            selectedCrop = "Rice",
            confidenceBand = ConfidenceBand.HIGH,
        )
        val confAdv = advisoryRepository.getAdvisoryForDiagnosticResult(confident) as AdvisoryResult.Available
        assertEquals(AdvisoryConfidenceLevel.CONFIDENT, confAdv.presentation.confidenceLevel)
        assertNull(confAdv.presentation.noticeMessage)
        assertTrue(confAdv.presentation.isActionable)
        assertTrue(confAdv.presentation.immediateActions.isNotEmpty())

        // B. MODERATE (>= 0.50 and < 0.75)
        val moderate = DiagnosticResult(
            primaryPrediction = DiseasePrediction("rice blast", 0.62f, 43, "Rice", 1),
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.MODERATE_CONFIDENCE,
            message = "Moderate",
            selectedCrop = "Rice",
            confidenceBand = ConfidenceBand.MEDIUM,
        )
        val modAdv = advisoryRepository.getAdvisoryForDiagnosticResult(moderate) as AdvisoryResult.Available
        assertEquals(AdvisoryConfidenceLevel.MODERATE, modAdv.presentation.confidenceLevel)
        assertNotNull("Moderate confidence MUST have verification warning", modAdv.presentation.noticeMessage)
        assertTrue(modAdv.presentation.isActionable)
        assertTrue(modAdv.presentation.immediateActions.isNotEmpty())

        // C. LOW (>= 0.35 and < 0.50)
        val low = DiagnosticResult(
            primaryPrediction = DiseasePrediction("rice blast", 0.42f, 43, "Rice", 1),
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.LOW_CONFIDENCE,
            message = "Low",
            selectedCrop = "Rice",
            confidenceBand = ConfidenceBand.LOW,
        )
        val lowAdv = advisoryRepository.getAdvisoryForDiagnosticResult(low) as AdvisoryResult.Available
        assertEquals(AdvisoryConfidenceLevel.LOW, lowAdv.presentation.confidenceLevel)
        assertNotNull(lowAdv.presentation.noticeMessage)
        assertFalse(lowAdv.presentation.isActionable)
        assertTrue("Low confidence MUST withhold immediate intervention actions", lowAdv.presentation.immediateActions.isEmpty())

        // D. UNKNOWN_OR_UNCERTAIN (< 0.35)
        val uncertain = DiagnosticResult(
            primaryPrediction = null,
            topPredictions = emptyList(),
            cropCompatiblePredictions = emptyList(),
            status = DiagnosticStatus.UNKNOWN_OR_UNCERTAIN,
            message = "Uncertain",
            selectedCrop = "Rice",
            confidenceBand = ConfidenceBand.UNCERTAIN,
        )
        val uncertAdv = advisoryRepository.getAdvisoryForDiagnosticResult(uncertain)
        assertTrue("Uncertain MUST yield AdvisoryResult.Uncertain", uncertAdv is AdvisoryResult.Uncertain)
    }

    // 3. OFFLINE PERSISTENCE & SYNC LIFECYCLE
    @Test
    fun testOfflinePersistenceAndSyncLifecycle() = runBlocking {
        // Step 1: Diagnose offline -> Saved locally with PENDING status
        fakeApiClient.shouldSucceed = false
        fakeApiClient.mockException = IOException("Device offline")

        val localRecord = diagnosisRepository.recordLocalDiagnosis(
            cropId = "tomato",
            cropName = "Tomato",
            diseaseId = 53,
            diseaseName = "tomato bacterial leaf spot",
            confidence = 0.88f,
            diagnosticStatus = "CONFIDENT",
        )

        val stored = fakeDao.getDiagnosisById(localRecord.id)
        assertNotNull("Local record must exist immediately", stored)
        assertEquals(SyncStatus.FAILED, stored?.syncStatus)
        assertEquals(1, stored?.retryCount)

        // Step 2: Connectivity restored -> Retry synchronization
        fakeApiClient.shouldSucceed = true
        fakeApiClient.mockResponseId = "diag_server_stage10"

        val retryResult = diagnosisRepository.retryDiagnosis(localRecord.id)
        assertTrue("Retry must succeed when online", retryResult.isSuccess)

        val updated = fakeDao.getDiagnosisById(localRecord.id)
        assertEquals(SyncStatus.SYNCED, updated?.syncStatus)
        assertEquals("diag_server_stage10", updated?.backendDiagnosisId)
        assertNotNull(updated?.syncedAt)

        // Step 3: Duplicate protection check
        val callCountBefore = fakeApiClient.recordCallCount
        val duplicateSyncResult = diagnosisRepository.syncDiagnosis(updated!!)
        assertTrue(duplicateSyncResult.isSuccess)
        assertEquals("Duplicate sync must not re-trigger API call", callCountBefore, fakeApiClient.recordCallCount)
    }
}
