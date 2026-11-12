package com.editorshub.ui.theme

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat

val LocalUiScale = staticCompositionLocalOf { 1f }

private val DarkColors = darkColorScheme(
    primary = EditorPalette.Purple,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF000000),
    secondary = EditorPalette.Cyan,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF000000),
    tertiary = EditorPalette.Amber,
    background = EditorPalette.Black,
    onBackground = EditorPalette.TextPrimary,
    surface = EditorPalette.Surface,
    onSurface = EditorPalette.TextPrimary,
    surfaceVariant = EditorPalette.SurfaceVariant,
    onSurfaceVariant = EditorPalette.TextMuted,
    outline = EditorPalette.Outline,
    outlineVariant = EditorPalette.OutlineSoft,
    error = EditorPalette.Red
)

// The existing light variant stays functional but is not extended into RomKit.
private val LightColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFD8DCFF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF121214),
    secondary = androidx.compose.ui.graphics.Color(0xFF01FFA7),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF121214),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFF3B7F),
    background = androidx.compose.ui.graphics.Color(0xFF121214),
    onBackground = androidx.compose.ui.graphics.Color(0xFFF3F3F6),
    surface = androidx.compose.ui.graphics.Color(0xFF1C1C1E),
    onSurface = androidx.compose.ui.graphics.Color(0xFFF3F3F6),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF262628),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC8C8D2),
    outline = androidx.compose.ui.graphics.Color(0xFF2E2E33),
    error = androidx.compose.ui.graphics.Color(0xFFFF6B6B)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight(800), fontSize = 30.sp, letterSpacing = (-0.5).sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 9.5f.sp, letterSpacing = 0.4.sp, lineHeight = 13.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp)
)

@Composable
fun EditorsHubTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    uiScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val density = LocalDensity.current
    val scaled = Density(
        density = density.density * uiScale.coerceIn(0.8f, 1.4f),
        fontScale = density.fontScale * uiScale.coerceIn(0.8f, 1.4f)
    )
    CompositionLocalProvider(LocalDensity provides scaled, LocalUiScale provides uiScale) {
        MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
    }
}

/** Actually applies the selected language across the whole app. */
object LanguageManager {
    fun apply(tag: String) {
        // Some OEMs ship an AppCompat build that rejects setApplicationLocales on
        // certain configurations. A language change must never crash the app.
        try {
            val locales = if (tag == "system") LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(locales)
        } catch (e: Throwable) {
            android.util.Log.w("EditorsHub", "Could not apply locale $tag", e)
        }
    }
}
