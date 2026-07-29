package com.quotacheck.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quotacheck.app.core.model.QuotaRepository
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncTrigger
import com.quotacheck.app.core.network.QuotaRemoteDataSource
import com.quotacheck.app.core.preferences.UserPreferencesRepository
import com.quotacheck.app.core.security.CredentialVault
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val vault: CredentialVault,
    private val remote: QuotaRemoteDataSource,
    private val repository: QuotaRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Loading)
    val uiState: StateFlow<OnboardingUiState> = mutableUiState
    private val mutableAlertsEnabled = MutableStateFlow(true)
    val alertsEnabled: StateFlow<Boolean> = mutableAlertsEnabled
    private val mutableNotificationPermissionRequested = MutableStateFlow(false)
    val notificationPermissionRequested: StateFlow<Boolean> = mutableNotificationPermissionRequested

    fun load() = viewModelScope.launch {
        val saved = preferences.preferences.first()
        mutableAlertsEnabled.value = saved.resetNotificationsEnabled || saved.failureNotificationsEnabled || saved.successNotificationsEnabled
        mutableUiState.value = if (saved.onboardingCompleted) OnboardingUiState.Connected else OnboardingUiState.NeedsToken
    }

    fun setAlertsEnabled(enabled: Boolean) = viewModelScope.launch {
        mutableAlertsEnabled.value = enabled
        preferences.setResetNotificationsEnabled(enabled)
        preferences.setFailureNotificationsEnabled(enabled)
    }

    fun submitToken(token: CharArray) = viewModelScope.launch { submitTokenNow(token) }

    suspend fun submitTokenNow(token: CharArray) {
        if (token.isEmpty()) {
            mutableUiState.value = OnboardingUiState.TokenRequired
            return
        }
        var persisted = false
        try {
            mutableUiState.value = OnboardingUiState.Validating
            if (remote.validate(token).isFailure) {
                mutableUiState.value = OnboardingUiState.ValidationFailed
                return
            }
            vault.saveRefreshToken(token)
            persisted = true
            mutableUiState.value = OnboardingUiState.InitialSyncing
            if (repository.synchronize(SyncTrigger.ONBOARDING) !is SyncResult.Success) {
                clearPersistedToken()
                mutableUiState.value = OnboardingUiState.InitialSyncFailed
                return
            }
            preferences.setOnboardingCompleted(true)
            mutableUiState.value = OnboardingUiState.Connected
        } catch (error: CancellationException) {
            mutableUiState.value = OnboardingUiState.InitialSyncFailed
            if (persisted) clearPersistedToken()
            throw error
        } catch (_: Throwable) {
            if (persisted) clearPersistedToken()
            mutableUiState.value = OnboardingUiState.InitialSyncFailed
        } finally {
            token.fill('\u0000')
        }
    }

    fun onHomeVisible() = viewModelScope.launch {
        val saved = preferences.preferences.first()
        mutableNotificationPermissionRequested.value =
            mutableUiState.value is OnboardingUiState.Connected && !saved.notificationRationaleCompleted && mutableAlertsEnabled.value
    }

    fun onNotificationPermissionResult() = viewModelScope.launch {
        preferences.setNotificationRationaleCompleted(true)
        mutableNotificationPermissionRequested.value = false
    }

    private suspend fun clearPersistedToken() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            runCatching { vault.clear() }
        }
    }
}

class OnboardingViewModelFactory(
    private val vault: CredentialVault,
    private val remote: QuotaRemoteDataSource,
    private val repository: QuotaRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(OnboardingViewModel::class.java))
        return OnboardingViewModel(vault, remote, repository, preferences) as T
    }
}
