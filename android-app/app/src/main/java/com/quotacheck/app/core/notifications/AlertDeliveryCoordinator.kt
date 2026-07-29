package com.quotacheck.app.core.notifications

import com.quotacheck.app.core.database.AlertDao
import com.quotacheck.app.core.database.entity.AlertEventEntity
import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.core.model.SyncResult
import com.quotacheck.app.core.model.SyncTrigger
import com.quotacheck.app.core.model.UserPreferences

/** Persists notification intent before delivery and retries only undelivered durable events. */
class AlertDeliveryCoordinator(
    private val evaluator: AlertEvaluator,
    private val alertDao: AlertDao,
    private val publisher: NotificationPublisher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun evaluateAndPublish(
        previousPools: List<QuotaPool>,
        currentPools: List<QuotaPool>,
        result: SyncResult,
        trigger: SyncTrigger,
        preferences: UserPreferences,
        consecutiveFailuresBefore: Int,
    ) {
        retryPendingDeliveries(preferences)
        evaluator.commands(previousPools, currentPools, result, trigger, preferences, consecutiveFailuresBefore)
            .forEach { command -> deliver(command) }
    }

    private suspend fun retryPendingDeliveries(preferences: UserPreferences) {
        alertDao.pendingDeliveries()
            .asSequence()
            .filter { it.isEnabled(preferences) }
            .mapNotNull { it.toCommand() }
            .forEach { command -> deliver(command) }
    }

    private suspend fun deliver(command: AlertCommand) {
        val event = AlertEventEntity(
            alertKey = command.alertKey,
            poolId = command.poolId,
            cycleId = command.cycleEndEpoch.toString(),
            alertType = command.type,
            thresholdPercent = command.thresholdOrZero.takeIf { it != 0 },
            deliveredAt = null,
        )
        alertDao.insertIgnore(event)
        val now = nowMillis()
        val claimToken = -now.coerceAtLeast(1L)
        val claimed = alertDao.claimDelivery(
            alertKey = command.alertKey,
            claimToken = claimToken,
            staleBefore = now - CLAIM_TIMEOUT_MILLIS,
        ) == 1
        if (!claimed) return

        if (publisher.publish(command)) {
            alertDao.markDelivered(command.alertKey, claimToken, nowMillis())
        } else {
            alertDao.releaseDeliveryClaim(command.alertKey, claimToken)
        }
    }

    private fun AlertEventEntity.isEnabled(preferences: UserPreferences): Boolean = when (alertType) {
        "LOW" -> preferences.lowQuotaNotificationsEnabled
        "RESET" -> preferences.resetNotificationsEnabled
        "SYNC_FAILURE" -> preferences.failureNotificationsEnabled
        "SYNC_SUCCESS" -> preferences.successNotificationsEnabled
        else -> true
    }

    private fun AlertEventEntity.toCommand(): AlertCommand? {
        val cycle = cycleId?.toLongOrNull() ?: return null
        return when (alertType) {
            "LOW" -> AlertCommand.Low(poolId ?: return null, cycle, thresholdPercent ?: return null)
            "CRITICAL" -> AlertCommand.Critical(poolId ?: return null, cycle, thresholdPercent ?: return null)
            "RESET" -> AlertCommand.Reset(poolId ?: return null, cycle)
            "SYNC_FAILURE" -> AlertCommand.SyncFailure(thresholdPercent ?: return null, cycle)
            "SYNC_SUCCESS" -> AlertCommand.SyncSuccess(thresholdPercent ?: 0, cycle)
            else -> null
        }
    }

    private companion object {
        const val CLAIM_TIMEOUT_MILLIS = 10 * 60 * 1_000L
    }
}
