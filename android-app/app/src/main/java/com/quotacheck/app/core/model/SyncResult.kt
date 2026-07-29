package com.quotacheck.app.core.model

import java.time.Instant

/** Public outcome of a sync. Network implementation details never cross this boundary. */
sealed interface SyncResult {
    data class Success(val updatedAt: Instant, val poolCount: Int) : SyncResult
    data object Unconfigured : SyncResult
    data object AuthRequired : SyncResult
    data class Failed(val category: FailureCategory) : SyncResult
}

enum class FailureCategory { RATE_LIMITED, RETRYABLE, SCHEMA, REMOTE, PERSISTENCE }
