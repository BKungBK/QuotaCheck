package com.quotacheck.app.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quotacheck.app.core.designsystem.QuotaCheckSpacing
import com.quotacheck.app.core.designsystem.component.QuotaCard
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onSelectPool: (String) -> Unit = {},
    onSelectPeriod: (HistoryPeriod) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { testTag = "history_content" },
        contentPadding = PaddingValues(QuotaCheckSpacing.md),
        verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.md),
    ) {
        item {
            Text("Usage history", style = MaterialTheme.typography.titleLarge)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.sm),
                modifier = Modifier.fillMaxWidth().semantics { testTag = "history_pool_selector" },
            ) {
                items(uiState.pools, key = { it.poolId }) { pool ->
                    FilterChip(selected = pool.poolId == uiState.selectedPoolId, onClick = { onSelectPool(pool.poolId) }, label = { Text(pool.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.sm), modifier = Modifier.semantics { testTag = "history_period_control" }) {
                HistoryPeriod.entries.forEach { period ->
                    FilterChip(selected = uiState.period == period, onClick = { onSelectPeriod(period) }, label = { Text(period.label) })
                }
            }
        }
        if (uiState.isEmpty) {
            item { QuotaCard { Text("Not enough history yet. Usage will appear after more quota changes.", modifier = Modifier.semantics { testTag = "history_empty" }) } }
        } else {
            item {
                QuotaCard {
                    Column(verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.sm)) {
                        Text("Consumed", style = MaterialTheme.typography.titleMedium)
                        UsageBarChart(uiState.bars, Modifier.fillMaxWidth().heightIn(min = 180.dp).semantics { testTag = "usage_bar_chart" })
                    }
                }
            }
            item { Text("Timeline", style = MaterialTheme.typography.titleMedium) }
            items(uiState.bars, key = { it.startAt }) { bar ->
                val value = if (!bar.isPercentage && uiState.selectedPool?.unitLabel != null) "${bar.consumed} ${uiState.selectedPool?.unitLabel}" else "${bar.consumed.toInt()}% consumed"
                Row(
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${DATE.format(bar.startAt)}: $value" },
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(DATE.format(bar.startAt), style = MaterialTheme.typography.bodyMedium)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
