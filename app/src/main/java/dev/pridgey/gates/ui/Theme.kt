package dev.pridgey.gates.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1F4D3A)
private val Cream = Color(0xFFF2EBDD)

private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFD9E7DD),
    surface = Color(0xFFFBF9F5),
    surfaceContainerLow = Color(0xFFF3F0EA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9CCFB4),
    onPrimary = Color(0xFF0B2A1D),
    secondaryContainer = Color(0xFF2C4A3B),
    surface = Color(0xFF151816),
    surfaceContainerLow = Color(0xFF1C201E),
)

/** Still mode (STILL-2): warm, low-contrast. */
private val Still = darkColorScheme(
    primary = Color(0xFFD8B98C),
    onPrimary = Color(0xFF2B2117),
    secondaryContainer = Color(0xFF3A3026),
    surface = Color(0xFF221D18),
    surfaceContainerLow = Color(0xFF29231D),
    onSurface = Color(0xFFD9CDBB),
    onSurfaceVariant = Color(0xFFB4A894),
)

val LocalStill = compositionLocalOf { false }

@Composable
fun GatesTheme(still: Boolean = false, content: @Composable () -> Unit) {
    val scheme = when {
        still -> Still
        isSystemInDarkTheme() -> Dark
        else -> Light
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalStill provides still) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

val GatesGreen = Green
val GatesCream = Cream
