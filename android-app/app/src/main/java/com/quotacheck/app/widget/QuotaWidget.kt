package com.quotacheck.app.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.quotacheck.app.QuotaCheckApp
import com.quotacheck.app.widget.ui.QuotaWidgetContent
import com.quotacheck.app.widget.ui.QuotaWidgetTheme
import kotlinx.coroutines.flow.first

class QuotaWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            SMALL_SQUARE,
            MEDIUM_HORIZONTAL,
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContainer = (context.applicationContext as QuotaCheckApp).appContainer
        val pools = appContainer.quotaRepository.currentPools.first()

        provideContent {
            QuotaWidgetTheme {
                QuotaWidgetContent(pools = pools)
            }
        }
    }

    companion object {
        private val SMALL_SQUARE = DpSize(120.dp, 100.dp)
        private val MEDIUM_HORIZONTAL = DpSize(220.dp, 100.dp)
    }
}
