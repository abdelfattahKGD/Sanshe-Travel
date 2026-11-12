package com.microforge.wordforge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection

val Blue = Color(0xFF1F4E79)
val Gold = Color(0xFFC9A227)
val Good = Color(0xFF2E9E5B)
val Bad = Color(0xFFD64545)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F3),
    onPrimaryContainer = Blue,
    secondary = Gold,
    onSecondary = Color(0xFF222222),
    background = Color.White,
    onBackground = Color(0xFF222222),
    surface = Color.White,
    onSurface = Color(0xFF222222),
    surfaceVariant = Color(0xFFF2F5F9),
    onSurfaceVariant = Color(0xFF55606B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DB8E3),
    onPrimary = Color(0xFF0B2A44),
    primaryContainer = Color(0xFF1F3B57),
    onPrimaryContainer = Color(0xFFD6E4F3),
    secondary = Gold,
    onSecondary = Color(0xFF222222),
    background = Color(0xFF10151B),
    onBackground = Color(0xFFE6EAEE),
    surface = Color(0xFF161C23),
    onSurface = Color(0xFFE6EAEE),
    surfaceVariant = Color(0xFF1E2630),
    onSurfaceVariant = Color(0xFFA9B4BF),
)

/** The whole app UI is Arabic, so layout is forced to RTL. English text is rendered LTR via [En]. */
@Composable
fun WordForgeTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
