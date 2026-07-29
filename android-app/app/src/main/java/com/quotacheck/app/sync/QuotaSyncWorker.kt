package com.quotacheck.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.quotacheck.app.QuotaCheckApp
import com.quotacheck.app.core.model.FailureCategory
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncTrigger

class QuotaSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val trigger = inputData.getString(TRIGGER_KEY)
            ?.let(SyncTrigger::valueOf)
            ?: SyncTrigger.PERIODIC
        val repository = (applicationContext as QuotaCheckApp).appContainer.quotaRepository
        return resultFor(repository.synchronize(trigger))
    }

    companion object {
        const val TRIGGER_KEY = "sync_trigger"

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
