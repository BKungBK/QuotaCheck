package com.quotacheck.app.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object NotificationChannels {
    const val QUOTA_ALERTS = "quota_alerts"
    const val SYNC_STATUS = "sync_status"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            QUOTA_ALERTS, "Quota alerts", NotificationManager.IMPORTANCE_HIGH,
        ).apply { setShowBadge(false) })
        manager.createNotificationChannel(NotificationChannel(
            SYNC_STATUS, "Sync status", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { setShowBadge(false) })
    }
}
