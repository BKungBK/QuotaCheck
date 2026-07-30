package com.quotacheck.app.feature.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.quotacheck.app.core.designsystem.QuotaCheckSpacing
import com.quotacheck.app.core.designsystem.component.QuotaCard
import com.quotacheck.app.core.designsystem.component.Stepper
import com.quotacheck.app.core.model.UserPreferences

@Composable
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
        contentPadding = PaddingValues(QuotaCheckSpacing.lg),
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
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.padding(end = QuotaCheckSpacing.xs))
                Text("Android notification settings")
            }
        }
    }
}

@Composable private fun ThresholdCard(label: String, value: Int, validValues: IntRange, onValue: (Int) -> Unit, tag: String) {
    QuotaCard {
        Text(label, style = MaterialTheme.typography.titleMedium)
        ThresholdControls(value, validValues, onValue, tag)
    }
}

@Composable private fun ThresholdControls(value: Int, validValues: IntRange, onValue: (Int) -> Unit, tag: String, enabled: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Notify at $value% remaining", style = MaterialTheme.typography.bodyMedium)
        Stepper(
            value = "$value%",
            onDecrement = { onValue((value - 1).coerceAtLeast(validValues.first)) },
            onIncrement = { onValue((value + 1).coerceAtMost(validValues.last)) },
            decrementEnabled = enabled && value > validValues.first,
            incrementEnabled = enabled && value < validValues.last,
            tag = tag,
        )
    }
}

@Composable private fun AlertToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, tag: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { testTag = tag },
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
