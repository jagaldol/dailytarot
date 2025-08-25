package com.jagaldol.dailytarot.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFF5C86E),
    onPrimary = Color(0xFF1A1204),
    secondary = Color(0xFF7C6AD7),
    onSecondary = Color(0xFF0A0718),
    tertiary = Color(0xFFB58FFF),
    onTertiary = Color(0xFF120A1F),
    background = Color(0xFF0B1028),
    onBackground = Color(0xFFF5F7FF),
    surface = Color(0xFF101736),
    onSurface = Color(0xFFE9ECF9),
    surfaceVariant = Color(0xFF1A2250),
    onSurfaceVariant = Color(0xFFCDD4FF),
    outline = Color(0xFF6B73A7),
)

@Composable
fun DailytarotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

