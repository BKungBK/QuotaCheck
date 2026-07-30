package com.quotacheck.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    alertsEnabled: Boolean,
    onAlertsEnabled: (Boolean) -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    var tokenInput by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Connect QuotaCheck", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Paste a refresh token. It is validated before encrypted storage and is never shown again.",
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("Refresh token") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().semantics { password(); testTag = "onboarding_refresh_token" },
        )
        Row(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = alertsEnabled, onCheckedChange = onAlertsEnabled)
            Text("Enable quota alerts", style = MaterialTheme.typography.bodyLarge)
        }
        when (uiState) {
            OnboardingUiState.Validating -> Text("Validating token...")
            OnboardingUiState.InitialSyncing -> Text("Getting your quota...")
            OnboardingUiState.TokenRequired -> Text("Enter a refresh token to continue.")
            OnboardingUiState.ValidationFailed -> Text(
                "Token is expired, revoked, or invalid. Please check and paste an active refresh token.",
                color = MaterialTheme.colorScheme.error,
            )
            OnboardingUiState.NetworkError -> Text(
                "Could not reach Google's servers. Check your internet connection and try again.",
                color = MaterialTheme.colorScheme.error,
            )
            OnboardingUiState.RateLimited -> Text(
                "Too many requests. Please wait a moment and try again.",
                color = MaterialTheme.colorScheme.error,
            )
            OnboardingUiState.InitialSyncFailed -> Text("Quota could not be loaded. The token was not kept.")
            OnboardingUiState.Loading -> CircularProgressIndicator()
            else -> Unit
        }
        Button(
            enabled = uiState !is OnboardingUiState.Validating && uiState !is OnboardingUiState.InitialSyncing,
            onClick = { onSubmit(tokenInput.toCharArray()); tokenInput = "" },
            modifier = Modifier.fillMaxWidth().semantics { testTag = "onboarding_connect" },
        ) { Text("Connect") }
    }
}
