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
    fun referenceHomeImportsAndCreatesProjects() {
        compose.onNodeWithText("Upload XML file").assertIsDisplayed()
        compose.onNodeWithText("Import Project Package").assertIsDisplayed()
        compose.onNodeWithText("Paste a link or scan the QR code.").assertIsDisplayed()
        compose.onNodeWithText("https://alight...").assertIsDisplayed()
        compose.onNodeWithText("Scan QR image").assertIsDisplayed()

        compose.onNodeWithContentDescription("Projects").performClick()
        compose.onNodeWithText("Your projects").assertIsDisplayed()
        compose.onNodeWithText("Sort by Date").assertIsDisplayed()
        compose.onNodeWithContentDescription("Home").performClick()

        compose.onNodeWithContentDescription("Create project").performClick()
        compose.onNodeWithText("Project name").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close create project").performClick()

        compose.onNodeWithContentDescription("Create project").performClick()
        compose.onNodeWithText("Create Project").performClick()
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithContentDescription("Play / pause").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Play / pause").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add layer").assertIsDisplayed()
    }
}
