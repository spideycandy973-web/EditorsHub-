package com.editorshub.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Small, shared touch response for surfaces that own their clickable interaction source. */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.82f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = androidx.compose.animation.core.tween<Float>(
            com.editorshub.ui.theme.AppMotion.Fast,
            easing = com.editorshub.ui.theme.AppMotion.Emphasized
        ),
        label = "press-scale"
    )
    return graphicsLayer {
        alpha = scale
    }
}
