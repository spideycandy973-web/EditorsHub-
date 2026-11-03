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
    val Black = Color(0xFF10141F)
    val Charcoal = Color(0xFF1B2130)
    val Surface = Color(0xFF1B2130)
    val SurfaceRaised = Color(0xFF222A3B)
    val SurfaceVariant = Color(0xFF2C3549)
    val Outline = Color(0xFF3A4560)
    val OutlineSoft = Color(0xFF2B3448)
    val TextPrimary = Color(0xFFF0F3F8)
    val TextSecondary = Color(0xFFB4BDD0)
    val TextMuted = Color(0xFF7E8AA3)

    // Legacy names kept so every existing screen picks up the new skin.
    val Purple = Color(0xFF10E0A0)       // primary mint/teal accent
    val PurpleDeep = Color(0xFF00B98A)   // darker teal
    val Cyan = Color(0xFF4C8DFF)         // media blue
    val Pink = Color(0xFFF5A623)         // text amber
    val Amber = Color(0xFFFBBF24)
    val Green = Color(0xFF10E0A0)
    val Red = Color(0xFFFF6B6B)
    val Blue = Color(0xFF4C8DFF)
    val Coral = Color(0xFFFF6B6B)
    val Violet = Color(0xFFA97BFF)

    val AccentBrush = Brush.linearGradient(listOf(Color(0xFF10E0A0), Color(0xFF00B98A)))
    val HeroBrush = Brush.linearGradient(listOf(Color(0xFF17243A), Color(0xFF14283A), Color(0xFF182036)))
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
