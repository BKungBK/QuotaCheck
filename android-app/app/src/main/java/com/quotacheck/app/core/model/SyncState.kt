package com.quotacheck.app.core.model

import java.time.Instant

/** The availability of locally cached quota data and its latest synchronization. */
sealed interface SyncState {
    data object Unconfigured : SyncState

    data object InitialLoading : SyncState

    data class Fresh(val updatedAt: Instant) : SyncState

    data class Stale(val updatedAt: Instant?) : SyncState

    data class OfflineCached(val updatedAt: Instant?) : SyncState

    data object AuthRequired : SyncState

    data class ErrorEmpty(val message: String? = null) : SyncState

    /** A transient refresh overlay over the state that remains visible to the user. */
    data class Refreshing(val content: SyncState) : SyncState
}
