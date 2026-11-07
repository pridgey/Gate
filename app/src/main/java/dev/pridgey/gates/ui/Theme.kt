package dev.pridgey.gates.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.pridgey.gates.R

/*
 * Gates design system: calm and minimal.
 * One typeface (Manrope), a quiet near-neutral palette with a single sage accent,
 * text first, generous space. Home sits on the wallpaper; everything else on
 * soft surfaces.
 */

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(
    manrope(200), manrope(300), manrope(400), manrope(500), manrope(600), manrope(700),
)

private val GatesType = Typography(
    // The home clock.
    displayLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(200), fontSize = 80.sp, lineHeight = 84.sp, letterSpacing = (-0.04).em),
    displayMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(300), fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.03).em),
    displaySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(300), fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.02).em),
    // Home app names.
    headlineMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(300), fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(400), fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em),
    // All apps names, sheet titles.
    titleLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(400), fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(500), fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(600), fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(400), fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(600), fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(500), fontSize = 13.sp, lineHeight = 16.sp),
    // Eyebrows and tags: small caps feel.
    labelSmall = TextStyle(fontFamily = Manrope, fontWeight = FontWeight(700), fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.12.em),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2F5D47),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E6DC),
    onPrimaryContainer = Color(0xFF0F2A1D),
    background = Color(0xFFF6F5F1),
    surface = Color(0xFFF6F5F1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0EFEA),
    surfaceContainer = Color(0xFFEBEAE4),
    surfaceContainerHigh = Color(0xFFE4E3DD),
    surfaceContainerHighest = Color(0xFFDCDBD4),
    onSurface = Color(0xFF1A1C1A),
    onSurfaceVariant = Color(0xFF61665F),
    outline = Color(0xFFBFC0B8),
    outlineVariant = Color(0xFFDFDED7),
    error = Color(0xFFB3261E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB7D3C1),
    onPrimary = Color(0xFF0F2219),
    primaryContainer = Color(0xFF2A4236),
    onPrimaryContainer = Color(0xFFD7E6DC),
    background = Color(0xFF0F100F),
    surface = Color(0xFF0F100F),
    surfaceContainerLowest = Color(0xFF0A0B0A),
    surfaceContainerLow = Color(0xFF151715),
    surfaceContainer = Color(0xFF1A1C1A),
    surfaceContainerHigh = Color(0xFF222522),
    surfaceContainerHighest = Color(0xFF2B2E2B),
    onSurface = Color(0xFFECEDE8),
    onSurfaceVariant = Color(0xFFA2A79F),
    outline = Color(0xFF454944),
    outlineVariant = Color(0xFF2A2D2A),
    error = Color(0xFFF2B8B5),
)

/** Still mode (STILL-2): warm, low contrast, dark. */
private val Still = darkColorScheme(
    primary = Color(0xFFD9BC92),
    onPrimary = Color(0xFF2B2117),
    primaryContainer = Color(0xFF3D3122),
    onPrimaryContainer = Color(0xFFEBD9BE),
    background = Color(0xFF16120E),
    surface = Color(0xFF16120E),
    surfaceContainerLowest = Color(0xFF110E0B),
    surfaceContainerLow = Color(0xFF1C1712),
    surfaceContainer = Color(0xFF221C16),
    surfaceContainerHigh = Color(0xFF2A231C),
    surfaceContainerHighest = Color(0xFF332B22),
    onSurface = Color(0xFFE2D5C2),
    onSurfaceVariant = Color(0xFFA5977F),
    outline = Color(0xFF4F4436),
    outlineVariant = Color(0xFF30281F),
    error = Color(0xFFE8B4A6),
)

val LocalStill = compositionLocalOf { false }

@Composable
fun GatesTheme(still: Boolean = false, content: @Composable () -> Unit) {
    val scheme = when {
        still -> Still
        isSystemInDarkTheme() -> Dark
        else -> Light
    }
    CompositionLocalProvider(LocalStill provides still) {
        MaterialTheme(colorScheme = scheme, typography = GatesType) {
            // Text and icons without their own color follow the theme, never default black.
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}
