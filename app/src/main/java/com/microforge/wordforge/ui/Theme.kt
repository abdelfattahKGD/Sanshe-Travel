package com.microforge.wordforge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import com.microforge.wordforge.R

// Palette taken from the turtle mascot: leaf green, warm cream, pencil amber, sky blue.
val Leaf = Color(0xFF3E8E5A)
val LeafDark = Color(0xFF2B6B42)
val Mint = Color(0xFFDDEFD6)
val Cream = Color(0xFFFBF8F1)
val Amber = Color(0xFFE0A63A)
val Sky = Color(0xFF5BA8D6)
val Coral = Color(0xFFE5705F)
val Good = Color(0xFF2E9E5B)
val Bad = Color(0xFFD64545)
val Gold = Amber

private val LightColors = lightColorScheme(
    primary = Leaf,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = Color(0xFF16361F),
    secondary = Amber,
    onSecondary = Color(0xFF2B2108),
    secondaryContainer = Color(0xFFFBEBC6),
    onSecondaryContainer = Color(0xFF4A3708),
    tertiary = Sky,
    background = Cream,
    onBackground = Color(0xFF22301F),
    surface = Color.White,
    onSurface = Color(0xFF22301F),
    surfaceVariant = Color(0xFFEEF2E6),
    onSurfaceVariant = Color(0xFF5A6757),
    outline = Color(0xFFB9C4B3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD19E),
    onPrimary = Color(0xFF0E2A16),
    primaryContainer = Color(0xFF244A31),
    onPrimaryContainer = Color(0xFFDDEFD6),
    secondary = Amber,
    onSecondary = Color(0xFF2B2108),
    secondaryContainer = Color(0xFF4A3708),
    onSecondaryContainer = Color(0xFFFBEBC6),
    tertiary = Sky,
    background = Color(0xFF121A14),
    onBackground = Color(0xFFE4EBE1),
    surface = Color(0xFF1A241C),
    onSurface = Color(0xFFE4EBE1),
    surfaceVariant = Color(0xFF243127),
    onSurfaceVariant = Color(0xFFAAB8A7),
    outline = Color(0xFF55665A),
)

/** One accent color per topic so the topic grid is not a wall of identical cards. */
private val topicColors = listOf(
    Color(0xFF3E8E5A), Color(0xFFE0A63A), Color(0xFF5BA8D6), Color(0xFFE5705F), Color(0xFF9B7ED8),
    Color(0xFF3DB5A6), Color(0xFF4F7BD9), Color(0xFFD9803D), Color(0xFFD9669C), Color(0xFF7A9E3D),
)

fun topicColor(num: Int): Color = topicColors[(num - 1).coerceAtLeast(0) % topicColors.size]

private val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

private fun appTypography(): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = Tajawal),
        displayMedium = b.displayMedium.copy(fontFamily = Tajawal),
        displaySmall = b.displaySmall.copy(fontFamily = Tajawal),
        headlineLarge = b.headlineLarge.copy(fontFamily = Tajawal),
        headlineMedium = b.headlineMedium.copy(fontFamily = Tajawal),
        headlineSmall = b.headlineSmall.copy(fontFamily = Tajawal),
        titleLarge = b.titleLarge.copy(fontFamily = Tajawal),
        titleMedium = b.titleMedium.copy(fontFamily = Tajawal),
        titleSmall = b.titleSmall.copy(fontFamily = Tajawal),
        bodyLarge = b.bodyLarge.copy(fontFamily = Tajawal),
        bodyMedium = b.bodyMedium.copy(fontFamily = Tajawal),
        bodySmall = b.bodySmall.copy(fontFamily = Tajawal),
        labelLarge = b.labelLarge.copy(fontFamily = Tajawal),
        labelMedium = b.labelMedium.copy(fontFamily = Tajawal),
        labelSmall = b.labelSmall.copy(fontFamily = Tajawal),
    )
}

/** The whole app UI is Arabic, so layout is forced to RTL. English text is rendered LTR via [En]. */
@Composable
fun WordForgeTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, typography = appTypography(), content = content)
    }
}
