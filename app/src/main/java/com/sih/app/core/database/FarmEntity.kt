package com.sih.app.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farm_profile")
data class FarmEntity(
    @PrimaryKey val id: Long = 1L,
    val farmName: String?,
    val state: String,
    val district: String,
    val village: String,
    val farmArea: Double,
    val farmAreaUnit: String,
    val soilType: String,
    val currentCrop: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
