package com.quotacheck.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.quotacheck.app.core.designsystem.QuotaCheckSpacing
import com.quotacheck.app.core.designsystem.WarningAmber
import com.quotacheck.app.core.designsystem.component.QuotaProgressBar
import com.quotacheck.app.core.model.QuotaFormatter
import com.quotacheck.app.core.model.QuotaPool
import java.time.Instant

@Composable
fun QuotaPoolRow(pool: QuotaPool, now: Instant, modifier: Modifier = Modifier) {
    val remaining = QuotaFormatter.percent(pool.remainingFraction)
    val warning = pool.remainingFraction <= .20
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${pool.displayName}, $remaining remaining" }
            .padding(vertical = QuotaCheckSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(pool.displayName, style = MaterialTheme.typography.titleMedium)
                pool.windowLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(remaining, style = MaterialTheme.typography.titleMedium)
        }
        QuotaProgressBar(progress = pool.remainingFraction.toFloat())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Reset ${QuotaFormatter.reset(pool.cycleEndAt, now)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (warning) Text("Low quota", style = MaterialTheme.typography.bodySmall, color = WarningAmber)
        }
    }
}
