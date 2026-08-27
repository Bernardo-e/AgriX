package com.sih.app.core.sensor

import kotlin.math.max
import kotlin.math.min

/**
 * Agronomic hydrological parameters for different soil classifications.
 * FC = Field Capacity (volumetric moisture content at field capacity, %)
 * PWP = Permanent Wilting Point (volumetric moisture content at wilting point, %)
 */
data class SoilHydrologyProfile(
    val soilType: String,
    val fieldCapacity: Double, // FC %
    val wiltingPoint: Double,  // PWP %
    val baseAdcDry: Int,       // Typical ADC in oven-dry soil
    val baseAdcWet: Int,       // Typical ADC in water-saturated soil
) {
    /**
     * Compute Plant-Available Water Fraction (AWF).
     * Formula: AWF = (VWC - PWP) / (FC - PWP), clamped to [0.0, 1.0].
     */
    fun calculateAvailableWaterFraction(vwc: Double): Double {
        if (fieldCapacity <= wiltingPoint) return 0.5
        val rawFraction = (vwc - wiltingPoint) / (fieldCapacity - wiltingPoint)
        return max(0.0, min(1.0, rawFraction))
    }
}

object SoilContextRegistry {
    val PROFILES: Map<String, SoilHydrologyProfile> = mapOf(
        "Sandy" to SoilHydrologyProfile("Sandy", fieldCapacity = 12.0, wiltingPoint = 4.0, baseAdcDry = 2800, baseAdcWet = 1200),
        "Sandy Loam" to SoilHydrologyProfile("Sandy Loam", fieldCapacity = 18.0, wiltingPoint = 8.0, baseAdcDry = 2950, baseAdcWet = 1350),
        "Loamy" to SoilHydrologyProfile("Loamy", fieldCapacity = 28.0, wiltingPoint = 12.0, baseAdcDry = 3100, baseAdcWet = 1450),
        "Clay Loam" to SoilHydrologyProfile("Clay Loam", fieldCapacity = 34.0, wiltingPoint = 18.0, baseAdcDry = 3250, baseAdcWet = 1550),
        "Clay" to SoilHydrologyProfile("Clay", fieldCapacity = 40.0, wiltingPoint = 24.0, baseAdcDry = 3400, baseAdcWet = 1650),
    )

    val DEFAULT_PROFILE = PROFILES["Loamy"]!!

    fun getProfile(soilType: String?): SoilHydrologyProfile {
        if (soilType.isNullOrBlank()) return DEFAULT_PROFILE
        return PROFILES.entries.firstOrNull { it.key.equals(soilType.trim(), ignoreCase = true) }?.value
            ?: DEFAULT_PROFILE
    }
}

/**
 * A single empirical calibration sample mapping raw sensor readings and soil context to reference VWC.
 */
data class CalibrationSample(
    val id: String,
    val soilAdc: Int,
    val temperature: Double,
    val humidity: Double,
    val soilType: String,
    val referenceVwc: Double, // % Volumetric Water Content from laboratory gravimetric reference
    val timestamp: Long = System.currentTimeMillis(),
    val isDemo: Boolean = true,
)

/**
 * Statistical accuracy metrics for the calibration model.
 */
data class CalibrationMetrics(
    val sampleCount: Int,
    val meanAbsoluteError: Double,  // MAE (%)
    val rootMeanSquaredError: Double, // RMSE (%)
    val rSquared: Double,           // R² Coefficient of determination
    val statusDescription: String,
    val isTrained: Boolean,
)
