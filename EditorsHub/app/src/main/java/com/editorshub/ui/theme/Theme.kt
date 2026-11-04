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
    onPrimary = EditorPalette.Black,
    secondary = EditorPalette.Cyan,
    onSecondary = EditorPalette.Black,
    tertiary = EditorPalette.Pink,
    background = EditorPalette.Black,
    onBackground = EditorPalette.TextPrimary,
    surface = EditorPalette.Surface,
    onSurface = EditorPalette.TextPrimary,
    surfaceVariant = EditorPalette.SurfaceVariant,
    onSurfaceVariant = EditorPalette.TextSecondary,
    outline = EditorPalette.Outline,
    outlineVariant = EditorPalette.OutlineSoft,
    error = EditorPalette.Red
)

// The reference design is a near-black studio UI in both system themes.
private val LightColors = darkColorScheme(
    primary = EditorPalette.Purple,
    onPrimary = EditorPalette.Black,
    secondary = EditorPalette.Cyan,
    onSecondary = EditorPalette.Black,
    tertiary = EditorPalette.Pink,
    background = EditorPalette.Black,
    onBackground = EditorPalette.TextPrimary,
    surface = EditorPalette.Surface,
    onSurface = EditorPalette.TextPrimary,
    surfaceVariant = EditorPalette.SurfaceVariant,
    onSurfaceVariant = EditorPalette.TextSecondary,
    outline = EditorPalette.Outline,
    error = EditorPalette.Red
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 23.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 11.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 9.sp)
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
