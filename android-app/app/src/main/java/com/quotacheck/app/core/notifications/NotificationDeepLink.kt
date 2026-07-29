package com.quotacheck.app.core.notifications

import android.content.Intent

/** Explicit, allow-listed navigation contract for notification content intents. */
object NotificationDeepLink {
    const val ACTION_OPEN_NOTIFICATION = "com.quotacheck.app.action.OPEN_NOTIFICATION"
    const val EXTRA_ROUTE = "com.quotacheck.app.extra.NOTIFICATION_ROUTE"
    const val EXTRA_ALERT_KEY = "com.quotacheck.app.extra.ALERT_KEY"

    const val ROUTE_HOME = "home"
    const val ROUTE_ALERTS = "alerts"

    fun routeFor(command: AlertCommand): String = when (command) {
        is AlertCommand.Low, is AlertCommand.Critical, is AlertCommand.Reset -> ROUTE_ALERTS
        is AlertCommand.SyncFailure, is AlertCommand.SyncSuccess -> ROUTE_HOME
    }

    fun routeFrom(intent: Intent): String? = intent.takeIf { it.action == ACTION_OPEN_NOTIFICATION }
        ?.getStringExtra(EXTRA_ROUTE)
        ?.takeIf { it == ROUTE_HOME || it == ROUTE_ALERTS }
}
