package com.quotacheck.app.widget.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.quotacheck.app.MainActivity
import com.quotacheck.app.R
import com.quotacheck.app.core.model.QuotaFormatter
import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.widget.RefreshQuotaActionCallback
import java.time.Instant

@Composable
fun QuotaWidgetContent(
    pools: List<QuotaPool>,
    modifier: GlanceModifier = GlanceModifier,
) {
    val size = LocalSize.current
    val isExpanded = size.width >= 220.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WidgetColors.background)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (pools.isEmpty()) {
            EmptyWidgetContent()
        } else if (isExpanded) {
            ExpandedWidgetContent(pools = pools)
        } else {
            CompactWidgetContent(pools = pools)
        }
    }
}

@Composable
private fun EmptyWidgetContent() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "QuotaCheck",
            style = TextStyle(
                color = WidgetColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "No pools configured",
            style = TextStyle(
                color = WidgetColors.textMuted,
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun CompactWidgetContent(pools: List<QuotaPool>) {
    val lowestPool = pools.minByOrNull { it.remainingFraction } ?: pools.first()
    val percentageStr = QuotaFormatter.percent(lowestPool.remainingFraction)
    val now = Instant.now()
    val resetText = QuotaFormatter.reset(lowestPool.cycleEndAt, now)

    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "QuotaCheck",
                style = TextStyle(
                    color = WidgetColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "● live",
                style = TextStyle(
                    color = WidgetColors.statusHealthy,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        Text(
            text = lowestPool.displayName,
            style = TextStyle(
                color = WidgetColors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = percentageStr,
            style = TextStyle(
                color = WidgetColors.statusColor(lowestPool.remainingFraction),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "reset $resetText",
                style = TextStyle(
                    color = WidgetColors.textMuted,
                    fontSize = 11.sp,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            // Generous touch target wrapper around icon
            Box(
                modifier = GlanceModifier
                    .size(36.dp)
                    .clickable(actionRunCallback<RefreshQuotaActionCallback>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ExpandedWidgetContent(pools: List<QuotaPool>) {
    val now = Instant.now()

    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "QuotaCheck",
                style = TextStyle(
                    color = WidgetColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "● live",
                style = TextStyle(
                    color = WidgetColors.statusHealthy,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Pool Items
        pools.take(3).forEach { pool ->
            val pctStr = QuotaFormatter.percent(pool.remainingFraction)
            val resetStr = QuotaFormatter.reset(pool.cycleEndAt, now)
            Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = pool.displayName,
                        style = TextStyle(
                            color = WidgetColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = pctStr,
                        style = TextStyle(
                            color = WidgetColors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                Spacer(modifier = GlanceModifier.height(3.dp))
                LinearProgressIndicator(
                    progress = pool.remainingFraction.toFloat(),
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                    color = WidgetColors.statusColor(pool.remainingFraction),
                    backgroundColor = WidgetColors.surface,
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = "reset $resetStr",
                        style = TextStyle(
                            color = WidgetColors.textMuted,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.defaultWeight())

        // Footer with 36dp Touch Target Refresh Button at bottom right
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Cloud • QuotaCheck",
                style = TextStyle(
                    color = WidgetColors.textMuted,
                    fontSize = 10.sp,
                ),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Box(
                modifier = GlanceModifier
                    .size(36.dp)
                    .clickable(actionRunCallback<RefreshQuotaActionCallback>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = "Refresh",
                    modifier = GlanceModifier.size(16.dp),
                )
            }
        }
    }
}
