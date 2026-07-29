package com.quotacheck.app.feature.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quotacheck.app.core.model.UserPreferences
import com.quotacheck.app.core.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertsViewModel(private val preferences: UserPreferencesRepository) : ViewModel() {
    val uiState: StateFlow<UserPreferences> = preferences.preferences.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences(),
    )

    fun setLowThreshold(percent: Int) = update { preferences.setLowThresholdPercent(percent) }
    fun setLowQuotaEnabled(enabled: Boolean) = update { preferences.setLowQuotaNotificationsEnabled(enabled) }
    fun setCriticalThreshold(percent: Int) = update { preferences.setCriticalThresholdPercent(percent) }
    fun setResetEnabled(enabled: Boolean) = update { preferences.setResetNotificationsEnabled(enabled) }
    fun setFailureEnabled(enabled: Boolean) = update { preferences.setFailureNotificationsEnabled(enabled) }
    fun setSuccessEnabled(enabled: Boolean) = update { preferences.setSuccessNotificationsEnabled(enabled) }

    private fun update(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
    }
}

class AlertsViewModelFactory(private val preferences: UserPreferencesRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(AlertsViewModel::class.java))
        return AlertsViewModel(preferences) as T
    }
}
