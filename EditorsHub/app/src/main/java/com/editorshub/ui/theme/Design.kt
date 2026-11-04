package com.editorshub.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Single source of truth for spacing, colour, shape and motion.
 *  Alight-Motion-style midnight navy editor with a mint/teal accent. */
object EditorDimens {
    val Space0: Dp = 2.dp
    val Space1: Dp = 4.dp
    val Space2: Dp = 8.dp
    val Space3: Dp = 12.dp
    val Space4: Dp = 16.dp
    val Space5: Dp = 24.dp
    val Space6: Dp = 32.dp
    val RadiusSmall: Dp = 8.dp
    val Radius: Dp = 14.dp
    val RadiusLarge: Dp = 22.dp
    val Icon: Dp = 20.dp
    val IconSmall: Dp = 16.dp
    val IconLarge: Dp = 28.dp
    val BarHeight: Dp = 56.dp
    val RowHeight: Dp = 44.dp
    val TouchTarget: Dp = 44.dp
}

object EditorPalette {
    // Surfaces: deep navy → slate navy, like the Alight Motion editor.
    val Black = Color(0xFF0B0B0D)
    val Charcoal = Color(0xFF121214)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceRaised = Color(0xFF262628)
    val SurfaceVariant = Color(0xFF2A2A2D)
    val Outline = Color(0xFF3A3A40)
    val OutlineSoft = Color(0xFF303036)
    val TextPrimary = Color(0xFFF3F3F6)
    val TextSecondary = Color(0xFFC8C8D2)
    val TextMuted = Color(0xFF92929C)

    // Legacy names kept so every existing screen picks up the new skin.
    val Purple = Color(0xFF02FFA7)       // primary mint/teal accent
    val PurpleDeep = Color(0xFF00C987)   // darker teal
    val Cyan = Color(0xFFD8DCFF)         // media blue
    val Pink = Color(0xFFD9A15B)         // text amber
    val Amber = Color(0xFFFBBF24)
    val Green = Color(0xFF02FFA7)
    val Red = Color(0xFFFF6B6B)
    val Blue = Color(0xFFD8DCFF)
    val Coral = Color(0xFFFF6B6B)
    val Violet = Color(0xFFD8DCFF)

    val AccentBrush = Brush.linearGradient(listOf(Color(0xFF02FFA7), Color(0xFF00C987)))
    val HeroBrush = Brush.linearGradient(listOf(Color(0xFF1C1C1E), Color(0xFF121214), Color(0xFF262628)))
    val GlowBrush = Brush.radialGradient(listOf(Color(0x3310E0A0), Color.Transparent))
}

/** Shared motion specs so every screen/tab transition feels identical. */
object AppMotion {
    const val Fast = 180
    const val Normal = 300
    const val Slow = 420
    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val Standard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    fun <T> fadeSpec(duration: Int = Normal) = tween<T>(duration, easing = Standard)
    fun <T> springSpec() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
