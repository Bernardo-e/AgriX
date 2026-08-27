package com.sih.app.core.sensor

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int = 0,
    val isDemo: Boolean = false,
) {
    val isEsp32OrAgriX: Boolean
        get() = isDemo ||
            name.contains("ESP32", ignoreCase = true) ||
            name.contains("AgriX", ignoreCase = true) ||
            name.contains("Soil", ignoreCase = true)
}
