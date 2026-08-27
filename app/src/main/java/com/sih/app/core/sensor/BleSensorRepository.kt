package com.sih.app.core.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private const val TAG = "AgriX_BLE"
private const val SCAN_TIMEOUT_MS = 10_000L

class BleSensorRepository(
    private val context: Context,
    private val soilCalibrationEngine: SoilCalibrationEngine = SoilCalibrationEngine(),
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val bleScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    // Explicit Sensor State Machine (Single source of truth)
    private val _sensorState = MutableStateFlow<SensorState>(SensorState.DisconnectedInitial)
    val sensorState: StateFlow<SensorState> = _sensorState.asStateFlow()

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _latestReading = MutableStateFlow<SensorReading?>(null)
    val latestReading: StateFlow<SensorReading?> = _latestReading.asStateFlow()

    private val discoveredDevices = ConcurrentHashMap<String, BleDevice>()
    private var scanJob: Job? = null
    private var simDiscoveryJob: Job? = null
    private var connectJob: Job? = null
    private var activeGatt: BluetoothGatt? = null
    private var connectingDevice: BleDevice? = null

    // Track scan attempt count:
    // Attempt 1 -> Scan1NoSensor ("No sensor detected")
    // Attempt 2+ -> Scan2SensorFound ("AgriX Sensor", Available)
    private var scanAttemptCount: Int = 0

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val address = device.address ?: return
                val rawName = device.name?.takeIf { it.isNotBlank() }
                    ?: result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                    ?: "Unknown BLE Device"

                val bleDevice = BleDevice(
                    name = rawName,
                    address = address,
                    rssi = result.rssi,
                    isDemo = false,
                )

                discoveredDevices[address] = bleDevice
                val sortedList = getSortedDiscoveredDevices()
                _connectionState.value = BleConnectionState.DevicesFound(sortedList)
                Log.d(TAG, "Discovered physical device: $rawName ($address)")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            _isScanning.value = false
            _connectionState.value = BleConnectionState.ConnectionFailed("Scan failed ($errorCode)")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            Log.d(TAG, "GATT connection state changed. Status: $status, NewState: $newState")
            val target = connectingDevice ?: return

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Successfully connected to: ${target.name} (${target.address})")
                    _connectionState.value = BleConnectionState.Connected(target)
                    _sensorState.value = SensorState.ConnectedDemo(target)
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from: ${target.name}")
                    _connectionState.value = BleConnectionState.Disconnected
                    _sensorState.value = SensorState.DisconnectedInitial
                    cleanupGatt()
                }
            } else {
                Log.e(TAG, "GATT connection error status: $status")
                _connectionState.value = BleConnectionState.ConnectionFailed("Connection error ($status)")
                _sensorState.value = SensorState.DisconnectedInitial
                cleanupGatt()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "GATT Services discovered for ESP32 soil characteristics.")
            }
        }
    }

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothAvailable(): Boolean {
        val adapter = bluetoothAdapter ?: return true // Return true for demo prototype mode
        return adapter.isEnabled
    }

    /**
     * Executes intentional two-phase demo discovery:
     * - Scan 1: Realistic 1-2s scanning animation -> Ends with realistic empty state ("No sensor detected").
     * - Scan 2+: Realistic 1-2s scanning animation -> Discovers "AgriX Sensor" (Demo BLE Sensor, Available).
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        stopScan()
        discoveredDevices.clear()
        _isScanning.value = true
        _connectionState.value = BleConnectionState.Scanning

        scanAttemptCount++
        val isFirstAttempt = (scanAttemptCount == 1)

        if (isFirstAttempt) {
            _sensorState.value = SensorState.Scan1NoSensor(isScanning = true)
        } else {
            val demoDevice = BleDevice(
                name = "AgriX Sensor",
                address = "DEMO:BLE:AGRIX:01",
                rssi = -55,
                isDemo = true,
            )
            _sensorState.value = SensorState.Scan2SensorFound(device = demoDevice, isScanning = true)
        }

        // Optional hardware BLE scan if enabled
        val scanner = bleScanner
        if (hasRequiredPermissions() && scanner != null && bluetoothAdapter?.isEnabled == true) {
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            try {
                scanner.startScan(null, scanSettings, scanCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Real BLE scanner exception: ${e.message}")
            }
        }

        // Simulated discovery timeline
        simDiscoveryJob = scope.launch {
            delay(1500) // 1.5s realistic scanning delay
            _isScanning.value = false

            if (isFirstAttempt) {
                // Empty state for demo
                _sensorState.value = SensorState.Scan1NoSensor(isScanning = false)
                _connectionState.value = BleConnectionState.DevicesFound(emptyList())
            } else {
                // Discovered device for demo
                val demoDevice = BleDevice(
                    name = "AgriX Sensor",
                    address = "DEMO:BLE:AGRIX:01",
                    rssi = -55,
                    isDemo = true,
                )
                discoveredDevices[demoDevice.address] = demoDevice
                _sensorState.value = SensorState.Scan2SensorFound(device = demoDevice, isScanning = false)
                _connectionState.value = BleConnectionState.DevicesFound(getSortedDiscoveredDevices())
            }
        }

        scanJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        simDiscoveryJob?.cancel()
        simDiscoveryJob = null

        if (_isScanning.value) {
            _isScanning.value = false
            try {
                bleScanner?.stopScan(scanCallback)
                Log.d(TAG, "BLE scan stopped.")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
        }
    }

    /**
     * Connects to the simulated or physical BLE sensor device.
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice) {
        stopScan()
        cleanupGatt()
        connectJob?.cancel()

        connectingDevice = device
        _connectionState.value = BleConnectionState.Connecting(device)
        _sensorState.value = SensorState.Connecting(device)

        if (device.isDemo || device.address.startsWith("DEMO")) {
            // Short realistic connecting animation (1.2s)
            connectJob = scope.launch {
                delay(1200)
                _connectionState.value = BleConnectionState.Connected(device)
                _sensorState.value = SensorState.ConnectedDemo(device)
            }
            return
        }

        // Physical BLE GATT connection fallback
        try {
            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
            if (bluetoothDevice == null) {
                _connectionState.value = BleConnectionState.ConnectionFailed("Device not found")
                _sensorState.value = SensorState.DisconnectedInitial
                return
            }

            Log.d(TAG, "Connecting to GATT at ${device.address}...")
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bluetoothDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                bluetoothDevice.connectGatt(context, false, gattCallback)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during connect: ${e.message}", e)
            _connectionState.value = BleConnectionState.PermissionRequired
            _sensorState.value = SensorState.DisconnectedInitial
        } catch (e: Exception) {
            Log.e(TAG, "Exception during connect: ${e.message}", e)
            _connectionState.value = BleConnectionState.ConnectionFailed(e.message ?: "Connection failed")
            _sensorState.value = SensorState.DisconnectedInitial
        }
    }

    /**
     * Disconnects active sensor connection.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        try {
            activeGatt?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting: ${e.message}")
        }
        cleanupGatt()
        _connectionState.value = BleConnectionState.Disconnected
        _sensorState.value = SensorState.DisconnectedInitial
    }

    /**
     * Resets the entire demo state to initial state (Scan attempt count = 0),
     * allowing the hackathon presenter to re-run the demonstration from the very beginning.
     */
    fun resetDemo() {
        disconnect()
        scanAttemptCount = 0
        discoveredDevices.clear()
        _sensorState.value = SensorState.DisconnectedInitial
        Log.d(TAG, "Demo reset: Returned to initial disconnected state.")
    }

    fun updateSensorState(state: SensorState) {
        _sensorState.value = state
    }

    /**
     * Executes multi-stage simulated sensor telemetry acquisition over 2.5-3.5 seconds.
     * Emits realistic progress across the 6 specified agronomic measurement steps.
     * Accurately distinguishes Raw ADC from Soil-Context Calibrated VWC.
     */
    suspend fun acquireSoilTelemetry(
        device: BleDevice,
        targetSoilType: String = "Loamy",
        onProgress: (stepName: String, progress: Float) -> Unit,
    ): SensorReading {
        // Step 1: Preparing
        onProgress("Preparing sensor...", 0.15f)
        _sensorState.value = SensorState.ScanningSoil(device, "Preparing sensor...", 0.15f)
        delay(500)

        // Step 2: Soil Moisture
        onProgress("Reading soil moisture...", 0.35f)
        _sensorState.value = SensorState.ScanningSoil(device, "Reading soil moisture...", 0.35f)
        delay(500)

        // Step 3: Temperature
        onProgress("Reading temperature...", 0.55f)
        _sensorState.value = SensorState.ScanningSoil(device, "Reading temperature...", 0.55f)
        delay(500)

        // Step 4: Humidity
        onProgress("Reading humidity...", 0.75f)
        _sensorState.value = SensorState.ScanningSoil(device, "Reading humidity...", 0.75f)
        delay(500)

        // Step 5: Soil pH
        onProgress("Estimating soil pH...", 0.90f)
        _sensorState.value = SensorState.ScanningSoil(device, "Estimating soil pH...", 0.90f)
        delay(500)

        // Step 6: Analyzing Soil
        onProgress("Analyzing soil...", 1.00f)
        _sensorState.value = SensorState.ScanningSoil(device, "Analyzing soil...", 1.00f)
        delay(400)

        // Generate coherent agricultural sensor values with slight natural variance
        // Centered around: Raw ADC 1850 ± 120, Temp 28.5 °C, Humidity 62 %, pH 6.7
        val rawAdc = 1750 + Random.nextInt(200) // 1750 - 1950 ADC
        val temp = 27.5 + (Random.nextDouble() * 2.0)   // 27.5 - 29.5 °C
        val humidity = 58.0 + (Random.nextDouble() * 8.0) // 58.0 - 66.0 %
        val ph = 6.5 + (Random.nextDouble() * 0.4)       // 6.5 - 6.9

        val roundedTemp = Math.round(temp * 10.0) / 10.0
        val roundedHumidity = Math.round(humidity * 10.0) / 10.0
        val roundedPh = Math.round(ph * 10.0) / 10.0

        // Use ML / Agronomic Calibration Engine to compute soil-context-aware Estimated VWC (%)
        val estimatedVwc = soilCalibrationEngine.estimateVwc(
            soilAdc = rawAdc,
            temperature = roundedTemp,
            humidity = roundedHumidity,
            soilType = targetSoilType,
        )

        val soilProfile = SoilContextRegistry.getProfile(targetSoilType)
        val awf = soilProfile.calculateAvailableWaterFraction(estimatedVwc)

        val reading = SensorReading(
            temperature = roundedTemp,
            humidity = roundedHumidity,
            soilMoisture = estimatedVwc,
            soilPH = roundedPh,
            timestamp = System.currentTimeMillis(),
            source = "DEMO_BLE",
            rawAdc = rawAdc,
            soilType = soilProfile.soilType,
            estimatedVwc = estimatedVwc,
            availableWaterFraction = (awf * 100).toInt() / 100.0,
            fieldCapacity = soilProfile.fieldCapacity,
            wiltingPoint = soilProfile.wiltingPoint,
        )

        _latestReading.value = reading
        _sensorState.value = SensorState.DataReady(device, reading)
        return reading
    }

    @SuppressLint("MissingPermission")
    private fun cleanupGatt() {
        try {
            activeGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GATT: ${e.message}")
        }
        activeGatt = null
        connectingDevice = null
    }

    private fun getSortedDiscoveredDevices(): List<BleDevice> {
        return discoveredDevices.values.sortedWith(
            compareByDescending<BleDevice> { it.isEsp32OrAgriX }
                .thenByDescending { it.rssi }
        )
    }

    fun cleanup() {
        stopScan()
        disconnect()
    }
}
