package com.quotacheck.app.feature.home

import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.core.model.SyncState
import java.time.Instant

data class HomeContent(
    val pools: List<QuotaPool>,
    val updatedAt: Instant?,
    val syncIntervalMinutes: Int,
)

sealed interface HomeUiState {
    data object Unconfigured : HomeUiState
    data object InitialLoading : HomeUiState
    data class Fresh(val content: HomeContent) : HomeUiState
    data class Stale(val content: HomeContent) : HomeUiState
    data class OfflineCached(val content: HomeContent) : HomeUiState
    data object AuthRequired : HomeUiState
    data class ErrorEmpty(val message: String?) : HomeUiState
    data class Refreshing(val content: HomeContent, val status: HomeUiState) : HomeUiState

    companion object {
        fun from(pools: List<QuotaPool>, syncState: SyncState, intervalMinutes: Int): HomeUiState {
            fun content(updatedAt: Instant?) = HomeContent(pools, updatedAt, intervalMinutes)
            return when (syncState) {
                SyncState.Unconfigured -> Unconfigured
                SyncState.InitialLoading -> InitialLoading
                is SyncState.Fresh -> Fresh(content(syncState.updatedAt))
                is SyncState.Stale -> Stale(content(syncState.updatedAt))
                is SyncState.OfflineCached -> OfflineCached(content(syncState.updatedAt))
                SyncState.AuthRequired -> AuthRequired
                is SyncState.ErrorEmpty -> ErrorEmpty(syncState.message)
                is SyncState.Refreshing -> {
                    val stable = from(pools, syncState.content, intervalMinutes)
                    Refreshing(
                        when (stable) {
                            is Fresh -> stable.content
                            is Stale -> stable.content
                            is OfflineCached -> stable.content
                            is Refreshing -> stable.content
                            else -> content(null)
                        },
                        stable,
                    )
                }
            }
        }
    }
}
