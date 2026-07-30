package com.quotacheck.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quotacheck.app.core.model.QuotaRepository
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncTrigger
import com.quotacheck.app.core.network.QuotaRemoteDataSource
import com.quotacheck.app.core.network.RemoteError
import com.quotacheck.app.core.network.RetrofitQuotaRemoteDataSource
import com.quotacheck.app.core.preferences.UserPreferencesRepository
import com.quotacheck.app.core.security.CredentialVault
import com.quotacheck.app.sync.SyncScheduler
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
    private val scheduler: SyncScheduler,
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
        // Sanitize once here. RetrofitQuotaRemoteDataSource.exchangeToken() previously
        // also called sanitizeRefreshToken() — that second sanitize has been removed there
        // to avoid double processing.
        val cleanedToken = RetrofitQuotaRemoteDataSource.sanitizeRefreshToken(token.concatToString()).toCharArray()
        if (cleanedToken.isEmpty()) {
            mutableUiState.value = OnboardingUiState.TokenRequired
            return
        }
        var persisted = false
        try {
            mutableUiState.value = OnboardingUiState.Validating
            val valRes = remote.validate(cleanedToken)
            if (valRes.isFailure) {
                val error = valRes.exceptionOrNull()
                android.util.Log.e("QuotaCheckVM", "Validate failed (${error?.javaClass?.simpleName}): $error", error)
                mutableUiState.value = when (error) {
                    is RemoteError.AuthRequired ->
                        // OAuth explicitly rejected the token (HTTP 401/403).
                        OnboardingUiState.ValidationFailed
                    is RemoteError.RateLimited ->
                        OnboardingUiState.RateLimited
                    else ->
                        // IOException/timeout/DNS/SchemaMismatch/NonRetryable — token may be fine.
                        OnboardingUiState.NetworkError
                }
                return
            }
            vault.saveRefreshToken(cleanedToken)
            persisted = true
            mutableUiState.value = OnboardingUiState.InitialSyncing
            val syncResult = repository.synchronize(SyncTrigger.ONBOARDING)
            android.util.Log.d("QuotaCheckVM", "Sync result: $syncResult")
            if (syncResult !is SyncResult.Success) {
                clearPersistedToken()
                if (syncResult is SyncResult.AuthRequired) {
                    mutableUiState.value = OnboardingUiState.ValidationFailed
                } else {
                    mutableUiState.value = OnboardingUiState.InitialSyncFailed
                }
                return
            }
            preferences.setOnboardingCompleted(true)
            runCatching {
                preferences.preferences.first().takeIf { it.autoSyncEnabled }?.let(scheduler::ensurePeriodic)
            }
            mutableUiState.value = OnboardingUiState.Connected
        } catch (error: CancellationException) {
            android.util.Log.e("QuotaCheckVM", "CancellationException", error)
            mutableUiState.value = OnboardingUiState.InitialSyncFailed
            if (persisted) clearPersistedToken()
            throw error
        } catch (error: Throwable) {
            android.util.Log.e("QuotaCheckVM", "Throwable exception: ${error.message}", error)
            if (persisted) clearPersistedToken()
            mutableUiState.value = OnboardingUiState.InitialSyncFailed
        } finally {
            cleanedToken.fill('\u0000')
            token.fill('\u0000')
        }
    }

    fun onHomeVisible() = viewModelScope.launch {
        val saved = preferences.preferences.first()
        if (saved.autoSyncEnabled) scheduler.ensurePeriodic(saved) else scheduler.cancelPeriodic()
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
    private val scheduler: SyncScheduler,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(OnboardingViewModel::class.java))
        return OnboardingViewModel(vault, remote, repository, preferences, scheduler) as T
    }
}
