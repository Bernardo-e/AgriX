package com.sih.app.core.location

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
)

sealed interface LocationResult {
    data class Success(val location: LocationData) : LocationResult
    data object PermissionDenied : LocationResult
    data object LocationServicesDisabled : LocationResult
    data object Unavailable : LocationResult
    data class Error(val message: String) : LocationResult
}
