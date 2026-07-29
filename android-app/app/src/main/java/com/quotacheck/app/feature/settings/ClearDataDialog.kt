package com.quotacheck.app.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag

@Composable
fun ClearDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear local history?") },
        text = { Text("This removes saved quota history from this device. Your account stays connected.") },
        confirmButton = { TextButton(onClick = onConfirm, modifier = androidx.compose.ui.Modifier.semantics { testTag = "confirm_clear_history" }) { Text("Clear history") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
