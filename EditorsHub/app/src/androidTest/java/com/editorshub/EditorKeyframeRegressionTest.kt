package com.editorshub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end regression for the reference-matched editor: Transform diamonds,
 * timeline markers, Keyframes panel, graph dragging, and Effects diamonds all
 * must keep writing real project keyframes.
 */
@RunWith(AndroidJUnit4::class)
class EditorKeyframeRegressionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForDescription(label: String, timeoutMillis: Long = 15_000L) {
        compose.waitUntil(timeoutMillis) {
            compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun keyframesWorkFromTransformTimelineMarkersPanelGraphAndEffects() {
        compose.onNodeWithContentDescription("Create project").performClick()
        compose.onNodeWithText("Create Project").performClick()
        waitForDescription("Play / pause")

        compose.onNodeWithContentDescription("Add layer").performClick()
        waitForDescription("Add sheet")
        compose.onNodeWithContentDescription("Add Text layer").performClick()
        waitForDescription("Back to properties")

        // Transform diamond -> a real opacity track and timeline marker.
        compose.onNodeWithContentDescription("Back to properties").performClick()
        waitForDescription("Move & Transform")
        compose.onAllNodesWithContentDescription("Move & Transform")[0].performClick()
        waitForDescription("Opacity keyframe")
        compose.onNodeWithContentDescription("Opacity keyframe").performScrollTo().performClick()
        waitForDescription("Keyframe at 0 ms")

        // Timeline marker -> Keyframes panel with the one real keyframe.
        compose.onNodeWithContentDescription("Keyframe at 0 ms").performClick()
        waitForDescription("Add keyframe")
        compose.onNodeWithText("1 keyframes · local playhead 0ms").assertIsDisplayed()

        // Keyframes panel -> a second real keyframe at the project end.
        compose.onNodeWithContentDescription("Go to end").performClick()
        compose.onNodeWithContentDescription("Add keyframe").performScrollTo().performClick()
        compose.waitUntil(15_000L) {
            compose.onAllNodesWithText("2 keyframes", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Graph editor accepts a continuous drag without losing the track.
        compose.onNodeWithContentDescription("Keyframe graph editor")
            .performScrollTo()
            .performTouchInput {
                swipe(Offset(20f, 95f), Offset(110f, 45f), durationMillis = 250L)
            }
        compose.onNodeWithContentDescription("Keyframe graph editor").assertIsDisplayed()

        // Effects diamond -> a real animated effect parameter.
        compose.onNodeWithContentDescription("Back to properties").performClick()
        waitForDescription("Effects")
        compose.onNodeWithContentDescription("Effects").performClick()
        waitForDescription("Add Gaussian Blur effect")
        compose.onNodeWithContentDescription("Add Gaussian Blur effect").performScrollTo().performClick()
        waitForDescription("Radius keyframe")
        compose.onNodeWithContentDescription("Radius keyframe").performScrollTo().performClick()
        compose.waitUntil(15_000L) {
            compose.onAllNodesWithText("1 animated parameters")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
