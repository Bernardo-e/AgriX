package com.sih.app.ui.sensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sih.app.core.sensor.BleConnectionState
import com.sih.app.core.sensor.BleDevice
import com.sih.app.core.sensor.BleSensorRepository
import kotlinx.coroutines.flow.StateFlow

class SensorConnectionViewModel(
    private val bleSensorRepository: BleSensorRepository,
) : ViewModel() {

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

    fun isBluetoothAvailable(): Boolean {
        return bleSensorRepository.isBluetoothAvailable()
    }

    fun hasRequiredPermissions(): Boolean {
        return bleSensorRepository.hasRequiredPermissions()
    }

    override fun onCleared() {
        super.onCleared()
        bleSensorRepository.stopScan()
    }

    companion object {
        fun provideFactory(bleSensorRepository: BleSensorRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SensorConnectionViewModel(bleSensorRepository) as T
                }
            }
    }
}
