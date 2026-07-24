package com.antigravity.quota.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class QuotaWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.antigravity.quota.widget.ACTION_REFRESH"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, QuotaWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                val views = buildRemoteViews(context)
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        private fun formatResetTime(resetIsoStr: String?): String {
            if (resetIsoStr.isNull_or_empty()) return ""
            return try {
                val sdfInput = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                sdfInput.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdfInput.parse(resetIsoStr) ?: return ""
                val sdfOutput = SimpleDateFormat("HH:mm", Locale.getDefault())
                "Reset " + sdfOutput.format(date)
            } catch (_: Exception) {
                ""
            }
        }

        fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quota)

            // Setup Tap Container -> Launch MainActivity
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val appPendingIntent = PendingIntent.getActivity(
                context, 0, appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, appPendingIntent)

            // Setup Refresh Button -> Send Broadcast
            val refreshIntent = Intent(context, QuotaRefreshReceiver::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, 100, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)

            // Read Cached Data
            val prefs = QuotaPlugin.getSecurePreferences(context)
            val cacheStr = prefs.getString("quota_cache", "") ?: ""

            if (cacheStr.isNotEmpty()) {
                try {
                    val cacheObj = JSONObject(cacheStr)
                    val pools = cacheObj.optJSONArray("pools")
                    val lastUpdated = cacheObj.optString("last_updated", "--:--")
                    val isOffline = cacheObj.optBoolean("is_offline", false)
                    val source = cacheObj.optString("source", "cloud")

                    // Status Dot & Text
                    if (isOffline) {
                        views.setImageViewResource(R.id.widget_status_dot, R.drawable.dot_offline)
                        views.setTextViewText(R.id.widget_status_text, "OFFLINE")
                        views.setTextColor(R.id.widget_status_text, Color.parseColor("#71717A"))
                        views.setTextViewText(R.id.widget_source_text, "Offline 🔴")
                    } else {
                        views.setImageViewResource(R.id.widget_status_dot, R.drawable.dot_live)
                        views.setTextViewText(R.id.widget_status_text, "LIVE")
                        views.setTextColor(R.id.widget_status_text, Color.parseColor("#22C55E"))
                        val srcLabel = if (source == "local") "Local 🟢" else "Cloud ☁️"
                        views.setTextViewText(R.id.widget_source_text, srcLabel)
                    }

                    views.setTextViewText(R.id.widget_time_ago_text, "Synced $lastUpdated")

                    if (pools != null && pools.length() > 0) {
                        // Slot 1
                        val p1 = pools.optJSONObject(0)
                        if (p1 != null) {
                            val label = p1.optString("label", "Claude")
                            val rem = p1.optDouble("remaining_fraction", 1.0)
                            val resetStr = p1.optString("reset_time", "")
                            val pct = (rem * 100).toInt().coerceIn(0, 100)

                            views.setTextViewText(R.id.pool1_label, label)
                            views.setTextViewText(R.id.pool1_percent, "$pct%")
                            views.setTextViewText(R.id.pool1_reset_text, formatResetTime(resetStr))
                            views.setProgressBar(R.id.pool1_progress, 100, pct, false)
                            views.setViewVisibility(R.id.pool1_container, View.VISIBLE)
                        }

                        // Slot 2
                        if (pools.length() > 1) {
                            val p2 = pools.optJSONObject(1)
                            if (p2 != null) {
                                val label = p2.optString("label", "Gemini")
                                val rem = p2.optDouble("remaining_fraction", 1.0)
                                val resetStr = p2.optString("reset_time", "")
                                val pct = (rem * 100).toInt().coerceIn(0, 100)

                                views.setTextViewText(R.id.pool2_label, label)
                                views.setTextViewText(R.id.pool2_percent, "$pct%")
                                views.setTextViewText(R.id.pool2_reset_text, formatResetTime(resetStr))
                                views.setProgressBar(R.id.pool2_progress, 100, pct, false)
                                views.setViewVisibility(R.id.pool2_container, View.VISIBLE)
                            }
                        } else {
                            views.setViewVisibility(R.id.pool2_container, View.GONE)
                        }
                    }
                } catch (_: Exception) {}
            }

            return views
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = buildRemoteViews(context)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

private fun String?.isNull_or_empty(): Boolean = this == null || this.trim().isEmpty()
