package com.quotacheck.app.core.repository

import com.quotacheck.app.core.model.FailureCategory
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncTrigger
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFirstQuotaRepositoryTest {
    @Test fun `sync success exposes only domain result`() {
        val result: SyncResult = SyncResult.Success(Instant.ofEpochMilli(1_000), 2)
        assertEquals(2, (result as SyncResult.Success).poolCount)
        assertEquals(Instant.ofEpochMilli(1_000), result.updatedAt)
    }

    @Test fun `sync failure categories remain stable`() {
        assertEquals(FailureCategory.RETRYABLE, (SyncResult.Failed(FailureCategory.RETRYABLE) as SyncResult.Failed).category)
        assertTrue(SyncTrigger.entries.contains(SyncTrigger.MANUAL))
    }
}
