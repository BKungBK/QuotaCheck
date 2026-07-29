package com.quotacheck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.quotacheck.app.core.designsystem.QuotaCheckTheme
import com.quotacheck.app.feature.AppShell
import com.quotacheck.app.feature.onboarding.NotificationPermissionGate
import com.quotacheck.app.feature.onboarding.OnboardingScreen
import com.quotacheck.app.feature.onboarding.OnboardingUiState
import com.quotacheck.app.feature.onboarding.OnboardingViewModel
import com.quotacheck.app.feature.onboarding.OnboardingViewModelFactory

class MainActivity : ComponentActivity() {
    private val onboardingViewModel: OnboardingViewModel by lazy {
        val container = (application as QuotaCheckApp).appContainer
        ViewModelProvider(
            this,
            OnboardingViewModelFactory(
                container.credentialVault,
                container.quotaRemoteDataSource,
                container.quotaRepository,
                container.userPreferencesRepository,
            ),
        )[OnboardingViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuotaCheckTheme {
                Surface {
                    val uiState by onboardingViewModel.uiState.collectAsState()
                    val alertsEnabled by onboardingViewModel.alertsEnabled.collectAsState()
                    val shouldRequestPermission by onboardingViewModel.notificationPermissionRequested.collectAsState()
                    LaunchedEffect(Unit) { onboardingViewModel.load() }
                    LaunchedEffect(uiState) {
                        if (uiState is OnboardingUiState.Connected) onboardingViewModel.onHomeVisible()
                    }
                    Box {
                        if (uiState is OnboardingUiState.Connected) {
                            AppShell()
                        } else {
                            OnboardingScreen(
                                uiState = uiState,
                                alertsEnabled = alertsEnabled,
                                onAlertsEnabled = onboardingViewModel::setAlertsEnabled,
                                onSubmit = onboardingViewModel::submitToken,
                            )
                        }
                        NotificationPermissionGate(
                            shouldRequest = shouldRequestPermission,
                            onResult = onboardingViewModel::onNotificationPermissionResult,
                        )
                    }
                }
            }
        }
    }
}
