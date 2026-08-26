package com.sih.app.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationRepository(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context),
) {

    fun hasLocationPermission(): Boolean {
        val finePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        val coarsePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return finePermission || coarsePermission
    }

    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    suspend fun getFreshLocation(timeoutMs: Long = 15_000L): LocationResult = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            Log.w("AgriX_Location", "Location permission not granted.")
            return@withContext LocationResult.PermissionDenied
        }

        if (!isLocationEnabled()) {
            Log.w("AgriX_Location", "Device location services are turned off.")
            return@withContext LocationResult.LocationServicesDisabled
        }

        val result = withTimeoutOrNull(timeoutMs) {
            fetchFusedLocation() ?: fetchFrameworkLocation()
        }

        if (result != null) {
            LocationResult.Success(
                LocationData(
                    latitude = result.latitude,
                    longitude = result.longitude,
                    accuracyMeters = if (result.hasAccuracy()) result.accuracy else 0f,
                ),
            )
        } else {
            Log.w("AgriX_Location", "Location acquisition timed out or returned null.")
            LocationResult.Unavailable
        }
    }

    @Suppress("MissingPermission")
    private suspend fun fetchFusedLocation(): Location? {
        return try {
            val cancellationTokenSource = CancellationTokenSource()
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    cancellationTokenSource.cancel()
                }

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token,
                ).addOnSuccessListener { location: Location? ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }.addOnFailureListener { error ->
                    Log.e("AgriX_Location", "FusedLocationProvider failed: ${error.message}", error)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AgriX_Location", "Exception in fetchFusedLocation: ${e.message}", e)
            null
        }
    }

    @Suppress("MissingPermission")
    private fun fetchFrameworkLocation(): Location? {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return null

            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            var bestLocation: Location? = null

            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                        bestLocation = loc
                    }
                }
            }
            bestLocation
        } catch (e: Exception) {
            Log.e("AgriX_Location", "Exception in fetchFrameworkLocation: ${e.message}", e)
            null
        }
    }
}
