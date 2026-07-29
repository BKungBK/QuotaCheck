package com.quotacheck.app.feature

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.quotacheck.app.navigation.Destination
import com.quotacheck.app.navigation.QuotaCheckNavHost

val AppShellMinTouchTarget = 48.dp

@Composable
fun AppShell(content: (@Composable (PaddingValues) -> Unit)? = null) {
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(modifier = Modifier.semantics { testTag = "bottom_navigation" }) {
                Destination.bottomNavigation.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        modifier = Modifier.heightIn(min = AppShellMinTouchTarget),
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(Destination.Home.route) { saveState = true }
                                }
                            }
                        },
                        icon = {},
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(),
                    )
                }
            }
        },
    ) { innerPadding: PaddingValues ->
        content?.invoke(innerPadding) ?: QuotaCheckNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
        )
    }
}
