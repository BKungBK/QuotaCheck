package com.quotacheck.app.core.notifications

import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncTrigger
import com.quotacheck.app.core.model.UserPreferences

/** Converts committed sync transitions into deterministic, deduplicable alert commands. */
class AlertEvaluator {
    fun commands(
        previousPools: List<QuotaPool>,
        currentPools: List<QuotaPool>,
        result: SyncResult,
        trigger: SyncTrigger,
        preferences: UserPreferences,
        consecutiveFailuresBefore: Int,
    ): List<AlertCommand> = when (result) {
        is SyncResult.Success -> successCommands(
            previousPools.associateBy { it.poolId }, currentPools, trigger, preferences,
            consecutiveFailuresBefore, result.updatedAt.toEpochMilli(),
        )
        is SyncResult.Failed -> if (preferences.failureNotificationsEnabled && consecutiveFailuresBefore + 1 == FAILURE_NOTIFY_COUNT) {
            listOf(AlertCommand.SyncFailure(FAILURE_NOTIFY_COUNT, result.failedAt.toEpochMilli()))
        } else emptyList()
        SyncResult.AuthRequired, SyncResult.Unconfigured -> emptyList()
    }

    private fun successCommands(
        previousById: Map<String, QuotaPool>,
        currentPools: List<QuotaPool>,
        trigger: SyncTrigger,
        preferences: UserPreferences,
        consecutiveFailuresBefore: Int,
        syncEventEpoch: Long,
    ): List<AlertCommand> = buildList {
        currentPools.forEach { current ->
            val previous = previousById[current.poolId] ?: return@forEach
            val cycleEnd = current.cycleEndAt?.toEpochMilli() ?: return@forEach
            if (preferences.resetNotificationsEnabled && previous.cycleEndAt != current.cycleEndAt &&
                current.remainingFraction > previous.remainingFraction
            ) add(AlertCommand.Reset(current.poolId, cycleEnd))

            thresholdCommand(previous, current, preferences.criticalThresholdPercent, AlertType.CRITICAL)?.let(::add)
            if (preferences.lowQuotaNotificationsEnabled) {
                thresholdCommand(previous, current, preferences.lowThresholdPercent, AlertType.LOW)?.let(::add)
            }
        }
        if (preferences.successNotificationsEnabled &&
            (trigger == SyncTrigger.MANUAL || consecutiveFailuresBefore > 0)
        ) add(AlertCommand.SyncSuccess(consecutiveFailuresBefore, syncEventEpoch))
    }

    private fun thresholdCommand(
        previous: QuotaPool,
        current: QuotaPool,
        thresholdPercent: Int,
        type: AlertType,
    ): AlertCommand? {
        val threshold = thresholdPercent / 100.0
        if (previous.remainingFraction <= threshold || current.remainingFraction > threshold) return null
        val cycleEnd = current.cycleEndAt?.toEpochMilli() ?: return null
        return when (type) {
            AlertType.LOW -> AlertCommand.Low(current.poolId, cycleEnd, thresholdPercent)
            AlertType.CRITICAL -> AlertCommand.Critical(current.poolId, cycleEnd, thresholdPercent)
        }
    }

    private enum class AlertType { LOW, CRITICAL }

    private companion object { const val FAILURE_NOTIFY_COUNT = 3 }
}

sealed interface AlertCommand {
    val poolId: String?
    val cycleEndEpoch: Long
    val type: String
    val thresholdOrZero: Int

    val alertKey: String get() = "${poolId ?: "sync"}:$cycleEndEpoch:$type:$thresholdOrZero"

    data class Low(override val poolId: String, override val cycleEndEpoch: Long, val threshold: Int) : AlertCommand {
        override val type = "LOW"
        override val thresholdOrZero = threshold
    }
    data class Critical(override val poolId: String, override val cycleEndEpoch: Long, val threshold: Int) : AlertCommand {
        override val type = "CRITICAL"
        override val thresholdOrZero = threshold
    }
    data class Reset(override val poolId: String, override val cycleEndEpoch: Long) : AlertCommand {
        override val type = "RESET"
        override val thresholdOrZero = 0
    }
    data class SyncFailure(val count: Int, val eventEpoch: Long) : AlertCommand {
        override val poolId: String? = null
        override val cycleEndEpoch = eventEpoch
        override val type = "SYNC_FAILURE"
        override val thresholdOrZero = count
    }
    data class SyncSuccess(val recoveredFromFailures: Int, val eventEpoch: Long) : AlertCommand {
        override val poolId: String? = null
        override val cycleEndEpoch = eventEpoch
        override val type = "SYNC_SUCCESS"
        override val thresholdOrZero = recoveredFromFailures
    }
}
