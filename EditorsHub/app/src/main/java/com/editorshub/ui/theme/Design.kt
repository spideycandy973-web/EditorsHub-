package com.editorshub.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared spacing, motion and v4.5.1 `--um-*` design tokens for native Compose. */
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

/** Exact colour tokens restored from v4.5.1 `src/app.css` :root. */
object ReferencePalette {
    val DeepBlack = Color(0xFF070707)       // --um-bg
    val Background = Color(0xFF121214)      // --um-home
    val Card = Color(0xFF1C1C1E)            // --um-card
    val Input = Color(0xFF262628)           // --um-input / --um-card-2
    val Line = Color(0xFF2E2E33)            // --um-line
    val Text = Color(0xFFF3F3F6)            // --um-text
    val SecondaryText = Color(0xFFC8C8D2)
    val MutedText = Color(0xFF92929C)       // --um-muted
    val Lavender = Color(0xFFD8DCFF)        // --um-lav
    val Mint = Color(0xFF01FFA7)            // --um-teal
    val Pink = Color(0xFFFF3B7F)            // --um-pink
    val Avatar = Color(0xFF3A3A42)
}

object EditorPalette {
    val Black = ReferencePalette.Background
    val Charcoal = ReferencePalette.Card
    val Surface = ReferencePalette.Card
    val SurfaceRaised = ReferencePalette.Input
    val SurfaceVariant = ReferencePalette.Input
    val Outline = ReferencePalette.Line
    val OutlineSoft = Color(0xFF2A2A30)
    val TextPrimary = ReferencePalette.Text
    val TextSecondary = ReferencePalette.SecondaryText
    val TextMuted = ReferencePalette.MutedText

    // Legacy names kept so every native editing control inherits v4.5.1 colours.
    val Purple = ReferencePalette.Lavender
    val PurpleDeep = Color(0xFFBCC5EF)
    val Cyan = ReferencePalette.Mint
    val Pink = ReferencePalette.Pink
    val Amber = Color(0xFFFBBF24)
    val Green = ReferencePalette.Mint
    val Red = Color(0xFFFF6B6B)
    val Blue = ReferencePalette.Lavender
    val Coral = Color(0xFFFF6B6B)
    val Violet = ReferencePalette.Lavender

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
