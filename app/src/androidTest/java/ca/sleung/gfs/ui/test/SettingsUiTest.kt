package ca.sleung.gfs.ui.test

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import ca.sleung.gfs.domain.model.Tag
import ca.sleung.gfs.ui.screens.settings.SettingsContent
import ca.sleung.gfs.ui.screens.settings.SettingsUiState
import ca.sleung.gfs.ui.theme.GFSTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsContent_displaysConfigurationsAndHideDecimalsSwitch() {
        var hideDecimalsToggled: Boolean? = null

        composeTestRule.setContent {
            GFSTheme(dynamicColor = false) {
                SettingsContent(
                    uiState = SettingsUiState(
                        monthlyBudget = 600.0,
                        refillDayOfMonth = 1,
                        maxRolloverCap = 1200.0,
                        currentRollover = 180.0,
                        totalExtraSaved = 340.0,
                        tags = listOf(
                            Tag(1, "Eating Out", "#FF7043", 0),
                            Tag(2, "Shopping", "#AB47BC", 1)
                        ),
                        transactionCount = 15,
                        hideDecimalPlaces = true
                    ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onNavigateBack = {},
                    onResetExtraSaved = {},
                    onAddTag = { _, _ -> },
                    onDeleteTag = {},
                    onToggleHideDecimals = { hideDecimalsToggled = it },
                    onExportBackup = {},
                    onRequestImportBackup = {},
                    onSaveSettings = { _, _, _ -> }
                )
            }
        }

        // Verify configuration headers & stat tiles
        composeTestRule.onNodeWithText("CURRENT CYCLE BREAKDOWN").assertIsDisplayed()
        composeTestRule.onNodeWithText("CUSTOMIZE TAGS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hide decimal places").performScrollTo().assertIsDisplayed()

        // Toggle hide decimal places
        composeTestRule.onNodeWithText("Hide decimal places").performClick()
        assertEquals(false, hideDecimalsToggled)

        // Capture screenshot of settings
        val screenshot = ScreenshotHelper.captureAndSave(composeTestRule.onRoot(), "settings_screen")
        assertTrue(screenshot.exists())
    }

    @Test
    fun settingsContent_deleteTag_showsConfirmationDialog() {
        var deletedTagId: Long? = null

        composeTestRule.setContent {
            GFSTheme(dynamicColor = false) {
                SettingsContent(
                    uiState = SettingsUiState(
                        tags = listOf(
                            Tag(42, "Gym", "#42A5F5", 0)
                        )
                    ),
                    snackbarHostState = remember { SnackbarHostState() },
                    onNavigateBack = {},
                    onResetExtraSaved = {},
                    onAddTag = { _, _ -> },
                    onDeleteTag = { deletedTagId = it },
                    onToggleHideDecimals = {},
                    onExportBackup = {},
                    onRequestImportBackup = {},
                    onSaveSettings = { _, _, _ -> }
                )
            }
        }

        composeTestRule.onNodeWithText("Gym").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Gym").performClick()

        // Verify confirmation dialog
        composeTestRule.onNodeWithText("Delete Tag?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove \"Gym\" from available tags? Past expenses with this tag will preserve their label.").assertIsDisplayed()

        // Capture dialog screenshot
        val dialogScreenshot = ScreenshotHelper.captureAndSave(composeTestRule.onRoot(), "settings_tag_delete_dialog")
        assertTrue(dialogScreenshot.exists())

        // Confirm delete
        composeTestRule.onNodeWithText("Delete Tag").performClick()
        assertEquals(42L, deletedTagId)
    }
}
