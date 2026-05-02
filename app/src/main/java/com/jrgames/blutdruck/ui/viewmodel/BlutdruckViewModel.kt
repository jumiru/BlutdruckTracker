package com.jrgames.blutdruck.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jrgames.blutdruck.data.local.BlutdruckDatabase
import com.jrgames.blutdruck.data.local.MeasurementSession
import com.jrgames.blutdruck.data.repo.MeasurementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BlutdruckViewModel(
    private val repository: MeasurementRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlutdruckUiState())
    val uiState: StateFlow<BlutdruckUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeLatest().collectLatest { latest ->
                _uiState.update { it.copy(latestSession = latest) }
            }
        }
        viewModelScope.launch {
            repository.observeAll().collectLatest { list ->
                _uiState.update { it.copy(sessions = list) }
            }
        }
        viewModelScope.launch {
            repository.observeNoteSuggestions().collectLatest { notes ->
                _uiState.update { it.copy(noteSuggestions = notes) }
            }
        }
    }

    fun saveMeasurement(session: MeasurementSession) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveSuccess = false) }
            repository.save(session)
            _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearSaveSuccess() = _uiState.update { it.copy(saveSuccess = false) }
    fun clearError()       = _uiState.update { it.copy(errorMessage = null) }

    // ── Factory ──────────────────────────────────────────────────────────────
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dao  = BlutdruckDatabase.getInstance(context).measurementDao()
            val repo = MeasurementRepository(dao)
            return BlutdruckViewModel(repo) as T
        }
    }
}

