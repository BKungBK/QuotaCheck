package com.quotacheck.app.core.model

enum class AppTheme {
    DARK,
    LIGHT,
    SYSTEM,
}

data class UserPreferences(
    val autoSyncEnabled: Boolean = true,
    val syncIntervalMinutes: Int = 30,
    val wifiOnly: Boolean = false,
    val lowThresholdPercent: Int = 20,
    val criticalThresholdPercent: Int = 10,
    val resetNotificationsEnabled: Boolean = true,
    val failureNotificationsEnabled: Boolean = true,
    val successNotificationsEnabled: Boolean = false,
    val theme: AppTheme = AppTheme.DARK,
    val historyRetentionDays: Int = 90,
    val onboardingCompleted: Boolean = false,
    val notificationRationaleCompleted: Boolean = false,
)
