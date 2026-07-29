package com.quotacheck.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import com.quotacheck.app.core.designsystem.QuotaCheckTheme
import com.quotacheck.app.core.model.AppTheme
import com.quotacheck.app.core.model.UserPreferences
import com.quotacheck.app.core.notifications.NotificationDeepLink
import com.quotacheck.app.feature.AppShell
import com.quotacheck.app.feature.onboarding.NotificationPermissionGate
import com.quotacheck.app.feature.onboarding.OnboardingScreen
import com.quotacheck.app.feature.onboarding.OnboardingUiState
import com.quotacheck.app.feature.onboarding.OnboardingViewModel
import com.quotacheck.app.feature.onboarding.OnboardingViewModelFactory

class MainActivity : ComponentActivity() {
    private var pendingNotificationRoute by mutableStateOf<String?>(null)
    private val onboardingViewModel: OnboardingViewModel by lazy {
        val container = (application as QuotaCheckApp).appContainer
        ViewModelProvider(
            this,
            OnboardingViewModelFactory(
                container.credentialVault,
                container.quotaRemoteDataSource,
                container.quotaRepository,
                container.userPreferencesRepository,
                container.syncScheduler,
            ),
        )[OnboardingViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeNotificationIntent(intent)
        setContent {
            val container = application as QuotaCheckApp
            val preferences by container.appContainer.userPreferencesRepository.preferences.collectAsState(initial = UserPreferences())
            QuotaCheckTheme(darkTheme = when (preferences.theme) {
                AppTheme.DARK -> true
                AppTheme.LIGHT -> false
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }) {
                Surface {
                    val uiState by onboardingViewModel.uiState.collectAsState()
                    val alertsEnabled by onboardingViewModel.alertsEnabled.collectAsState()
                    val shouldRequestPermission by onboardingViewModel.notificationPermissionRequested.collectAsState()
                    LaunchedEffect(Unit) { onboardingViewModel.load() }
                    LaunchedEffect(uiState) {
                        if (uiState is OnboardingUiState.Connected) onboardingViewModel.onHomeVisible()
                    }
                    Box {
                        if (uiState is OnboardingUiState.Connected && preferences.onboardingCompleted) {
                            AppShell(
                                notificationRoute = pendingNotificationRoute,
                                onNotificationRouteHandled = { pendingNotificationRoute = null },
                            )
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent(intent)
    }

    private fun consumeNotificationIntent(intent: Intent?) {
        val route = intent?.let(NotificationDeepLink::routeFrom) ?: return
        pendingNotificationRoute = route
        intent.action = null
        intent.removeExtra(NotificationDeepLink.EXTRA_ROUTE)
    }
}
