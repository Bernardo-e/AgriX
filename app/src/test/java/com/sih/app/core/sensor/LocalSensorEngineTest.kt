package com.sih.app.core.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSensorEngineTest {

    private val engine = LocalSensorEngine()

    @Test
    fun `low soil moisture triggers high priority irrigation in unified recommendation`() {
        val reading = SensorReading(
            temperature = 28.0,
            humidity = 55.0,
            soilMoisture = 22.0, // < 40% for Tomato
            soilPH = 6.5,
        )
        val rec = engine.synthesizeUnifiedRecommendation(reading, "Tomato")
        assertEquals(RecommendationPriority.HIGH, rec.priority)
        assertTrue(rec.wateringDecision.contains("Irrigate now", ignoreCase = true))
        assertTrue(rec.wateringAction.contains("irrigation", ignoreCase = true))
        assertTrue(rec.wateringTiming.contains("hours", ignoreCase = true))
        assertTrue(rec.wateringExplanation.contains("below", ignoreCase = true))
    }

    @Test
    fun `optimal soil moisture triggers low priority maintain schedule`() {
        val reading = SensorReading(
            temperature = 27.5,
            humidity = 60.0,
            soilMoisture = 48.0, // within 40..60% for Tomato
            soilPH = 6.7,
        )
        val rec = engine.synthesizeUnifiedRecommendation(reading, "Tomato")
        assertEquals(RecommendationPriority.LOW, rec.priority)
        assertTrue(rec.wateringDecision.contains("No immediate", ignoreCase = true))
        assertTrue(rec.wateringTiming.contains("12–24 hours", ignoreCase = true))
        assertTrue(rec.wateringAction.contains("current watering schedule", ignoreCase = true))
    }

    @Test
    fun `saturated soil moisture warns to pause irrigation and check drainage`() {
        val reading = SensorReading(
            temperature = 26.0,
            humidity = 70.0,
            soilMoisture = 72.0, // > 60% for Tomato
            soilPH = 6.8,
        )
        val rec = engine.synthesizeUnifiedRecommendation(reading, "Tomato")
        assertTrue(rec.wateringDecision.contains("Pause irrigation", ignoreCase = true))
        assertTrue(rec.wateringAction.contains("drainage", ignoreCase = true))
    }

    @Test
    fun `crop specific threshold differs for Rice vs Tomato`() {
        // 48% is optimal for Tomato (40-60%), but a moisture deficit for Rice (55-80%)
        val reading = SensorReading(
            temperature = 28.0,
            humidity = 65.0,
            soilMoisture = 48.0,
            soilPH = 6.5,
        )
        val tomatoRec = engine.synthesizeUnifiedRecommendation(reading, "Tomato")
        val riceRec = engine.synthesizeUnifiedRecommendation(reading, "Rice")

        assertEquals(RecommendationPriority.LOW, tomatoRec.priority)
        assertTrue(tomatoRec.wateringDecision.contains("No immediate", ignoreCase = true))

        assertEquals(RecommendationPriority.HIGH, riceRec.priority)
        assertTrue(riceRec.wateringDecision.contains("Irrigate now", ignoreCase = true))
    }

    @Test
    fun `disease context elevates preventive warnings under high humidity`() {
        val reading = SensorReading(
            temperature = 27.0,
            humidity = 82.0, // Elevated humidity > 75%
            soilMoisture = 50.0,
            soilPH = 6.5,
        )
        val rec = engine.synthesizeUnifiedRecommendation(
            reading = reading,
            cropName = "Tomato",
            diseaseName = "Early Blight",
            diseaseConfidence = 0.92f,
            diseaseStatus = "confirmed",
        )
        assertEquals(RecommendationPriority.HIGH, rec.priority)
        assertTrue(rec.diseasePrevention.contains("Early Blight", ignoreCase = true))
        assertTrue(rec.diseasePrevention.contains("humidity", ignoreCase = true))
        assertTrue(rec.diseasePrevention.contains("overhead", ignoreCase = true))
    }

    @Test
    fun `acidic soil pH is flagged accurately in soil condition`() {
        val reading = SensorReading(
            temperature = 26.0,
            humidity = 60.0,
            soilMoisture = 45.0,
            soilPH = 5.2, // < 5.8
        )
        val rec = engine.synthesizeUnifiedRecommendation(reading, "Tomato")
        assertTrue(rec.soilCondition.contains("Acidic", ignoreCase = true))
    }

    @Test
    fun `legacy analyze method continues functioning`() {
        val reading = SensorReading(
            temperature = 28.0,
            humidity = 55.0,
            soilMoisture = 22.0,
            soilPH = 6.5,
        )
        val analysis = engine.analyze(reading, "Tomato")
        assertEquals(IrrigationPriority.HIGH, analysis.irrigationPriority)
        assertTrue(analysis.immediateAction.isNotBlank())
    }
}
