package com.sih.app.core.sensor

import java.util.Locale

/**
 * 100% Offline Crop-Aware & Soil-Context Agricultural Rule Engine.
 * Evaluates real-time sensor readings (Raw ADC, Calibrated Estimated VWC, pH, temp, humidity),
 * soil context (Sandy, Loamy, Clay with Field Capacity and Wilting Point),
 * target crop profiles, growth stages, and any previously diagnosed disease context.
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

        val soilProfile = SoilContextRegistry.getProfile(reading.soilType)
        val vwc = reading.estimatedVwc
        val awf = soilProfile.calculateAvailableWaterFraction(vwc)
        val ph = reading.soilPH
        val temp = reading.temperature
        val humidity = reading.humidity

        // 1. Evaluate Plant-Available Water (AWF) & Determine Water Status
        val isWetlandCrop = normalizedCropKey == "rice" || normalizedCropKey == "paddy"

        val (waterStatus, wateringDecision, wateringWhy, wateringWhen, wateringAction, moisturePriority) = when {
            // For Rice (wetland crop): requires saturated / standing water (AWF > 0.70)
            isWetlandCrop && awf < 0.70 -> {
                Hexuple(
                    "Moisture Deficit for Wetland Rice (AWF ${(awf * 100).toInt()}%)",
                    "Irrigate now to maintain standing water",
                    "Paddy rice requires saturated soil conditions (AWF > 70%). Current estimated VWC is ${String.format(Locale.US, "%.1f", vwc)}% in ${soilProfile.soilType} soil (AWF ${(awf * 100).toInt()}%).",
                    "Initiate field flooding / irrigation within 2–4 hours.",
                    "Apply continuous flooding or flush irrigation to reach optimal saturation.",
                    RecommendationPriority.HIGH,
                )
            }
            // Critically dry (AWF < 0.25)
            awf < 0.25 -> {
                Hexuple(
                    "Very Dry (Plant-Available Water < 25%)",
                    "Irrigate now to restore root zone moisture",
                    "Estimated VWC (${String.format(Locale.US, "%.1f", vwc)}%) is approaching Permanent Wilting Point (${soilProfile.wiltingPoint}%) in ${soilProfile.soilType} soil (FC = ${soilProfile.fieldCapacity}%). Available water fraction is critically low at ${(awf * 100).toInt()}%.",
                    "Initiate watering within 2–6 hours (prefer early morning or evening).",
                    "Apply controlled irrigation until soil moisture nears Field Capacity (${soilProfile.fieldCapacity}%). Avoid sudden excess flooding.",
                    RecommendationPriority.HIGH,
                )
            }
            // Moderately dry (AWF 0.25..0.50)
            awf in 0.25..0.50 -> {
                Hexuple(
                    "Moderately Dry / Approaching Stress (AWF ${(awf * 100).toInt()}%)",
                    "Plan irrigation within 6–12 hours",
                    "Soil moisture (${String.format(Locale.US, "%.1f", vwc)}%) is depleting towards the management allowable limit for $displayCropName in ${soilProfile.soilType} soil.",
                    "Irrigate within the next 6–12 hours before crop shows visible wilting signs.",
                    "Apply light to moderate irrigation to bring root-zone moisture to adequate level.",
                    RecommendationPriority.MEDIUM,
                )
            }
            // Adequate / Optimal (AWF 0.50..0.75)
            awf in 0.50..0.75 -> {
                Hexuple(
                    "Adequate Moisture (AWF ${(awf * 100).toInt()}%)",
                    "No immediate irrigation required",
                    "Current estimated VWC (${String.format(Locale.US, "%.1f", vwc)}%) is in the optimal plant-available range (AWF ${(awf * 100).toInt()}%) for $displayCropName in ${soilProfile.soilType} soil.",
                    "Recheck soil moisture within 12–24 hours.",
                    "Maintain current watering schedule and avoid over-irrigation.",
                    RecommendationPriority.LOW,
                )
            }
            // High moisture / Saturated (AWF > 0.75)
            else -> {
                Hexuple(
                    "High Moisture / Saturated (AWF ${(awf * 100).toInt()}%)",
                    "Pause irrigation and inspect field drainage",
                    "Soil moisture (${String.format(Locale.US, "%.1f", vwc)}%) is at or above Field Capacity (${soilProfile.fieldCapacity}%), which may limit root zone oxygenation.",
                    "Withhold watering for 24–48 hours until moisture recedes.",
                    "Inspect drainage channels to prevent waterlogging and root suffocation.",
                    if (humidity > profile.maxSafeHumidity) RecommendationPriority.HIGH else RecommendationPriority.LOW,
                )
            }
        }

        // 2. Evaluate Soil pH
        val phCondition = when {
            ph < profile.minPh -> "Acidic (pH ${String.format(Locale.US, "%.1f", ph)} — may restrict phosphorus and calcium uptake)"
            ph > profile.maxPh -> "Alkaline (pH ${String.format(Locale.US, "%.1f", ph)} — micronutrient availability like zinc/iron is limited)"
            else -> "Optimal (pH ${String.format(Locale.US, "%.1f", ph)} — ideal for nutrient assimilation)"
        }

        val soilConditionSummary = "$waterStatus (${String.format(Locale.US, "%.1f", vwc)}% VWC in ${soilProfile.soilType}) • $phCondition"

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
            hasActiveDisease && (humidity > 70.0 || vwc > profile.maxMoisture) -> {
                "Recent scan flagged '$diseaseName'. Current humidity (${String.format(Locale.US, "%.0f", humidity)}%) and moisture (${String.format(Locale.US, "%.1f", vwc)}%) create conditions that favor disease progression. Avoid overhead watering, inspect lower canopy leaves, and apply recommended bio-protective treatments."
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
            append(" Maintaining balanced moisture near Field Capacity (${soilProfile.fieldCapacity}%) prevents root stress and supports optimal nutrient absorption.")
        }

        // 6. Overall Priority Calculation
        val finalPriority = when {
            moisturePriority == RecommendationPriority.HIGH || (hasActiveDisease && humidity > 75.0) -> RecommendationPriority.HIGH
            moisturePriority == RecommendationPriority.MEDIUM || ph < profile.minPh || ph > profile.maxPh || temp > profile.maxTemp -> RecommendationPriority.MEDIUM
            else -> RecommendationPriority.LOW
        }

        val overallCondition = when (finalPriority) {
            RecommendationPriority.HIGH -> if (awf < 0.35) "Needs Attention: Moisture Deficit" else "Needs Attention: High Disease/Moisture Risk"
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
            waterStatus = waterStatus,
            availableWaterFraction = (awf * 100).toInt() / 100.0,
            fieldCapacity = soilProfile.fieldCapacity,
            wiltingPoint = soilProfile.wiltingPoint,
            soilType = soilProfile.soilType,
            rawAdc = reading.rawAdc,
            estimatedVwc = vwc,
        )
    }

    /**
     * Backward-compatible helper for legacy callers.
     */
    fun analyze(reading: SensorReading, cropName: String? = null): LocalSensorAnalysis {
        val unified = synthesizeUnifiedRecommendation(reading, cropName)
        val risks = mutableListOf<String>()
        if (unified.availableWaterFraction < 0.25) risks.add("Critical moisture deficit in root zone (AWF < 25%).")
        if (unified.availableWaterFraction > 0.85) risks.add("Excess soil moisture may limit root aeration.")
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
