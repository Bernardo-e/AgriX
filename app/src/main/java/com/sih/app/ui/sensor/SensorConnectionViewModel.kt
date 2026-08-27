package com.sih.app.ui.sensor

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.data.FarmRepository
import com.sih.app.core.data.api.cloud.CloudAiClient
import com.sih.app.core.data.api.cloud.CloudSensorRequestData
import com.sih.app.core.locale.LanguageStore
import com.sih.app.core.sensor.BleConnectionState
import com.sih.app.core.sensor.BleDevice
import com.sih.app.core.sensor.BleSensorRepository
import com.sih.app.core.sensor.CloudSensorAnalysis
import com.sih.app.core.sensor.CombinedSensorReport
import com.sih.app.core.sensor.LocalSensorEngine
import com.sih.app.core.sensor.SensorState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "AgriX_SensorVM"

class SensorConnectionViewModel(
    private val bleSensorRepository: BleSensorRepository,
    private val localSensorEngine: LocalSensorEngine,
    private val cloudAiClient: CloudAiClient,
    private val farmRepository: FarmRepository,
    private val languageStore: LanguageStore,
) : ViewModel() {

    // Single source of truth for the 11-stage Sensor State Machine
    val sensorState: StateFlow<SensorState> = bleSensorRepository.sensorState
    val connectionState: StateFlow<BleConnectionState> = bleSensorRepository.connectionState
    val isScanning: StateFlow<Boolean> = bleSensorRepository.isScanning

    fun startScan() {
        bleSensorRepository.startScan()
    }

    fun stopScan() {
        bleSensorRepository.stopScan()
    }

    fun connect(device: BleDevice) {
        bleSensorRepository.connect(device)
    }

    fun disconnect() {
        bleSensorRepository.disconnect()
    }

    fun resetDemo() {
        bleSensorRepository.resetDemo()
    }

    fun isBluetoothAvailable(): Boolean {
        return bleSensorRepository.isBluetoothAvailable()
    }

    fun hasRequiredPermissions(): Boolean {
        return bleSensorRepository.hasRequiredPermissions()
    }

    fun performSoilScan() {
        viewModelScope.launch {
            val currentState = bleSensorRepository.sensorState.value
            val device = when (currentState) {
                is SensorState.ConnectedDemo -> currentState.device
                is SensorState.ResultReady -> currentState.device
                is SensorState.DataReady -> currentState.device
                is SensorState.AnalyzingLocal -> currentState.device
                is SensorState.AnalyzingCloud -> currentState.device
                else -> BleDevice("AgriX Sensor", "DEMO:BLE:AGRIX:01", -55, isDemo = true)
            }

            try {
                // 1. Multi-stage simulated BLE telemetry acquisition (2-4 seconds)
                val reading = bleSensorRepository.acquireSoilTelemetry(device) { stepName, progress ->
                    // Progress emitted directly into sensorState by repository
                }

                // 2. Fetch farm profile context for tailored analysis
                val farm = farmRepository.getFarm()
                val cropName = farm?.currentCrop ?: "Tomato"
                val soilType = farm?.soilType ?: "Loamy"
                val languageTag = languageStore.getLanguageTag().ifBlank { "en" }

                // 3. Instant Local Agricultural Rule Engine Evaluation (100% Offline)
                bleSensorRepository.updateSensorState(SensorState.AnalyzingLocal(device, reading))
                val localAnalysis = localSensorEngine.analyze(reading, cropName)

                // 4. Companion Cloud AI Escalation (FastAPI -> Gemini)
                bleSensorRepository.updateSensorState(SensorState.AnalyzingCloud(device, reading, localAnalysis))
                var cloudAnalysis: CloudSensorAnalysis? = null
                var isCloudFallback = false

                try {
                    val cloudReq = CloudSensorRequestData(
                        source = reading.source,
                        temperature = reading.temperature,
                        humidity = reading.humidity,
                        soilMoisture = reading.soilMoisture,
                        soilPH = reading.soilPH,
                        cropName = cropName,
                        soilType = soilType,
                        language = languageTag,
                    )
                    val cloudResult = cloudAiClient.performCloudSensorAnalysis(cloudReq)
                    cloudResult.fold(
                        onSuccess = { analysis ->
                            cloudAnalysis = analysis
                            Log.d(TAG, "Cloud sensor analysis succeeded via ${analysis.provider}")
                        },
                        onFailure = { err ->
                            Log.w(TAG, "Cloud sensor analysis unavailable: ${err.message}. Using local rule fallback.")
                            isCloudFallback = true
                        }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud sensor dispatch error: ${e.message}. Using local rule fallback.")
                    isCloudFallback = true
                }

                // 5. Harmonize Final AgriX Recommendation
                val finalRec = if (cloudAnalysis != null && !cloudAnalysis!!.farmerSummary.isNullOrBlank()) {
                    cloudAnalysis!!.farmerSummary
                } else {
                    buildString {
                        append("Current soil moisture is ${reading.soilMoisture.toInt()}% (${localAnalysis.soilCondition.lowercase()}). ")
                        append(localAnalysis.immediateAction)
                        append(" Soil pH is ${reading.soilPH} which is ${if (reading.soilPH in 5.8..7.5) "within a generally suitable range" else "outside ideal range"}.")
                    }
                }

                val report = CombinedSensorReport(
                    reading = reading,
                    localAnalysis = localAnalysis,
                    cloudAnalysis = cloudAnalysis,
                    isCloudFallback = isCloudFallback || cloudAnalysis == null,
                    finalRecommendation = finalRec,
                )

                bleSensorRepository.updateSensorState(
                    SensorState.ResultReady(
                        device = device,
                        report = report,
                        isCloudFallback = isCloudFallback || cloudAnalysis == null,
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Soil scan failed: ${e.message}", e)
                // Fallback to connected demo state so user can retry easily
                bleSensorRepository.updateSensorState(SensorState.ConnectedDemo(device))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleSensorRepository.stopScan()
    }

    companion object {
        fun provideFactory(
            bleSensorRepository: BleSensorRepository,
            localSensorEngine: LocalSensorEngine,
            cloudAiClient: CloudAiClient,
            farmRepository: FarmRepository,
            languageStore: LanguageStore,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SensorConnectionViewModel(
                        bleSensorRepository = bleSensorRepository,
                        localSensorEngine = localSensorEngine,
                        cloudAiClient = cloudAiClient,
                        farmRepository = farmRepository,
                        languageStore = languageStore,
                    ) as T
                }
            }
    }
}
