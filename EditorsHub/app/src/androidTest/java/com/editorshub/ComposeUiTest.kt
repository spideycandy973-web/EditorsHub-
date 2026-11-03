package com.editorshub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.editorshub.ui.components.SliderRow
import com.editorshub.ui.theme.EditorsHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun sliderRowShowsLabelAndReportsChanges() {
        var value = 0.5f
        composeRule.setContent {
            EditorsHubTheme {
                SliderRow(label = "Opacity", value = value, range = 0f..1f,
                    onChange = { value = it })
            }
        }
        composeRule.onNodeWithText("Opacity").assertIsDisplayed()
        composeRule.onNodeWithText("0.50").assertIsDisplayed()
    }

    @Test fun keyframeDiamondIsClickable() {
        var clicks = 0
        composeRule.setContent {
            EditorsHubTheme {
                SliderRow(label = "Rotation", value = 30f, range = 0f..360f,
                    onChange = {}, onKeyframe = { clicks++ }, keyed = true)
            }
        }
        composeRule.onNodeWithText("◆").performClick()
        assertEquals(1, clicks)
    }
}
