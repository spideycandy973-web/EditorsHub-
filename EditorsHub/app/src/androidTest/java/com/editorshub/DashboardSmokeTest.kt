package com.editorshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DashboardSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardCreatesAndOpensAComposition() {
        compose.onNodeWithText("Create your next edit").assertIsDisplayed()
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithText("Project name").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Recent projects").assertIsDisplayed()

        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithContentDescription("Play / pause").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Play / pause").assertIsDisplayed()
    }
}
