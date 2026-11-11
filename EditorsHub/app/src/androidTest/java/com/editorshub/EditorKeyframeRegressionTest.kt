package com.editorshub

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
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

    private var stage = "start"

    private fun mark(value: String) {
        stage = value
        println("KEYFRAME_REGRESSION_STAGE=$value")
    }

    private fun diagnostics(): String {
        val labels = listOf(
            "Open keyframes panel", "Add keyframe", "Move & Transform",
            "Effects", "Back to properties", "Keyframe graph editor"
        )
        val texts = listOf("Keyframe engine", "1 keyframes", "2 keyframes", "Graph editor")
        return (labels.map { label ->
            "$label=${compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().size}"
        } + texts.map { text ->
            "text:$text=${compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size}"
        }).joinToString(", ")
    }

    private fun waitFor(predicate: () -> Boolean, timeoutMillis: Long = 15_000L) {
        try {
            compose.waitUntil(timeoutMillis) { predicate() }
        } catch (error: Throwable) {
            val detail = try { diagnostics() } catch (diagnosticError: Throwable) { "diagnostics failed: $diagnosticError" }
            throw AssertionError("Timed out at stage '$stage'; $detail", error)
        }
    }

    private fun waitForDescription(label: String, timeoutMillis: Long = 15_000L) =
        waitFor({ compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes().isNotEmpty() }, timeoutMillis)

    private fun waitForText(text: String, substring: Boolean = false) =
        waitFor({ compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() })

    @Test
    fun keyframesWorkFromTransformTimelineMarkersPanelGraphAndEffects() {
        mark("open-editor")
        compose.onNodeWithContentDescription("Create project").performClick()
        compose.onNodeWithText("Create Project").performClick()
        waitForDescription("Play / pause")

        mark("open-add-sheet")
        compose.onNodeWithContentDescription("Add layer").performClick()
        waitForDescription("Add sheet")
        mark("add-text-layer")
        compose.onNodeWithContentDescription("Add Text layer").performClick()
        waitForDescription("Back to properties")

        // Transform diamond -> a real opacity track and timeline marker.
        mark("open-transform")
        compose.onNodeWithContentDescription("Back to properties").performClick()
        waitForDescription("Move & Transform")
        compose.onAllNodesWithContentDescription("Move & Transform")[0].performClick()
        waitForDescription("Opacity keyframe")
        mark("toggle-opacity-keyframe")
        compose.onNodeWithContentDescription("Opacity keyframe").performScrollTo().performClick()
        waitForDescription("Keyframe at 0 ms")

        // Timeline marker -> Keyframes panel with the one real keyframe.
        mark("open-keyframes-panel")
        // The marker exposes a real click action via combinedClickable; invoke it
        // directly so the regression verifies the control rather than the tiny
        // rotated diamond's touch geometry.
        compose.onNodeWithContentDescription("Keyframe at 0 ms")
            .performSemanticsAction(SemanticsActions.OnClick)
        waitForDescription("Open keyframes panel")
        mark("keyframes-panel-content")
        waitForText("Keyframe engine")
        waitForDescription("Add keyframe")
        compose.onNodeWithText("1 keyframes · local playhead 0ms").assertIsDisplayed()

        // Keyframes panel -> a second real keyframe at the project end.
        mark("add-second-keyframe")
        compose.onNodeWithContentDescription("Go to end").performClick()
        compose.onNodeWithContentDescription("Add keyframe").performScrollTo().performClick()
        waitForText("2 keyframes", substring = true)

        // Graph editor accepts a continuous drag without losing the track.
        mark("drag-graph")
        compose.onNodeWithContentDescription("Keyframe graph editor")
            .performScrollTo()
            .performTouchInput {
                swipe(Offset(20f, 95f), Offset(110f, 45f), durationMillis = 250L)
            }
        compose.onNodeWithContentDescription("Keyframe graph editor").assertIsDisplayed()

        // Effects diamond -> a real animated effect parameter.
        mark("open-effects")
        compose.onNodeWithContentDescription("Back to properties").performClick()
        waitForDescription("Effects")
        compose.onNodeWithContentDescription("Effects").performClick()
        waitForDescription("Add Gaussian Blur effect")
        mark("add-gaussian-blur")
        compose.onNodeWithContentDescription("Add Gaussian Blur effect").performScrollTo().performClick()
        waitForDescription("Radius keyframe")
        mark("toggle-radius-keyframe")
        compose.onNodeWithContentDescription("Radius keyframe").performScrollTo().performClick()
        waitForText("1 animated parameters")
    }
}
