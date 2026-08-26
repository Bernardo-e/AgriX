package com.sih.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.sih.app.core.data.DiagnosisRepository
import com.sih.app.core.database.DiagnosisEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiagnosisHistoryViewModel(
    private val diagnosisRepository: DiagnosisRepository,
) : ViewModel() {

    val diagnoses: StateFlow<List<DiagnosisEntity>> = diagnosisRepository
        .getAllDiagnosesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    init {
        // Automatically attempt background sync when entering history
        syncPending()
    }

    fun syncPending() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val count = diagnosisRepository.syncPendingDiagnoses()
                if (count > 0) {
                    _syncMessage.value = "Successfully synced $count record(s) with AgriX backend."
                }
            } catch (e: Exception) {
                _syncMessage.value = "Sync failed: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun retryDiagnosis(id: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = diagnosisRepository.retryDiagnosis(id)
                if (result.isSuccess) {
                    _syncMessage.value = "Diagnosis synced successfully."
                } else {
                    _syncMessage.value = "Retry failed: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _syncMessage.value = "Retry failed: ${e.message}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearMessage() {
        _syncMessage.value = null
    }

    companion object {
        fun provideFactory(diagnosisRepository: DiagnosisRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DiagnosisHistoryViewModel(diagnosisRepository) as T
                }
            }
    }
}
