package com.sih.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.data.FarmRepository
import com.sih.app.core.database.FarmEntity
import com.sih.app.core.sensor.BleConnectionState
import com.sih.app.core.sensor.BleSensorRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(
        val farm: FarmEntity,
        val isSensorConnected: Boolean = false,
        val connectedDeviceName: String? = null,
    ) : HomeUiState
    data object NoFarm : HomeUiState
}

class HomeViewModel(
    private val farmRepository: FarmRepository,
    private val bleSensorRepository: BleSensorRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        farmRepository.getFarmFlow(),
        bleSensorRepository.connectionState,
    ) { farm, bleState ->
        if (farm != null) {
            val isConnected = bleState is BleConnectionState.Connected
            val deviceName = (bleState as? BleConnectionState.Connected)?.device?.name
            HomeUiState.Success(
                farm = farm,
                isSensorConnected = isConnected,
                connectedDeviceName = deviceName,
            )
        } else {
            HomeUiState.NoFarm
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState.Loading,
    )

    companion object {
        fun provideFactory(
            farmRepository: FarmRepository,
            bleSensorRepository: BleSensorRepository,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(farmRepository, bleSensorRepository) as T
                }
            }
    }
}
