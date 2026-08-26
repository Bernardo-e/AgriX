package com.sih.app.core.ai.local

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sih.app.core.ai.AdvisoryRepository
import com.sih.app.core.ai.AiEngine
import com.sih.app.core.ai.AiEngineException
import com.sih.app.core.ai.AiEngineType
import com.sih.app.core.ai.AiResult
import com.sih.app.core.ai.DemoPrototypeDiagnosisEngine
import com.sih.app.core.ai.DiagnosisEngine
import com.sih.app.core.ai.DiagnosisSource
import com.sih.app.core.ai.DiagnosticStatus
import com.sih.app.core.ai.ImageAssessment
import com.sih.app.core.ai.PlantDiseaseClassifier
import com.sih.app.core.ai.RealTfliteDiagnosisEngine
import com.sih.app.ui.ai.ImageUtils

private const val TAG = "AgriX_LocalAI"

enum class DiagnosisMode {
    DEMO_PROTOTYPE,
    REAL_AI,
}

class LocalAiEngine(
    private val context: Context,
    val classifier: PlantDiseaseClassifier = TflitePlantDiseaseClassifier(context),
    val advisoryRepository: AdvisoryRepository = AdvisoryRepository(context),
    var mode: DiagnosisMode = DiagnosisMode.DEMO_PROTOTYPE,
) : AiEngine {

    override val type: AiEngineType = AiEngineType.LOCAL

    val demoEngine: DiagnosisEngine by lazy {
        DemoPrototypeDiagnosisEngine(advisoryRepository, classifier)
    }

    val realEngine: DiagnosisEngine by lazy {
        RealTfliteDiagnosisEngine(classifier)
    }

    override fun isAvailable(): Boolean {
        return classifier.isAvailable()
    }

    override suspend fun analyze(imageUri: Uri?, cropHint: String?): Result<AiResult> {
        if (imageUri == null) {
            return Result.failure(
                AiEngineException.AnalysisFailed("Image URI is required for local analysis.")
            )
        }

        val bitmap = ImageUtils.loadDownsampledBitmap(context, imageUri)
            ?: return Result.failure(
                AiEngineException.AnalysisFailed("Failed to decode leaf image.")
            )

        val activeEngine = if (mode == DiagnosisMode.DEMO_PROTOTYPE) demoEngine else realEngine
        val engineResult = activeEngine.diagnose(bitmap, cropHint)

        return engineResult.fold(
            onSuccess = { res ->
                val diag = res.diagnosticResult
                val primary = diag?.primaryPrediction
                val diseaseName = primary?.diseaseName ?: if (res.isIrrelevant) "Irrelevant Image" else "Unable to identify disease"
                val confidence = primary?.confidence ?: 0f

                val topSymptoms = diag?.topPredictions?.map {
                    "${it.rank}. ${it.diseaseName} (${(it.confidence * 100).toInt()}%)"
                } ?: emptyList()

                val severity = when {
                    res.isIrrelevant -> "Irrelevant Image"
                    res.isHealthy -> "Healthy Crop"
                    diag?.status == DiagnosticStatus.PROTOTYPE_FALLBACK -> "Prototype Guidance"
                    diag?.status == DiagnosticStatus.CONFIDENT -> "High Confidence"
                    diag?.status == DiagnosticStatus.MODERATE_CONFIDENCE -> "Moderate Confidence"
                    diag?.status == DiagnosticStatus.LOW_CONFIDENCE -> "Low Confidence"
                    else -> "Uncertain"
                }

                val aiResult = AiResult(
                    disease = diseaseName,
                    confidence = confidence,
                    severity = severity,
                    symptoms = topSymptoms,
                    recommendation = diag?.message ?: "Please capture a clear crop image.",
                    prevention = emptyList(),
                    engineType = AiEngineType.LOCAL,
                    diagnosticResult = diag,
                    assessment = res.assessment,
                    source = res.source,
                    isHealthy = res.isHealthy,
                    isIrrelevant = res.isIrrelevant,
                )
                Result.success(aiResult)
            },
            onFailure = { error ->
                Log.e(TAG, "Local AI analysis failed: ${error.message}", error)
                Result.failure(
                    AiEngineException.AnalysisFailed("Local AI analysis failed: ${error.message}", error)
                )
            }
        )
    }
}
