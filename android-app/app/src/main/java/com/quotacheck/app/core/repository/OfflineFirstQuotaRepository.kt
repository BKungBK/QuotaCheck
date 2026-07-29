package com.quotacheck.app.core.repository

import com.quotacheck.app.core.database.QuotaDatabase
import com.quotacheck.app.core.database.QuotaDatabase.Companion.toEntity
import com.quotacheck.app.core.database.entity.QuotaPoolEntity
import com.quotacheck.app.core.database.entity.SyncRunEntity
import com.quotacheck.app.core.model.FailureCategory
import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.core.model.QuotaRepository
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncState
import com.quotacheck.app.core.model.SyncTrigger
import com.quotacheck.app.core.network.QuotaRemoteDataSource
import com.quotacheck.app.core.network.RemoteError
import com.quotacheck.app.core.preferences.UserPreferencesRepository
import com.quotacheck.app.core.security.CredentialVault
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class OfflineFirstQuotaRepository(
    private val database: QuotaDatabase,
    private val remote: QuotaRemoteDataSource,
    private val credentialVault: CredentialVault,
    private val preferences: UserPreferencesRepository,
    private val now: () -> Instant = Instant::now,
) : QuotaRepository {
    override val currentPools: Flow<List<QuotaPool>> = database.quotaDao().observeCurrentPools().map { pools ->
        pools.map { it.toDomain() }
    }

    override val syncState: Flow<SyncState> = combine(
        database.quotaDao().observeCurrentPools(),
        database.syncDao().observeLatestSync(),
    ) { pools, run ->
        when {
            run == null && pools.isEmpty() -> SyncState.InitialLoading
            run?.result == SUCCESS -> SyncState.Fresh(Instant.ofEpochMilli(checkNotNull(run.finishedAt)))
            run?.errorCategory == AUTH -> SyncState.AuthRequired
            pools.isNotEmpty() -> SyncState.OfflineCached(pools.maxOfOrNull { it.receivedAt }?.let(Instant::ofEpochMilli))
            else -> SyncState.ErrorEmpty(run?.errorCategory)
        }
    }

    override suspend fun synchronize(trigger: SyncTrigger): SyncResult {
        val token = credentialVault.readRefreshToken() ?: return SyncResult.Unconfigured
        try {
            val fetched = remote.fetchQuota(token).getOrElse { throw it }
            val finishedAt = now()
            val retentionDays = preferences.preferences.first().historyRetentionDays
            database.commitSuccessfulSync(
                pools = fetched.map { it.toEntity() },
                syncRun = SyncRunEntity(
                    startedAt = finishedAt.toEpochMilli(),
                    finishedAt = finishedAt.toEpochMilli(),
                    trigger = trigger.name,
                    result = SUCCESS,
                    errorCategory = null,
                    consecutiveFailureCount = 0,
                ),
                retentionCutoffAt = finishedAt.minusSeconds(retentionDays.toLong() * 86_400).toEpochMilli(),
            )
            return SyncResult.Success(finishedAt, fetched.size)
        } catch (error: Throwable) {
            val category = error.toFailureCategory()
            val failedAt = now()
            val persistenceFailure = runCatching {
                val previousFailureCount = database.syncDao().latestSync()
                    ?.takeIf { it.result == FAILURE }
                    ?.consecutiveFailureCount ?: 0
                database.syncDao().insert(
                    SyncRunEntity(
                        startedAt = failedAt.toEpochMilli(), finishedAt = failedAt.toEpochMilli(),
      trigger = trigger.name, result = FAILURE, errorCategory = if (error is RemoteError.AuthRequired) AUTH else category.name,
                        consecutiveFailureCount = previousFailureCount + 1,
                    ),
                )
            }.isFailure
            return if (persistenceFailure) SyncResult.Failed(FailureCategory.PERSISTENCE)
            else if (category == FailureCategory.REMOTE && error is RemoteError.AuthRequired) SyncResult.AuthRequired
            else SyncResult.Failed(category)
        } finally {
            token.fill('\u0000')
        }
    }

    private fun Throwable.toFailureCategory() = when (this) {
        is RemoteError.RateLimited -> FailureCategory.RATE_LIMITED
        is RemoteError.Retryable -> FailureCategory.RETRYABLE
        is RemoteError.SchemaMismatch -> FailureCategory.SCHEMA
        else -> FailureCategory.REMOTE
    }

    private fun QuotaPoolEntity.toDomain() = QuotaPool(
        poolId, displayName, windowLabel, unitLabel, totalUnits?.let(::BigDecimal), usedUnits?.let(::BigDecimal),
        remainingUnits?.let(::BigDecimal), remainingFraction, cycleStartAt?.let(Instant::ofEpochMilli),
        cycleEndAt?.let(Instant::ofEpochMilli), providerUpdatedAt?.let(Instant::ofEpochMilli),
        Instant.ofEpochMilli(receivedAt), schemaVersion,
    )

    private companion object {
        const val SUCCESS = "SUCCESS"
        const val FAILURE = "FAILURE"
        const val AUTH = "AUTH_REQUIRED"
    }
}
