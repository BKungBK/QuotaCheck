package com.quotacheck.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quotacheck.app.core.designsystem.QuotaCheckSpacing
import com.quotacheck.app.core.designsystem.QuotaHeroNumberStyle
import com.quotacheck.app.core.designsystem.component.QuotaProgressBar
import com.quotacheck.app.core.designsystem.providerColor
import com.quotacheck.app.core.designsystem.statusColor
import com.quotacheck.app.core.model.QuotaFormatter
import com.quotacheck.app.core.model.QuotaPool
import java.time.Instant

@Composable
fun QuotaPoolRow(pool: QuotaPool, now: Instant, modifier: Modifier = Modifier) {
    val remaining = QuotaFormatter.percent(pool.remainingFraction)
    val identity = providerColor(pool.displayName)
    val status = statusColor(pool.remainingFraction)
    val warning = pool.remainingFraction <= .20
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${pool.displayName}, $remaining remaining" }
            .padding(vertical = QuotaCheckSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.sm),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(identity, CircleShape),
                )
                Column(Modifier.padding(start = QuotaCheckSpacing.sm)) {
                    Text(pool.displayName, style = MaterialTheme.typography.titleMedium)
                    pool.windowLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Text(remaining, style = QuotaHeroNumberStyle, color = MaterialTheme.colorScheme.onSurface)
        }
        QuotaProgressBar(progress = pool.remainingFraction.toFloat(), fillColor = status)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Reset ${QuotaFormatter.reset(pool.cycleEndAt, now)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (warning) Text("Low quota", style = MaterialTheme.typography.bodySmall, color = status)
        }
    }
}
