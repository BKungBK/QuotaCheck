package com.quotacheck.app.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.quotacheck.app.core.model.SyncTrigger
import com.quotacheck.app.core.model.UserPreferences
import java.util.concurrent.TimeUnit

interface WorkManagerGateway {
    fun enqueueUniquePeriodicWork(
        name: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    )

    fun cancelUniqueWork(name: String)

    fun enqueueUniqueWork(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest)
}

class AndroidWorkManagerGateway(private val workManager: WorkManager) : WorkManagerGateway {
    override fun enqueueUniquePeriodicWork(
        name: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    ) {
        workManager.enqueueUniquePeriodicWork(name, policy, request)
    }

    override fun cancelUniqueWork(name: String) {
        workManager.cancelUniqueWork(name)
    }

    override fun enqueueUniqueWork(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) {
        workManager.enqueueUniqueWork(name, policy, request)
    }
}

class WorkManagerSyncScheduler(private val workManager: WorkManagerGateway) : SyncScheduler {
    override fun schedulePeriodic(preferences: UserPreferences) =
        enqueuePeriodic(preferences, ExistingPeriodicWorkPolicy.UPDATE)

    override fun ensurePeriodic(preferences: UserPreferences) =
        enqueuePeriodic(preferences, ExistingPeriodicWorkPolicy.KEEP)

    private fun enqueuePeriodic(preferences: UserPreferences, policy: ExistingPeriodicWorkPolicy) {
        runCatching {
            val intervalMinutes = preferences.syncIntervalMinutes.toLong().coerceAtLeast(15L)
            val request = PeriodicWorkRequestBuilder<QuotaSyncWorker>(
                intervalMinutes,
                TimeUnit.MINUTES,
            )
                .setConstraints(networkConstraints(preferences.wifiOnly))
                .setInputData(triggerData(SyncTrigger.PERIODIC))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, policy, request)
        }
    }

    override fun cancelPeriodic() = workManager.cancelUniqueWork(PERIODIC_WORK_NAME)

    override fun refreshNow() {
        val request = OneTimeWorkRequestBuilder<QuotaSyncWorker>()
            .setConstraints(networkConstraints(wifiOnly = false))
            .setInputData(triggerData(SyncTrigger.MANUAL))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(MANUAL_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun networkConstraints(wifiOnly: Boolean): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    private fun triggerData(trigger: SyncTrigger): Data = Data.Builder()
        .putString(QuotaSyncWorker.TRIGGER_KEY, trigger.name)
        .build()

    companion object {
        const val PERIODIC_WORK_NAME = "quota-periodic-sync"
        const val MANUAL_WORK_NAME = "quota-manual-sync"
        private const val BACKOFF_SECONDS = 30L
    }
}
