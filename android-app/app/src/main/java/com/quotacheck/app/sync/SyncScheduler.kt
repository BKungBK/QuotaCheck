package com.quotacheck.app.sync

import com.quotacheck.app.core.model.UserPreferences

/** Schedules the repository's single synchronization path without exposing WorkManager to callers. */
interface SyncScheduler {
    fun schedulePeriodic(preferences: UserPreferences)

    fun ensurePeriodic(preferences: UserPreferences) = schedulePeriodic(preferences)

    fun cancelPeriodic()

    fun refreshNow()
}
