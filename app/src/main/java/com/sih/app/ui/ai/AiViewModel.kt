package com.sih.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.data.DiagnosisRepository
import com.sih.app.core.data.FarmRepository
import com.sih.app.core.database.DiagnosisEntity
import com.sih.app.core.database.FarmEntity
import com.sih.app.core.sensor.BleSensorRepository
import com.sih.app.core.sensor.LocalSensorEngine
import com.sih.app.core.sensor.SensorReading
import com.sih.app.core.sensor.UnifiedAgriXRecommendation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AiUiState(
    val farm: FarmEntity? = null,
    val latestReading: SensorReading? = null,
    val latestDiagnosis: DiagnosisEntity? = null,
    val recommendation: UnifiedAgriXRecommendation? = null,
)

class AiViewModel(
    farmRepository: FarmRepository,
    bleSensorRepository: BleSensorRepository,
    diagnosisRepository: DiagnosisRepository,
    localSensorEngine: LocalSensorEngine,
) : ViewModel() {

    val uiState: StateFlow<AiUiState> = combine(
        farmRepository.getFarmFlow(),
        bleSensorRepository.latestReading,
        diagnosisRepository.getLatestDiagnosisFlow(),
    ) { farm, reading, latestDiag ->
        val cropName = farm?.currentCrop ?: latestDiag?.cropName ?: "Tomato"
        val recommendation = if (reading != null) {
            localSensorEngine.synthesizeUnifiedRecommendation(
                reading = reading,
                cropName = cropName,
                diseaseName = latestDiag?.diseaseName,
                diseaseConfidence = latestDiag?.confidence,
                diseaseStatus = latestDiag?.diagnosticStatus,
            )
        } else {
            null
        }

        AiUiState(
            farm = farm,
            latestReading = reading,
            latestDiagnosis = latestDiag,
            recommendation = recommendation,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiUiState(),
    )

    companion object {
        fun provideFactory(
            farmRepository: FarmRepository,
            bleSensorRepository: BleSensorRepository,
            diagnosisRepository: DiagnosisRepository,
            localSensorEngine: LocalSensorEngine,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AiViewModel(
                        farmRepository = farmRepository,
                        bleSensorRepository = bleSensorRepository,
                        diagnosisRepository = diagnosisRepository,
                        localSensorEngine = localSensorEngine,
                    ) as T
                }
            }
    }
}
