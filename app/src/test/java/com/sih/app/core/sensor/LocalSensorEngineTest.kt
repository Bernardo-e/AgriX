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
            soilMoisture = 14.0, // AWF = (14 - 12)/16 = 0.125 < 0.25 (Critically dry for Loamy)
            soilPH = 6.5,
            rawAdc = 2200,
            soilType = "Loamy",
        )
        val rec = engine.synthesizeUnifiedRecommendation(reading, "Tomato")
        assertEquals(RecommendationPriority.HIGH, rec.priority)
        assertTrue(rec.wateringDecision.contains("Irrigate now", ignoreCase = true))
        assertTrue(rec.wateringAction.contains("irrigation", ignoreCase = true))
        assertTrue(rec.wateringTiming.contains("hours", ignoreCase = true))
        assertTrue(rec.waterStatus.contains("Dry", ignoreCase = true))
        assertEquals(28.0, rec.fieldCapacity, 0.01)
        assertEquals(12.0, rec.wiltingPoint, 0.01)
    }

    @Test
    fun `optimal soil moisture triggers low priority maintain schedule`() {
        val reading = SensorReading(
            temperature = 27.5,
            humidity = 60.0,
            soilMoisture = 23.0, // AWF = (23 - 12)/16 = 0.687 (Optimal 0.50..0.75 for Loamy)
            soilPH = 6.7,
            rawAdc = 1850,
            soilType = "Loamy",
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
            soilMoisture = 34.0, // > FC (28%) for Loamy
            soilPH = 6.8,
            rawAdc = 1400,
            soilType = "Loamy",
        )
        val rec = engine.synthesizeUnifiedRecommendation(reading, "Tomato")
        assertTrue(rec.wateringDecision.contains("Pause irrigation", ignoreCase = true))
        assertTrue(rec.wateringAction.contains("drainage", ignoreCase = true))
    }

    @Test
    fun `crop specific threshold differs for Rice vs Tomato`() {
        // 17% VWC (AWF = 0.31) is manageable dry for Tomato, but a critical moisture deficit for water-intensive Rice
        val reading = SensorReading(
            temperature = 28.0,
            humidity = 65.0,
            soilMoisture = 17.0,
            soilPH = 6.5,
            soilType = "Loamy",
        )
        val tomatoRec = engine.synthesizeUnifiedRecommendation(reading, "Tomato")
        val riceRec = engine.synthesizeUnifiedRecommendation(reading, "Rice")

        assertEquals(RecommendationPriority.MEDIUM, tomatoRec.priority)
        assertEquals(RecommendationPriority.HIGH, riceRec.priority)
        assertTrue(riceRec.wateringDecision.contains("Irrigate now", ignoreCase = true))
    }

    @Test
    fun `disease context elevates preventive warnings under high humidity`() {
        val reading = SensorReading(
            temperature = 27.0,
            humidity = 82.0, // Elevated humidity > 75%
            soilMoisture = 22.0,
            soilPH = 6.5,
            soilType = "Loamy",
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
            soilMoisture = 22.0,
            soilPH = 5.2, // < 5.8
            soilType = "Loamy",
        )
        val rec = engine.synthesizeUnifiedRecommendation(reading, "Tomato")
        assertTrue(rec.soilCondition.contains("Acidic", ignoreCase = true))
    }

    @Test
    fun `legacy analyze method continues functioning`() {
        val reading = SensorReading(
            temperature = 28.0,
            humidity = 55.0,
            soilMoisture = 13.0,
            soilPH = 6.5,
            soilType = "Loamy",
        )
        val analysis = engine.analyze(reading, "Tomato")
        assertEquals(IrrigationPriority.HIGH, analysis.irrigationPriority)
        assertTrue(analysis.immediateAction.isNotBlank())
    }
}
