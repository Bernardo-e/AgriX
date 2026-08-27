package com.sih.app.core.sensor

/**
 * Sensor telemetry data model representing soil and environmental readings.
 * Designed modularly so simulated BLE telemetry can be replaced by real hardware packets
 * without altering downstream analysis or UI presentation.
 */
data class SensorReading(
    val temperature: Double,
    val humidity: Double,
    val soilMoisture: Double,
    val soilPH: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "SIMULATED_BLE",
)
