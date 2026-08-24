package com.sih.app.core.ai.local

import android.content.Context
import android.net.Uri
import android.util.Log
import com.sih.app.core.ai.AiEngine
import com.sih.app.core.ai.AiEngineException
import com.sih.app.core.ai.AiEngineType
import com.sih.app.core.ai.AiResult

private const val TAG = "AgriX_LocalAI"
private const val LOCAL_MODEL_FILENAME = "disease_model.tflite"

class LocalAiEngine(
    private val context: Context,
) : AiEngine {

    override val type: AiEngineType = AiEngineType.LOCAL

    override fun isAvailable(): Boolean {
        return try {
            val assetsList = context.assets.list("") ?: emptyArray()
            assetsList.contains(LOCAL_MODEL_FILENAME)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking asset model availability: ${e.message}")
            false
        }
    }

    override suspend fun analyze(imageUri: Uri, cropHint: String?): Result<AiResult> {
        if (!isAvailable()) {
            Log.d(TAG, "Local disease model ($LOCAL_MODEL_FILENAME) not found in assets. Returning failure.")
            return Result.failure(
                AiEngineException.Unavailable("Local disease model is not available yet.")
            )
        }
        return Result.failure(
            AiEngineException.NotConfigured("Local disease model inference engine pending model initialization.")
        )
    }
}
