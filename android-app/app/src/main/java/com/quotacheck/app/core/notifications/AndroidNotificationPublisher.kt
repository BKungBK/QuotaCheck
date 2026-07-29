package com.quotacheck.app.core.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import com.quotacheck.app.MainActivity

class AndroidNotificationPublisher(private val context: Context) : NotificationPublisher {
    init { NotificationChannels.create(context) }

    override fun publish(command: AlertCommand): Boolean {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(command.alertKey.hashCode(), notificationFor(command))
        return true
    }

    internal fun notificationFor(command: AlertCommand): Notification {
        val (title, text, channel) = when (command) {
            is AlertCommand.Low -> Triple("Quota running low", "A quota pool is below ${command.threshold}%.", NotificationChannels.QUOTA_ALERTS)
            is AlertCommand.Critical -> Triple("Quota critically low", "A quota pool is below ${command.threshold}%.", NotificationChannels.QUOTA_ALERTS)
            is AlertCommand.Reset -> Triple("Quota reset", "A quota pool has started a new cycle.", NotificationChannels.QUOTA_ALERTS)
            is AlertCommand.SyncFailure -> Triple("Quota sync needs attention", "Quota sync has failed three times.", NotificationChannels.SYNC_STATUS)
            is AlertCommand.SyncSuccess -> Triple("Quota sync complete", "Quota data is up to date.", NotificationChannels.SYNC_STATUS)
        }
        val publicVersion = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("QuotaCheck notification")
            .setContentText("Open QuotaCheck for details.")
            .build()
        return Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(contentIntent(command))
            .setAutoCancel(true)
            .apply {
                if (command is AlertCommand.SyncFailure) addAction(
                    Notification.Action.Builder(
                        Icon.createWithResource(context, android.R.drawable.ic_popup_sync),
                        "Refresh",
                        refreshIntent(),
                    ).build(),
                )
            }
            .build()
    }

    private fun contentIntent(command: AlertCommand): PendingIntent = PendingIntent.getActivity(
        context, command.alertKey.hashCode(), Intent(context, MainActivity::class.java)
            .setAction("com.quotacheck.app.OPEN_ALERT")
            .putExtra("alert_key", command.alertKey), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun refreshIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationActionReceiver.ACTION_REFRESH), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
