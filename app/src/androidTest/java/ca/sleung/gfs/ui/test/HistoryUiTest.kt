package ca.sleung.gfs.ui.test

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.sleung.gfs.domain.model.CategoryBreakdown
import ca.sleung.gfs.domain.model.Transaction
import ca.sleung.gfs.ui.screens.history.HistoryContent
import ca.sleung.gfs.ui.screens.history.HistoryUiState
import ca.sleung.gfs.ui.theme.GFSTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun historyContent_displaysBreakdownDonutChartAndFiltersCycles() {
        var selectedCycleKey: String? = "UNSET"

        val breakdown = listOf(
            CategoryBreakdown("Eating Out", "#FF7043", 120.0, 60f),
            CategoryBreakdown("Shopping", "#AB47BC", 80.0, 40f)
        )
        val transactions = listOf(
            Transaction(
                id = 10,
                amountCents = 4500L,
                description = "Ramen Dinner",
                timestamp = System.currentTimeMillis() - 7200_000,
                cycleKey = "2026-10",
                tag = "Eating Out",
                tagColor = "#FF7043"
            )
        )

        composeTestRule.setContent {
            GFSTheme(dynamicColor = false) {
                HistoryContent(
                    uiState = HistoryUiState(
                        transactions = transactions,
                        totalSpent = 200.0,
                        availableCycles = listOf("2026-09", "2026-08"),
                        currentCycleKey = "2026-10",
                        selectedCycle = null,
                        remainingBalance = 300.0,
                        breakdown = breakdown,
                        hideDecimalPlaces = true
                    ),
                    onNavigateBack = {},
                    onSelectCycle = { selectedCycleKey = it },
                    onSelectTransaction = {},
                    onDismissEdit = {},
                    onUpdateExpense = { _, _, _, _, _, _ -> },
                    onDeleteExpense = {}
                )
            }
        }

        // Verify title & spent summary
        composeTestRule.onNodeWithText("Spending History").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spent: $200").assertIsDisplayed()
        composeTestRule.onNodeWithText("SPENDING BREAKDOWN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ramen Dinner").assertIsDisplayed()

        // Capture screenshot of History with breakdown chart
        val screenshot = ScreenshotHelper.captureAndSave(composeTestRule.onRoot(), "history_screen")
        assertTrue(screenshot.exists())

        // Switch to "All Time"
        composeTestRule.onNodeWithText("All Time").performClick()
        assertEquals("ALL", selectedCycleKey)
    }
}
