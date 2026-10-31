package ca.sleung.gfs.ui.test

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.sleung.gfs.ui.screens.onboarding.OnboardingContent
import ca.sleung.gfs.ui.theme.GFSTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingJourneyTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun onboardingContent_entersBudgetAndCompletesOnboarding() {
        var completedBudget = 0.0

        composeTestRule.setContent {
            GFSTheme(dynamicColor = false) {
                OnboardingContent(
                    isSubmitting = false,
                    onStartSpending = { budget ->
                        completedBudget = budget
                    }
                )
            }
        }

        // Verify title & initial default
        composeTestRule.onNodeWithText("Welcome to\nGuilt-Free Spending").assertIsDisplayed()
        composeTestRule.onNodeWithText("MONTHLY GUILT-FREE BUDGET").assertIsDisplayed()

        // Capture screenshot of Onboarding screen
        val screenshot = ScreenshotHelper.captureAndSave(composeTestRule.onRoot(), "onboarding_screen")
        assertTrue(screenshot.exists())

        // Click start spending
        composeTestRule.onNodeWithText("Start Spending Guilt-Free →").performClick()
        assertEquals(500.0, completedBudget, 0.001)
    }
}
