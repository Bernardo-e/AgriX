package com.sih.app.core.ai

enum class ConfidenceBand {
    HIGH,      // >= 0.75
    MEDIUM,    // >= 0.50 and < 0.75
    LOW,       // >= 0.35 and < 0.50
    UNCERTAIN, // < 0.35
    PROTOTYPE_FALLBACK,
}

enum class DiagnosticStatus {
    CONFIDENT,
    MODERATE_CONFIDENCE,
    LOW_CONFIDENCE,
    UNKNOWN_OR_UNCERTAIN,
    PROTOTYPE_FALLBACK,
}

data class DiseasePrediction(
    val diseaseName: String,
    val confidence: Float,
    val classId: Int,
    val crop: String,
    val rank: Int = 1,
)

data class DiagnosticResult(
    val primaryPrediction: DiseasePrediction?,
    val topPredictions: List<DiseasePrediction>,
    val cropCompatiblePredictions: List<DiseasePrediction>,
    val status: DiagnosticStatus,
    val message: String,
    val selectedCrop: String?,
    val confidenceBand: ConfidenceBand,
    val isPrototypeFallback: Boolean = false,
    val rawModelTopPrediction: DiseasePrediction? = null,
    val fallbackNotice: String? = null,
)
