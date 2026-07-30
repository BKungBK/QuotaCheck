package com.quotacheck.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.quotacheck.app.QuotaCheckApp
import com.quotacheck.app.core.model.FailureCategory
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncTrigger
import com.quotacheck.app.widget.QuotaWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class QuotaSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = syncMutex.withLock {
        val trigger = inputData.getString(TRIGGER_KEY)
            ?.let(SyncTrigger::valueOf)
            ?: SyncTrigger.PERIODIC
        val container = (applicationContext as QuotaCheckApp).appContainer
        val previousPools = container.quotaRepository.currentPools.first()
        val consecutiveFailuresBefore = container.syncDao.latestSync()
            ?.takeIf { it.result == "FAILURE" }
            ?.consecutiveFailureCount ?: 0
        val result = container.quotaRepository.synchronize(trigger)
        container.alertDeliveryCoordinator.evaluateAndPublish(
            previousPools = previousPools,
            currentPools = container.quotaRepository.currentPools.first(),
            result = result,
            trigger = trigger,
            preferences = container.userPreferencesRepository.preferences.first(),
            consecutiveFailuresBefore = consecutiveFailuresBefore,
        )
        runCatching {
            QuotaWidget().updateAll(applicationContext)
        }
        resultFor(result)
    }

    companion object {
        const val TRIGGER_KEY = "sync_trigger"
        private val syncMutex = Mutex()

        internal fun resultFor(syncResult: SyncResult): Result = when (syncResult) {
            is SyncResult.Success, SyncResult.Unconfigured -> Result.success()
            is SyncResult.Failed -> when (syncResult.category) {
                FailureCategory.RATE_LIMITED, FailureCategory.RETRYABLE -> Result.retry()
                FailureCategory.SCHEMA, FailureCategory.REMOTE, FailureCategory.PERSISTENCE -> Result.failure()
            }
            SyncResult.AuthRequired -> Result.failure()
        }
    }
}
