package com.sih.app.core.ai

sealed interface AiAnalysisState {
    data object Idle : AiAnalysisState
    data object Analyzing : AiAnalysisState
    data class Success(val result: AiResult) : AiAnalysisState
    data class Unavailable(val message: String) : AiAnalysisState
    data class Error(val message: String) : AiAnalysisState
}
