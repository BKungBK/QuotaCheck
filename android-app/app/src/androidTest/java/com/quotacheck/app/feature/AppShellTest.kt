package com.quotacheck.app.feature

import com.quotacheck.app.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellTest {
    @Test fun exposesFourFixedBottomNavigationDestinations() {
        assertEquals(
            listOf("home", "history", "alerts", "settings"),
            Destination.bottomNavigation.map(Destination::route),
        )
        assertTrue(Destination.bottomNavigation.all { it.label.isNotBlank() })
    }

    @Test fun bottomNavigationTargetsMeetAccessibilityMinimum() {
        assertTrue(AppShellMinTouchTarget.value >= 48f)
    }
}
