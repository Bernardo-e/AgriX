package com.sih.app.core.ai

import android.net.Uri
import android.util.Log
import com.sih.app.core.ai.cloud.CloudAiEngine

private const val TAG = "AgriX_AiRouter"

enum class AiRouterMode {
    AUTO,
    LOCAL,
    CLOUD,
}

class AiEngineRouter(
    private val localAiEngine: AiEngine,
    private val cloudAiEngine: AiEngine,
) {

    suspend fun analyze(
        imageUri: Uri?,
        mode: AiRouterMode = AiRouterMode.AUTO,
        cropHint: String? = null,
        language: String = "en",
        state: String? = null,
        district: String? = null,
    ): Result<AiResult> {
        return when (mode) {
            AiRouterMode.LOCAL -> {
                localAiEngine.analyze(imageUri, cropHint)
            }
            AiRouterMode.CLOUD -> {
                // If cloud mode explicitly requested, attempt cloud first, but fallback safely to local
                val cloudResult = if (cloudAiEngine.isAvailable()) {
                    if (cloudAiEngine is CloudAiEngine) {
                        cloudAiEngine.analyzeWithContext(
                            imageUri = imageUri,
                            cropHint = cropHint,
                            language = language,
                            state = state,
                            district = district,
                        )
                    } else {
                        cloudAiEngine.analyze(imageUri, cropHint)
                    }
                } else {
                    Result.failure(AiEngineException.Unavailable("Cloud AI is unavailable."))
                }

                cloudResult.fold(
                    onSuccess = { Result.success(it) },
                    onFailure = { cloudError ->
                        Log.w(TAG, "Cloud mode failed (${cloudError.message}), falling back to local AI.")
                        localAiEngine.analyze(imageUri, cropHint)
                    }
                )
            }
            AiRouterMode.AUTO -> {
                // Step 1: Execute primary on-device inference (100% offline-first)
                val localResult = localAiEngine.analyze(imageUri, cropHint)

                localResult.fold(
                    onSuccess = { localAi ->
                        // Step 2: Check if image is non-plant or clearly healthy -> return immediately
                        if (localAi.isIrrelevant || localAi.isHealthy) {
                            return@fold Result.success(localAi)
                        }

                        // Step 3: If local AI is high-confidence, return local immediately
                        if (localAi.confidence >= 0.75f && localAi.source == DiagnosisSource.REAL_TFLITE) {
                            return@fold Result.success(localAi)
                        }

                        // Step 4: If local AI is uncertain/moderate and cloud is available, attempt cloud enhancement
                        if (cloudAiEngine.isAvailable()) {
                            try {
                                val cloudResult = if (cloudAiEngine is CloudAiEngine) {
                                    cloudAiEngine.analyzeWithContext(
                                        imageUri = imageUri,
                                        cropHint = cropHint,
                                        localPrediction = localAi.diagnosticResult?.primaryPrediction,
                                        localStatus = localAi.diagnosticResult?.status?.name,
                                        language = language,
                                        state = state,
                                        district = district,
                                    )
                                } else {
                                    cloudAiEngine.analyze(imageUri, cropHint)
                                }

                                cloudResult.fold(
                                    onSuccess = { cloudAi ->
                                        Log.i(TAG, "Cloud AI enhanced diagnosis successfully obtained.")
                                        Result.success(cloudAi)
                                    },
                                    onFailure = { cloudErr ->
                                        Log.w(TAG, "Cloud AI failed (${cloudErr.message}); retaining local result.")
                                        Result.success(localAi)
                                    }
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Exception during Cloud AI call; retaining local result: ${e.message}")
                                Result.success(localAi)
                            }
                        } else {
                            // Cloud not available or offline -> return local result
                            Result.success(localAi)
                        }
                    },
                    onFailure = { localError ->
                        // If local AI execution failed, try cloud as backup if available
                        if (cloudAiEngine.isAvailable()) {
                            if (cloudAiEngine is CloudAiEngine) {
                                cloudAiEngine.analyzeWithContext(
                                    imageUri = imageUri,
                                    cropHint = cropHint,
                                    language = language,
                                    state = state,
                                    district = district,
                                )
                            } else {
                                cloudAiEngine.analyze(imageUri, cropHint)
                            }
                        } else {
                            Result.failure(localError)
                        }
                    }
                )
            }
        }
    }
}
