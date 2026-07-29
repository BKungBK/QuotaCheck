package com.quotacheck.app.feature

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.quotacheck.app.core.designsystem.QuotaCheckTheme
import com.quotacheck.app.core.model.UserPreferences
import com.quotacheck.app.feature.alerts.AlertsScreen
import com.quotacheck.app.feature.settings.SettingsScreen
import com.quotacheck.app.feature.settings.SettingsViewModel
import com.quotacheck.app.core.database.QuotaDatabase
import com.quotacheck.app.core.model.AppTheme
import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.core.model.QuotaRepository
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncState
import com.quotacheck.app.core.model.SyncTrigger
import com.quotacheck.app.core.preferences.UserPreferencesRepository
import com.quotacheck.app.core.security.CredentialVault
import com.quotacheck.app.sync.SyncScheduler
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlertsSettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun alertsOfferNotificationSettingsWithoutBadgeUi() {
        var opened = false
        composeRule.setContent { QuotaCheckTheme { AlertsScreen(UserPreferences(), onOpenNotificationSettings = { opened = true }) } }
        composeRule.onNodeWithTag("notification_settings").performClick()
        assertTrue(opened)
        assertTrue(composeRule.onAllNodesWithText("badge", substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
    }

    @Test fun settingsRequiresConfirmationBeforeClearingHistoryAndScrollsAtLargeFont() {
        var cleared = false
        composeRule.setContent {
            QuotaCheckTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.4f)) {
                    SettingsScreen(UserPreferences(), onClearHistory = { cleared = true })
                }
            }
        }
        composeRule.onNodeWithTag("clear_history").performClick()
        composeRule.onNodeWithText("Clear local history?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_clear_history").performClick()
        assertTrue(cleared)
    }

    @Test fun settingsChoicesRemainVisibleAtLargeFontScale() {
        composeRule.setContent { QuotaCheckTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.4f)) { SettingsScreen(UserPreferences()) }
        } }
        composeRule.onNodeWithTag("theme-System").assertIsDisplayed()
    }

    @Test fun alertThresholdControlsRemainVisibleAtLargeFontScale() {
        composeRule.setContent { QuotaCheckTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.4f)) { AlertsScreen(UserPreferences()) }
        } }
        composeRule.onNodeWithTag("low_threshold").assertIsDisplayed()
    }

    @Test fun settingsChangesRescheduleAndCredentialRemovalClearsLocalState() {
        val scheduler = FakeScheduler()
        val vault = FakeVault()
        val preferences = FakePreferences()
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), QuotaDatabase::class.java).allowMainThreadQueries().build()
        val viewModel = SettingsViewModel(preferences, vault, scheduler, database, FakeRepository())
        viewModel.setInterval(60)
        viewModel.setWifiOnly(true)
        viewModel.setTheme(AppTheme.LIGHT)
        viewModel.setRetention(180)
        composeRule.waitUntil(2_000) { scheduler.scheduled.size == 2 && preferences.state.value.theme == AppTheme.LIGHT && preferences.state.value.historyRetentionDays == 180 }
        viewModel.removeCredential()
        composeRule.waitUntil(2_000) { vault.cleared && scheduler.cancelled && !preferences.state.value.onboardingCompleted }
        database.close()
    }

    private class FakeScheduler : SyncScheduler { val scheduled = mutableListOf<UserPreferences>(); var cancelled = false; override fun schedulePeriodic(preferences: UserPreferences) { scheduled += preferences }; override fun cancelPeriodic() { cancelled = true }; override fun refreshNow() = Unit }
    private class FakeVault : CredentialVault { var cleared = false; override suspend fun saveRefreshToken(token: CharArray) = Unit; override suspend fun readRefreshToken(): CharArray? = null; override suspend fun clear() { cleared = true } }
    private class FakeRepository : QuotaRepository { override val currentPools: Flow<List<QuotaPool>> = MutableStateFlow(emptyList()); override val syncState: Flow<SyncState> = MutableStateFlow(SyncState.Unconfigured); override suspend fun synchronize(trigger: SyncTrigger): SyncResult = SyncResult.Unconfigured }
    private class FakePreferences : UserPreferencesRepository {
        val state = MutableStateFlow(UserPreferences(onboardingCompleted = true)); override val preferences = state
        override suspend fun setAutoSyncEnabled(enabled: Boolean) { state.value = state.value.copy(autoSyncEnabled = enabled) }; override suspend fun setSyncIntervalMinutes(minutes: Int) { state.value = state.value.copy(syncIntervalMinutes = minutes) }; override suspend fun setWifiOnly(enabled: Boolean) { state.value = state.value.copy(wifiOnly = enabled) }; override suspend fun setLowQuotaNotificationsEnabled(enabled: Boolean) { state.value = state.value.copy(lowQuotaNotificationsEnabled = enabled) }; override suspend fun setLowThresholdPercent(percent: Int) = Unit; override suspend fun setCriticalThresholdPercent(percent: Int) = Unit; override suspend fun setResetNotificationsEnabled(enabled: Boolean) = Unit; override suspend fun setFailureNotificationsEnabled(enabled: Boolean) = Unit; override suspend fun setSuccessNotificationsEnabled(enabled: Boolean) = Unit; override suspend fun setTheme(theme: AppTheme) { state.value = state.value.copy(theme = theme) }; override suspend fun setHistoryRetentionDays(days: Int) { state.value = state.value.copy(historyRetentionDays = days) }; override suspend fun setOnboardingCompleted(completed: Boolean) { state.value = state.value.copy(onboardingCompleted = completed) }; override suspend fun setNotificationRationaleCompleted(completed: Boolean) = Unit
    }
}
