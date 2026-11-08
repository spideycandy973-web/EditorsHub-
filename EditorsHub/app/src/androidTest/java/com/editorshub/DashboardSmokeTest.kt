package com.editorshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class DashboardSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardCreatesAnEditorEntryPoint() {
        compose.onNodeWithText("Create your next edit").assertIsDisplayed()
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithText("Project name").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Recent projects").assertIsDisplayed()
    }
}
