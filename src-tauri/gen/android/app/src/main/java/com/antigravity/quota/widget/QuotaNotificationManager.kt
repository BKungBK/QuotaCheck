package com.antigravity.quota.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray

object QuotaNotificationManager {

    private const val CHANNEL_ID = "quota_alerts"
    private const val NOTIFICATION_ID = 2001
    private const val KEY_LAST_NOTIFIED_TIER = "last_notified_tier"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Quota Alerts"
            val descriptionText = "Alerts when Antigravity quota is running low"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun checkAndNotify(context: Context, poolsJsonArray: JSONArray) {
        var minRemaining = 1.0
        var lowestPoolLabel = ""

        for (i in 0 until poolsJsonArray.length()) {
            val pool = poolsJsonArray.optJSONObject(i) ?: continue
            val remaining = pool.optDouble("remaining_fraction", 1.0)
            val label = pool.optString("label", "Quota Pool")
            if (remaining < minRemaining) {
                minRemaining = remaining
                lowestPoolLabel = label
            }
        }

        val currentTier = when {
            minRemaining <= 0.05 -> 3
            minRemaining <= 0.10 -> 2
            minRemaining <= 0.20 -> 1
            else -> 0
        }

        val prefs = QuotaPlugin.getSecurePreferences(context)
        val lastNotifiedTier = prefs.getInt(KEY_LAST_NOTIFIED_TIER, 0)

        if (currentTier > lastNotifiedTier) {
            val percent = (minRemaining * 100).toInt()
            val title = when (currentTier) {
                3 -> "⚠️ Quota Critical ($percent%)"
                2 -> "⚠️ Quota Very Low ($percent%)"
                else -> "⚡ Quota Running Low ($percent%)"
            }
            val content = "$lowestPoolLabel has $percent% remaining. Check your usage!"

            sendNotification(context, title, content)
            prefs.edit().putInt(KEY_LAST_NOTIFIED_TIER, currentTier).apply()
        } else if (currentTier == 0 && lastNotifiedTier > 0) {
            // Quota has been refilled above 20%
            prefs.edit().putInt(KEY_LAST_NOTIFIED_TIER, 0).apply()
        }
    }

    private fun sendNotification(context: Context, title: String, content: String) {
        createNotificationChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(NOTIFICATION_ID, builder.build())
            }
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }
}
