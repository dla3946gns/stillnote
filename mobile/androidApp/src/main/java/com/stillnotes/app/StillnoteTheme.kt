package com.stillnotes.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5746),
    onPrimary = Color(0xFFFFFCF6),
    primaryContainer = Color(0xFFEBD8C5),
    onPrimaryContainer = Color(0xFF493329),
    secondary = Color(0xFF786251),
    secondaryContainer = Color(0xFFEBD8C5),
    onSecondaryContainer = Color(0xFF493329),
    background = Color(0xFFF5EFE3),
    onBackground = Color(0xFF332920),
    surface = Color(0xFFFFFCF6),
    onSurface = Color(0xFF332920),
    surfaceVariant = Color(0xFFF5EFE3),
    onSurfaceVariant = Color(0xFF6B584B),
    outline = Color(0xFF998576),
    outlineVariant = Color(0xFFE4D7C8),
    inverseSurface = Color(0xFF382E26),
    inverseOnSurface = Color(0xFFFFFCF6),
    inversePrimary = Color(0xFFE7BA9B),
    error = Color(0xFFA53F2D),
    errorContainer = Color(0xFFFFDAD1),
    onErrorContainer = Color(0xFF552015)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE7BA9B),
    onPrimary = Color(0xFF432C20),
    primaryContainer = Color(0xFF614538),
    onPrimaryContainer = Color(0xFFF8DFC9),
    secondary = Color(0xFFD9BEA7),
    secondaryContainer = Color(0xFF514137),
    onSecondaryContainer = Color(0xFFF4DECB),
    background = Color(0xFF241F1A),
    onBackground = Color(0xFFF5EFE3),
    surface = Color(0xFF2C2520),
    onSurface = Color(0xFFF5EFE3),
    surfaceVariant = Color(0xFF382E26),
    onSurfaceVariant = Color(0xFFD0BFAF),
    outline = Color(0xFFA89585),
    outlineVariant = Color(0xFF55463A),
    inverseSurface = Color(0xFFF5EFE3),
    inverseOnSurface = Color(0xFF332920),
    inversePrimary = Color(0xFF7A5746)
)

@Composable
fun StillnoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
