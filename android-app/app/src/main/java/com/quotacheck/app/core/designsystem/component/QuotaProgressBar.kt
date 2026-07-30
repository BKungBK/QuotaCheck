package com.quotacheck.app.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quotacheck.app.core.designsystem.ProgressBarShape

/**
 * Progress bar for remaining quota. The fill color shifts healthy -> warning -> critical as
 * the value drops, and both color and width animate — this used to snap instantly, which read
 * as broken on refresh (the bar would just jump).
 */
@Composable
fun QuotaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    fillColor: Color = MaterialTheme.colorScheme.primary,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = safeProgress,
        animationSpec = tween(durationMillis = 500),
        label = "quota_progress",
    )
    val animatedColor by animateColorAsState(
        targetValue = fillColor,
        animationSpec = tween(durationMillis = 300),
        label = "quota_progress_color",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(ProgressBarShape)
            .background(trackColor)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(ProgressBarShape)
                .background(animatedColor),
        )
    }
}
