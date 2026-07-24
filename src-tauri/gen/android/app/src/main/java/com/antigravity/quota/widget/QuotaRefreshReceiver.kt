package com.antigravity.quota.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class QuotaRefreshReceiver : BroadcastReceiver() {

    companion object {
        private const val KEY_LAST_REFRESH_TIME = "last_manual_refresh_time"
        private const val COOLDOWN_MS = 60_000L // 60 seconds
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == QuotaWidgetProvider.ACTION_REFRESH_WIDGET) {
            val prefs = QuotaPlugin.getSecurePreferences(context)
            val lastRefresh = prefs.getLong(KEY_LAST_REFRESH_TIME, 0L)
            val now = System.currentTimeMillis()

            if (now - lastRefresh >= COOLDOWN_MS) {
                prefs.edit().putLong(KEY_LAST_REFRESH_TIME, now).apply()
                QuotaSyncWorker.triggerImmediateSync(context)
                Toast.makeText(context, "Refreshing Quota...", Toast.LENGTH_SHORT).show()
            } else {
                val remainingSec = ((COOLDOWN_MS - (now - lastRefresh)) / 1000).coerceAtLeast(1)
                Toast.makeText(context, "Please wait ${remainingSec}s before refreshing", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
