package com.quotacheck.app.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.quotacheck.app.core.model.AppTheme
import com.quotacheck.app.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreUserPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {
    override val preferences: Flow<UserPreferences> = dataStore.data.map { stored ->
        UserPreferences(
            autoSyncEnabled = stored[AUTO_SYNC_ENABLED] ?: true,
            syncIntervalMinutes = stored[SYNC_INTERVAL_MINUTES] ?: 30,
            wifiOnly = stored[WIFI_ONLY] ?: false,
            lowQuotaNotificationsEnabled = stored[LOW_QUOTA_NOTIFICATIONS_ENABLED] ?: true,
            lowThresholdPercent = stored[LOW_THRESHOLD_PERCENT] ?: 20,
            criticalThresholdPercent = stored[CRITICAL_THRESHOLD_PERCENT] ?: 10,
            resetNotificationsEnabled = stored[RESET_NOTIFICATIONS_ENABLED] ?: true,
            failureNotificationsEnabled = stored[FAILURE_NOTIFICATIONS_ENABLED] ?: true,
            successNotificationsEnabled = stored[SUCCESS_NOTIFICATIONS_ENABLED] ?: false,
            theme = stored[THEME]?.let(::themeFromStorage) ?: AppTheme.DARK,
            historyRetentionDays = stored[HISTORY_RETENTION_DAYS] ?: 90,
            onboardingCompleted = stored[ONBOARDING_COMPLETED] ?: false,
            notificationRationaleCompleted = stored[NOTIFICATION_RATIONALE_COMPLETED] ?: false,
        )
    }

    override suspend fun setAutoSyncEnabled(enabled: Boolean) = write(AUTO_SYNC_ENABLED, enabled)

    override suspend fun setSyncIntervalMinutes(minutes: Int) {
        require(minutes in SUPPORTED_SYNC_INTERVAL_MINUTES) {
            "Sync interval must be one of $SUPPORTED_SYNC_INTERVAL_MINUTES minutes."
        }
        write(SYNC_INTERVAL_MINUTES, minutes)
    }

    override suspend fun setWifiOnly(enabled: Boolean) = write(WIFI_ONLY, enabled)

    override suspend fun setLowQuotaNotificationsEnabled(enabled: Boolean) =
        write(LOW_QUOTA_NOTIFICATIONS_ENABLED, enabled)

    override suspend fun setLowThresholdPercent(percent: Int) {
        require(percent in MIN_PERCENT..MAX_PERCENT) { "Low threshold must be between 1 and 100." }
        dataStore.edit { stored ->
            require(percent > (stored[CRITICAL_THRESHOLD_PERCENT] ?: 10)) {
                "Low threshold must be above the critical threshold."
            }
            stored[LOW_THRESHOLD_PERCENT] = percent
        }
    }

    override suspend fun setCriticalThresholdPercent(percent: Int) {
        require(percent in MIN_PERCENT..MAX_PERCENT) { "Critical threshold must be between 1 and 100." }
        dataStore.edit { stored ->
            require(percent < (stored[LOW_THRESHOLD_PERCENT] ?: 20)) {
                "Critical threshold must be below the low threshold."
            }
            stored[CRITICAL_THRESHOLD_PERCENT] = percent
        }
    }

    override suspend fun setResetNotificationsEnabled(enabled: Boolean) =
        write(RESET_NOTIFICATIONS_ENABLED, enabled)

    override suspend fun setFailureNotificationsEnabled(enabled: Boolean) =
        write(FAILURE_NOTIFICATIONS_ENABLED, enabled)

    override suspend fun setSuccessNotificationsEnabled(enabled: Boolean) =
        write(SUCCESS_NOTIFICATIONS_ENABLED, enabled)

    override suspend fun setTheme(theme: AppTheme) = write(THEME, theme.name)

    override suspend fun setHistoryRetentionDays(days: Int) {
        require(days in SUPPORTED_HISTORY_RETENTION_DAYS) {
            "History retention must be one of $SUPPORTED_HISTORY_RETENTION_DAYS days."
        }
        write(HISTORY_RETENTION_DAYS, days)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) = write(ONBOARDING_COMPLETED, completed)

    override suspend fun setNotificationRationaleCompleted(completed: Boolean) =
        write(NOTIFICATION_RATIONALE_COMPLETED, completed)

    private suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        dataStore.edit { stored -> stored[key] = value }
    }

    private fun themeFromStorage(value: String): AppTheme =
        AppTheme.entries.firstOrNull { it.name == value } ?: AppTheme.DARK

    private companion object {
        const val MIN_PERCENT = 1
        const val MAX_PERCENT = 100
        val SUPPORTED_SYNC_INTERVAL_MINUTES = setOf(30, 60, 120, 240)
        val SUPPORTED_HISTORY_RETENTION_DAYS = setOf(30, 90, 180)

        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val LOW_QUOTA_NOTIFICATIONS_ENABLED = booleanPreferencesKey("low_quota_notifications_enabled")
        val LOW_THRESHOLD_PERCENT = intPreferencesKey("low_threshold_percent")
        val CRITICAL_THRESHOLD_PERCENT = intPreferencesKey("critical_threshold_percent")
        val RESET_NOTIFICATIONS_ENABLED = booleanPreferencesKey("reset_notifications_enabled")
        val FAILURE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("failure_notifications_enabled")
        val SUCCESS_NOTIFICATIONS_ENABLED = booleanPreferencesKey("success_notifications_enabled")
        val THEME = stringPreferencesKey("theme")
        val HISTORY_RETENTION_DAYS = intPreferencesKey("history_retention_days")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val NOTIFICATION_RATIONALE_COMPLETED = booleanPreferencesKey("notification_rationale_completed")
    }
}
