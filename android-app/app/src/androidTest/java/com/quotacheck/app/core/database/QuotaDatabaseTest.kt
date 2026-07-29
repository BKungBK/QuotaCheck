package com.quotacheck.app.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.quotacheck.app.core.database.entity.AlertEventEntity
import com.quotacheck.app.core.database.entity.QuotaPoolEntity
import com.quotacheck.app.core.database.entity.UsageSampleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QuotaDatabaseTest {
    private lateinit var database: QuotaDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            QuotaDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun replaceCurrentAndAppendSamples_replacesPoolsAtomically() = runBlocking {
        database.replaceCurrentAndAppendSamples(listOf(pool("first")))
        database.replaceCurrentAndAppendSamples(listOf(pool("second")))

        assertEquals(listOf("second"), database.quotaDao().observeCurrentPools().first().map { it.poolId })
        assertEquals(2, database.historyDao().sampleCount())
    }

    @Test
    fun replaceCurrentAndAppendSamples_skipsIdenticalConsecutiveSample() = runBlocking {
        val unchanged = pool("pool", receivedAt = 1_000L)
        database.replaceCurrentAndAppendSamples(listOf(unchanged))
        database.replaceCurrentAndAppendSamples(listOf(unchanged.copy(receivedAt = 2_000L)))

        assertEquals(1, database.historyDao().sampleCount())
    }

    @Test
    fun replaceCurrentAndAppendSamples_preservesCycleChange() = runBlocking {
        database.replaceCurrentAndAppendSamples(listOf(pool("pool", cycleEndAt = 10_000L)))
        database.replaceCurrentAndAppendSamples(listOf(pool("pool", cycleEndAt = 20_000L)))

        assertEquals(2, database.historyDao().sampleCount())
    }

    @Test
    fun deleteSamplesBefore_removesSamplesOlderThanNinetyDays() = runBlocking {
        val now = 100L * DAY_MS
        database.historyDao().insertSample(sample("old", now - 91L * DAY_MS))
        database.historyDao().insertSample(sample("kept", now - 90L * DAY_MS))

        database.historyDao().deleteSamplesBefore(now - 90L * DAY_MS)

        assertEquals(1, database.historyDao().sampleCount())
    }

    @Test
    fun dailyAggregates_groupsSamplesByUtcDay() = runBlocking {
        database.historyDao().insertSample(sample("pool", DAY_MS + 1L, remainingFraction = 0.8))
        database.historyDao().insertSample(sample("pool", DAY_MS + 2L, remainingFraction = 0.6))
        database.historyDao().insertSample(sample("pool", 2L * DAY_MS, remainingFraction = 0.4))

        val aggregates = database.historyDao().dailyAggregates("pool", DAY_MS, 3L * DAY_MS)

        assertEquals(2, aggregates.size)
        assertEquals(DAY_MS, aggregates[0].dayStartAt)
        assertEquals(2, aggregates[0].sampleCount)
        assertEquals(0.7, aggregates[0].averageRemainingFraction, 0.0001)
    }

    @Test
    fun alertKey_isUniqueAndDeduplicatesDeterministically() = runBlocking {
        val event = AlertEventEntity(
            alertKey = "pool:cycle:low:20",
            poolId = "pool",
            cycleId = "cycle",
            alertType = "low",
            thresholdPercent = 20,
            deliveredAt = 1L,
        )

        database.alertDao().insertIgnore(event)
        database.alertDao().insertIgnore(event.copy(deliveredAt = 2L))

        assertTrue(database.alertDao().exists(event.alertKey))
        assertEquals(1, database.alertDao().count())
    }

    @Test
    fun alertDeliveryClaim_isAtomicAndRecoversOnlyAfterTimeout() = runBlocking {
        val event = AlertEventEntity(
            alertKey = "pool:10000:LOW:20",
            poolId = "pool",
            cycleId = "10000",
            alertType = "LOW",
            thresholdPercent = 20,
            deliveredAt = null,
        )
        val dao = database.alertDao()
        dao.insertIgnore(event)

        assertEquals(1, dao.claimDelivery(event.alertKey, claimToken = -1_000L, staleBefore = 0L))
        assertEquals(0, dao.claimDelivery(event.alertKey, claimToken = -1_001L, staleBefore = 999L))
        assertEquals(1, dao.claimDelivery(event.alertKey, claimToken = -2_000L, staleBefore = 1_000L))
        assertEquals(1, dao.markDelivered(event.alertKey, claimToken = -2_000L, deliveredAt = 2_001L))
        assertEquals(0, dao.claimDelivery(event.alertKey, claimToken = -3_000L, staleBefore = 3_000L))
    }

    private fun pool(
        poolId: String,
        receivedAt: Long = 1L,
        cycleEndAt: Long? = 10_000L,
    ) = QuotaPoolEntity(
        poolId = poolId,
        displayName = poolId,
        windowLabel = null,
        unitLabel = null,
        totalUnits = null,
        usedUnits = null,
        remainingUnits = null,
        remainingFraction = 0.5,
        cycleStartAt = null,
        cycleEndAt = cycleEndAt,
        providerUpdatedAt = null,
        receivedAt = receivedAt,
        schemaVersion = "v1",
    )

    private fun sample(poolId: String, receivedAt: Long, remainingFraction: Double = 0.5) = UsageSampleEntity(
        poolId = poolId,
        remainingFraction = remainingFraction,
        totalUnits = null,
        usedUnits = null,
        remainingUnits = null,
        cycleEndAt = null,
        receivedAt = receivedAt,
    )

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1_000L
    }
}
