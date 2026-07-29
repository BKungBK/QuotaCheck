package com.quotacheck.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Connect QuotaCheck")
        Text("Paste a refresh token. It is validated before encrypted storage and is never shown again.")
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("Refresh token") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.semantics { password(); testTag = "onboarding_refresh_token" },
        )
        Checkbox(checked = alertsEnabled, onCheckedChange = onAlertsEnabled)
        Text("Enable quota alerts")
        when (uiState) {
            OnboardingUiState.Validating -> Text("Validating token...")
            OnboardingUiState.InitialSyncing -> Text("Getting your quota...")
            OnboardingUiState.TokenRequired -> Text("Enter a refresh token to continue.")
            OnboardingUiState.ValidationFailed -> Text("That token could not be validated. Check it and try again.")
            OnboardingUiState.InitialSyncFailed -> Text("Quota could not be loaded. The token was not kept.")
            OnboardingUiState.Loading -> CircularProgressIndicator()
            else -> Unit
        }
        Button(
            enabled = uiState !is OnboardingUiState.Validating && uiState !is OnboardingUiState.InitialSyncing,
            onClick = { onSubmit(tokenInput.toCharArray()); tokenInput = "" },
            modifier = Modifier.semantics { testTag = "onboarding_connect" },
        ) { Text("Connect") }
    }
}
