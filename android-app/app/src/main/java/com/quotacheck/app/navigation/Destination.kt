package com.quotacheck.app.navigation

sealed class Destination(val route: String, val label: String) {
    data object Home : Destination("home", "Home")
    data object History : Destination("history", "History")
    data object Alerts : Destination("alerts", "Alerts")
    data object Settings : Destination("settings", "Settings")

    companion object {
        val bottomNavigation = listOf(Home, History, Alerts, Settings)
    }
}
