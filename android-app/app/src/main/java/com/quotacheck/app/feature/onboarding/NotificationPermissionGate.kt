package com.quotacheck.app.feature.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun NotificationPermissionGate(
    shouldRequest: Boolean,
    onResult: () -> Unit,
    requestPermission: (((Boolean) -> Unit) -> Unit)? = null,
) {
    var explanationVisible by remember(shouldRequest) { mutableStateOf(shouldRequest) }
    var denied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        onResult()
    }
    val request: ((Boolean) -> Unit) -> Unit = requestPermission ?: { _: (Boolean) -> Unit ->
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (explanationVisible && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || requestPermission != null)) {
        AlertDialog(
            title = { Text("Enable quota alerts?") },
            text = { Text("Alerts can warn you about low quota. You can still view and refresh quota if you decline.") },
            onDismissRequest = { explanationVisible = false; onResult() },
            confirmButton = { Button(onClick = { explanationVisible = false; request { granted -> denied = !granted; onResult() } }) { Text("Allow alerts") } },
            dismissButton = { Button(onClick = { explanationVisible = false; onResult() }) { Text("Not now") } },
        )
    }
    LaunchedEffect(shouldRequest) {
        if (shouldRequest && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onResult()
    }
    if (denied) Text("Notifications are off. Quota remains available.")
}
