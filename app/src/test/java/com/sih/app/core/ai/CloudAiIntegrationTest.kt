package com.sih.app.core.ai

import android.net.Uri
import com.sih.app.core.ai.cloud.CloudAiEngine
import com.sih.app.core.data.api.ApiException
import com.sih.app.core.data.api.BackendCropRef
import com.sih.app.core.data.api.BackendDiseaseRef
import com.sih.app.core.data.api.cloud.CloudAdvisoryInfoData
import com.sih.app.core.data.api.cloud.CloudAiClient
import com.sih.app.core.data.api.cloud.CloudDiagnosisInfoData
import com.sih.app.core.data.api.cloud.CloudDiagnosisRequestData
import com.sih.app.core.data.api.cloud.CloudDiagnosisResponseData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAiIntegrationTest {

    private fun createFakeAiResult(
        disease: String = "Tomato Early Blight",
        confidence: Float = 0.60f,
        source: DiagnosisSource = DiagnosisSource.REAL_TFLITE,
        isHealthy: Boolean = false,
        isIrrelevant: Boolean = false,
        engineType: AiEngineType = AiEngineType.LOCAL,
    ): AiResult {
        val pred = DiseasePrediction(
            diseaseName = disease,
            confidence = confidence,
            classId = 54,
            crop = "Tomato",
            rank = 1,
        )
        return AiResult(
            disease = disease,
            confidence = confidence,
            severity = "Moderate Confidence",
            symptoms = listOf("Leaf lesions"),
            recommendation = "Prune leaves",
            prevention = listOf("Crop rotation"),
            engineType = engineType,
            diagnosticResult = DiagnosticResult(
                primaryPrediction = pred,
                topPredictions = listOf(pred),
                cropCompatiblePredictions = listOf(pred),
                status = DiagnosticStatus.MODERATE_CONFIDENCE,
                message = "Guidance message",
                selectedCrop = "Tomato",
                confidenceBand = ConfidenceBand.MEDIUM,
                isPrototypeFallback = false,
            ),
            assessment = if (isIrrelevant) ImageAssessment.IRRELEVANT_IMAGE else if (isHealthy) ImageAssessment.HEALTHY_CROP else ImageAssessment.PLANT_RELEVANT,
            source = source,
            isHealthy = isHealthy,
            isIrrelevant = isIrrelevant,
        )
    }

    private class FakeAiEngine(
        override val type: AiEngineType,
        var available: Boolean = true,
        var resultToReturn: Result<AiResult>? = null,
    ) : AiEngine {
        var analyzeCallCount: Int = 0

        override fun isAvailable(): Boolean = available

        override suspend fun analyze(imageUri: Uri?, cropHint: String?): Result<AiResult> {
            analyzeCallCount++
            return resultToReturn ?: Result.failure(Exception("Not configured"))
        }
    }

    private class FakeCloudAiClient(
        var shouldSucceed: Boolean = true,
        var errorResponseCode: Int = 500,
    ) : CloudAiClient {
        var lastRequest: CloudDiagnosisRequestData? = null

        override suspend fun performCloudDiagnosis(
            request: CloudDiagnosisRequestData,
        ): Result<CloudDiagnosisResponseData> {
            lastRequest = request
            return if (shouldSucceed) {
                Result.success(
                    CloudDiagnosisResponseData(
                        status = "success",
                        provider = "mock-cloud",
                        model = "gemini-1.5-flash",
                        latencyMs = 950,
                        diagnosis = CloudDiagnosisInfoData(
                            crop = BackendCropRef(request.cropId, request.cropId.replaceFirstChar { it.uppercase() }),
                            disease = BackendDiseaseRef(request.localDiseaseId ?: 54, "Tomato Early Blight"),
                            confidence = 0.93f,
                            diagnosticStatus = "CONFIDENT",
                        ),
                        visualReasoning = "Visual lesions verified on foliage.",
                        advisory = CloudAdvisoryInfoData(
                            severity = "moderate",
                            urgency = "prompt",
                            overview = "Overview of condition",
                            symptoms = listOf("Dark spots"),
                            immediateActions = listOf("Prune lower leaves"),
                            prevention = listOf("Sanitation"),
                            monitoring = listOf("Scout weekly"),
                            expertEscalation = "Contact local KVK",
                            safetyNote = "Follow IPM practices",
                        ),
                    )
                )
            } else {
                Result.failure(ApiException(errorResponseCode, "Cloud AI error: HTTP $errorResponseCode"))
            }
        }
    }

    @Test
    fun test_ai_engine_router_local_mode() = runBlocking {
        val fakeLocal = FakeAiEngine(AiEngineType.LOCAL, resultToReturn = Result.success(createFakeAiResult()))
        val fakeCloud = FakeAiEngine(AiEngineType.CLOUD, resultToReturn = Result.success(createFakeAiResult(engineType = AiEngineType.CLOUD)))

        val router = AiEngineRouter(fakeLocal, fakeCloud)
        val result = router.analyze(null, mode = AiRouterMode.LOCAL, cropHint = "tomato")

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.analyzeCallCount)
        assertEquals(0, fakeCloud.analyzeCallCount)
        assertEquals(AiEngineType.LOCAL, result.getOrNull()?.engineType)
    }

    @Test
    fun test_ai_engine_router_auto_mode_offline() = runBlocking {
        val fakeLocal = FakeAiEngine(AiEngineType.LOCAL, resultToReturn = Result.success(createFakeAiResult(confidence = 0.55f)))
        val fakeCloud = FakeAiEngine(AiEngineType.CLOUD, available = false)

        val router = AiEngineRouter(fakeLocal, fakeCloud)
        val result = router.analyze(null, mode = AiRouterMode.AUTO, cropHint = "tomato")

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.analyzeCallCount)
        assertEquals(0, fakeCloud.analyzeCallCount)
        assertEquals(AiEngineType.LOCAL, result.getOrNull()?.engineType)
    }

    @Test
    fun test_ai_engine_router_auto_mode_cloud_enhancement_on_moderate_confidence() = runBlocking {
        val localRes = createFakeAiResult(confidence = 0.50f, engineType = AiEngineType.LOCAL)
        val cloudRes = createFakeAiResult(disease = "Tomato Early Blight (Cloud Enhanced)", confidence = 0.94f, source = DiagnosisSource.CLOUD_AI, engineType = AiEngineType.CLOUD)

        val fakeLocal = FakeAiEngine(AiEngineType.LOCAL, resultToReturn = Result.success(localRes))
        val fakeCloud = FakeAiEngine(AiEngineType.CLOUD, available = true, resultToReturn = Result.success(cloudRes))

        val router = AiEngineRouter(fakeLocal, fakeCloud)
        val result = router.analyze(null, mode = AiRouterMode.AUTO, cropHint = "tomato")

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.analyzeCallCount)
        assertEquals(1, fakeCloud.analyzeCallCount)
        assertEquals(AiEngineType.CLOUD, result.getOrNull()?.engineType)
        assertEquals(DiagnosisSource.CLOUD_AI, result.getOrNull()?.source)
        assertEquals("Tomato Early Blight (Cloud Enhanced)", result.getOrNull()?.disease)
    }

    @Test
    fun test_ai_engine_router_auto_mode_cloud_failure_falls_back_to_local() = runBlocking {
        val localRes = createFakeAiResult(confidence = 0.50f, engineType = AiEngineType.LOCAL)

        val fakeLocal = FakeAiEngine(AiEngineType.LOCAL, resultToReturn = Result.success(localRes))
        val fakeCloud = FakeAiEngine(AiEngineType.CLOUD, available = true, resultToReturn = Result.failure(Exception("Cloud timeout 504")))

        val router = AiEngineRouter(fakeLocal, fakeCloud)
        val result = router.analyze(null, mode = AiRouterMode.AUTO, cropHint = "tomato")

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.analyzeCallCount)
        assertEquals(1, fakeCloud.analyzeCallCount)
        // Must retain local result seamlessly without failing
        assertEquals(AiEngineType.LOCAL, result.getOrNull()?.engineType)
        assertEquals("Tomato Early Blight", result.getOrNull()?.disease)
    }

    @Test
    fun test_ai_engine_router_cloud_mode_503_or_timeout_falls_back_to_local() = runBlocking {
        val localRes = createFakeAiResult(confidence = 0.65f, engineType = AiEngineType.LOCAL)

        val fakeLocal = FakeAiEngine(AiEngineType.LOCAL, resultToReturn = Result.success(localRes))
        val fakeCloud = FakeAiEngine(AiEngineType.CLOUD, available = true, resultToReturn = Result.failure(ApiException(503, "Service Unavailable")))

        val router = AiEngineRouter(fakeLocal, fakeCloud)
        val result = router.analyze(null, mode = AiRouterMode.CLOUD, cropHint = "tomato")

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.analyzeCallCount)
        assertEquals(1, fakeCloud.analyzeCallCount)
        assertEquals(AiEngineType.LOCAL, result.getOrNull()?.engineType)
    }

    @Test
    fun test_ai_engine_router_auto_mode_high_confidence_uses_local_fast() = runBlocking {
        val localRes = createFakeAiResult(confidence = 0.88f, source = DiagnosisSource.REAL_TFLITE, engineType = AiEngineType.LOCAL)

        val fakeLocal = FakeAiEngine(AiEngineType.LOCAL, resultToReturn = Result.success(localRes))
        val fakeCloud = FakeAiEngine(AiEngineType.CLOUD, available = true)

        val router = AiEngineRouter(fakeLocal, fakeCloud)
        val result = router.analyze(null, mode = AiRouterMode.AUTO, cropHint = "tomato")

        assertTrue(result.isSuccess)
        assertEquals(1, fakeLocal.analyzeCallCount)
        // Cloud should not even be called when local is high confidence
        assertEquals(0, fakeCloud.analyzeCallCount)
        assertEquals(AiEngineType.LOCAL, result.getOrNull()?.engineType)
    }

    @Test
    fun test_ai_engine_router_auto_mode_healthy_or_irrelevant_returns_local_immediately() = runBlocking {
        val healthyRes = createFakeAiResult(isHealthy = true)

        val fakeLocal = FakeAiEngine(AiEngineType.LOCAL, resultToReturn = Result.success(healthyRes))
        val fakeCloud = FakeAiEngine(AiEngineType.CLOUD, available = true)

        val router = AiEngineRouter(fakeLocal, fakeCloud)
        val result = router.analyze(null, mode = AiRouterMode.AUTO, cropHint = "tomato")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isHealthy)
        assertEquals(0, fakeCloud.analyzeCallCount)
    }

    @Test
    fun test_fake_cloud_ai_client_request_construction() = runBlocking {
        val fakeClient = FakeCloudAiClient(shouldSucceed = true)
        val request = CloudDiagnosisRequestData(
            imageBytes = byteArrayOf(1, 2, 3),
            cropId = "tomato",
            localDiseaseId = 54,
            localConfidence = 0.65f,
            localStatus = "MODERATE_CONFIDENCE",
            language = "hi",
            state = "Maharashtra",
            district = "Pune",
        )

        val result = fakeClient.performCloudDiagnosis(request)
        assertTrue(result.isSuccess)

        val req = fakeClient.lastRequest
        assertNotNull(req)
        assertEquals("tomato", req?.cropId)
        assertEquals(54, req?.localDiseaseId)
        assertEquals(0.65f, req?.localConfidence)
        assertEquals("MODERATE_CONFIDENCE", req?.localStatus)
        assertEquals("hi", req?.language)
        assertEquals("Maharashtra", req?.state)
        assertEquals("Pune", req?.district)
    }

    @Test
    fun test_cloud_diagnosis_response_data_structure() {
        val resp = CloudDiagnosisResponseData(
            status = "success",
            provider = "gemini-1.5-flash",
            model = "gemini-1.5-flash",
            latencyMs = 1200,
            diagnosis = CloudDiagnosisInfoData(
                crop = BackendCropRef("tomato", "Tomato"),
                disease = BackendDiseaseRef(54, "Tomato Early Blight"),
                confidence = 0.92f,
                diagnosticStatus = "CONFIDENT",
            ),
            visualReasoning = "Concentric rings observed on lower foliage.",
            advisory = CloudAdvisoryInfoData(
                severity = "moderate",
                urgency = "prompt",
                overview = "Early blight overview",
                symptoms = listOf("Dark spots", "Yellowing"),
                immediateActions = listOf("Prune leaves"),
                prevention = listOf("Crop rotation"),
                monitoring = listOf("Scout weekly"),
                expertEscalation = "Contact local KVK",
                safetyNote = "Follow IPM practices",
            ),
        )

        assertEquals("success", resp.status)
        assertEquals(54, resp.diagnosis.disease.id)
        assertEquals("Tomato Early Blight", resp.diagnosis.disease.name)
        assertEquals(2, resp.advisory.symptoms.size)
        assertEquals(1, resp.advisory.immediateActions.size)
    }
}
