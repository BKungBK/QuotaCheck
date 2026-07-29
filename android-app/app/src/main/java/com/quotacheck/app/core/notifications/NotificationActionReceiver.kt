package com.quotacheck.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.quotacheck.app.QuotaCheckApp

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            (context.applicationContext as QuotaCheckApp).appContainer.syncScheduler.refreshNow()
        }
    }

    companion object { const val ACTION_REFRESH = "com.quotacheck.app.action.REFRESH" }
}
