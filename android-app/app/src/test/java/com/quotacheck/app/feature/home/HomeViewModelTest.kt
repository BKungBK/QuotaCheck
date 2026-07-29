package com.quotacheck.app.feature.home

import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.core.model.SyncState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {
    private val pool = QuotaPool(
        poolId = "gemini", displayName = "Gemini", windowLabel = "5 hours", unitLabel = null,
        totalUnits = null, usedUnits = null, remainingUnits = null, remainingFraction = .68,
        cycleStartAt = null, cycleEndAt = Instant.parse("2030-01-01T03:00:00Z"),
        providerUpdatedAt = null, receivedAt = Instant.parse("2030-01-01T00:00:00Z"), schemaVersion = "test",
    )

    @Test fun mapsEveryRepositoryState() {
        assertEquals(HomeUiState.Unconfigured, HomeUiState.from(emptyList(), SyncState.Unconfigured, 30))
        assertEquals(HomeUiState.InitialLoading, HomeUiState.from(emptyList(), SyncState.InitialLoading, 30))
        assertTrue(HomeUiState.from(listOf(pool), SyncState.Fresh(pool.receivedAt), 30) is HomeUiState.Fresh)
        assertTrue(HomeUiState.from(listOf(pool), SyncState.Stale(pool.receivedAt), 30) is HomeUiState.Stale)
        assertTrue(HomeUiState.from(listOf(pool), SyncState.OfflineCached(pool.receivedAt), 30) is HomeUiState.OfflineCached)
        assertEquals(HomeUiState.AuthRequired, HomeUiState.from(emptyList(), SyncState.AuthRequired, 30))
        assertTrue(HomeUiState.from(emptyList(), SyncState.ErrorEmpty("network"), 30) is HomeUiState.ErrorEmpty)
    }

    @Test fun refreshingKeepsCachedPoolsVisible() {
        val state = HomeUiState.from(listOf(pool), SyncState.Refreshing(SyncState.Fresh(pool.receivedAt)), 30)
        assertTrue(state is HomeUiState.Refreshing)
        assertEquals(listOf(pool), (state as HomeUiState.Refreshing).content.pools)
    }

    @Test fun manualRefreshStateKeepsTheLastFreshContentForTheViewModel() {
        val beforeRefresh = HomeUiState.from(listOf(pool), SyncState.Fresh(pool.receivedAt), 30)
        val duringRefresh = HomeUiState.from(listOf(pool), SyncState.Refreshing(SyncState.Fresh(pool.receivedAt)), 30)
        assertTrue(beforeRefresh is HomeUiState.Fresh)
        assertTrue(duringRefresh is HomeUiState.Refreshing)
        assertEquals((beforeRefresh as HomeUiState.Fresh).content, (duringRefresh as HomeUiState.Refreshing).content)
    }
}
