package com.sih.app.core.sensor

enum class IrrigationPriority {
    HIGH,
    MODERATE,
    NONE,
}

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
)

data class CombinedSensorReport(
    val reading: SensorReading,
    val localAnalysis: LocalSensorAnalysis,
    val cloudAnalysis: CloudSensorAnalysis? = null,
    val isCloudFallback: Boolean = false,
    val finalRecommendation: String,
)
