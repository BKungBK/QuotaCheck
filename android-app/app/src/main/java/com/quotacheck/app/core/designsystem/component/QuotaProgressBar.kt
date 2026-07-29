package com.quotacheck.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quotacheck.app.core.designsystem.ProgressBarShape

@Composable
fun QuotaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(ProgressBarShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safeProgress)
                .height(6.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
