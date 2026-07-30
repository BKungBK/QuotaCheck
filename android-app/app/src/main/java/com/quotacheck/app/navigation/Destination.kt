package com.quotacheck.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Home : Destination("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object History : Destination("history", "History", Icons.Filled.ShowChart, Icons.Outlined.ShowChart)
    data object Alerts : Destination("alerts", "Alerts", Icons.Filled.NotificationsActive, Icons.Outlined.NotificationsActive)
    data object Settings : Destination("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)

    companion object {
        val bottomNavigation = listOf(Home, History, Alerts, Settings)
    }
}
