package com.sih.app.ui.ai

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.ai.AdvisoryRepository
import com.sih.app.core.ai.AdvisoryResult
import com.sih.app.core.ai.AiAnalysisState
import com.sih.app.core.ai.AiEngineException
import com.sih.app.core.ai.AiEngineRouter
import com.sih.app.core.ai.AiRouterMode
import com.sih.app.core.ai.DiagnosisSource
import com.sih.app.core.ai.ImageAssessment
import com.sih.app.core.ai.PlantDiseaseClassifier
import com.sih.app.core.data.DiagnosisRepository
import com.sih.app.core.data.FarmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

import com.sih.app.core.locale.LanguageStore

class CropDiseaseScanViewModel(
    private val aiEngineRouter: AiEngineRouter,
    private val classifier: PlantDiseaseClassifier,
    private val farmRepository: FarmRepository,
    private val diagnosisRepository: DiagnosisRepository? = null,
    private val advisoryRepository: AdvisoryRepository? = null,
    private val languageStore: LanguageStore? = null,
) : ViewModel() {

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _selectedAiMode = MutableStateFlow(AiRouterMode.AUTO)
    val selectedAiMode: StateFlow<AiRouterMode> = _selectedAiMode.asStateFlow()

    private val _selectedCrop = MutableStateFlow<String?>(null)
    val selectedCrop: StateFlow<String?> = _selectedCrop.asStateFlow()

    private val _aiAnalysisState = MutableStateFlow<AiAnalysisState>(AiAnalysisState.Idle)
    val aiAnalysisState: StateFlow<AiAnalysisState> = _aiAnalysisState.asStateFlow()

    private val _advisoryResult = MutableStateFlow<AdvisoryResult?>(null)
    val advisoryResult: StateFlow<AdvisoryResult?> = _advisoryResult.asStateFlow()

    val supportedCrops: List<String> = classifier.getSupportedCrops()

    init {
        viewModelScope.launch {
            val farm = farmRepository.getFarmFlow().firstOrNull()
            if (farm != null && !farm.currentCrop.isNullOrBlank()) {
                _selectedCrop.value = farm.currentCrop
            }
        }
    }

    fun onImageSelected(uri: Uri?) {
        _selectedImageUri.value = uri
        _aiAnalysisState.value = AiAnalysisState.Idle
        _advisoryResult.value = null
    }

    fun onClearImage() {
        _selectedImageUri.value = null
        _aiAnalysisState.value = AiAnalysisState.Idle
        _advisoryResult.value = null
    }

    fun onCropSelected(crop: String?) {
        _selectedCrop.value = crop
    }

    fun onAiModeSelected(mode: AiRouterMode) {
        _selectedAiMode.value = mode
    }

    fun analyzeCropPhoto() {
        val imageUri = _selectedImageUri.value ?: return

        viewModelScope.launch {
            _aiAnalysisState.value = AiAnalysisState.Analyzing
            _advisoryResult.value = null

            val farm = farmRepository.getFarm()
            val language = languageStore?.getLanguageTag() ?: "en"

            val result = aiEngineRouter.analyze(
                imageUri = imageUri,
                mode = _selectedAiMode.value,
                cropHint = _selectedCrop.value,
                language = language,
                state = farm?.state,
                district = farm?.district,
            )

            result.fold(
                onSuccess = { aiResult ->
                    _aiAnalysisState.value = AiAnalysisState.Success(aiResult)

                    val diagResult = aiResult.diagnosticResult
                    if (diagResult != null && advisoryRepository != null) {
                        _advisoryResult.value = advisoryRepository.getAdvisoryForDiagnosticResult(diagResult)
                    }

                    // Offline-First Diagnosis Recording (Only when not irrelevant)
                    val primary = diagResult?.primaryPrediction
                    if (!aiResult.isIrrelevant && primary != null && diagnosisRepository != null) {
                        val sourceStr = when (aiResult.source) {
                            DiagnosisSource.HEALTHY_ASSESSMENT -> "healthy_assessment"
                            DiagnosisSource.DEMO_PROTOTYPE -> "demo_prototype"
                            DiagnosisSource.REAL_TFLITE -> "on_device_tflite"
                            DiagnosisSource.CLOUD_AI -> "cloud_ai"
                            else -> if (aiResult.isHealthy) "healthy_assessment" else if (diagResult.isPrototypeFallback) "demo_prototype" else "on_device_tflite"
                        }
                        val statusStr = if (aiResult.isHealthy) "HEALTHY_CROP" else diagResult.status.name
                        launch {
                            diagnosisRepository.recordLocalDiagnosis(
                                cropId = primary.crop,
                                cropName = primary.crop.replaceFirstChar { it.uppercase() },
                                diseaseId = primary.classId,
                                diseaseName = primary.diseaseName,
                                confidence = primary.confidence,
                                diagnosticStatus = statusStr,
                                source = sourceStr,
                                imageId = imageUri.lastPathSegment,
                            )
                        }
                    }
                },
                onFailure = { exception ->
                    _advisoryResult.value = null
                    when (exception) {
                        is AiEngineException.Unavailable,
                        is AiEngineException.NotConfigured -> {
                            _aiAnalysisState.value = AiAnalysisState.Unavailable(
                                exception.message ?: "AI model not available."
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
            classifier: PlantDiseaseClassifier,
            farmRepository: FarmRepository,
            diagnosisRepository: DiagnosisRepository? = null,
            advisoryRepository: AdvisoryRepository? = null,
            languageStore: LanguageStore? = null,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CropDiseaseScanViewModel(
                        aiEngineRouter = aiEngineRouter,
                        classifier = classifier,
                        farmRepository = farmRepository,
                        diagnosisRepository = diagnosisRepository,
                        advisoryRepository = advisoryRepository,
                        languageStore = languageStore,
                    ) as T
                }
            }
    }
}
