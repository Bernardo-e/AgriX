package com.sih.app.core.sensor

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data object BluetoothUnavailable : BleConnectionState
    data object PermissionRequired : BleConnectionState
    data object Scanning : BleConnectionState
    data class DevicesFound(val devices: List<BleDevice>) : BleConnectionState
    data class Connecting(val device: BleDevice) : BleConnectionState
    data class Connected(val device: BleDevice) : BleConnectionState
    data class ConnectionFailed(val message: String) : BleConnectionState
}
