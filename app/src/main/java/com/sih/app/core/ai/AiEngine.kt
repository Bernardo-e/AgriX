package com.sih.app.core.ai

import android.net.Uri

interface AiEngine {
    val type: AiEngineType
    suspend fun analyze(imageUri: Uri?, cropHint: String? = null): Result<AiResult>
    fun isAvailable(): Boolean
}
