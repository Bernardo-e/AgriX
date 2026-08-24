package com.sih.app.ui.farmsetup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.data.FarmRepository
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

class FarmSetupViewModel(
    private val farmRepository: FarmRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FarmSetupUiState>(FarmSetupUiState.Idle)
    val uiState: StateFlow<FarmSetupUiState> = _uiState.asStateFlow()

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

        viewModelScope.launch {
            try {
                Log.d("AgriX_Debug", "4.3. [ViewModel] Calling farmRepository.saveFarm(...)")
                farmRepository.saveFarm(
                    farmName = farmName,
                    state = state,
                    district = district,
                    village = village,
                    farmArea = farmArea,
                    farmAreaUnit = farmAreaUnit,
                    soilType = soilType,
                    currentCrop = currentCrop,
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
        fun provideFactory(farmRepository: FarmRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FarmSetupViewModel(farmRepository) as T
                }
            }
    }
}
