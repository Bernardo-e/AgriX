package com.sih.app.core.sensor

import com.sih.app.core.data.api.cloud.CloudAiClient
import com.sih.app.core.data.api.cloud.CloudDiagnosisRequestData
import com.sih.app.core.data.api.cloud.CloudDiagnosisResponseData
import com.sih.app.core.data.api.cloud.CloudSensorRequestData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorWorkflowIntegrationTest {

    private val localEngine = LocalSensorEngine()

    @Test
    fun `test discrete stages of SensorState`() {
        val demoDevice = BleDevice("AgriX Sensor", "DEMO:BLE:AGRIX:01", -55, isDemo = true)
        val reading = SensorReading(28.5, 62.0, 47.0, 6.7, source = "SIMULATED_BLE")
        val localAnalysis = localEngine.analyze(reading, "Tomato")
        val recommendation = localEngine.synthesizeUnifiedRecommendation(reading, "Tomato")
        val report = CombinedSensorReport(
            reading = reading,
            recommendation = recommendation,
            localAnalysis = localAnalysis,
            cloudAnalysis = null,
            isCloudFallback = true,
            finalRecommendation = recommendation.immediateActionSummary,
        )

        assertEquals(SensorStateStage.DISCONNECTED_INITIAL, SensorState.DisconnectedInitial.stage)
        assertEquals(SensorStateStage.SCAN_1_NO_SENSOR, SensorState.Scan1NoSensor(isScanning = false).stage)
        assertEquals(SensorStateStage.SCAN_2_SENSOR_FOUND, SensorState.Scan2SensorFound(demoDevice, isScanning = false).stage)
        assertEquals(SensorStateStage.CONNECTING, SensorState.Connecting(demoDevice).stage)
        assertEquals(SensorStateStage.CONNECTED_DEMO, SensorState.ConnectedDemo(demoDevice).stage)
        assertEquals(SensorStateStage.SCANNING_SOIL, SensorState.ScanningSoil(demoDevice, "Reading moisture...", 0.35f).stage)
        assertEquals(SensorStateStage.DATA_READY, SensorState.DataReady(demoDevice, reading).stage)
        assertEquals(SensorStateStage.ANALYZING_LOCAL, SensorState.AnalyzingLocal(demoDevice, reading).stage)
        assertEquals(SensorStateStage.ANALYZING_CLOUD, SensorState.AnalyzingCloud(demoDevice, reading, localAnalysis).stage)
        assertEquals(SensorStateStage.CLOUD_FAILED_LOCAL_FALLBACK, SensorState.ResultReady(demoDevice, report, isCloudFallback = true).stage)
        assertEquals(SensorStateStage.RESULT_READY, SensorState.ResultReady(demoDevice, report, isCloudFallback = false).stage)
    }

    @Test
    fun `test unified recommendation with cloud AI enhancement`() {
        val reading = SensorReading(
            temperature = 28.5,
            humidity = 62.0,
            soilMoisture = 47.0,
            soilPH = 6.7,
            source = "SIMULATED_BLE",
        )

        val localAnalysis = localEngine.analyze(reading, "Tomato")
        val localRec = localEngine.synthesizeUnifiedRecommendation(reading, "Tomato")

        val cloudAnalysis = CloudSensorAnalysis(
            provider = "mock",
            model = "mock-v1",
            soilInterpretation = "Soil moisture is optimal at 47%.",
            cropImplications = "Optimal for tomato vegetative growth.",
            irrigationAdvice = "Continue standard irrigation.",
            possibleRisks = listOf("No acute risks."),
            recommendedNextAction = "Monitor weekly.",
            farmerSummary = "Current soil moisture is adequate. Continue monitoring.",
            latencyMs = 45,
            wateringDecision = "No immediate irrigation required",
            wateringExplanation = "Soil moisture is optimal.",
            wateringTiming = "Recheck in 24 hours.",
            wateringAction = "Maintain current watering.",
            actionNowSummary = "Current soil moisture is adequate. Continue monitoring.",
        )

        val unified = localRec.copy(
            wateringDecision = cloudAnalysis.wateringDecision ?: localRec.wateringDecision,
            immediateActionSummary = cloudAnalysis.actionNowSummary ?: localRec.immediateActionSummary,
            isCloudEnhanced = true,
        )

        val report = CombinedSensorReport(
            reading = reading,
            recommendation = unified,
            localAnalysis = localAnalysis,
            cloudAnalysis = cloudAnalysis,
            isCloudFallback = false,
            finalRecommendation = unified.immediateActionSummary,
        )

        assertEquals("SIMULATED_BLE", report.reading.source)
        assertEquals(28.5, report.reading.temperature, 0.01)
        assertEquals(RecommendationPriority.LOW, report.recommendation.priority)
        assertNotNull(report.cloudAnalysis)
        assertTrue(report.recommendation.isCloudEnhanced)
        assertEquals("Current soil moisture is adequate. Continue monitoring.", report.finalRecommendation)
    }

    @Test
    fun `test unified recommendation with offline fallback`() {
        val reading = SensorReading(
            temperature = 33.0,
            humidity = 75.0,
            soilMoisture = 28.0, // Low for Tomato (< 40%)
            soilPH = 5.5,        // Acidic
            source = "SIMULATED_BLE",
        )

        val localAnalysis = localEngine.analyze(reading, "Tomato")
        val localRec = localEngine.synthesizeUnifiedRecommendation(reading, "Tomato")

        val report = CombinedSensorReport(
            reading = reading,
            recommendation = localRec,
            localAnalysis = localAnalysis,
            cloudAnalysis = null,
            isCloudFallback = true,
            finalRecommendation = localRec.immediateActionSummary,
        )

        assertEquals(RecommendationPriority.HIGH, report.recommendation.priority)
        assertTrue(report.isCloudFallback)
        assertTrue(report.recommendation.wateringDecision.contains("Irrigate now", ignoreCase = true))
    }

    @Test
    fun `test mock cloud AI client sensor analysis dispatch`() = runBlocking {
        val mockClient = object : CloudAiClient {
            override suspend fun performCloudDiagnosis(request: CloudDiagnosisRequestData): Result<CloudDiagnosisResponseData> {
                throw NotImplementedError()
            }

            override suspend fun performCloudSensorAnalysis(request: CloudSensorRequestData): Result<CloudSensorAnalysis> {
                return Result.success(
                    CloudSensorAnalysis(
                        provider = "Mock Gemini",
                        model = "gemini-2.5-flash",
                        soilInterpretation = "Soil moisture ${request.soilMoisture}% evaluated.",
                        cropImplications = "Favorable conditions.",
                        irrigationAdvice = "Maintain routine watering.",
                        possibleRisks = emptyList(),
                        recommendedNextAction = "No action needed.",
                        farmerSummary = "Optimal soil health.",
                        latencyMs = 120,
                        wateringDecision = "No immediate irrigation required",
                        actionNowSummary = "Optimal soil health.",
                    )
                )
            }
        }

        val req = CloudSensorRequestData(
            source = "SIMULATED_BLE",
            temperature = 28.0,
            humidity = 60.0,
            soilMoisture = 45.0,
            soilPH = 6.5,
            cropName = "Tomato",
        )

        val result = mockClient.performCloudSensorAnalysis(req)
        assertTrue(result.isSuccess)
        val analysis = result.getOrThrow()
        assertEquals("Mock Gemini", analysis.provider)
        assertEquals("Optimal soil health.", analysis.farmerSummary)
    }
}
