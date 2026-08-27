package com.sih.app.core.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSensorEngineTest {

    private val engine = LocalSensorEngine()

    @Test
    fun `low soil moisture triggers high priority irrigation`() {
        val reading = SensorReading(
            temperature = 28.0,
            humidity = 55.0,
            soilMoisture = 22.0, // < 35%
            soilPH = 6.5,
        )
        val analysis = engine.analyze(reading, "Tomato")
        assertEquals(IrrigationPriority.HIGH, analysis.irrigationPriority)
        assertTrue(analysis.irrigationRecommendation.contains("High Priority", ignoreCase = true))
        assertTrue(analysis.immediateAction.contains("immediately", ignoreCase = true))
        assertTrue(analysis.riskIndicators.any { it.contains("deficit", ignoreCase = true) || it.contains("moisture", ignoreCase = true) })
    }

    @Test
    fun `moderate soil moisture triggers monitor recommendation`() {
        val reading = SensorReading(
            temperature = 27.5,
            humidity = 60.0,
            soilMoisture = 48.0, // 35..55%
            soilPH = 6.7,
        )
        val analysis = engine.analyze(reading, "Wheat")
        assertEquals(IrrigationPriority.MODERATE, analysis.irrigationPriority)
        assertTrue(analysis.irrigationRecommendation.contains("Monitor", ignoreCase = true))
        assertFalse(analysis.riskIndicators.any { it.contains("deficit", ignoreCase = true) })
    }

    @Test
    fun `saturated soil moisture pauses irrigation`() {
        val reading = SensorReading(
            temperature = 26.0,
            humidity = 70.0,
            soilMoisture = 68.0, // > 55%
            soilPH = 6.8,
        )
        val analysis = engine.analyze(reading, "Rice")
        assertEquals(IrrigationPriority.NONE, analysis.irrigationPriority)
        assertTrue(analysis.irrigationRecommendation.contains("No Immediate", ignoreCase = true))
        assertTrue(analysis.riskIndicators.any { it.contains("Excess", ignoreCase = true) || it.contains("saturation", ignoreCase = true) })
    }

    @Test
    fun `acidic soil pH is flagged accurately`() {
        val reading = SensorReading(
            temperature = 26.0,
            humidity = 60.0,
            soilMoisture = 45.0,
            soilPH = 5.2, // < 5.8
        )
        val analysis = engine.analyze(reading)
        assertTrue(analysis.soilCondition.contains("Acidic", ignoreCase = true))
        assertTrue(analysis.riskIndicators.any { it.contains("Acidic", ignoreCase = true) })
    }

    @Test
    fun `alkaline soil pH is flagged accurately`() {
        val reading = SensorReading(
            temperature = 26.0,
            humidity = 60.0,
            soilMoisture = 45.0,
            soilPH = 8.1, // > 7.5
        )
        val analysis = engine.analyze(reading)
        assertTrue(analysis.soilCondition.contains("Alkaline", ignoreCase = true))
        assertTrue(analysis.riskIndicators.any { it.contains("Alkaline", ignoreCase = true) })
    }

    @Test
    fun `heat stress and fungal risks are detected when thresholds exceeded`() {
        val reading = SensorReading(
            temperature = 35.5, // > 32°C
            humidity = 86.0,    // > 80%
            soilMoisture = 40.0,
            soilPH = 6.5,
        )
        val analysis = engine.analyze(reading)
        assertTrue(analysis.riskIndicators.any { it.contains("Heat stress", ignoreCase = true) })
        assertTrue(analysis.riskIndicators.any { it.contains("Fungal", ignoreCase = true) })
    }
}
