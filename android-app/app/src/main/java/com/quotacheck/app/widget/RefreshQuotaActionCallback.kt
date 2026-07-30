package com.quotacheck.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.quotacheck.app.QuotaCheckApp

class RefreshQuotaActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appContainer = (context.applicationContext as QuotaCheckApp).appContainer
        appContainer.syncScheduler.refreshNow()
        QuotaWidget().update(context, glanceId)
    }
}
