package com.sih.app.core.ai

/**
 * Offline Advisory domain models for AgriX Stage 9 & Stage 12.
 */
data class Advisory(
    val diseaseId: Int,
    val cropId: String,
    val diseaseName: String,
    val overview: String,
    val symptoms: List<String>,
    val immediateActions: List<String>,
    val prevention: List<String>,
    val monitoring: List<String>,
    val expertEscalation: String,
    val safetyNote: String,
    val isPrototypeFallback: Boolean = false,
)

enum class AdvisoryConfidenceLevel {
    CONFIDENT,
    MODERATE,
    LOW,
    UNCERTAIN,
    PROTOTYPE_FALLBACK,
    HEALTHY,
}

data class AdvisoryPresentation(
    val confidenceLevel: AdvisoryConfidenceLevel,
    val title: String,
    val noticeMessage: String? = null,
    val cropId: String,
    val cropName: String,
    val diseaseId: Int?,
    val diseaseName: String?,
    val overview: String?,
    val symptoms: List<String>,
    val immediateActions: List<String>,
    val prevention: List<String>,
    val monitoring: List<String>,
    val expertEscalation: String?,
    val safetyNote: String?,
    val isActionable: Boolean = true,
    val isPrototypeFallback: Boolean = false,
    val fallbackNotice: String? = null,
    val isHealthy: Boolean = false,
)

sealed class AdvisoryResult {
    data class Available(val presentation: AdvisoryPresentation) : AdvisoryResult()
    data class Healthy(
        val cropName: String,
        val message: String,
        val monitoringGuidance: List<String>,
    ) : AdvisoryResult()
    data class Uncertain(
        val message: String,
        val generalGuidance: List<String>,
        val safetyNote: String,
    ) : AdvisoryResult()
    data class Unavailable(val reason: String) : AdvisoryResult()
}
