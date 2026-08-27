package com.sih.app.core.sensor

/**
 * Sensor telemetry data model representing soil and environmental readings.
 * Designed modularly so simulated BLE telemetry can be replaced by real ESP32 BLE hardware packets
 * without altering downstream analysis or UI presentation.
 *
 * Strictly distinguishes Raw ADC sensor response from Soil-Context Calibrated Estimated VWC.
 */
data class SensorReading(
    val temperature: Double,
    val humidity: Double,
    val soilMoisture: Double, // Calibrated Estimated VWC (%)
    val soilPH: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "DEMO_BLE",
    val rawAdc: Int = 1850,
    val soilType: String = "Loamy",
    val estimatedVwc: Double = soilMoisture,
    val availableWaterFraction: Double = 0.65, // AWF in [0.0, 1.0]
    val fieldCapacity: Double = 28.0,          // FC %
    val wiltingPoint: Double = 12.0,           // PWP %
)
