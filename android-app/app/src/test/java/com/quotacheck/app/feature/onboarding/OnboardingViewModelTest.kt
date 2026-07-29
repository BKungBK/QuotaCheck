package com.quotacheck.app.feature.onboarding

import com.quotacheck.app.core.model.AppTheme
import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.core.model.QuotaRepository
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncState
import com.quotacheck.app.core.model.SyncTrigger
import com.quotacheck.app.core.network.QuotaRemoteDataSource
import com.quotacheck.app.core.preferences.UserPreferencesRepository
import com.quotacheck.app.core.security.CredentialVault
import com.quotacheck.app.core.model.UserPreferences
import com.quotacheck.app.sync.SyncScheduler
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {
    @Test fun emptyTokenIsRejectedWithoutValidationOrPersistence() = runBlocking {
        val vault = FakeVault(); val remote = FakeRemote()
        val viewModel = newViewModel(vault, remote, FakeRepository())
        viewModel.submitTokenNow(charArrayOf())
        assertEquals(OnboardingUiState.TokenRequired, viewModel.uiState.value)
        assertFalse(remote.validated); assertFalse(vault.saved)
    }

    @Test fun validationFailureDoesNotPersistToken() = runBlocking {
        val vault = FakeVault(); val remote = FakeRemote(Result.failure(IllegalArgumentException()))
        val viewModel = newViewModel(vault, remote, FakeRepository())
        viewModel.submitTokenNow("secret".toCharArray())
        assertEquals(OnboardingUiState.ValidationFailed, viewModel.uiState.value); assertFalse(vault.saved)
    }

    @Test fun validTokenCompletesInitialSync() = runBlocking {
        val vault = FakeVault(); val preferences = FakePreferences(); val scheduler = FakeScheduler()
        val viewModel = newViewModel(vault, FakeRemote(), FakeRepository(), preferences, scheduler)
        viewModel.submitTokenNow("secret".toCharArray())
        assertTrue(vault.saved); assertTrue(preferences.onboardingCompleted)
        assertEquals(1, scheduler.ensured)
        assertEquals(OnboardingUiState.Connected, viewModel.uiState.value)
    }

    @Test fun failedInitialSyncRollsBackSavedToken() = runBlocking {
        val vault = FakeVault(); val viewModel = newViewModel(vault, FakeRemote(), FakeRepository(SyncResult.AuthRequired))
        viewModel.submitTokenNow("secret".toCharArray())
        assertTrue(vault.cleared); assertEquals(OnboardingUiState.InitialSyncFailed, viewModel.uiState.value)
    }

    @Test fun exceptionAfterPersistenceClearsTokenAndLeavesRecoverableState() = runBlocking {
        val vault = FakeVault()
        val repository = object : QuotaRepository {
            override val currentPools: Flow<List<QuotaPool>> = MutableStateFlow(emptyList())
            override val syncState: Flow<SyncState> = MutableStateFlow(SyncState.Unconfigured)
            override suspend fun synchronize(trigger: SyncTrigger): SyncResult = error("sync exploded")
        }
        val viewModel = newViewModel(vault, FakeRemote(), repository)
        viewModel.submitTokenNow("secret".toCharArray())
        assertTrue(vault.cleared); assertEquals(OnboardingUiState.InitialSyncFailed, viewModel.uiState.value)
    }

    private fun newViewModel(
        vault: FakeVault,
        remote: FakeRemote,
        repository: QuotaRepository,
        preferences: FakePreferences = FakePreferences(),
        scheduler: FakeScheduler = FakeScheduler(),
    ) = OnboardingViewModel(vault, remote, repository, preferences, scheduler)

    private class FakeScheduler : SyncScheduler {
        var ensured = 0
        override fun schedulePeriodic(preferences: UserPreferences) = Unit
        override fun ensurePeriodic(preferences: UserPreferences) { ensured++ }
        override fun cancelPeriodic() = Unit
        override fun refreshNow() = Unit
    }

    private class FakeVault : CredentialVault {
        var saved = false; var cleared = false
        override suspend fun saveRefreshToken(token: CharArray) { saved = true }
        override suspend fun readRefreshToken(): CharArray? = null
        override suspend fun clear() { cleared = true }
    }
    private class FakeRemote(private val validation: Result<String?> = Result.success(null)) : QuotaRemoteDataSource {
        var validated = false
        override suspend fun validate(refreshToken: CharArray): Result<String?> { validated = true; return validation }
        override suspend fun fetchQuota(refreshToken: CharArray): Result<List<QuotaPool>> = Result.success(emptyList())
    }
    private class FakeRepository(private val result: SyncResult = SyncResult.Success(Instant.EPOCH, 1)) : QuotaRepository {
        override val currentPools: Flow<List<QuotaPool>> = MutableStateFlow(emptyList())
        override val syncState: Flow<SyncState> = MutableStateFlow(SyncState.Unconfigured)
        override suspend fun synchronize(trigger: SyncTrigger): SyncResult = result
    }
    private class FakePreferences : UserPreferencesRepository {
        val state = MutableStateFlow(com.quotacheck.app.core.model.UserPreferences()); var onboardingCompleted = false
        override val preferences = state
        override suspend fun setOnboardingCompleted(completed: Boolean) { onboardingCompleted = completed }
        override suspend fun setNotificationRationaleCompleted(completed: Boolean) = Unit
        override suspend fun setAutoSyncEnabled(enabled: Boolean) = Unit
        override suspend fun setSyncIntervalMinutes(minutes: Int) = Unit
        override suspend fun setWifiOnly(enabled: Boolean) = Unit
        override suspend fun setLowQuotaNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setLowThresholdPercent(percent: Int) = Unit
        override suspend fun setCriticalThresholdPercent(percent: Int) = Unit
        override suspend fun setResetNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setFailureNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setSuccessNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun setTheme(theme: AppTheme) = Unit
        override suspend fun setHistoryRetentionDays(days: Int) = Unit
    }
}
