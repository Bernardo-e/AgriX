package com.sih.app.ui.sensor

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.data.DiagnosisRepository
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
import com.sih.app.core.sensor.RecommendationPriority
import com.sih.app.core.sensor.SensorState
import com.sih.app.core.sensor.UnifiedAgriXRecommendation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "AgriX_SensorVM"

class SensorConnectionViewModel(
    private val bleSensorRepository: BleSensorRepository,
    private val localSensorEngine: LocalSensorEngine,
    private val cloudAiClient: CloudAiClient,
    private val farmRepository: FarmRepository,
    private val diagnosisRepository: DiagnosisRepository,
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

                // 2. Fetch farm profile and latest disease diagnosis for unified context
                val farm = farmRepository.getFarm()
                val latestDiag = diagnosisRepository.getLatestDiagnosis()
                val cropName = farm?.currentCrop ?: latestDiag?.cropName ?: "Tomato"
                val soilType = farm?.soilType ?: "Loamy"
                val diseaseName = latestDiag?.diseaseName
                val diseaseConfidence = latestDiag?.confidence
                val diseaseStatus = latestDiag?.diagnosticStatus
                val languageTag = languageStore.getLanguageTag().ifBlank { "en" }

                // 3. Instant Local Agricultural Rule Engine Evaluation (100% Offline)
                bleSensorRepository.updateSensorState(SensorState.AnalyzingLocal(device, reading))
                val localAnalysis = localSensorEngine.analyze(reading, cropName)
                val localUnified = localSensorEngine.synthesizeUnifiedRecommendation(
                    reading = reading,
                    cropName = cropName,
                    diseaseName = diseaseName,
                    diseaseConfidence = diseaseConfidence,
                    diseaseStatus = diseaseStatus,
                )

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
                        diseaseName = diseaseName,
                        diseaseConfidence = diseaseConfidence,
                        diseaseStatus = diseaseStatus,
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

                // 5. Harmonize into ONE Unified AgriX Recommendation
                val finalUnifiedRec = if (cloudAnalysis != null && !cloudAnalysis!!.wateringDecision.isNullOrBlank()) {
                    val parsedPriority = runCatching {
                        RecommendationPriority.valueOf(cloudAnalysis!!.priority ?: "LOW")
                    }.getOrDefault(localUnified.priority)

                    UnifiedAgriXRecommendation(
                        cropName = cropName,
                        overallCondition = cloudAnalysis!!.overallCondition ?: localUnified.overallCondition,
                        priority = parsedPriority,
                        soilCondition = cloudAnalysis!!.soilInterpretation.ifBlank { localUnified.soilCondition },
                        wateringDecision = cloudAnalysis!!.wateringDecision ?: localUnified.wateringDecision,
                        wateringExplanation = cloudAnalysis!!.wateringExplanation ?: localUnified.wateringExplanation,
                        wateringTiming = cloudAnalysis!!.wateringTiming ?: localUnified.wateringTiming,
                        wateringAction = cloudAnalysis!!.wateringAction ?: localUnified.wateringAction,
                        environmentAssessment = cloudAnalysis!!.environmentAssessment ?: localUnified.environmentAssessment,
                        diseasePrevention = cloudAnalysis!!.diseasePrevention ?: localUnified.diseasePrevention,
                        cropGrowthGuidance = cloudAnalysis!!.cropGrowthGuidance ?: localUnified.cropGrowthGuidance,
                        immediateActionSummary = cloudAnalysis!!.actionNowSummary ?: localUnified.immediateActionSummary,
                        isCloudEnhanced = true,
                    )
                } else {
                    localUnified.copy(isCloudEnhanced = false)
                }

                val report = CombinedSensorReport(
                    reading = reading,
                    recommendation = finalUnifiedRec,
                    localAnalysis = localAnalysis,
                    cloudAnalysis = cloudAnalysis,
                    isCloudFallback = isCloudFallback || cloudAnalysis == null,
                    finalRecommendation = finalUnifiedRec.immediateActionSummary,
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
            diagnosisRepository: DiagnosisRepository,
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
                        diagnosisRepository = diagnosisRepository,
                        languageStore = languageStore,
                    ) as T
                }
            }
    }
}
