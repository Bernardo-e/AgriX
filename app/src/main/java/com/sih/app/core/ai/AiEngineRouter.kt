package com.sih.app.core.ai

import android.net.Uri

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
        imageUri: Uri,
        mode: AiRouterMode = AiRouterMode.AUTO,
        cropHint: String? = null,
    ): Result<AiResult> {
        return when (mode) {
            AiRouterMode.LOCAL -> {
                localAiEngine.analyze(imageUri, cropHint)
            }
            AiRouterMode.CLOUD -> {
                cloudAiEngine.analyze(imageUri, cropHint)
            }
            AiRouterMode.AUTO -> {
                if (localAiEngine.isAvailable()) {
                    localAiEngine.analyze(imageUri, cropHint)
                } else if (cloudAiEngine.isAvailable()) {
                    cloudAiEngine.analyze(imageUri, cropHint)
                } else {
                    Result.failure(
                        AiEngineException.Unavailable("AI model is not connected or configured yet.")
                    )
                }
            }
        }
    }
}
