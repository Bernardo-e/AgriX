package com.sih.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.data.FarmRepository
import com.sih.app.core.database.FarmEntity
import com.sih.app.core.sensor.BleSensorRepository
import com.sih.app.core.sensor.LocalSensorAnalysis
import com.sih.app.core.sensor.LocalSensorEngine
import com.sih.app.core.sensor.SensorReading
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AiUiState(
    val farm: FarmEntity? = null,
    val latestReading: SensorReading? = null,
    val localAnalysis: LocalSensorAnalysis? = null,
)

class AiViewModel(
    farmRepository: FarmRepository,
    bleSensorRepository: BleSensorRepository,
    localSensorEngine: LocalSensorEngine,
) : ViewModel() {

    val uiState: StateFlow<AiUiState> = combine(
        farmRepository.getFarmFlow(),
        bleSensorRepository.latestReading,
    ) { farm, reading ->
        val localAnalysis = if (reading != null) {
            localSensorEngine.analyze(reading, farm?.currentCrop ?: "Tomato")
        } else {
            null
        }
        AiUiState(
            farm = farm,
            latestReading = reading,
            localAnalysis = localAnalysis,
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
            localSensorEngine: LocalSensorEngine,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AiViewModel(
                        farmRepository = farmRepository,
                        bleSensorRepository = bleSensorRepository,
                        localSensorEngine = localSensorEngine,
                    ) as T
                }
            }
    }
}
