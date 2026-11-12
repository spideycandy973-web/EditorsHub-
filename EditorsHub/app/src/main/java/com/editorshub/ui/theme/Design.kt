package com.editorshub.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared spacing, motion and RomKit palette tokens for native Compose. */
object EditorDimens {
    val Space0: Dp = 3.dp
    val Space1: Dp = 4.dp
    val Space2: Dp = 8.dp
    val Space3: Dp = 10.dp
    val Space4: Dp = 16.dp
    val Space5: Dp = 18.dp
    val Space6: Dp = 24.dp
    val RadiusSmall: Dp = 12.dp
    val Radius: Dp = 16.dp
    val RadiusLarge: Dp = 18.dp
    val Icon: Dp = 20.dp
    val IconSmall: Dp = 16.dp
    val IconLarge: Dp = 28.dp
    val BarHeight: Dp = 58.dp
    val RowHeight: Dp = 44.dp
    val TouchTarget: Dp = 44.dp
}

/** Exact RomKit colour tokens. Legacy names are preserved. */
object ReferencePalette {
    val DeepBlack = Color(0xFF05060A)       // INK
    val Background = Color(0xFF05060A)      // INK
    val Card = Color(0xFF0D1017)            // PANEL
    val Input = Color(0xFF141926)           // PANEL2
    val Line = Color(0xFF232B3D)            // STROKE
    val Text = Color(0xFFF2F5FA)            // TEXT-HI
    val SecondaryText = Color(0xFF8A94A8)   // TEXT-LO
    val MutedText = Color(0xFF8A94A8)       // TEXT-LO
    val Lavender = Color(0xFFFF6900)        // EMBER
    val Mint = Color(0xFF35E1F2)            // CYAN
    val Pink = Color(0xFFFF5C6C)            // BAD
    val Avatar = Color(0xFF141926)          // PANEL2
}

object EditorPalette {
    val Black = ReferencePalette.Background
    val Charcoal = ReferencePalette.Card
    val Surface = ReferencePalette.Card
    val SurfaceRaised = ReferencePalette.Input
    val SurfaceVariant = ReferencePalette.Input
    val Outline = ReferencePalette.Line
    val OutlineSoft = Color(0xFF1A2030)     // DIVIDER
    val TextPrimary = ReferencePalette.Text
    val TextSecondary = ReferencePalette.SecondaryText
    val TextMuted = ReferencePalette.MutedText

    // Legacy names kept so every existing control inherits RomKit colours.
    val Purple = ReferencePalette.Lavender       // EMBER
    val PurpleDeep = Color(0xFFD95300)           // EMBER-DEEP
    val Cyan = ReferencePalette.Mint             // CYAN
    val Pink = ReferencePalette.Pink             // BAD
    val Amber = Color(0xFFFFC857)                // WARN
    val Green = Color(0xFF38E08B)                // GOOD
    val Red = Color(0xFFFF5C6C)                  // BAD
    val Blue = ReferencePalette.Mint             // CYAN
    val Coral = Color(0xFFFF5C6C)                // BAD
    val Violet = Color(0xFF8A94A8)               // TEXT-LO

    val AccentBrush = Brush.linearGradient(listOf(Color(0xFFFF6900), Color(0xFFFF8A3D)))
    val HeroBrush = Brush.linearGradient(listOf(Color(0xFF0D1017), Color(0xFF0D1017)))
    val GlowBrush = Brush.radialGradient(listOf(Color.Transparent, Color.Transparent))
}

/** Shared motion specs: 180/300/420 ms, one easing, no spring. */
object AppMotion {
    const val Fast = 180
    const val Normal = 300
    const val Slow = 420
    val Emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val Standard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    fun <T> fadeSpec(duration: Int = Normal) = tween<T>(duration, easing = Emphasized)
    fun <T> springSpec() = tween<T>(Normal, easing = Emphasized)
}
