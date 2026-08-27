package com.sih.app.core.sensor

/**
 * Explicit discrete stages for the AgriX Sensor Demo state machine.
 */
enum class SensorStateStage {
    DISCONNECTED_INITIAL,
    SCAN_1_NO_SENSOR,
    SCAN_2_SENSOR_FOUND,
    CONNECTING,
    CONNECTED_DEMO,
    SCANNING_SOIL,
    DATA_READY,
    ANALYZING_LOCAL,
    ANALYZING_CLOUD,
    RESULT_READY,
    CLOUD_FAILED_LOCAL_FALLBACK,
}

/**
 * Sealed hierarchy representing the active state of the AgriX Sensor workflow.
 */
sealed class SensorState(val stage: SensorStateStage) {

    /**
     * Initial idle state before any scan has been initiated.
     */
    data object DisconnectedInitial : SensorState(SensorStateStage.DISCONNECTED_INITIAL)

    /**
     * First scan attempt:
     * - While isScanning == true: Scanning animation (1-2s).
     * - When isScanning == false: Realistic empty state ("No sensor detected").
     */
    data class Scan1NoSensor(
        val isScanning: Boolean = false,
    ) : SensorState(SensorStateStage.SCAN_1_NO_SENSOR)

    /**
     * Second scan attempt:
     * - While isScanning == true: Scanning animation (1-2s).
     * - When isScanning == false: Discovered device ("AgriX Sensor", Available).
     */
    data class Scan2SensorFound(
        val device: BleDevice,
        val isScanning: Boolean = false,
    ) : SensorState(SensorStateStage.SCAN_2_SENSOR_FOUND)

    /**
     * Connecting to the simulated BLE sensor assembly.
     */
    data class Connecting(
        val device: BleDevice,
    ) : SensorState(SensorStateStage.CONNECTING)

    /**
     * Sensor Connected and ready for probe insertion in soil.
     * SCAN SOIL button is active.
     */
    data class ConnectedDemo(
        val device: BleDevice,
    ) : SensorState(SensorStateStage.CONNECTED_DEMO)

    /**
     * Active soil scanning with 6 sequential steps over 2-4 seconds.
     */
    data class ScanningSoil(
        val device: BleDevice,
        val stepName: String,
        val progress: Float,
    ) : SensorState(SensorStateStage.SCANNING_SOIL)

    /**
     * Soil telemetry packet generated and ready.
     */
    data class DataReady(
        val device: BleDevice,
        val reading: SensorReading,
    ) : SensorState(SensorStateStage.DATA_READY)

    /**
     * Evaluating 100% offline local rule engine.
     */
    data class AnalyzingLocal(
        val device: BleDevice,
        val reading: SensorReading,
    ) : SensorState(SensorStateStage.ANALYZING_LOCAL)

    /**
     * Dispatching to FastAPI -> Gemini cloud AI.
     */
    data class AnalyzingCloud(
        val device: BleDevice,
        val reading: SensorReading,
        val localAnalysis: LocalSensorAnalysis,
    ) : SensorState(SensorStateStage.ANALYZING_CLOUD)

    /**
     * Complete AgriX Sensor Report ready.
     * Contains telemetry parameters, local analysis, cloud AI analysis (or fallback), and final recommendation.
     * SCAN SOIL AGAIN button remains active.
     */
    data class ResultReady(
        val device: BleDevice,
        val report: CombinedSensorReport,
        val isCloudFallback: Boolean = false,
    ) : SensorState(
        if (isCloudFallback) SensorStateStage.CLOUD_FAILED_LOCAL_FALLBACK
        else SensorStateStage.RESULT_READY,
    )
}
