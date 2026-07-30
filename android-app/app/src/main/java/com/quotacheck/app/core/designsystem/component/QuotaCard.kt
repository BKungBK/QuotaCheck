package com.quotacheck.app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quotacheck.app.core.designsystem.QuotaCheckSpacing

/**
 * Base surface for grouped content. Defaults to full width — previously this only wrapped
 * its content's width, so a card with short/narrow content (e.g. a single stepper row) would
 * render half-width next to full-width siblings. Always pass an explicit modifier if a card
 * genuinely needs to be narrower than its container.
 */
@Composable
fun QuotaCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(QuotaCheckSpacing.lg),
    contentSpacing: androidx.compose.ui.unit.Dp = QuotaCheckSpacing.sm,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        shadowElevation = 6.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(contentSpacing),
            content = content,
        )
    }
}
