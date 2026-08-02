package com.fusionlancers.grafusion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.fusionlancers.grafusion.data.prefs.ThemeMode

// Fusionlancers brand palette
val FusionNavy = Color(0xFF0B1739)
val FusionBlue = Color(0xFF142B5F)
val EnergyOrange = Color(0xFFFF8A00)
val DataPurple = Color(0xFF7C3AED)
val OnSurfaceMuted = Color(0xFF9CA3AF)

private val LightColors = lightColorScheme(
    primary = EnergyOrange,
    onPrimary = Color.White,
    secondary = DataPurple,
    onSecondary = Color.White,
    background = Color(0xFFF7F8FB),
    onBackground = FusionNavy,
    surface = Color.White,
    onSurface = FusionNavy,
)

private val DarkColors = darkColorScheme(
    primary = EnergyOrange,
    onPrimary = Color.White,
    secondary = DataPurple,
    onSecondary = Color.White,
    background = FusionNavy,
    onBackground = Color.White,
    surface = FusionBlue,
    onSurface = Color.White,
)

@Composable
fun GrafusionTheme(
    mode: ThemeMode = ThemeMode.AUTO,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
