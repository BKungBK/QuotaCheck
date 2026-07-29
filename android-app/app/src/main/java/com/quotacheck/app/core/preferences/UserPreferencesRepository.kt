package com.quotacheck.app.core.preferences

import com.quotacheck.app.core.model.AppTheme
import com.quotacheck.app.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setAutoSyncEnabled(enabled: Boolean)
    suspend fun setSyncIntervalMinutes(minutes: Int)
    suspend fun setWifiOnly(enabled: Boolean)
    suspend fun setLowThresholdPercent(percent: Int)
    suspend fun setCriticalThresholdPercent(percent: Int)
    suspend fun setResetNotificationsEnabled(enabled: Boolean)
    suspend fun setFailureNotificationsEnabled(enabled: Boolean)
    suspend fun setSuccessNotificationsEnabled(enabled: Boolean)
    suspend fun setTheme(theme: AppTheme)
    suspend fun setHistoryRetentionDays(days: Int)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setNotificationRationaleCompleted(completed: Boolean)
}
