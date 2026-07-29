package com.quotacheck.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quotacheck.app.core.database.QuotaDatabase
import com.quotacheck.app.core.model.AppTheme
import com.quotacheck.app.core.model.QuotaRepository
import com.quotacheck.app.core.model.UserPreferences
import com.quotacheck.app.core.preferences.UserPreferencesRepository
import com.quotacheck.app.core.security.CredentialVault
import com.quotacheck.app.sync.SyncScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: UserPreferencesRepository,
    private val vault: CredentialVault,
    private val scheduler: SyncScheduler,
    private val database: QuotaDatabase,
    private val repository: QuotaRepository,
) : ViewModel() {
    val uiState: StateFlow<UserPreferences> = preferences.preferences.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences(),
    )

    fun setAutoSync(enabled: Boolean) = viewModelScope.launch {
        preferences.setAutoSyncEnabled(enabled)
        reschedule(uiState.value.copy(autoSyncEnabled = enabled))
    }
    fun setInterval(minutes: Int) = viewModelScope.launch {
        preferences.setSyncIntervalMinutes(minutes)
        reschedule(uiState.value.copy(syncIntervalMinutes = minutes))
    }
    fun setWifiOnly(enabled: Boolean) = viewModelScope.launch {
        preferences.setWifiOnly(enabled)
        reschedule(uiState.value.copy(wifiOnly = enabled))
    }
    fun setTheme(theme: AppTheme) = viewModelScope.launch { preferences.setTheme(theme) }
    fun setRetention(days: Int) = viewModelScope.launch { preferences.setHistoryRetentionDays(days) }
    fun clearHistory() = viewModelScope.launch { database.historyDao().deleteSamplesBefore(Long.MAX_VALUE) }
    fun removeCredential() = viewModelScope.launch {
        scheduler.cancelPeriodic()
        vault.clear()
        database.clearAllTables()
        preferences.setOnboardingCompleted(false)
        repository.synchronize(com.quotacheck.app.core.model.SyncTrigger.MANUAL)
    }

    private fun reschedule(updated: UserPreferences) {
        if (updated.autoSyncEnabled) scheduler.schedulePeriodic(updated) else scheduler.cancelPeriodic()
    }
}

class SettingsViewModelFactory(
    private val preferences: UserPreferencesRepository,
    private val vault: CredentialVault,
    private val scheduler: SyncScheduler,
    private val database: QuotaDatabase,
    private val repository: QuotaRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        return SettingsViewModel(preferences, vault, scheduler, database, repository) as T
    }
}
