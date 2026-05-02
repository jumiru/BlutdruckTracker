package com.jrgames.blutdruck.ui.viewmodel

import com.jrgames.blutdruck.data.local.MeasurementSession

data class BlutdruckUiState(
    val latestSession: MeasurementSession? = null,
    val sessions: List<MeasurementSession> = emptyList(),
    val noteSuggestions: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
)

