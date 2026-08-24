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

private const val TAG = "AgriX_BLE"
private const val SCAN_TIMEOUT_MS = 10_000L

class BleSensorRepository(
    private val context: Context,
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val bleScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val discoveredDevices = ConcurrentHashMap<String, BleDevice>()
    private var scanJob: Job? = null
    private var activeGatt: BluetoothGatt? = null
    private var connectingDevice: BleDevice? = null

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
                )

                discoveredDevices[address] = bleDevice
                val sortedList = getSortedDiscoveredDevices()
                _connectionState.value = BleConnectionState.DevicesFound(sortedList)
                Log.d(TAG, "Discovered device: $rawName ($address), RSSI: ${result.rssi}")
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
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from: ${target.name}")
                    _connectionState.value = BleConnectionState.Disconnected
                    cleanupGatt()
                }
            } else {
                Log.e(TAG, "GATT connection error status: $status")
                _connectionState.value = BleConnectionState.ConnectionFailed("Connection error ($status)")
                cleanupGatt()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "GATT Services discovered for future ESP32 soil characteristics.")
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
        val adapter = bluetoothAdapter ?: return false
        return adapter.isEnabled
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasRequiredPermissions()) {
            _connectionState.value = BleConnectionState.PermissionRequired
            return
        }

        if (!isBluetoothAvailable()) {
            _connectionState.value = BleConnectionState.BluetoothUnavailable
            return
        }

        val scanner = bleScanner
        if (scanner == null) {
            _connectionState.value = BleConnectionState.BluetoothUnavailable
            return
        }

        stopScan()
        discoveredDevices.clear()
        _isScanning.value = true
        _connectionState.value = BleConnectionState.Scanning

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            Log.d(TAG, "Starting bounded BLE scan (${SCAN_TIMEOUT_MS / 1000}s)...")
            scanner.startScan(null, scanSettings, scanCallback)

            scanJob = scope.launch {
                delay(SCAN_TIMEOUT_MS)
                stopScan()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during startScan: ${e.message}", e)
            _isScanning.value = false
            _connectionState.value = BleConnectionState.PermissionRequired
        } catch (e: Exception) {
            Log.e(TAG, "Exception during startScan: ${e.message}", e)
            _isScanning.value = false
            _connectionState.value = BleConnectionState.ConnectionFailed(e.message ?: "Failed to start scan")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null

        if (_isScanning.value) {
            _isScanning.value = false
            try {
                bleScanner?.stopScan(scanCallback)
                Log.d(TAG, "BLE scan stopped.")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }

            if (_connectionState.value is BleConnectionState.Scanning) {
                _connectionState.value = BleConnectionState.DevicesFound(getSortedDiscoveredDevices())
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BleDevice) {
        if (!hasRequiredPermissions()) {
            _connectionState.value = BleConnectionState.PermissionRequired
            return
        }

        stopScan()
        cleanupGatt()

        connectingDevice = device
        _connectionState.value = BleConnectionState.Connecting(device)

        try {
            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
            if (bluetoothDevice == null) {
                _connectionState.value = BleConnectionState.ConnectionFailed("Device not found")
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
        } catch (e: Exception) {
            Log.e(TAG, "Exception during connect: ${e.message}", e)
            _connectionState.value = BleConnectionState.ConnectionFailed(e.message ?: "Connection failed")
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            activeGatt?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting: ${e.message}")
        }
        cleanupGatt()
        _connectionState.value = BleConnectionState.Disconnected
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
