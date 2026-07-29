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
        evaluator.commands(previousPools, currentPools, result, trigger, preferences, consecutiveFailuresBefore)
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
        if (alertDao.deliveredAt(command.alertKey) == null && publisher.publish(command)) {
            alertDao.markDelivered(command.alertKey, nowMillis())
        }
    }
}
