package com.quotacheck.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quotacheck.app.core.designsystem.QuotaCheckSpacing
import com.quotacheck.app.core.designsystem.component.QuotaCard
import com.quotacheck.app.core.model.QuotaFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(uiState: HomeUiState, onRefresh: () -> Unit = {}, modifier: Modifier = Modifier) {
    when (uiState) {
        HomeUiState.Unconfigured -> HomeEmptyState("Connect an account to see quota.", "Connect")
        HomeUiState.InitialLoading -> HomeLoading()
        HomeUiState.AuthRequired -> HomeEmptyState("Your credential needs to be replaced.", "Open settings")
        is HomeUiState.ErrorEmpty -> HomeEmptyState(uiState.message ?: "Quota could not be loaded.", "Refresh", onRefresh)
        is HomeUiState.Fresh -> HomeContent(uiState.content, false, null, onRefresh, modifier)
        is HomeUiState.Stale -> HomeContent(uiState.content, false, "Data may be out of date.", onRefresh, modifier)
        is HomeUiState.OfflineCached -> HomeContent(uiState.content, false, "Offline — showing cached quota.", onRefresh, modifier)
        is HomeUiState.Refreshing -> HomeContent(uiState.content, true, statusWarning(uiState.status), onRefresh, modifier)
    }
}

@Composable
private fun HomeContent(content: HomeContent, refreshing: Boolean, warning: String?, onRefresh: () -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { testTag = "home_content" },
        contentPadding = PaddingValues(QuotaCheckSpacing.md),
        verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.sm),
    ) {
        item { QuotaCard { Text("All quotas", style = MaterialTheme.typography.titleLarge) } }
        warning?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        items(content.pools, key = { it.poolId }) { pool ->
            val index = content.pools.indexOf(pool) + 1
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    QuotaPoolRow(pool, Instant.now(), Modifier.semantics { testTag = "quota_pool_row_$index" })
                    if (index < content.pools.size) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().semantics { testTag = "home_footer" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Updated ${content.updatedAt?.let(UPDATED_TIME::format) ?: "—"}", style = MaterialTheme.typography.bodySmall)
                    Text("Auto-refresh every ${content.syncIntervalMinutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onRefresh, enabled = !refreshing) { Text(if (refreshing) "Refreshing" else "Refresh") }
            }
        }
    }
}

@Composable
private fun HomeLoading() = LazyColumn(
    modifier = Modifier.fillMaxSize().semantics { testTag = "home_loading" },
    contentPadding = PaddingValues(QuotaCheckSpacing.md),
    verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.sm),
) {
    item { QuotaCard { Text("All quotas", style = MaterialTheme.typography.titleLarge) } }
    items(4) {
        QuotaCard {
            Column(verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.sm)) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(.45f).height(18.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)))
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)))
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(.3f).height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)))
            }
        }
    }
}
@Composable private fun HomeEmptyState(message: String, action: String, onAction: () -> Unit = {}) = Column(Modifier.fillMaxSize().padding(QuotaCheckSpacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(message); Button(onClick = onAction) { Text(action) } }
private fun statusWarning(state: HomeUiState): String? = when (state) { is HomeUiState.Stale -> "Data may be out of date."; is HomeUiState.OfflineCached -> "Offline — showing cached quota."; else -> null }
private val UPDATED_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
