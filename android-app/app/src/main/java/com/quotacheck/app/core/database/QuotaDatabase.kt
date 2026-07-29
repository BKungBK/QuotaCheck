package com.quotacheck.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.quotacheck.app.core.database.entity.AlertEventEntity
import com.quotacheck.app.core.database.entity.QuotaPoolEntity
import com.quotacheck.app.core.database.entity.SyncRunEntity
import com.quotacheck.app.core.database.entity.UsageSampleEntity
import com.quotacheck.app.core.model.QuotaPool

@Database(
    entities = [QuotaPoolEntity::class, UsageSampleEntity::class, SyncRunEntity::class, AlertEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class QuotaDatabase : RoomDatabase() {
    abstract fun quotaDao(): QuotaDao
    abstract fun historyDao(): HistoryDao
    abstract fun syncDao(): SyncDao
    abstract fun alertDao(): AlertDao

    /** Replaces current rows and appends only quota/cycle changes in one transaction. */
    suspend fun replaceCurrentAndAppendSamples(
        pools: List<QuotaPoolEntity>,
        alertEvents: List<AlertEventEntity> = emptyList(),
    ) = withTransaction {
        val historyDao = historyDao()
        quotaDao().clearCurrentPools()
        quotaDao().insertCurrentPools(pools)
        pools.forEach { pool ->
            val previous = historyDao.latestSample(pool.poolId)
            if (previous == null || previous.remainingFraction != pool.remainingFraction || previous.totalUnits != pool.totalUnits ||
                previous.usedUnits != pool.usedUnits || previous.remainingUnits != pool.remainingUnits ||
                previous.cycleEndAt != pool.cycleEndAt
            ) {
                historyDao.insertSample(pool.toSample())
            }
        }
        for (alertEvent in alertEvents) {
            alertDao().insertIgnore(alertEvent)
        }
    }

    /** Keeps persistence state consistent before downstream alert evaluation. */
    suspend fun recordAlertEvent(event: AlertEventEntity): Long = withTransaction {
        alertDao().insertIgnore(event)
    }

    private fun QuotaPoolEntity.toSample() = UsageSampleEntity(
        poolId = poolId,
        remainingFraction = remainingFraction,
        totalUnits = totalUnits,
        usedUnits = usedUnits,
        remainingUnits = remainingUnits,
        cycleEndAt = cycleEndAt,
        receivedAt = receivedAt,
    )

    companion object {
        fun QuotaPool.toEntity() = QuotaPoolEntity(
            poolId = poolId,
            displayName = displayName,
            windowLabel = windowLabel,
            unitLabel = unitLabel,
            totalUnits = totalUnits?.toDouble(),
            usedUnits = usedUnits?.toDouble(),
            remainingUnits = remainingUnits?.toDouble(),
            remainingFraction = remainingFraction,
            cycleStartAt = cycleStartAt?.toEpochMilli(),
            cycleEndAt = cycleEndAt?.toEpochMilli(),
            providerUpdatedAt = providerUpdatedAt?.toEpochMilli(),
            receivedAt = receivedAt.toEpochMilli(),
            schemaVersion = schemaVersion,
        )
    }
}
