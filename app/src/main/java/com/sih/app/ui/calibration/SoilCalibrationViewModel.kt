package com.sih.app.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.data.FarmRepository
import com.sih.app.core.database.FarmEntity
import com.sih.app.core.sensor.BleSensorRepository
import com.sih.app.core.sensor.CalibrationMetrics
import com.sih.app.core.sensor.CalibrationSample
import com.sih.app.core.sensor.SensorReading
import com.sih.app.core.sensor.SoilCalibrationEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SoilCalibrationUiState(
    val farm: FarmEntity? = null,
    val latestReading: SensorReading? = null,
    val selectedSoilType: String = "Loamy",
    val samples: List<CalibrationSample> = emptyList(),
    val metrics: CalibrationMetrics? = null,
    val isTrained: Boolean = true,
    val referenceVwcInput: String = "",
    val message: String? = null,
)

class SoilCalibrationViewModel(
    private val soilCalibrationEngine: SoilCalibrationEngine,
    private val bleSensorRepository: BleSensorRepository,
    private val farmRepository: FarmRepository,
) : ViewModel() {

    private val _selectedSoilType = MutableStateFlow("Loamy")
    private val _referenceInput = MutableStateFlow("")
    private val _statusMessage = MutableStateFlow<String?>(null)

    private val engineStateFlow = combine(
        soilCalibrationEngine.samplesFlow,
        soilCalibrationEngine.metricsFlow,
    ) { samples, metrics ->
        samples to metrics
    }

    private val inputStateFlow = combine(
        _selectedSoilType,
        _referenceInput,
        _statusMessage,
    ) { soilType, refInput, message ->
        Triple(soilType, refInput, message)
    }

    val uiState: StateFlow<SoilCalibrationUiState> = combine(
        farmRepository.getFarmFlow(),
        bleSensorRepository.latestReading,
        engineStateFlow,
        inputStateFlow,
    ) { farm, reading, (samples, metrics), (soilType, refInput, message) ->
        val activeSoilType = if (soilType.isNotBlank()) soilType else (farm?.soilType ?: "Loamy")
        SoilCalibrationUiState(
            farm = farm,
            latestReading = reading,
            selectedSoilType = activeSoilType,
            samples = samples,
            metrics = metrics,
            isTrained = metrics?.isTrained ?: true,
            referenceVwcInput = refInput,
            message = message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SoilCalibrationUiState(),
    )

    fun onSoilTypeSelected(soilType: String) {
        _selectedSoilType.value = soilType
    }

    fun onReferenceInputChange(input: String) {
        _referenceInput.value = input
    }

    fun addSample() {
        val input = _referenceInput.value.toDoubleOrNull()
        if (input == null || input !in 1.0..65.0) {
            _statusMessage.value = "Enter valid reference VWC (1.0 - 65.0%)"
            return
        }

        val reading = bleSensorRepository.latestReading.value
        val rawAdc = reading?.rawAdc ?: 1850
        val temp = reading?.temperature ?: 28.5
        val humidity = reading?.humidity ?: 62.0
        val soilType = _selectedSoilType.value

        soilCalibrationEngine.addSample(
            soilAdc = rawAdc,
            temperature = temp,
            humidity = humidity,
            soilType = soilType,
            referenceVwc = input,
            isDemo = false,
        )

        _referenceInput.value = ""
        _statusMessage.value = "✓ Sample added for $soilType (ADC: $rawAdc, Ref: $input%)"
    }

    fun trainModel() {
        val updatedMetrics = soilCalibrationEngine.updateModel()
        _statusMessage.value = "✓ Calibration updated: MAE ${updatedMetrics.meanAbsoluteError}%, R² ${updatedMetrics.rSquared}"
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    companion object {
        fun provideFactory(
            soilCalibrationEngine: SoilCalibrationEngine,
            bleSensorRepository: BleSensorRepository,
            farmRepository: FarmRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SoilCalibrationViewModel(
                        soilCalibrationEngine = soilCalibrationEngine,
                        bleSensorRepository = bleSensorRepository,
                        farmRepository = farmRepository,
                    ) as T
                }
            }
    }
}
