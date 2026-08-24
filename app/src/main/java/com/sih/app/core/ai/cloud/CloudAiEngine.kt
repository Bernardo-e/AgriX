package com.sih.app.core.ai.cloud

import android.net.Uri
import com.sih.app.core.ai.AiEngine
import com.sih.app.core.ai.AiEngineException
import com.sih.app.core.ai.AiEngineType
import com.sih.app.core.ai.AiResult

class CloudAiEngine : AiEngine {

    override val type: AiEngineType = AiEngineType.CLOUD

    override fun isAvailable(): Boolean {
        // Secure cloud backend endpoint/token not configured in this slice
        return false
    }

    override suspend fun analyze(imageUri: Uri, cropHint: String?): Result<AiResult> {
        return Result.failure(
            AiEngineException.NotConfigured("Cloud AI backend is not configured yet.")
        )
    }
}
