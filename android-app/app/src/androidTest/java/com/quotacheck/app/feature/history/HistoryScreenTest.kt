package com.quotacheck.app.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.quotacheck.app.core.designsystem.QuotaCheckTheme
import com.quotacheck.app.core.model.QuotaPool
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class HistoryScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun showsPoolPeriodControlsAndAccessibleTimelineFallback() {
        composeRule.setContent { QuotaCheckTheme { HistoryScreen(state()) } }
        composeRule.onNodeWithText("Gemini").assertIsDisplayed()
        composeRule.onNodeWithTag("history_pool_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("history_period_control").assertIsDisplayed()
        composeRule.onNodeWithTag("usage_bar_chart").assertIsDisplayed()
        composeRule.onNodeWithText("32% consumed").assertIsDisplayed()
    }

    @Test fun fourPoolsRemainReachableAtLargeFontScale() {
        composeRule.setContent { QuotaCheckTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.4f)) {
                HistoryScreen(state((1..4).map { pool("Pool $it") }))
            }
        } }
        composeRule.onNodeWithText("Pool 4").performScrollTo().assertIsDisplayed()
    }

    @Test fun insufficientHistoryShowsEmptyState() {
        composeRule.setContent { QuotaCheckTheme { HistoryScreen(HistoryUiState()) } }
        composeRule.onNodeWithTag("history_empty").assertIsDisplayed()
    }

    private fun state(pools: List<QuotaPool> = listOf(pool("Gemini"))) = HistoryUiState(
        pools = pools,
        selectedPoolId = pools.first().poolId,
        bars = listOf(HistoryBar(Instant.EPOCH, 32.0, true)),
    )
    private fun pool(name: String) = QuotaPool(name, name, "5 hours", null, null, null, null, .68, null, null, null, Instant.EPOCH, "test")
}
