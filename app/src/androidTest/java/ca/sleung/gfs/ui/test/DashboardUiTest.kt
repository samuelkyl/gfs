package ca.sleung.gfs.ui.test

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.sleung.gfs.domain.model.BudgetInfo
import ca.sleung.gfs.domain.model.Tag
import ca.sleung.gfs.domain.model.Transaction
import ca.sleung.gfs.ui.screens.dashboard.DashboardContent
import ca.sleung.gfs.ui.screens.dashboard.DashboardUiState
import ca.sleung.gfs.ui.theme.GFSTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dashboardContent_displaysBudgetAndRecentExpenses_withHideDecimalsOn() {
        val budgetInfo = BudgetInfo.fromDouble(
            cycleKey = "2026-10",
            cycleName = "October",
            daysRemaining = 12,
            monthlyBudget = 500.0,
            rolloverAmount = 150.0,
            totalCycleBudget = 650.0,
            totalSpent = 156.50,
            remainingBalance = 493.50,
            remainingPercentage = 0.76f,
            hideDecimalPlaces = true
        )
        val transactions = listOf(
            Transaction(
                id = 1,
                amountCents = 3500L,
                description = "Sushi Dinner",
                timestamp = System.currentTimeMillis() - 3600_000,
                cycleKey = "2026-10",
                tag = "Eating Out",
                tagColor = "#FF7043"
            )
        )

        composeTestRule.setContent {
            GFSTheme(dynamicColor = false) {
                DashboardContent(
                    uiState = DashboardUiState(
                        budgetInfo = budgetInfo,
                        recentTransactions = transactions,
                        isLoading = false
                    ),
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onOpenAddSheet = {},
                    onCloseAddSheet = {},
                    onAddExpense = { _, _, _, _ -> },
                    onLoadMore = {}
                )
            }
        }

        // Verify whole dollar display (493.50 rounded to $494)
        composeTestRule.onNodeWithText("$494").assertIsDisplayed()
        composeTestRule.onNodeWithText("AVAILABLE TO SPEND").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sushi Dinner").assertIsDisplayed()
        composeTestRule.onNodeWithText("-$35").assertIsDisplayed()

        // Capture screenshot
        val screenshot = ScreenshotHelper.captureAndSave(composeTestRule.onRoot(), "dashboard_screen")
        assertTrue(screenshot.exists())
    }

    @Test
    fun dashboardContent_openAddExpenseSheet_allowsTypingAndSaving() {
        var savedAmount = 0.0
        var savedDescription = ""
        var sheetOpen = true

        composeTestRule.setContent {
            GFSTheme(dynamicColor = false) {
                DashboardContent(
                    uiState = DashboardUiState(
                        budgetInfo = BudgetInfo.fromDouble(
                            cycleKey = "2026-10",
                            cycleName = "October",
                            daysRemaining = 12,
                            monthlyBudget = 500.0,
                            remainingBalance = 500.0,
                            totalCycleBudget = 500.0
                        ),
                        tags = listOf(
                            Tag(1, "Eating Out", "#FF7043", 0),
                            Tag(2, "Shopping", "#AB47BC", 1)
                        ),
                        showAddExpenseSheet = sheetOpen
                    ),
                    onNavigateToHistory = {},
                    onNavigateToSettings = {},
                    onOpenAddSheet = { sheetOpen = true },
                    onCloseAddSheet = { sheetOpen = false },
                    onAddExpense = { amount, desc, _, _ ->
                        savedAmount = amount
                        savedDescription = desc
                    },
                    onLoadMore = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Log Expense").assertIsDisplayed()

        // Type amount using comma (European format support verification)
        composeTestRule.onNodeWithText("0").performTextInput("12,50")
        composeTestRule.onNodeWithText("Description (Optional)").performTextInput("Matcha Latte")
        composeTestRule.onNodeWithText("Eating Out").performClick()

        // Capture sheet screenshot
        val sheetScreenshot = ScreenshotHelper.captureAndSave(composeTestRule.onRoot(), "add_expense_sheet")
        assertTrue(sheetScreenshot.exists())

        // Save
        composeTestRule.onNodeWithText("Save Expense").performClick()

        assertEquals(12.50, savedAmount, 0.001)
        assertEquals("Matcha Latte", savedDescription)
    }

    @Test
    fun dashboardContent_wideScreen_displaysDualPaneLayout() {
        val budgetInfo = BudgetInfo.fromDouble(
            cycleKey = "2026-10",
            cycleName = "October",
            daysRemaining = 12,
            monthlyBudget = 500.0,
            rolloverAmount = 150.0,
            totalCycleBudget = 650.0,
            totalSpent = 156.50,
            remainingBalance = 493.50,
            remainingPercentage = 0.76f,
            hideDecimalPlaces = false
        )
        val transactions = listOf(
            Transaction(
                id = 1,
                amountCents = 3500L,
                description = "Sushi Dinner",
                timestamp = System.currentTimeMillis() - 3600_000,
                cycleKey = "2026-10",
                tag = "Eating Out",
                tagColor = "#FF7043"
            )
        )

        composeTestRule.setContent {
            GFSTheme(dynamicColor = false) {
                Box(
                    modifier = Modifier.size(
                        width = 840.dp,
                        height = 600.dp
                    )
                ) {
                    DashboardContent(
                        uiState = DashboardUiState(
                            budgetInfo = budgetInfo,
                            recentTransactions = transactions,
                            isLoading = false
                        ),
                        onNavigateToHistory = {},
                        onNavigateToSettings = {},
                        onOpenAddSheet = {},
                        onCloseAddSheet = {},
                        onAddExpense = { _, _, _, _ -> },
                        onLoadMore = {}
                    )
                }
            }
        }

        // Verify elements in dual-pane layout are displayed
        composeTestRule.onNodeWithText("$493.50").assertIsDisplayed()
        composeTestRule.onNodeWithText("AVAILABLE TO SPEND").assertIsDisplayed()
        composeTestRule.onNodeWithText("RECENT ACTIVITY").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sushi Dinner").assertIsDisplayed()
        composeTestRule.onNodeWithText("-$35.00").assertIsDisplayed()

        // Capture screenshot of wide layout
        val screenshot = ScreenshotHelper.captureAndSave(composeTestRule.onRoot(), "dashboard_tablet_wide")
        assertTrue(screenshot.exists())
    }
}
