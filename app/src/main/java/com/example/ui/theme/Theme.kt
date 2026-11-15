package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

object OmsColors {
    val Bg = Color(0xFF0B0E14)
    val EditorBg = Color(0xFF10121D)
    val Bg2 = Color(0xFF151827)
    val Panel = Color(0xFF1A1D2D)
    val Panel2 = Color(0xFF202437)
    val Card = Color(0xFF18202E)
    val Border = Color(0xFF30364C)
    val Text = Color(0xFFF2F4FB)
    val Muted = Color(0xFF9BA3B8)
    val Accent = Color(0xFF00D7A7)
    val Mint = Color(0xFF73E2BD)
    val Purple = Color(0xFF8374FF)
    val Cyan = Color(0xFF43CBE8)
    val Orange = Color(0xFFFFC04A)
    val Pink = Color(0xFFFF6DA9)
    val Danger = Color(0xFFFF667C)
    val ClipShape = Color(0xFF50CED0)
    val ClipText = Color(0xFFFFC356)
    val ClipImage = Color(0xFF73D9D1)
    val ClipVideo = Color(0xFF4CC8C4)
    val ClipAudio = Color(0xFFCA8CFF)
    val ClipCamera = Color(0xFF94A2FF)
    val ClipMesh = Color(0xFFFF907C)
}

val InterFamily = FontFamily(Font(R.font.inter, FontWeight.Normal))
val SpaceGroteskFamily = FontFamily(Font(R.font.space_grotesk, FontWeight.Bold))
val RobotoMonoFamily = FontFamily(Font(R.font.roboto_mono, FontWeight.Normal))

val OmsTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = OmsColors.Text
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = OmsColors.Text
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = OmsColors.Text
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = OmsColors.Muted
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = OmsColors.Text
    )
)

private val OmsColorScheme = darkColorScheme(
    primary = OmsColors.Accent,
    onPrimary = Color(0xFF061B15),
    secondary = OmsColors.Purple,
    onSecondary = Color.White,
    tertiary = OmsColors.Cyan,
    background = OmsColors.Bg,
    onBackground = OmsColors.Text,
    surface = OmsColors.Panel,
    onSurface = OmsColors.Text,
    surfaceVariant = OmsColors.Panel2,
    onSurfaceVariant = OmsColors.Muted,
    outline = OmsColors.Border,
    error = OmsColors.Danger
)

@Composable
fun OpenMotionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OmsColorScheme,
        typography = OmsTypography,
        content = content
    )
}
