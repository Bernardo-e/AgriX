package com.sih.app.core.sensor

/**
 * 100% Offline Deterministic Agricultural Rule Engine.
 * Evaluates soil moisture, soil pH, ambient temperature, and relative humidity
 * against standard agronomic thresholds to produce immediate farmer-friendly guidance.
 */
class LocalSensorEngine {

    fun analyze(reading: SensorReading, cropName: String? = null): LocalSensorAnalysis {
        val risks = mutableListOf<String>()

        // 1. Soil Moisture Analysis
        val (moistureCondition, irrigationRec, priority, moistureAction) = when {
            reading.soilMoisture < 35.0 -> {
                risks.add("Critical moisture deficit: root zone lacks sufficient available water.")
                Quadruple(
                    "Dry / Moisture Deficit",
                    "High Priority Irrigation",
                    IrrigationPriority.HIGH,
                    "Initiate drip irrigation immediately for 45–60 minutes to replenish root zone moisture.",
                )
            }
            reading.soilMoisture <= 55.0 -> {
                Quadruple(
                    "Suitable / Optimal Moisture",
                    "Moderate / Monitor Moisture",
                    IrrigationPriority.MODERATE,
                    "Maintain current watering schedule and continue regular monitoring.",
                )
            }
            else -> {
                risks.add("Excess soil moisture: prolonged saturation may limit root oxygenation.")
                Quadruple(
                    "High Moisture / Saturated",
                    "No Immediate Irrigation",
                    IrrigationPriority.NONE,
                    "Pause irrigation cycles to prevent waterlogging and reduce root rot vulnerability.",
                )
            }
        }

        // 2. Soil pH Analysis
        val phCondition = when {
            reading.soilPH < 5.8 -> {
                risks.add("Acidic soil (pH ${String.format("%.1f", reading.soilPH)}): limits phosphorus and calcium availability.")
                "Acidic (pH ${String.format("%.1f", reading.soilPH)})"
            }
            reading.soilPH <= 7.5 -> {
                "Optimal (pH ${String.format("%.1f", reading.soilPH)})"
            }
            else -> {
                risks.add("Alkaline soil (pH ${String.format("%.1f", reading.soilPH)}): restricts zinc and iron micronutrient uptake.")
                "Alkaline (pH ${String.format("%.1f", reading.soilPH)})"
            }
        }

        // 3. Temperature & Heat Stress
        if (reading.temperature > 32.0) {
            risks.add("Heat stress risk: ambient temperature (${String.format("%.1f", reading.temperature)}°C) accelerates evapotranspiration.")
        }

        // 4. Relative Humidity & Fungal Risk
        if (reading.humidity > 80.0) {
            risks.add("Fungal pathogen risk: high ambient humidity (${String.format("%.0f", reading.humidity)}%) promotes spore germination.")
        }

        if (risks.isEmpty()) {
            risks.add("No immediate moisture, pH, or thermal stress detected.")
        }

        val cropContext = if (!cropName.isNullOrBlank()) " for $cropName" else ""
        val explanation = "Local agronomic rules evaluated 4 physical parameters (Moisture ${String.format("%.0f", reading.soilMoisture)}%, pH ${String.format("%.1f", reading.soilPH)}, Temp ${String.format("%.1f", reading.temperature)}°C, Humidity ${String.format("%.0f", reading.humidity)}%)$cropContext."

        val combinedSoilCondition = if (reading.soilPH in 5.8..7.5) {
            moistureCondition
        } else {
            "$moistureCondition • $phCondition"
        }

        return LocalSensorAnalysis(
            soilCondition = combinedSoilCondition,
            irrigationRecommendation = irrigationRec,
            irrigationPriority = priority,
            riskIndicators = risks,
            immediateAction = moistureAction,
            confidenceExplanation = explanation,
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
