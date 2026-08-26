package com.sih.app.core.ai

import android.graphics.Bitmap

class RealTfliteDiagnosisEngine(
    private val classifier: PlantDiseaseClassifier,
) : DiagnosisEngine {

    override val modeName: String = "REAL_TFLITE"

    override suspend fun diagnose(
        bitmap: Bitmap,
        selectedCrop: String?,
    ): Result<DiagnosisEngineResult> {
        val assessment = PlantRelevanceAssessor.assess(bitmap)

        if (assessment == ImageAssessment.IRRELEVANT_IMAGE) {
            return Result.success(
                DiagnosisEngineResult(
                    assessment = ImageAssessment.IRRELEVANT_IMAGE,
                    diagnosticResult = null,
                    source = DiagnosisSource.UNCERTAIN,
                    isIrrelevant = true,
                )
            )
        }

        val realResult = classifier.classify(bitmap, selectedCrop)
        return realResult.map { diag ->
            DiagnosisEngineResult(
                assessment = assessment,
                diagnosticResult = diag,
                source = if (diag.isPrototypeFallback) DiagnosisSource.DEMO_PROTOTYPE else DiagnosisSource.REAL_TFLITE,
                isHealthy = (assessment == ImageAssessment.HEALTHY_CROP),
            )
        }
    }
}
