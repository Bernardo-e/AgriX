package com.sih.app.core.ai

data class AiResult(
    val disease: String,
    val confidence: Float,
    val severity: String? = null,
    val symptoms: List<String> = emptyList(),
    val recommendation: String? = null,
    val prevention: List<String> = emptyList(),
    val engineType: AiEngineType? = null,
)
