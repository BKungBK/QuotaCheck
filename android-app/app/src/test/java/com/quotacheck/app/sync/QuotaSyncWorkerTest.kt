package com.quotacheck.app.sync

import androidx.work.ListenableWorker.Result
import com.quotacheck.app.core.model.FailureCategory
import com.quotacheck.app.core.model.SyncResult
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class QuotaSyncWorkerTest {
    @Test fun `successful and unconfigured syncs complete work`() {
        assertEquals(Result.success(), QuotaSyncWorker.resultFor(SyncResult.Success(Instant.EPOCH, 1)))
        assertEquals(Result.success(), QuotaSyncWorker.resultFor(SyncResult.Unconfigured))
    }

    @Test fun `network and rate limited failures retry`() {
        assertEquals(Result.retry(), QuotaSyncWorker.resultFor(SyncResult.Failed(FailureCategory.RETRYABLE)))
        assertEquals(Result.retry(), QuotaSyncWorker.resultFor(SyncResult.Failed(FailureCategory.RATE_LIMITED)))
    }

    @Test fun `authentication schema remote and persistence failures stop work`() {
        assertEquals(Result.failure(), QuotaSyncWorker.resultFor(SyncResult.AuthRequired))
        assertEquals(Result.failure(), QuotaSyncWorker.resultFor(SyncResult.Failed(FailureCategory.SCHEMA)))
        assertEquals(Result.failure(), QuotaSyncWorker.resultFor(SyncResult.Failed(FailureCategory.REMOTE)))
        assertEquals(Result.failure(), QuotaSyncWorker.resultFor(SyncResult.Failed(FailureCategory.PERSISTENCE)))
    }
}
