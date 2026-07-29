package com.quotacheck.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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

@Composable
fun QuotaCheckNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Destination.Home.route, modifier = modifier) {
        composable(Destination.Home.route) {
            val container = (LocalContext.current.applicationContext as QuotaCheckApp).appContainer
            val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(container.quotaRepository, container.userPreferencesRepository, container.syncScheduler))
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(state, viewModel::refresh)
        }
        Destination.bottomNavigation.filterNot { it == Destination.Home }.forEach { destination -> composable(destination.route) { DestinationPlaceholder(destination.label) } }
    }
}

@Composable
private fun DestinationPlaceholder(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = label, style = MaterialTheme.typography.titleLarge)
    }
}
