package com.sih.app.ui.ai

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.ai.AiAnalysisState
import com.sih.app.core.ai.AiEngineException
import com.sih.app.core.ai.AiEngineRouter
import com.sih.app.core.ai.AiRouterMode
import com.sih.app.core.data.FarmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CropDiseaseScanViewModel(
    private val aiEngineRouter: AiEngineRouter,
    farmRepository: FarmRepository,
) : ViewModel() {

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _selectedAiMode = MutableStateFlow(AiRouterMode.AUTO)
    val selectedAiMode: StateFlow<AiRouterMode> = _selectedAiMode.asStateFlow()

    private val _aiAnalysisState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val aiAnalysisState: StateFlow<AiAnalysisState> = _aiAnalysisState.asStateFlow()

    val cropHint: StateFlow<String?> = farmRepository.getFarmFlow()
        .map { it?.currentCrop }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun onImageSelected(uri: Uri?) {
        _selectedImageUri.value = uri
        _aiAnalysisState.value = AiAnalysisState.Idle
    }

    fun onClearImage() {
        _selectedImageUri.value = null
        _aiAnalysisState.value = AiAnalysisState.Idle
    }

    fun onAiModeSelected(mode: AiRouterMode) {
        _selectedAiMode.value = mode
    }

    fun analyzeCropPhoto() {
        val imageUri = _selectedImageUri.value ?: return

        viewModelScope.launch {
            _aiAnalysisState.value = AiAnalysisState.Analyzing
            val result = aiEngineRouter.analyze(
                imageUri = imageUri,
                mode = _selectedAiMode.value,
                cropHint = cropHint.value,
            )

            result.fold(
                onSuccess = { aiResult ->
                    _aiAnalysisState.value = AiAnalysisState.Success(aiResult)
                },
                onFailure = { exception ->
                    when (exception) {
                        is AiEngineException.Unavailable,
                        is AiEngineException.NotConfigured -> {
                            _aiAnalysisState.value = AiAnalysisState.Unavailable(
                                exception.message ?: "AI model not connected yet."
                            )
                        }
                        else -> {
                            _aiAnalysisState.value = AiAnalysisState.Error(
                                exception.message ?: "AI analysis failed."
                            )
                        }
                    }
                },
            )
        }
    }

    companion object {
        fun provideFactory(
            aiEngineRouter: AiEngineRouter,
            farmRepository: FarmRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CropDiseaseScanViewModel(aiEngineRouter, farmRepository) as T
                }
            }
    }
}
