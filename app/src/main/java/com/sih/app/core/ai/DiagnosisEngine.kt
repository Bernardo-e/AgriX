package com.sih.app.core.ai

import android.graphics.Bitmap

enum class DiagnosisSource {
    REAL_TFLITE,
    DEMO_PROTOTYPE,
    HEALTHY_ASSESSMENT,
    IRRELEVANT_IMAGE,
    UNCERTAIN,
    CLOUD_AI,
}

data class DiagnosisEngineResult(
    val assessment: ImageAssessment,
    val diagnosticResult: DiagnosticResult?,
    val source: DiagnosisSource,
    val isHealthy: Boolean = (assessment == ImageAssessment.HEALTHY_CROP),
    val isIrrelevant: Boolean = (assessment == ImageAssessment.IRRELEVANT_IMAGE),
)

interface DiagnosisEngine {
    val modeName: String
    suspend fun diagnose(bitmap: Bitmap, selectedCrop: String?): Result<DiagnosisEngineResult>
}
