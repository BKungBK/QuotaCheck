package com.quotacheck.app.feature.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quotacheck.app.core.designsystem.QuotaCheckSpacing
import com.quotacheck.app.core.designsystem.component.QuotaCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(uiState: HomeUiState, onRefresh: () -> Unit = {}, modifier: Modifier = Modifier) {
    when (uiState) {
        HomeUiState.Unconfigured -> HomeEmptyState(Icons.Filled.Link, "Connect an account to see quota.", "Connect")
        HomeUiState.InitialLoading -> HomeLoading()
        HomeUiState.AuthRequired -> HomeEmptyState(Icons.Filled.Link, "Your credential needs to be replaced.", "Open settings")
        is HomeUiState.ErrorEmpty -> HomeEmptyState(Icons.Filled.CloudOff, uiState.message ?: "Quota could not be loaded.", "Refresh", onRefresh)
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
        contentPadding = PaddingValues(QuotaCheckSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.md),
    ) {
        item { Text("All quotas", style = MaterialTheme.typography.titleLarge) }
        warning?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        item {
            QuotaCard(contentSpacing = 0.dp) {
                content.pools.forEachIndexed { index, pool ->
                    QuotaPoolRow(pool, Instant.now(), Modifier.semantics { testTag = "quota_pool_row_${index + 1}" })
                    if (index < content.pools.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
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
                RefreshButton(refreshing = refreshing, onClick = onRefresh)
            }
        }
    }
}

@Composable
private fun RefreshButton(refreshing: Boolean, onClick: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "refresh_spin")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (refreshing) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "refresh_spin_value",
    )
    Button(onClick = onClick, enabled = !refreshing) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.rotate(if (refreshing) rotation else 0f).size(18.dp),
            )
            Text(if (refreshing) "Refreshing" else "Refresh")
        }
    }
}

@Composable
private fun HomeLoading() = LazyColumn(
    modifier = Modifier.fillMaxSize().semantics { testTag = "home_loading" },
    contentPadding = PaddingValues(QuotaCheckSpacing.lg),
    verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.md),
) {
    item { Text("All quotas", style = MaterialTheme.typography.titleLarge) }
    items(2) { ShimmerQuotaCard() }
}

@Composable
private fun ShimmerQuotaCard() {
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
        label = "shimmer_alpha",
    )
    QuotaCard {
        Column(verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.sm)) {
            Box(Modifier.fillMaxWidth(.45f).height(18.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha), RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha), RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(.3f).height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha), RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun HomeEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String, action: String, onAction: () -> Unit = {}) = Column(
    Modifier.fillMaxSize().padding(QuotaCheckSpacing.xl),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    Box(
        modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    Column(Modifier.padding(vertical = QuotaCheckSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
    Button(onClick = onAction) { Text(action) }
}

private fun statusWarning(state: HomeUiState): String? = when (state) { is HomeUiState.Stale -> "Data may be out of date."; is HomeUiState.OfflineCached -> "Offline — showing cached quota."; else -> null }
private val UPDATED_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
