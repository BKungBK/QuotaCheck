package com.quotacheck.app.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.quotacheck.app.QuotaCheckApp
import com.quotacheck.app.feature.home.HomeScreen
import com.quotacheck.app.feature.home.HomeViewModel
import com.quotacheck.app.feature.home.HomeViewModelFactory
import com.quotacheck.app.feature.history.HistoryScreen
import com.quotacheck.app.feature.history.HistoryViewModel
import com.quotacheck.app.feature.history.HistoryViewModelFactory
import com.quotacheck.app.feature.alerts.AlertsScreen
import com.quotacheck.app.feature.alerts.AlertsViewModel
import com.quotacheck.app.feature.alerts.AlertsViewModelFactory
import com.quotacheck.app.feature.settings.SettingsScreen
import com.quotacheck.app.feature.settings.SettingsViewModel
import com.quotacheck.app.feature.settings.SettingsViewModelFactory

@Composable
fun QuotaCheckNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    notificationRoute: String? = null,
    onNotificationRouteHandled: () -> Unit = {},
) {
    LaunchedEffect(notificationRoute) {
        val destination = Destination.bottomNavigation.firstOrNull { it.route == notificationRoute } ?: return@LaunchedEffect
        if (navController.currentDestination?.route != destination.route) {
            navController.navigate(destination.route) { launchSingleTop = true }
        }
        onNotificationRouteHandled()
    }
    NavHost(
        navController = navController,
        startDestination = Destination.Home.route,
        modifier = modifier,
        enterTransition = { fadeIn(tween(180)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable(Destination.Home.route) {
            val container = (LocalContext.current.applicationContext as QuotaCheckApp).appContainer
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(container.quotaRepository, container.userPreferencesRepository, container.syncScheduler))
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(state, viewModel::refresh)
        }
        composable(Destination.History.route) {
            val container = (LocalContext.current.applicationContext as QuotaCheckApp).appContainer
            val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModelFactory(container.quotaRepository, container.historyDao))
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            HistoryScreen(state, viewModel::selectPool, viewModel::selectPeriod)
        }
        composable(Destination.Alerts.route) {
            val context = LocalContext.current
            val container = (context.applicationContext as QuotaCheckApp).appContainer
            val viewModel: AlertsViewModel = viewModel(factory = AlertsViewModelFactory(container.userPreferencesRepository))
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            AlertsScreen(state, viewModel::setLowThreshold, viewModel::setLowQuotaEnabled, viewModel::setCriticalThreshold, viewModel::setResetEnabled, viewModel::setFailureEnabled, viewModel::setSuccessEnabled, onOpenNotificationSettings = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            })
        }
        composable(Destination.Settings.route) {
            val container = (LocalContext.current.applicationContext as QuotaCheckApp).appContainer
            val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(container.userPreferencesRepository, container.credentialVault, container.syncScheduler, container.quotaDatabase, container.quotaRepository))
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(state, viewModel::setAutoSync, viewModel::setInterval, viewModel::setWifiOnly, viewModel::setTheme, viewModel::setRetention, viewModel::clearHistory, viewModel::removeCredential)
        }
    }
}
