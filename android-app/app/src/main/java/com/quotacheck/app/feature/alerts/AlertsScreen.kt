package com.quotacheck.app.feature.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.quotacheck.app.core.designsystem.QuotaCheckSpacing
import com.quotacheck.app.core.designsystem.component.QuotaCard
import com.quotacheck.app.core.model.UserPreferences

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun AlertsScreen(
    uiState: UserPreferences,
    onLowThreshold: (Int) -> Unit = {},
    onLowQuotaEnabled: (Boolean) -> Unit = {},
    onCriticalThreshold: (Int) -> Unit = {},
    onResetEnabled: (Boolean) -> Unit = {},
    onFailureEnabled: (Boolean) -> Unit = {},
    onSuccessEnabled: (Boolean) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().semantics { testTag = "alerts_content" },
        contentPadding = PaddingValues(QuotaCheckSpacing.md),
        verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.md),
    ) {
        item { Text("Alerts", style = MaterialTheme.typography.titleLarge) }
        item {
            QuotaCard {
                AlertToggle("Low quota", uiState.lowQuotaNotificationsEnabled, onLowQuotaEnabled, "low_quota_notifications")
                ThresholdControls(uiState.lowThresholdPercent, uiState.criticalThresholdPercent + 1..100, onLowThreshold, "low_threshold", enabled = uiState.lowQuotaNotificationsEnabled)
            }
        }
        item { ThresholdCard("Critical quota", uiState.criticalThresholdPercent, 1 until uiState.lowThresholdPercent, onCriticalThreshold, "critical_threshold") }
        item {
            QuotaCard {
                AlertToggle("Billing reset", uiState.resetNotificationsEnabled, onResetEnabled, "reset_notifications")
                AlertToggle("Sync failure", uiState.failureNotificationsEnabled, onFailureEnabled, "failure_notifications")
                AlertToggle("Sync success", uiState.successNotificationsEnabled, onSuccessEnabled, "success_notifications")
            }
        }
        item {
            OutlinedButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth().semantics { testTag = "notification_settings" }) {
                Text("Android notification settings")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ThresholdCard(label: String, value: Int, validValues: IntRange, onValue: (Int) -> Unit, tag: String) {
    QuotaCard {
        Text(label, style = MaterialTheme.typography.titleMedium)
        ThresholdControls(value, validValues, onValue, tag)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ThresholdControls(value: Int, validValues: IntRange, onValue: (Int) -> Unit, tag: String, enabled: Boolean = true) {
        Text("Notify at $value% remaining", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.sm), verticalArrangement = Arrangement.spacedBy(QuotaCheckSpacing.sm)) {
            OutlinedButton(onClick = { onValue((value - 1).coerceAtLeast(validValues.first)) }, enabled = enabled && value > validValues.first) { Text("-") }
            OutlinedButton(onClick = { onValue((value + 1).coerceAtMost(validValues.last)) }, enabled = enabled && value < validValues.last, modifier = Modifier.semantics { testTag = tag }) { Text("+") }
        }
}

@Composable private fun AlertToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, tag: String) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.semantics { testTag = tag })
    }
}
