package com.sih.app.ui.farmsetup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.data.FarmRepository
import com.sih.app.core.location.LocationData
import com.sih.app.core.location.LocationRepository
import com.sih.app.core.location.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface FarmSetupUiState {
    data object Idle : FarmSetupUiState
    data object Saving : FarmSetupUiState
    data object Success : FarmSetupUiState
    data class Error(val message: String) : FarmSetupUiState
}

sealed interface LocationUiState {
    data object Idle : LocationUiState
    data object PermissionRequired : LocationUiState
    data object LocationServicesDisabled : LocationUiState
    data object FetchingLocation : LocationUiState
    data class LocationCaptured(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
    ) : LocationUiState
    data object LocationUnavailable : LocationUiState
    data object PermissionDenied : LocationUiState
    data class LocationError(val message: String) : LocationUiState
}

class FarmSetupViewModel(
    private val farmRepository: FarmRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val _existingFarm = MutableStateFlow<com.sih.app.core.database.FarmEntity?>(null)
    val existingFarm: StateFlow<com.sih.app.core.database.FarmEntity?> = _existingFarm.asStateFlow()

    init {
        viewModelScope.launch {
            _existingFarm.value = farmRepository.getFarm()
        }
    }

    private val _uiState = MutableStateFlow<FarmSetupUiState>(FarmSetupUiState.Idle)
    val uiState: StateFlow<FarmSetupUiState> = _uiState.asStateFlow()

    private val _locationState = MutableStateFlow<LocationUiState>(LocationUiState.Idle)
    val locationState: StateFlow<LocationUiState> = _locationState.asStateFlow()

    fun onUseMyLocationClicked() {
        Log.d("AgriX_Location", "Farmer tapped 'Use My Location'. Checking permission...")
        if (!locationRepository.hasLocationPermission()) {
            Log.d("AgriX_Location", "Permission not yet granted. Requesting permission...")
            _locationState.value = LocationUiState.PermissionRequired
        } else if (!locationRepository.isLocationEnabled()) {
            Log.d("AgriX_Location", "Location services disabled.")
            _locationState.value = LocationUiState.LocationServicesDisabled
        } else {
            fetchLocation()
        }
    }

    fun onPermissionResult(isGranted: Boolean) {
        Log.d("AgriX_Location", "Permission result received: isGranted=$isGranted")
        if (isGranted) {
            if (!locationRepository.isLocationEnabled()) {
                _locationState.value = LocationUiState.LocationServicesDisabled
            } else {
                fetchLocation()
            }
        } else {
            _locationState.value = LocationUiState.PermissionDenied
        }
    }

    fun onLocationServicesCheck() {
        if (locationRepository.isLocationEnabled()) {
            if (locationRepository.hasLocationPermission()) {
                fetchLocation()
            } else {
                _locationState.value = LocationUiState.PermissionRequired
            }
        } else {
            _locationState.value = LocationUiState.LocationServicesDisabled
        }
    }

    fun fetchLocation() {
        _locationState.value = LocationUiState.FetchingLocation
        viewModelScope.launch {
            Log.d("AgriX_Location", "Fetching fresh one-time location...")
            when (val result = locationRepository.getFreshLocation()) {
                is LocationResult.Success -> {
                    Log.d("AgriX_Location", "Location captured: lat=${result.location.latitude}, lon=${result.location.longitude}, acc=${result.location.accuracyMeters}m")
                    _locationState.value = LocationUiState.LocationCaptured(
                        latitude = result.location.latitude,
                        longitude = result.location.longitude,
                        accuracyMeters = result.location.accuracyMeters,
                    )
                }
                is LocationResult.PermissionDenied -> {
                    Log.w("AgriX_Location", "LocationResult returned PermissionDenied")
                    _locationState.value = LocationUiState.PermissionDenied
                }
                is LocationResult.LocationServicesDisabled -> {
                    Log.w("AgriX_Location", "LocationResult returned LocationServicesDisabled")
                    _locationState.value = LocationUiState.LocationServicesDisabled
                }
                is LocationResult.Unavailable -> {
                    Log.w("AgriX_Location", "LocationResult returned Unavailable")
                    _locationState.value = LocationUiState.LocationUnavailable
                }
                is LocationResult.Error -> {
                    Log.e("AgriX_Location", "LocationResult returned Error: ${result.message}")
                    _locationState.value = LocationUiState.LocationError(result.message)
                }
            }
        }
    }

    fun saveFarm(
        farmName: String?,
        state: String,
        district: String,
        village: String,
        farmArea: Double,
        farmAreaUnit: String,
        soilType: String,
        currentCrop: String,
    ) {
        Log.d("AgriX_Debug", "4. [ViewModel] saveFarm() triggered. Current uiState=${_uiState.value}")
        if (_uiState.value is FarmSetupUiState.Saving) {
            Log.w("AgriX_Debug", "4.1. [ViewModel] Already in Saving state. Ignoring duplicate call.")
            return
        }
        _uiState.value = FarmSetupUiState.Saving
        Log.d("AgriX_Debug", "4.2. [ViewModel] Set uiState to Saving. Launching viewModelScope coroutine...")

        val currentLoc = _locationState.value as? LocationUiState.LocationCaptured

        viewModelScope.launch {
            try {
                Log.d("AgriX_Debug", "4.3. [ViewModel] Calling farmRepository.saveFarm(...) with location: $currentLoc")
                farmRepository.saveFarm(
                    farmName = farmName,
                    state = state,
                    district = district,
                    village = village,
                    farmArea = farmArea,
                    farmAreaUnit = farmAreaUnit,
                    soilType = soilType,
                    currentCrop = currentCrop,
                    latitude = currentLoc?.latitude,
                    longitude = currentLoc?.longitude,
                    locationAccuracyMeters = currentLoc?.accuracyMeters,
                )
                Log.d("AgriX_Debug", "4.4. [ViewModel] farmRepository.saveFarm(...) completed successfully. Setting uiState = Success")
                _uiState.value = FarmSetupUiState.Success
            } catch (e: Exception) {
                Log.e("AgriX_Debug", "4.ERROR. [ViewModel] Exception while saving farm: ${e.message}", e)
                _uiState.value = FarmSetupUiState.Error(e.message ?: "Failed to save farm profile")
            }
        }
    }

    fun resetState() {
        _uiState.value = FarmSetupUiState.Idle
    }

    companion object {
        fun provideFactory(
            farmRepository: FarmRepository,
            locationRepository: LocationRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FarmSetupViewModel(farmRepository, locationRepository) as T
                }
            }
    }
}
