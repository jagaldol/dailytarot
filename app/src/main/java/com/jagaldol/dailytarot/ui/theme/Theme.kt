package com.jagaldol.dailytarot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

// Warm paper and ink by day, a deep night sky after dark. Gold is the only accent.
private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F2A4E),
    onPrimary = Color(0xFFFBF8F3),
    primaryContainer = Color(0xFFE8E3F1),
    onPrimaryContainer = Color(0xFF221E3D),
    secondary = Color(0xFF9C7434),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF9C7434),
    background = Color(0xFFF6F2EB),
    onBackground = Color(0xFF1E1C29),
    surface = Color(0xFFF6F2EB),
    onSurface = Color(0xFF1E1C29),
    surfaceVariant = Color(0xFFECE5D9),
    onSurfaceVariant = Color(0xFF6A6476),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF8F3),
    surfaceContainer = Color(0xFFF1EBE1),
    surfaceContainerHigh = Color(0xFFEBE4D8),
    outline = Color(0xFFCFC5B4),
    outlineVariant = Color(0xFFE4DCCE),
    error = Color(0xFFA63A32),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFDCC08A),
    onPrimary = Color(0xFF2A1F0B),
    primaryContainer = Color(0xFF2C2940),
    onPrimaryContainer = Color(0xFFEDE6FF),
    secondary = Color(0xFFDCC08A),
    onSecondary = Color(0xFF2A1F0B),
    tertiary = Color(0xFFDCC08A),
    background = Color(0xFF12111A),
    onBackground = Color(0xFFEEE9E0),
    surface = Color(0xFF12111A),
    onSurface = Color(0xFFEEE9E0),
    surfaceVariant = Color(0xFF221F2E),
    onSurfaceVariant = Color(0xFFA7A1B4),
    surfaceContainerLowest = Color(0xFF0D0C13),
    surfaceContainerLow = Color(0xFF17151F),
    surfaceContainer = Color(0xFF1C1A26),
    surfaceContainerHigh = Color(0xFF24212F),
    outline = Color(0xFF45404F),
    outlineVariant = Color(0xFF2B2836),
    error = Color(0xFFE59A8F),
)

/** The single accent: gold leaf on paper, candlelight at night. */
val ColorScheme.gold: Color get() = secondary

private val Serif = FontFamily.Serif

// Korean wraps at spaces (phrases) instead of mid-word; headings balance their lines (API 33+).
private val Prose = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
private val Heading = LineBreak.Heading

private val BaseTypography = Typography(
    displaySmall = serif(30, 38),
    headlineMedium = serif(28, 36),
    headlineSmall = serif(22, 30),
    titleLarge = serif(19, 30),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, lineBreak = Prose),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, lineBreak = Prose),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 27.sp, lineBreak = Prose),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, lineBreak = Prose),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, lineBreak = Prose),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp,
    ),
)

// Phrase breaking only applies to text tagged as Korean, whatever the device language is.
private val Korean = LocaleList("ko-KR")

private val TarotTypography = with(BaseTypography) {
    Typography(
        displaySmall = displaySmall.copy(localeList = Korean),
        headlineMedium = headlineMedium.copy(localeList = Korean),
        headlineSmall = headlineSmall.copy(localeList = Korean),
        titleLarge = titleLarge.copy(localeList = Korean),
        titleMedium = titleMedium.copy(localeList = Korean),
        titleSmall = titleSmall.copy(localeList = Korean),
        bodyLarge = bodyLarge.copy(localeList = Korean),
        bodyMedium = bodyMedium.copy(localeList = Korean),
        bodySmall = bodySmall.copy(localeList = Korean),
        labelLarge = labelLarge.copy(localeList = Korean),
        labelMedium = labelMedium.copy(localeList = Korean),
        labelSmall = labelSmall.copy(localeList = Korean),
    )
}

private fun serif(size: Int, lineHeight: Int) = TextStyle(
    fontFamily = Serif,
    fontWeight = FontWeight.Medium,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    lineBreak = Heading,
)

@Composable
fun DailytarotTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = TarotTypography,
        content = content,
    )
}
