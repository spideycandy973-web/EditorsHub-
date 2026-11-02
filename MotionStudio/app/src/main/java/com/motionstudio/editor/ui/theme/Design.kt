package com.motionstudio.editor.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Single source of truth for spacing, colour, shape and motion. */
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
    val Black = Color(0xFF07080D)
    val Charcoal = Color(0xFF0E1018)
    val Surface = Color(0xFF131622)
    val SurfaceRaised = Color(0xFF1B1F2E)
    val SurfaceVariant = Color(0xFF22273A)
    val Outline = Color(0xFF2E3448)
    val OutlineSoft = Color(0xFF202538)
    val TextPrimary = Color(0xFFF2F4FB)
    val TextSecondary = Color(0xFFAEB5CD)
    val TextMuted = Color(0xFF77809C)
    val Purple = Color(0xFF8B7BFF)
    val PurpleDeep = Color(0xFF6354D9)
    val Cyan = Color(0xFF22D3EE)
    val Pink = Color(0xFFF472B6)
    val Amber = Color(0xFFFBBF24)
    val Green = Color(0xFF4ADE80)
    val Red = Color(0xFFFB7185)
    val AccentBrush = Brush.linearGradient(listOf(Purple, Cyan))
    val HeroBrush = Brush.linearGradient(listOf(Color(0xFF211B45), Color(0xFF102A3A), Color(0xFF1A1230)))
    val GlowBrush = Brush.radialGradient(listOf(Color(0x338B7BFF), Color.Transparent))
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
