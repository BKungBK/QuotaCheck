package com.quotacheck.app.core.model

import kotlinx.coroutines.flow.Flow

interface QuotaRepository {
    val currentPools: Flow<List<QuotaPool>>
    val syncState: Flow<SyncState>

    suspend fun synchronize(trigger: SyncTrigger): SyncResult
}
