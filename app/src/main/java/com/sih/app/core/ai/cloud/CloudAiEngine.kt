package com.sih.app.core.ai.cloud

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.sih.app.core.ai.AiEngine
import com.sih.app.core.ai.AiEngineException
import com.sih.app.core.ai.AiEngineType
import com.sih.app.core.ai.AiResult
import com.sih.app.core.ai.ConfidenceBand
import com.sih.app.core.ai.DiagnosisSource
import com.sih.app.core.ai.DiagnosticResult
import com.sih.app.core.ai.DiagnosticStatus
import com.sih.app.core.ai.DiseasePrediction
import com.sih.app.core.ai.ImageAssessment
import com.sih.app.core.data.api.cloud.CloudAiClient
import com.sih.app.core.data.api.cloud.CloudDiagnosisRequestData
import com.sih.app.core.data.api.cloud.HttpCloudAiClient
import com.sih.app.ui.ai.ImageUtils
import java.io.ByteArrayOutputStream

private const val TAG = "AgriX_CloudAI"

class CloudAiEngine(
    private val context: Context? = null,
    private val client: CloudAiClient = HttpCloudAiClient(),
    var isEnabled: Boolean = true,
) : AiEngine {

    override val type: AiEngineType = AiEngineType.CLOUD

    override fun isAvailable(): Boolean {
        return isEnabled
    }

    override suspend fun analyze(imageUri: Uri?, cropHint: String?): Result<AiResult> {
        return analyzeWithContext(
            imageUri = imageUri,
            cropHint = cropHint,
            localPrediction = null,
            localStatus = null,
            language = "en",
            state = null,
            district = null,
        )
    }

    suspend fun analyzeWithContext(
        imageUri: Uri?,
        cropHint: String?,
        localPrediction: DiseasePrediction? = null,
        localStatus: String? = null,
        language: String = "en",
        state: String? = null,
        district: String? = null,
    ): Result<AiResult> {
        if (!isEnabled) {
            return Result.failure(AiEngineException.Unavailable("Cloud AI is currently disabled."))
        }

        if (imageUri == null) {
            return Result.failure(
                AiEngineException.AnalysisFailed("Image URI is required for cloud analysis.")
            )
        }

        val ctx = context ?: return Result.failure(
            AiEngineException.AnalysisFailed("Context is required for loading image.")
        )

        val bitmap = ImageUtils.loadDownsampledBitmap(ctx, imageUri)
            ?: return Result.failure(
                AiEngineException.AnalysisFailed("Failed to decode leaf image for cloud analysis.")
            )

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val imageBytes = stream.toByteArray()

        val request = CloudDiagnosisRequestData(
            imageBytes = imageBytes,
            cropId = cropHint?.lowercase() ?: "tomato",
            localDiseaseId = localPrediction?.classId,
            localConfidence = localPrediction?.confidence,
            localStatus = localStatus,
            language = language,
            state = state,
            district = district,
        )

        val clientResult = client.performCloudDiagnosis(request)

        return clientResult.fold(
            onSuccess = { response ->
                val diagInfo = response.diagnosis
                val statusEnum = when (diagInfo.diagnosticStatus.uppercase()) {
                    "CONFIDENT" -> DiagnosticStatus.CONFIDENT
                    "MODERATE_CONFIDENCE" -> DiagnosticStatus.MODERATE_CONFIDENCE
                    "LOW_CONFIDENCE" -> DiagnosticStatus.LOW_CONFIDENCE
                    else -> DiagnosticStatus.MODERATE_CONFIDENCE
                }

                val confBand = when {
                    diagInfo.confidence >= 0.75f -> ConfidenceBand.HIGH
                    diagInfo.confidence >= 0.50f -> ConfidenceBand.MEDIUM
                    diagInfo.confidence >= 0.35f -> ConfidenceBand.LOW
                    else -> ConfidenceBand.UNCERTAIN
                }

                val primaryPrediction = DiseasePrediction(
                    diseaseName = diagInfo.disease.name,
                    confidence = diagInfo.confidence,
                    classId = diagInfo.disease.id,
                    crop = diagInfo.crop.name,
                    rank = 1,
                )

                val diagnosticResult = DiagnosticResult(
                    primaryPrediction = primaryPrediction,
                    topPredictions = listOf(primaryPrediction),
                    cropCompatiblePredictions = listOf(primaryPrediction),
                    status = statusEnum,
                    message = response.visualReasoning.ifBlank { response.advisory.overview },
                    selectedCrop = diagInfo.crop.name,
                    confidenceBand = confBand,
                    isPrototypeFallback = false,
                )

                val aiResult = AiResult(
                    disease = diagInfo.disease.name,
                    confidence = diagInfo.confidence,
                    severity = if (diagInfo.confidence >= 0.75f) "High Confidence" else "Moderate Confidence",
                    symptoms = response.advisory.symptoms,
                    recommendation = response.visualReasoning.ifBlank { response.advisory.overview },
                    prevention = response.advisory.prevention,
                    engineType = AiEngineType.CLOUD,
                    diagnosticResult = diagnosticResult,
                    assessment = ImageAssessment.PLANT_RELEVANT,
                    source = DiagnosisSource.CLOUD_AI,
                    isHealthy = false,
                    isIrrelevant = false,
                )
                Result.success(aiResult)
            },
            onFailure = { error ->
                Log.w(TAG, "Cloud AI diagnosis failed: ${error.message}", error)
                Result.failure(
                    AiEngineException.AnalysisFailed("Cloud AI diagnosis failed: ${error.message}", error)
                )
            }
        )
    }
}
