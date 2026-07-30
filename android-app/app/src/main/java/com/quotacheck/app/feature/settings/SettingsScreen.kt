package com.quotacheck.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.quotacheck.app.core.designsystem.QuotaCheckSpacing
import com.quotacheck.app.core.designsystem.component.QuotaCard
import com.quotacheck.app.core.designsystem.component.SegmentedControl
import com.quotacheck.app.core.model.AppTheme
import com.quotacheck.app.core.model.UserPreferences

@Composable
fun SettingsScreen(
    uiState: UserPreferences,
    onAutoSync: (Boolean) -> Unit = {}, onInterval: (Int) -> Unit = {}, onWifiOnly: (Boolean) -> Unit = {},
    onTheme: (AppTheme) -> Unit = {}, onRetention: (Int) -> Unit = {}, onClearHistory: () -> Unit = {},
    onRemoveCredential: () -> Unit = {}, modifier: Modifier = Modifier,
) {
    var clearHistoryDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { testTag = "settings_content" },
        contentPadding = PaddingValues(QuotaCheckSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.md),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.titleLarge) }
        item {
            QuotaCard {
                Text("Account", style = MaterialTheme.typography.titleMedium)
                Text("Connected account", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onRemoveCredential, modifier = Modifier.semantics { testTag = "remove_credential" }) { Text("Remove credential") }
            }
        }
        item {
            QuotaCard {
                ToggleRow("Auto-sync", uiState.autoSyncEnabled, onAutoSync, "auto_sync")
                SectionLabel("Refresh interval")
                SegmentedControl(listOf(30, 60, 120, 240), uiState.syncIntervalMinutes, onInterval, { "$it min" }, tag = "sync_interval")
                ToggleRow("Wi-Fi only", uiState.wifiOnly, onWifiOnly, "wifi_only")
            }
        }
        item {
            QuotaCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                SectionLabel("Theme")
                SegmentedControl(AppTheme.entries, uiState.theme, onTheme, { it.name.lowercase().replaceFirstChar(Char::titlecase) }, tag = "theme")
                Text("Motion follows system accessibility settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            QuotaCard {
                SectionLabel("History retention")
                SegmentedControl(listOf(30, 90, 180), uiState.historyRetentionDays, onRetention, { "$it days" }, tag = "history_retention")
                OutlinedButton(onClick = { clearHistoryDialog = true }, modifier = Modifier.semantics { testTag = "clear_history" }) { Text("Clear local history") }
            }
        }
    }
    if (clearHistoryDialog) ClearDataDialog(onConfirm = { clearHistoryDialog = false; onClearHistory() }, onDismiss = { clearHistoryDialog = false })
}

@Composable private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked, onChange,
            Modifier.semantics { testTag = tag },
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
