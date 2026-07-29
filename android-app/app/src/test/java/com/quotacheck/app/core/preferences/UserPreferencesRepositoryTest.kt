package com.quotacheck.app.core.preferences

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.quotacheck.app.core.model.AppTheme
import com.quotacheck.app.core.model.UserPreferences
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserPreferencesRepositoryTest {
    @Test
    fun defaultsMatchProductRequirements() = withRepository { repository ->
        assertEquals(
            UserPreferences(
                autoSyncEnabled = true,
                syncIntervalMinutes = 30,
                wifiOnly = false,
                lowQuotaNotificationsEnabled = true,
                lowThresholdPercent = 20,
                criticalThresholdPercent = 10,
                resetNotificationsEnabled = true,
                failureNotificationsEnabled = true,
                successNotificationsEnabled = false,
                theme = AppTheme.DARK,
                historyRetentionDays = 90,
                onboardingCompleted = false,
                notificationRationaleCompleted = false,
            ),
            repository.preferences.first(),
        )
    }

    @Test
    fun settersPersistEveryPreference() = withRepository { repository ->
        repository.setAutoSyncEnabled(false)
        repository.setSyncIntervalMinutes(120)
        repository.setWifiOnly(true)
        repository.setLowQuotaNotificationsEnabled(false)
        repository.setLowThresholdPercent(30)
        repository.setCriticalThresholdPercent(15)
        repository.setResetNotificationsEnabled(false)
        repository.setFailureNotificationsEnabled(false)
        repository.setSuccessNotificationsEnabled(true)
        repository.setTheme(AppTheme.SYSTEM)
        repository.setHistoryRetentionDays(180)
        repository.setOnboardingCompleted(true)
        repository.setNotificationRationaleCompleted(true)

        assertEquals(
            UserPreferences(
                autoSyncEnabled = false,
                syncIntervalMinutes = 120,
                wifiOnly = true,
                lowQuotaNotificationsEnabled = false,
                lowThresholdPercent = 30,
                criticalThresholdPercent = 15,
                resetNotificationsEnabled = false,
                failureNotificationsEnabled = false,
                successNotificationsEnabled = true,
                theme = AppTheme.SYSTEM,
                historyRetentionDays = 180,
                onboardingCompleted = true,
                notificationRationaleCompleted = true,
            ),
            repository.preferences.first(),
        )
    }

    @Test
    fun rejectsUnsupportedSyncIntervals() = withRepository { repository ->
        listOf(0, 29, 31, 45, 241).forEach { interval ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.setSyncIntervalMinutes(interval) }
            }
        }
        listOf(30, 60, 120, 240).forEach { interval ->
            repository.setSyncIntervalMinutes(interval)
            assertEquals(interval, repository.preferences.first().syncIntervalMinutes)
        }
    }

    @Test
    fun rejectsCriticalThresholdThatIsNotBelowLowThreshold() = withRepository { repository ->
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setCriticalThresholdPercent(20) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setCriticalThresholdPercent(21) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setLowThresholdPercent(10) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setLowThresholdPercent(0) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setLowThresholdPercent(101) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setCriticalThresholdPercent(-1) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.setCriticalThresholdPercent(100) }
        }
    }

    @Test
    fun rejectsUnsupportedHistoryRetention() = withRepository { repository ->
        listOf(0, 29, 31, 60, 91, 181).forEach { retention ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.setHistoryRetentionDays(retention) }
            }
        }
        listOf(30, 90, 180).forEach { retention ->
            repository.setHistoryRetentionDays(retention)
            assertEquals(retention, repository.preferences.first().historyRetentionDays)
        }
    }

    private fun withRepository(block: suspend (DataStoreUserPreferencesRepository) -> Unit) = runBlocking {
        block(DataStoreUserPreferencesRepository(InMemoryPreferencesDataStore()))
    }

    private class InMemoryPreferencesDataStore : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow(emptyPreferences())

        override val data = state.asStateFlow()

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock {
                transform(state.value).also { state.value = it }
            }
    }
}
