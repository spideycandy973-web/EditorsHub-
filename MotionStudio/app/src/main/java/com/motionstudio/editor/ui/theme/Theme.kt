package com.motionstudio.editor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Purple = Color(0xFF8B7BFF)
private val Cyan = Color(0xFF22D3EE)
private val Pink = Color(0xFFF472B6)
private val SurfaceDark = Color(0xFF141724)
private val BackgroundDark = Color(0xFF0B0C14)

private val DarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    secondary = Cyan,
    onSecondary = Color(0xFF0B0C14),
    tertiary = Pink,
    background = BackgroundDark,
    onBackground = Color(0xFFEEF0F8),
    surface = SurfaceDark,
    onSurface = Color(0xFFEEF0F8),
    surfaceVariant = Color(0xFF202331),
    onSurfaceVariant = Color(0xFFB9BED4),
    outline = Color(0xFF343950),
    error = Color(0xFFFB7185)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BD6),
    secondary = Color(0xFF0E7490),
    background = Color(0xFFF7F7FC),
    surface = Color.White
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 10.sp)
)

@Composable
fun MotionStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
