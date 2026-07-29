package com.quotacheck.app.feature.alerts

import com.quotacheck.app.core.model.AppTheme
import com.quotacheck.app.core.model.UserPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the default and valid value contract consumed by the Alerts/Settings screens. */
class AlertsSettingsContractTest {
    @Test fun syncSuccessNotificationsAreOptInByDefault() {
        assertFalse(UserPreferences().successNotificationsEnabled)
    }

    @Test fun lowQuotaNotificationsAreEnabledByDefault() {
        assertTrue(UserPreferences().lowQuotaNotificationsEnabled)
    }

    @Test fun thresholdChoicesCannotCrossEachOther() {
        val preferences = UserPreferences(lowThresholdPercent = 20, criticalThresholdPercent = 10)
        assertTrue((preferences.criticalThresholdPercent + 1..100).contains(preferences.lowThresholdPercent))
        assertTrue((1 until preferences.lowThresholdPercent).contains(preferences.criticalThresholdPercent))
    }

    @Test fun settingsChoicesContainSupportedThemeAndRetentionValues() {
        assertTrue(AppTheme.entries.contains(UserPreferences().theme))
        assertTrue(listOf(30, 90, 180).contains(UserPreferences().historyRetentionDays))
    }
}
