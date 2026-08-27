package com.sih.app.core.sensor

enum class RecommendationPriority {
    HIGH,
    MEDIUM,
    LOW,
}

enum class IrrigationPriority {
    HIGH,
    MODERATE,
    NONE,
}

data class UnifiedAgriXRecommendation(
    val cropName: String,
    val overallCondition: String,
    val priority: RecommendationPriority,
    val soilCondition: String,
    val wateringDecision: String,
    val wateringExplanation: String,
    val wateringTiming: String,
    val wateringAction: String,
    val environmentAssessment: String,
    val diseasePrevention: String,
    val cropGrowthGuidance: String,
    val immediateActionSummary: String,
    val isCloudEnhanced: Boolean = false,
)

data class LocalSensorAnalysis(
    val soilCondition: String,
    val irrigationRecommendation: String,
    val irrigationPriority: IrrigationPriority,
    val riskIndicators: List<String>,
    val immediateAction: String,
    val confidenceExplanation: String,
)

data class CloudSensorAnalysis(
    val provider: String,
    val model: String,
    val soilInterpretation: String,
    val cropImplications: String,
    val irrigationAdvice: String,
    val possibleRisks: List<String>,
    val recommendedNextAction: String,
    val farmerSummary: String,
    val latencyMs: Int,
    val overallCondition: String? = null,
    val priority: String? = null,
    val wateringDecision: String? = null,
    val wateringExplanation: String? = null,
    val wateringTiming: String? = null,
    val wateringAction: String? = null,
    val environmentAssessment: String? = null,
    val diseasePrevention: String? = null,
    val cropGrowthGuidance: String? = null,
    val actionNowSummary: String? = null,
)

data class CombinedSensorReport(
    val reading: SensorReading,
    val recommendation: UnifiedAgriXRecommendation,
    val localAnalysis: LocalSensorAnalysis,
    val cloudAnalysis: CloudSensorAnalysis? = null,
    val isCloudFallback: Boolean = false,
    val finalRecommendation: String = recommendation.immediateActionSummary,
)
