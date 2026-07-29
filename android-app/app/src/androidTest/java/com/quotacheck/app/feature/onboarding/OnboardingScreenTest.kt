package com.quotacheck.app.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun tokenFieldIsPasswordMaskedAndSubmissionClearsVisibleInput() {
        val submissions = mutableListOf<CharArray>()
        composeRule.setContent {
            OnboardingScreen(
                uiState = OnboardingUiState.NeedsToken,
                alertsEnabled = true,
                onAlertsEnabled = {},
                onSubmit = { submissions += it.copyOf() },
            )
        }

        val tokenField = composeRule.onNodeWithTag("onboarding_refresh_token")
        tokenField.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        tokenField.performTextInput("secret-token")
        composeRule.onNodeWithTag("onboarding_connect").performClick()
        composeRule.onNodeWithTag("onboarding_connect").performClick()
        composeRule.runOnIdle {
            assertArrayEquals("secret-token".toCharArray(), submissions[0])
            assertArrayEquals(charArrayOf(), submissions[1])
        }
    }

    @Test fun deniedPermissionKeepsHomeVisibleAndCompletesGate() {
        var completed = false
        composeRule.setContent {
            Box {
                Text("Home content")
                NotificationPermissionGate(
                    shouldRequest = true,
                    onResult = { completed = true },
                    requestPermission = { callback -> callback(false) },
                )
            }
        }

        composeRule.onNodeWithText("Allow alerts").performClick()

        composeRule.onNodeWithText("Home content").assertIsDisplayed()
        composeRule.onNodeWithText("Notifications are off. Quota remains available.").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(completed) }
    }
}
