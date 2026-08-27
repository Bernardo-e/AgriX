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
    fun `test combined report synthesis with local analysis and successful cloud analysis`() {
        val reading = SensorReading(
            temperature = 28.5,
            humidity = 62.0,
            soilMoisture = 47.0,
            soilPH = 6.7,
            source = "SIMULATED_BLE",
        )

        val localAnalysis = localEngine.analyze(reading, "Tomato")

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
        )

        val report = CombinedSensorReport(
            reading = reading,
            localAnalysis = localAnalysis,
            cloudAnalysis = cloudAnalysis,
            isCloudFallback = false,
            finalRecommendation = cloudAnalysis.farmerSummary,
        )

        assertEquals("SIMULATED_BLE", report.reading.source)
        assertEquals(28.5, report.reading.temperature, 0.01)
        assertEquals(IrrigationPriority.MODERATE, report.localAnalysis.irrigationPriority)
        assertNotNull(report.cloudAnalysis)
        assertEquals("Current soil moisture is adequate. Continue monitoring.", report.finalRecommendation)
    }

    @Test
    fun `test combined report synthesis with offline cloud fallback`() {
        val reading = SensorReading(
            temperature = 33.0,
            humidity = 75.0,
            soilMoisture = 28.0, // Low
            soilPH = 5.5,        // Acidic
            source = "SIMULATED_BLE",
        )

        val localAnalysis = localEngine.analyze(reading, "Tomato")

        // Offline: cloudAnalysis is null
        val fallbackRec = buildString {
            append("Current soil moisture is ${reading.soilMoisture.toInt()}% (${localAnalysis.soilCondition.lowercase()}). ")
            append(localAnalysis.immediateAction)
        }

        val report = CombinedSensorReport(
            reading = reading,
            localAnalysis = localAnalysis,
            cloudAnalysis = null,
            isCloudFallback = true,
            finalRecommendation = fallbackRec,
        )

        assertEquals(IrrigationPriority.HIGH, report.localAnalysis.irrigationPriority)
        assertTrue(report.isCloudFallback)
        assertTrue(report.finalRecommendation.contains("immediately", ignoreCase = true))
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
