package com.editorshub.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Single source of truth for shared spacing, colour, shape and motion tokens. */
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

object ReferencePalette {
    val Background = Color(0xFF121214)
    val Card = Color(0xFF1C1C1E)
    val Input = Color(0xFF262628)
    val Lavender = Color(0xFFDFE3FF)
    val Mint = Color(0xFF01FFA7)
    val Text = Color(0xFFF2F2F6)
    val SecondaryText = Color(0xFFB9B9C1)
    val MutedText = Color(0xFF85858D)
    val Avatar = Color(0xFF3A3A42)
}

object EditorPalette {
    // Clean-room dark workspace using the reference's graphite, lavender and mint tokens.
    val Black = ReferencePalette.Background
    val Charcoal = ReferencePalette.Card
    val Surface = ReferencePalette.Card
    val SurfaceRaised = ReferencePalette.Input
    val SurfaceVariant = Color(0xFF303036)
    val Outline = Color(0xFF3A3A42)
    val OutlineSoft = Color(0xFF2A2A30)
    val TextPrimary = ReferencePalette.Text
    val TextSecondary = ReferencePalette.SecondaryText
    val TextMuted = ReferencePalette.MutedText

    // Legacy names kept so existing native editing controls inherit the reference palette.
    val Purple = ReferencePalette.Lavender
    val PurpleDeep = Color(0xFFBCC5EF)
    val Cyan = ReferencePalette.Mint
    val Pink = Color(0xFFFF8B8B)
    val Amber = Color(0xFFFFCE5C)
    val Green = ReferencePalette.Mint
    val Red = Color(0xFFE0455D)
    val Blue = Color(0xFF55A7E6)
    val Coral = Color(0xFFFF7B70)
    val Violet = Color(0xFFA995FF)

    val AccentBrush = Brush.linearGradient(listOf(ReferencePalette.Lavender, ReferencePalette.Mint))
    val HeroBrush = Brush.linearGradient(listOf(ReferencePalette.Card, ReferencePalette.Input))
    val GlowBrush = Brush.radialGradient(listOf(ReferencePalette.Mint.copy(alpha = 0.18f), Color.Transparent))
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
