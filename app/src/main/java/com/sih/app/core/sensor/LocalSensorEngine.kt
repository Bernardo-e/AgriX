package com.sih.app.core.sensor

import java.util.Locale

/**
 * 100% Offline Crop-Aware Agricultural Rule Engine.
 * Evaluates real-time sensor readings (moisture, pH, temp, humidity) alongside
 * target crop profiles and any previously diagnosed disease context to synthesize
 * an actionable, farmer-friendly recommendation.
 */
class LocalSensorEngine {

    private data class CropAgronomicProfile(
        val standardName: String,
        val minMoisture: Double,
        val maxMoisture: Double,
        val minPh: Double,
        val maxPh: Double,
        val minTemp: Double,
        val maxTemp: Double,
        val maxSafeHumidity: Double,
        val growthAdvice: String,
        val diseaseRiskNotes: String,
    )

    private val cropProfiles = mapOf(
        "rice" to CropAgronomicProfile(
            standardName = "Rice",
            minMoisture = 55.0,
            maxMoisture = 80.0,
            minPh = 5.5,
            maxPh = 6.8,
            minTemp = 22.0,
            maxTemp = 33.0,
            maxSafeHumidity = 85.0,
            growthAdvice = "Maintaining adequate standing/saturated moisture during vegetative and panicle initiation stages ensures robust tiller development.",
            diseaseRiskNotes = "Excessive prolonged standing water with high humidity promotes blast and sheath blight.",
        ),
        "paddy" to CropAgronomicProfile(
            standardName = "Rice",
            minMoisture = 55.0,
            maxMoisture = 80.0,
            minPh = 5.5,
            maxPh = 6.8,
            minTemp = 22.0,
            maxTemp = 33.0,
            maxSafeHumidity = 85.0,
            growthAdvice = "Maintaining adequate standing/saturated moisture during vegetative and panicle initiation stages ensures robust tiller development.",
            diseaseRiskNotes = "Excessive prolonged standing water with high humidity promotes blast and sheath blight.",
        ),
        "tomato" to CropAgronomicProfile(
            standardName = "Tomato",
            minMoisture = 40.0,
            maxMoisture = 60.0,
            minPh = 6.0,
            maxPh = 6.8,
            minTemp = 20.0,
            maxTemp = 29.0,
            maxSafeHumidity = 75.0,
            growthAdvice = "Consistent, moderate root zone moisture prevents blossom end rot and fruit cracking while supporting uniform flowering.",
            diseaseRiskNotes = "High leaf wetness and humidity above 75% dramatically accelerate early and late blight.",
        ),
        "potato" to CropAgronomicProfile(
            standardName = "Potato",
            minMoisture = 45.0,
            maxMoisture = 65.0,
            minPh = 5.2,
            maxPh = 6.5,
            minTemp = 15.0,
            maxTemp = 24.0,
            maxSafeHumidity = 75.0,
            growthAdvice = "Adequate tuber initiation moisture ensures uniform sizing; avoid sudden drying cycles to prevent tuber deformation.",
            diseaseRiskNotes = "Saturated soil combined with moderate temperatures favors late blight and bacterial soft rot.",
        ),
        "wheat" to CropAgronomicProfile(
            standardName = "Wheat",
            minMoisture = 35.0,
            maxMoisture = 55.0,
            minPh = 6.0,
            maxPh = 7.0,
            minTemp = 15.0,
            maxTemp = 25.0,
            maxSafeHumidity = 70.0,
            growthAdvice = "Maintain timely irrigation at crown root initiation and flowering stages for optimum grain filling.",
            diseaseRiskNotes = "Excess moisture and humid overcast weather increase rust and powdery mildew vulnerability.",
        ),
        "cotton" to CropAgronomicProfile(
            standardName = "Cotton",
            minMoisture = 35.0,
            maxMoisture = 55.0,
            minPh = 6.0,
            maxPh = 7.5,
            minTemp = 22.0,
            maxTemp = 34.0,
            maxSafeHumidity = 70.0,
            growthAdvice = "Deep, controlled watering promotes taproot penetration; avoid waterlogging which triggers square and boll shedding.",
            diseaseRiskNotes = "Poor drainage and humid conditions heighten bacterial blight and boll rot risks.",
        ),
        "corn" to CropAgronomicProfile(
            standardName = "Corn",
            minMoisture = 40.0,
            maxMoisture = 60.0,
            minPh = 5.8,
            maxPh = 7.0,
            minTemp = 20.0,
            maxTemp = 30.0,
            maxSafeHumidity = 75.0,
            growthAdvice = "Adequate soil moisture during tasseling and silking is critical for kernel set and ear development.",
            diseaseRiskNotes = "High canopy humidity favors grey leaf spot and northern corn leaf blight.",
        ),
        "maize" to CropAgronomicProfile(
            standardName = "Corn",
            minMoisture = 40.0,
            maxMoisture = 60.0,
            minPh = 5.8,
            maxPh = 7.0,
            minTemp = 20.0,
            maxTemp = 30.0,
            maxSafeHumidity = 75.0,
            growthAdvice = "Adequate soil moisture during tasseling and silking is critical for kernel set and ear development.",
            diseaseRiskNotes = "High canopy humidity favors grey leaf spot and northern corn leaf blight.",
        ),
        "chilli" to CropAgronomicProfile(
            standardName = "Chilli",
            minMoisture = 35.0,
            maxMoisture = 55.0,
            minPh = 6.0,
            maxPh = 6.8,
            minTemp = 20.0,
            maxTemp = 30.0,
            maxSafeHumidity = 75.0,
            growthAdvice = "Moderate moisture promotes prolific branching and fruit set; avoid stagnant water around stems.",
            diseaseRiskNotes = "Stagnant moisture and high humidity encourage damping off, anthracnose, and fungal fruit rot.",
        ),
        "pepper" to CropAgronomicProfile(
            standardName = "Chilli",
            minMoisture = 35.0,
            maxMoisture = 55.0,
            minPh = 6.0,
            maxPh = 6.8,
            minTemp = 20.0,
            maxTemp = 30.0,
            maxSafeHumidity = 75.0,
            growthAdvice = "Moderate moisture promotes prolific branching and fruit set; avoid stagnant water around stems.",
            diseaseRiskNotes = "Stagnant moisture and high humidity encourage damping off, anthracnose, and fungal fruit rot.",
        ),
    )

    private val defaultProfile = CropAgronomicProfile(
        standardName = "General Crop",
        minMoisture = 35.0,
        maxMoisture = 55.0,
        minPh = 6.0,
        maxPh = 7.5,
        minTemp = 18.0,
        maxTemp = 32.0,
        maxSafeHumidity = 75.0,
        growthAdvice = "Balanced soil moisture and nutrient availability support healthy vegetative and reproductive growth cycles.",
        diseaseRiskNotes = "Excessive moisture and high humidity create favorable microclimates for soil and foliar pathogens.",
    )

    fun synthesizeUnifiedRecommendation(
        reading: SensorReading,
        cropName: String? = null,
        diseaseName: String? = null,
        diseaseConfidence: Float? = null,
        diseaseStatus: String? = null,
    ): UnifiedAgriXRecommendation {
        val normalizedCropKey = cropName?.trim()?.lowercase(Locale.ROOT) ?: ""
        val profile = cropProfiles[normalizedCropKey] ?: defaultProfile
        val displayCropName = if (!cropName.isNullOrBlank()) cropName else profile.standardName

        val moisture = reading.soilMoisture
        val ph = reading.soilPH
        val temp = reading.temperature
        val humidity = reading.humidity

        // 1. Evaluate Moisture & Determine Priority
        val (wateringDecision, wateringWhy, wateringWhen, wateringAction, moisturePriority, overallMoistureStatus) = when {
            moisture < profile.minMoisture -> {
                val deficit = profile.minMoisture - moisture
                Hexuple(
                    "Irrigate now to restore root zone moisture",
                    "Soil moisture (${String.format(Locale.US, "%.0f", moisture)}%) is below the recommended threshold of ${profile.minMoisture.toInt()}% for $displayCropName, risking moisture stress.",
                    "Initiate watering within 2–4 hours (prefer morning or late evening).",
                    "Apply drip/furrow irrigation until soil reaches ~${((profile.minMoisture + profile.maxMoisture) / 2).toInt()}% moisture. Avoid sudden heavy flooding.",
                    RecommendationPriority.HIGH,
                    "Moisture Deficit (Dry)",
                )
            }
            moisture > profile.maxMoisture -> {
                Hexuple(
                    "Pause irrigation and monitor field drainage",
                    "Soil moisture (${String.format(Locale.US, "%.0f", moisture)}%) exceeds the upper threshold of ${profile.maxMoisture.toInt()}% for $displayCropName. Saturated soil limits root oxygen.",
                    "Withhold watering for the next 24–48 hours until moisture recedes below ${profile.maxMoisture.toInt()}%.",
                    "Ensure drainage channels are clear to prevent water stagnation and root suffocation.",
                    if (humidity > profile.maxSafeHumidity) RecommendationPriority.HIGH else RecommendationPriority.MEDIUM,
                    "Excess Soil Moisture (Saturated)",
                )
            }
            else -> {
                Hexuple(
                    "No immediate irrigation required",
                    "Current soil moisture (${String.format(Locale.US, "%.0f", moisture)}%) is within the optimal range (${profile.minMoisture.toInt()}–${profile.maxMoisture.toInt()}%) for $displayCropName.",
                    "Recheck soil moisture within 12–24 hours.",
                    "Maintain the current watering schedule. Irrigate only when moisture approaches ${profile.minMoisture.toInt()}%.",
                    RecommendationPriority.LOW,
                    "Optimal Soil Moisture",
                )
            }
        }

        // 2. Evaluate Soil pH
        val phCondition = when {
            ph < profile.minPh -> "Acidic (pH ${String.format(Locale.US, "%.1f", ph)} — may restrict phosphorus and calcium uptake)"
            ph > profile.maxPh -> "Alkaline (pH ${String.format(Locale.US, "%.1f", ph)} — micronutrient availability like zinc/iron is limited)"
            else -> "Optimal (pH ${String.format(Locale.US, "%.1f", ph)} — ideal for nutrient assimilation)"
        }

        val soilConditionSummary = "$overallMoistureStatus (${String.format(Locale.US, "%.0f", moisture)}%) • $phCondition"

        // 3. Evaluate Environment (Temperature & Humidity)
        val tempAssessment = when {
            temp > profile.maxTemp -> "Elevated temperature (${String.format(Locale.US, "%.1f", temp)}°C) increases evapotranspiration"
            temp < profile.minTemp -> "Cooler ambient temperature (${String.format(Locale.US, "%.1f", temp)}°C) slows metabolic activity"
            else -> "Temperature (${String.format(Locale.US, "%.1f", temp)}°C) is in the favorable growth zone"
        }

        val humidityAssessment = when {
            humidity > profile.maxSafeHumidity -> "High humidity (${String.format(Locale.US, "%.0f", humidity)}%) increases foliar pathogen pressure"
            humidity < 35.0 -> "Low humidity (${String.format(Locale.US, "%.0f", humidity)}%) accelerates surface drying"
            else -> "Humidity (${String.format(Locale.US, "%.0f", humidity)}%) is within acceptable bounds"
        }

        val environmentSummary = "$tempAssessment. $humidityAssessment."

        // 4. Disease Prevention & Contextual Risk
        val hasActiveDisease = !diseaseName.isNullOrBlank() && !diseaseName.equals("Healthy", ignoreCase = true)
        val diseasePreventionSummary = when {
            hasActiveDisease && (humidity > 70.0 || moisture > profile.maxMoisture) -> {
                "Recent scan flagged '$diseaseName'. Current humidity (${String.format(Locale.US, "%.0f", humidity)}%) and moisture (${String.format(Locale.US, "%.0f", moisture)}%) create conditions that favor disease progression. Avoid overhead watering, inspect lower canopy leaves, and apply recommended bio-protective treatments."
            }
            hasActiveDisease -> {
                "Previous diagnosis detected '$diseaseName'. Soil moisture is currently manageable. Continue regular crop scouting and avoid wetting foliage during irrigation."
            }
            humidity > profile.maxSafeHumidity -> {
                "High relative humidity (${String.format(Locale.US, "%.0f", humidity)}%) may favor spore germination. ${profile.diseaseRiskNotes} Maintain row aeration and monitor foliage for early spotting."
            }
            else -> {
                "Current environmental parameters indicate low pathogen pressure. Continue routine field monitoring and maintain good sanitation."
            }
        }

        // 5. Crop Growth & Yield Guidance
        val cropGrowthGuidance = buildString {
            append(profile.growthAdvice)
            append(" Maintaining balanced moisture prevents root stress and supports optimal nutrient absorption.")
        }

        // 6. Overall Priority Calculation
        val finalPriority = when {
            moisturePriority == RecommendationPriority.HIGH || (hasActiveDisease && humidity > 75.0) -> RecommendationPriority.HIGH
            moisturePriority == RecommendationPriority.MEDIUM || ph < profile.minPh || ph > profile.maxPh || temp > profile.maxTemp -> RecommendationPriority.MEDIUM
            else -> RecommendationPriority.LOW
        }

        val overallCondition = when (finalPriority) {
            RecommendationPriority.HIGH -> if (moisture < profile.minMoisture) "Needs Attention: Moisture Deficit" else "Needs Attention: High Disease/Moisture Risk"
            RecommendationPriority.MEDIUM -> "Moderate Attention Required"
            RecommendationPriority.LOW -> "Suitable & Stable Conditions"
        }

        // 7. Immediate Action Summary (Action Now)
        val actionNow = buildString {
            append(wateringDecision)
            append(". ")
            append(wateringAction)
            if (hasActiveDisease) {
                append(" Monitor crop for '$diseaseName' symptoms.")
            }
        }

        return UnifiedAgriXRecommendation(
            cropName = displayCropName,
            overallCondition = overallCondition,
            priority = finalPriority,
            soilCondition = soilConditionSummary,
            wateringDecision = wateringDecision,
            wateringExplanation = wateringWhy,
            wateringTiming = wateringWhen,
            wateringAction = wateringAction,
            environmentAssessment = environmentSummary,
            diseasePrevention = diseasePreventionSummary,
            cropGrowthGuidance = cropGrowthGuidance,
            immediateActionSummary = actionNow,
            isCloudEnhanced = false,
        )
    }

    /**
     * Backward-compatible helper for legacy callers.
     */
    fun analyze(reading: SensorReading, cropName: String? = null): LocalSensorAnalysis {
        val unified = synthesizeUnifiedRecommendation(reading, cropName)
        val risks = mutableListOf<String>()
        if (reading.soilMoisture < 35.0) risks.add("Critical moisture deficit in root zone.")
        if (reading.soilMoisture > 65.0) risks.add("Excess soil moisture may limit aeration.")
        if (reading.soilPH < 5.8) risks.add("Acidic soil (pH ${String.format(Locale.US, "%.1f", reading.soilPH)}).")
        if (reading.soilPH > 7.5) risks.add("Alkaline soil (pH ${String.format(Locale.US, "%.1f", reading.soilPH)}).")
        if (reading.temperature > 32.0) risks.add("Thermal stress at ${String.format(Locale.US, "%.1f", reading.temperature)}°C.")
        if (reading.humidity > 80.0) risks.add("High humidity (${String.format(Locale.US, "%.0f", reading.humidity)}%) increases fungal risk.")
        if (risks.isEmpty()) risks.add("All physical parameters in standard range.")

        val priorityEnum = when (unified.priority) {
            RecommendationPriority.HIGH -> IrrigationPriority.HIGH
            RecommendationPriority.MEDIUM -> IrrigationPriority.MODERATE
            RecommendationPriority.LOW -> IrrigationPriority.NONE
        }

        return LocalSensorAnalysis(
            soilCondition = unified.soilCondition,
            irrigationRecommendation = unified.wateringDecision,
            irrigationPriority = priorityEnum,
            riskIndicators = risks,
            immediateAction = unified.wateringAction,
            confidenceExplanation = "Local rule engine synthesized recommendations for ${unified.cropName}.",
        )
    }

    private data class Hexuple<A, B, C, D, E, F>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F,
    )
}
