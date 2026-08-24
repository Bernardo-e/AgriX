package com.sih.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.data.FarmRepository
import com.sih.app.core.database.FarmEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AiViewModel(
    farmRepository: FarmRepository,
) : ViewModel() {

    val farmProfile: StateFlow<FarmEntity?> = farmRepository.getFarmFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    companion object {
        fun provideFactory(farmRepository: FarmRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AiViewModel(farmRepository) as T
                }
            }
    }
}
