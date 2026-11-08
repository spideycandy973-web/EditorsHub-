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
    // Graphite workspace with cool blue controls, inspired by pro motion editors.
    val Black = Color(0xFF090B10)
    val Charcoal = Color(0xFF11151C)
    val Surface = Color(0xFF191F29)
    val SurfaceRaised = Color(0xFF222B38)
    val SurfaceVariant = Color(0xFF293444)
    val Outline = Color(0xFF394657)
    val OutlineSoft = Color(0xFF2A3543)
    val TextPrimary = Color(0xFFF3F3F6)
    val TextSecondary = Color(0xFFC8C8D2)
    val TextMuted = Color(0xFF92929C)

    // Legacy names kept so every existing screen picks up the new skin.
    val Purple = Color(0xFF159BFF)       // primary electric-blue accent
    val PurpleDeep = Color(0xFF0875D1)
    val Cyan = Color(0xFF39C8FF)
    val Pink = Color(0xFFFFB65C)
    val Amber = Color(0xFFFFB547)
    val Green = Color(0xFF40D6A2)
    val Red = Color(0xFFFF657A)
    val Blue = Color(0xFF399CFF)
    val Coral = Color(0xFFFF7B70)
    val Violet = Color(0xFF9C82FF)

    val AccentBrush = Brush.linearGradient(listOf(Color(0xFF38B6FF), Color(0xFF0875D1)))
    val HeroBrush = Brush.linearGradient(listOf(Color(0xFF1C2B3D), Color(0xFF111923), Color(0xFF202B3A)))
    val GlowBrush = Brush.radialGradient(listOf(Color(0x3338B6FF), Color.Transparent))
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
