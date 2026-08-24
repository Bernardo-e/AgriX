package com.sih.app.core.ai

sealed class AiEngineException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Unavailable(message: String = "AI engine is not available") : AiEngineException(message)
    class NotConfigured(message: String = "AI engine is not configured") : AiEngineException(message)
    class AnalysisFailed(message: String = "AI analysis failed", cause: Throwable? = null) : AiEngineException(message, cause)
}
