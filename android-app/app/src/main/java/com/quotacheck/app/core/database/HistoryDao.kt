package com.quotacheck.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.quotacheck.app.core.database.entity.UsageSampleEntity
import kotlinx.coroutines.flow.Flow

data class DailyUsageAggregate(
    val dayStartAt: Long,
    val sampleCount: Int,
    val averageRemainingFraction: Double,
    val averageUsedUnits: Double?,
    val usedUnitSampleCount: Int = if (averageUsedUnits == null) 0 else sampleCount,
)

data class PeriodUsageAggregate(
    val periodStartAt: Long,
    val sampleCount: Int,
    val averageRemainingFraction: Double,
    val averageUsedUnits: Double?,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM usage_samples WHERE poolId = :poolId ORDER BY receivedAt DESC LIMIT 1")
    suspend fun latestSample(poolId: String): UsageSampleEntity?

    @Insert
    suspend fun insertSample(sample: UsageSampleEntity): Long

    @Query("DELETE FROM usage_samples WHERE receivedAt < :cutoffAt")
    suspend fun deleteSamplesBefore(cutoffAt: Long): Int

    @Query("SELECT COUNT(*) FROM usage_samples")
    suspend fun sampleCount(): Int

    @Query(
        "SELECT (receivedAt / 86400000) * 86400000 AS dayStartAt, " +
            "COUNT(*) AS sampleCount, AVG(remainingFraction) AS averageRemainingFraction, " +
            "AVG(usedUnits) AS averageUsedUnits, COUNT(usedUnits) AS usedUnitSampleCount " +
            "FROM usage_samples WHERE poolId = :poolId AND receivedAt >= :fromAt AND receivedAt < :untilAt " +
            "GROUP BY dayStartAt ORDER BY dayStartAt",
    )
    suspend fun dailyAggregates(poolId: String, fromAt: Long, untilAt: Long): List<DailyUsageAggregate>

    @Query(
        "SELECT MIN(receivedAt) AS dayStartAt, " +
            "COUNT(*) AS sampleCount, AVG(remainingFraction) AS averageRemainingFraction, " +
            "AVG(usedUnits) AS averageUsedUnits, COUNT(usedUnits) AS usedUnitSampleCount " +
            "FROM usage_samples WHERE poolId = :poolId AND receivedAt >= :fromAt AND receivedAt < :untilAt " +
            "GROUP BY strftime('%Y-%m-%d', receivedAt / 1000, 'unixepoch', 'localtime') ORDER BY dayStartAt",
    )
    fun observeDailyAggregates(poolId: String, fromAt: Long, untilAt: Long): Flow<List<DailyUsageAggregate>>

    @Query(
        "SELECT MIN(receivedAt) AS periodStartAt, " +
            "COUNT(*) AS sampleCount, AVG(remainingFraction) AS averageRemainingFraction, AVG(usedUnits) AS averageUsedUnits " +
            "FROM usage_samples WHERE poolId = :poolId AND receivedAt >= :fromAt AND receivedAt < :untilAt " +
            "GROUP BY strftime('%Y-%W', receivedAt / 1000, 'unixepoch', 'localtime') ORDER BY periodStartAt",
    )
    fun observeWeeklyAggregates(poolId: String, fromAt: Long, untilAt: Long): Flow<List<PeriodUsageAggregate>>

    @Query(
        "SELECT MIN(receivedAt) AS periodStartAt, " +
            "COUNT(*) AS sampleCount, AVG(remainingFraction) AS averageRemainingFraction, AVG(usedUnits) AS averageUsedUnits " +
            "FROM usage_samples WHERE poolId = :poolId AND receivedAt >= :fromAt AND receivedAt < :untilAt " +
            "GROUP BY strftime('%Y-%m', receivedAt / 1000, 'unixepoch', 'localtime') ORDER BY periodStartAt",
    )
    fun observeMonthlyAggregates(poolId: String, fromAt: Long, untilAt: Long): Flow<List<PeriodUsageAggregate>>
}
