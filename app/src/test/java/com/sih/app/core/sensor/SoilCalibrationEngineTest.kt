package com.sih.app.core.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoilCalibrationEngineTest {

    private val engine = SoilCalibrationEngine()

    @Test
    fun `same ADC value produces different VWC across sandy vs clay soil context`() {
        val rawAdc = 1850
        val temp = 28.5
        val humidity = 62.0

        val sandyVwc = engine.estimateVwc(rawAdc, temp, humidity, "Sandy")
        val loamyVwc = engine.estimateVwc(rawAdc, temp, humidity, "Loamy")
        val clayVwc = engine.estimateVwc(rawAdc, temp, humidity, "Clay")

        // Soil texture physics: Clay holds more bound water/higher saturation VWC than sand
        assertNotEquals(sandyVwc, loamyVwc, 0.5)
        assertNotEquals(loamyVwc, clayVwc, 0.5)
        assertTrue("Clay VWC should be higher than Sandy VWC for same ADC", clayVwc > sandyVwc)
    }

    @Test
    fun `plant available water fraction clamps accurately between 0 and 1`() {
        val loamyProfile = SoilContextRegistry.getProfile("Loamy") // FC=28, PWP=12

        // Saturated (above FC)
        val saturatedAwf = loamyProfile.calculateAvailableWaterFraction(35.0)
        assertEquals(1.0, saturatedAwf, 0.001)

        // Wilting (below PWP)
        val wiltingAwf = loamyProfile.calculateAvailableWaterFraction(8.0)
        assertEquals(0.0, wiltingAwf, 0.001)

        // Mid-point: 20% VWC -> (20 - 12) / (28 - 12) = 8 / 16 = 0.5
        val midAwf = loamyProfile.calculateAvailableWaterFraction(20.0)
        assertEquals(0.5, midAwf, 0.001)
    }

    @Test
    fun `adding calibration samples updates metrics and sample count`() {
        val initialCount = engine.samplesFlow.value.size
        assertTrue(initialCount > 0)

        val initialMetrics = engine.metricsFlow.value
        assertTrue(initialMetrics.isTrained)
        assertTrue(initialMetrics.rSquared in 0.0..1.0)
        assertTrue(initialMetrics.meanAbsoluteError >= 0.0)

        // Add a new empirical reference measurement
        engine.addSample(
            soilAdc = 1900,
            temperature = 28.0,
            humidity = 60.0,
            soilType = "Loamy",
            referenceVwc = 30.0,
            isDemo = false,
        )

        val updatedCount = engine.samplesFlow.value.size
        assertEquals(initialCount + 1, updatedCount)

        val updatedMetrics = engine.updateModel()
        assertEquals(updatedCount, updatedMetrics.sampleCount)
        assertTrue(updatedMetrics.statusDescription.contains("Field Calibrated", ignoreCase = true))
    }

    @Test
    fun `temperature compensation adjusts estimated VWC correctly`() {
        val adc = 1850
        val humidity = 60.0
        val soilType = "Loamy"

        val normalTempVwc = engine.estimateVwc(adc, 25.0, humidity, soilType)
        val highTempVwc = engine.estimateVwc(adc, 35.0, humidity, soilType)

        // Higher temperature causes negative drift compensation
        assertTrue(normalTempVwc >= highTempVwc)
    }
}
