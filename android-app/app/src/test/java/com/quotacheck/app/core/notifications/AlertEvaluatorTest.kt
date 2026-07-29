package com.quotacheck.app.core.notifications

import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncTrigger
import com.quotacheck.app.core.model.UserPreferences
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AlertEvaluatorTest {
    private val cycleOne = Instant.parse("2030-01-01T03:00:00Z")

    @Test fun `emits threshold only for a downward crossing once per pool cycle`() {
        val evaluator = AlertEvaluator()
        val previous = pool(remaining = 0.30, cycleEnd = cycleOne)
        val current = pool(remaining = 0.20, cycleEnd = cycleOne)

        val commands = evaluator.commands(
            previousPools = listOf(previous), currentPools = listOf(current),
            result = SyncResult.Success(Instant.EPOCH, 1), trigger = SyncTrigger.PERIODIC,
            preferences = UserPreferences(), consecutiveFailuresBefore = 0,
        )

        assertEquals(listOf(AlertCommand.Low("pool", cycleOne.toEpochMilli(), 20)), commands)
        assertEquals(emptyList<AlertCommand>(), evaluator.commands(
            previousPools = listOf(current), currentPools = listOf(current),
            result = SyncResult.Success(Instant.EPOCH, 1), trigger = SyncTrigger.PERIODIC,
            preferences = UserPreferences(), consecutiveFailuresBefore = 0,
        ))
    }

    @Test fun `does not emit threshold when initial committed pool is already low`() {
        assertEquals(emptyList<AlertCommand>(), AlertEvaluator().commands(
            previousPools = emptyList(), currentPools = listOf(pool(remaining = 0.05, cycleEnd = cycleOne)),
            result = SyncResult.Success(Instant.EPOCH, 1), trigger = SyncTrigger.PERIODIC,
            preferences = UserPreferences(), consecutiveFailuresBefore = 0,
        ))
    }

    @Test fun `does not emit low quota alert when low alerts are disabled`() {
        assertEquals(emptyList<AlertCommand>(), AlertEvaluator().commands(
            previousPools = listOf(pool(remaining = 0.30, cycleEnd = cycleOne)), currentPools = listOf(pool(remaining = 0.20, cycleEnd = cycleOne)),
            result = SyncResult.Success(Instant.EPOCH, 1), trigger = SyncTrigger.PERIODIC,
            preferences = UserPreferences(lowQuotaNotificationsEnabled = false), consecutiveFailuresBefore = 0,
        ))
    }

    @Test fun `emits reset once when a pool starts a new cycle`() {
        val commands = AlertEvaluator().commands(
            previousPools = listOf(pool(remaining = 0.02, cycleEnd = cycleOne)),
            currentPools = listOf(pool(remaining = 0.90, cycleEnd = cycleOne.plusSeconds(3600))),
            result = SyncResult.Success(Instant.EPOCH, 1), trigger = SyncTrigger.PERIODIC,
            preferences = UserPreferences(), consecutiveFailuresBefore = 0,
        )
        assertEquals(listOf(AlertCommand.Reset("pool", cycleOne.plusSeconds(3600).toEpochMilli())), commands)
    }

    @Test fun `emits failure exactly at three and suppresses until recovery`() {
        val evaluator = AlertEvaluator()
        val preferences = UserPreferences()
        val firstFailureStreak = Instant.parse("2030-01-02T00:00:00Z")
        val secondFailureStreak = Instant.parse("2030-01-03T00:00:00Z")
        assertEquals(emptyList<AlertCommand>(), evaluator.commands(emptyList(), emptyList(), SyncResult.Failed(com.quotacheck.app.core.model.FailureCategory.RETRYABLE, firstFailureStreak), SyncTrigger.PERIODIC, preferences, 1))
        val firstAlert = AlertCommand.SyncFailure(3, firstFailureStreak.toEpochMilli())
        assertEquals(listOf(firstAlert), evaluator.commands(emptyList(), emptyList(), SyncResult.Failed(com.quotacheck.app.core.model.FailureCategory.RETRYABLE, firstFailureStreak), SyncTrigger.PERIODIC, preferences, 2))
        assertEquals(emptyList<AlertCommand>(), evaluator.commands(emptyList(), emptyList(), SyncResult.Failed(com.quotacheck.app.core.model.FailureCategory.RETRYABLE, firstFailureStreak), SyncTrigger.PERIODIC, preferences, 3))
        assertEquals(emptyList<AlertCommand>(), evaluator.commands(emptyList(), emptyList(), SyncResult.Success(Instant.EPOCH, 0), SyncTrigger.PERIODIC, preferences, 0))
        val secondAlert = evaluator.commands(emptyList(), emptyList(), SyncResult.Failed(com.quotacheck.app.core.model.FailureCategory.RETRYABLE, secondFailureStreak), SyncTrigger.PERIODIC, preferences, 2).single()
        assertEquals(AlertCommand.SyncFailure(3, secondFailureStreak.toEpochMilli()), secondAlert)
        org.junit.Assert.assertTrue(firstAlert.alertKey != secondAlert.alertKey)
    }

    @Test fun `success is only manual or recovery when enabled`() {
        val evaluator = AlertEvaluator()
        val preferences = UserPreferences(successNotificationsEnabled = true)
        assertEquals(emptyList<AlertCommand>(), evaluator.commands(emptyList(), emptyList(), SyncResult.Success(Instant.EPOCH, 0), SyncTrigger.PERIODIC, preferences, 0))
        assertEquals(listOf(AlertCommand.SyncSuccess(0, 0L)), evaluator.commands(emptyList(), emptyList(), SyncResult.Success(Instant.EPOCH, 0), SyncTrigger.MANUAL, preferences, 0))
        assertEquals(listOf(AlertCommand.SyncSuccess(2, 0L)), evaluator.commands(emptyList(), emptyList(), SyncResult.Success(Instant.EPOCH, 0), SyncTrigger.PERIODIC, preferences, 2))
    }

    private fun pool(remaining: Double, cycleEnd: Instant) = QuotaPool(
        poolId = "pool", displayName = "Pool", windowLabel = null, unitLabel = null,
        totalUnits = null, usedUnits = null, remainingUnits = null, remainingFraction = remaining,
        cycleStartAt = null, cycleEndAt = cycleEnd, providerUpdatedAt = null,
        receivedAt = Instant.EPOCH, schemaVersion = "test",
    )
}
