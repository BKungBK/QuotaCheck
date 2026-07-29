package com.quotacheck.app.feature.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.quotacheck.app.core.designsystem.QuotaCheckTheme
import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.feature.AppShell
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun fourPoolRowsAndFooterFitCurrentDeviceWindow() {
        composeRule.setContent {
            QuotaCheckTheme {
                AppShell { padding -> HomeScreen(content(fourPools()), modifier = Modifier.fillMaxSize().padding(padding)) }
            }
        }
        (1..4).forEach { composeRule.onNodeWithTag("quota_pool_row_$it").assertIsDisplayed() }
        composeRule.onNodeWithTag("home_footer").assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_navigation").assertIsDisplayed()
    }

    @Test fun increasedFontScaleUsesScrollableContentWhileBottomNavRemainsVisible() {
        composeRule.setContent {
            QuotaCheckTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.4f)) {
                    AppShell { padding -> HomeScreen(content(fourPools() + pool(5)), modifier = Modifier.fillMaxSize().padding(padding)) }
                }
            }
        }
        composeRule.onNodeWithTag("quota_pool_row_5").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("bottom_navigation").assertIsDisplayed()
    }

    @Test fun refreshInProgressKeepsRowsAndDisablesRefresh() {
        val fresh = content(fourPools())
        composeRule.setContent {
            QuotaCheckTheme {
                HomeScreen(HomeUiState.Refreshing(fresh.content, fresh))
            }
        }
        composeRule.onNodeWithTag("quota_pool_row_1").assertIsDisplayed()
        composeRule.onNodeWithText("Refreshing").assertIsNotEnabled()
    }

    private fun content(pools: List<QuotaPool>) = HomeUiState.Fresh(HomeContent(pools, Instant.parse("2030-01-01T00:00:00Z"), 30))
    private fun fourPools() = (1..4).map(::pool)
    private fun pool(index: Int) = QuotaPool(
        poolId = "$index", displayName = "Pool $index", windowLabel = "5 hours", unitLabel = null,
        totalUnits = null, usedUnits = null, remainingUnits = null, remainingFraction = .68,
        cycleStartAt = null, cycleEndAt = Instant.parse("2030-01-01T03:00:00Z"), providerUpdatedAt = null,
        receivedAt = Instant.parse("2030-01-01T00:00:00Z"), schemaVersion = "test",
    )
}
