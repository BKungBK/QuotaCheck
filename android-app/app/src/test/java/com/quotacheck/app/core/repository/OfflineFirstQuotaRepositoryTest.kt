package com.quotacheck.app.core.repository

import com.quotacheck.app.core.model.FailureCategory
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncTrigger
import com.quotacheck.app.core.model.SyncState
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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

    @Test fun `manual refresh overlays cached state without clearing it`() {
        val fresh = SyncState.Fresh(Instant.ofEpochMilli(1_000))
        assertEquals(SyncState.Refreshing(fresh), fresh.withRefreshOverlay(refreshing = true))
        assertEquals(fresh, fresh.withRefreshOverlay(refreshing = false))
    }

    @Test fun `unconfigured sync is never presented as refreshing`() {
        assertEquals(SyncState.Unconfigured, SyncState.Unconfigured.withRefreshOverlay(refreshing = true))
    }

    @Test fun `two suspended remote calls keep refreshing until both finish`() = runBlocking {
        val tracker = RefreshTracker()
        val firstRemote = CompletableDeferred<Unit>()
        val secondRemote = CompletableDeferred<Unit>()

        suspend fun fetchSuspended(gate: CompletableDeferred<Unit>) {
            tracker.begin()
            try {
                gate.await()
            } finally {
                tracker.finish()
            }
        }

        val first = async { fetchSuspended(firstRemote) }
        val second = async { fetchSuspended(secondRemote) }
        while (tracker.activeCountValue() != 2) kotlinx.coroutines.yield()

        firstRemote.complete(Unit)
        first.await()
        assertTrue(tracker.isRefreshing)
        assertEquals(1, tracker.activeCountValue())

        secondRemote.complete(Unit)
        second.await()
        assertTrue(!tracker.isRefreshing)
        assertEquals(0, tracker.activeCountValue())
    }
}
