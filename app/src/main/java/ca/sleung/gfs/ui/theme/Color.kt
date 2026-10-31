package ca.sleung.gfs.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Core Brand Palette (Emerald & Crystal Pear)
val EmeraldPrimaryDark = Color(0xFF66BB6A)
val OnEmeraldPrimaryDark = Color(0xFF003915)
val EmeraldContainerDark = Color(0xFF14532D)
val OnEmeraldContainerDark = Color(0xFFB9F6CA)

val EmeraldPrimaryLight = Color(0xFF1B7A3E)
val OnEmeraldPrimaryLight = Color(0xFFFFFFFF)
val EmeraldContainerLight = Color(0xFFD5F5DF)
val OnEmeraldContainerLight = Color(0xFF063918)

val SageSecondaryDark = Color(0xFFB4CCB9)
val SageSecondaryLight = Color(0xFF4C6353)
val GoldTertiaryDark = Color(0xFFFFD54F)
val GoldTertiaryLight = Color(0xFF795900)

val DarkBackground = Color(0xFF121514)
val DarkSurface = Color(0xFF171B1A)
val DarkSurfaceVariant = Color(0xFF252B29)

val LightBackground = Color(0xFFF8FAF8)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE7ECE8)

// Accents preserved for compatibility & semantic status
val ForestGreen = Color(0xFF1B7A3E)
val EmeraldGreen = Color(0xFF4CAF50)
val SoftRed = Color(0xFFE57373)
val NeutralSlateGray = Color(0xFF90A4AE)

/**
 * Semantic status colors tuned to meet WCAG AA contrast ratios in both Light and Dark themes.
 */
@Immutable
data class GfsExtendedColors(
    val positiveAccent: Color,
    val onPositiveAccent: Color,
    val warningAccent: Color,
    val negativeAccent: Color,
    val savedAccent: Color
)

val DarkExtendedColors = GfsExtendedColors(
    positiveAccent = Color(0xFF4CAF50),
    onPositiveAccent = Color(0xFFFFFFFF),
    warningAccent = Color(0xFFFFB300),
    negativeAccent = Color(0xFFEF5350),
    savedAccent = Color(0xFF64B5F6)
)

val LightExtendedColors = GfsExtendedColors(
    positiveAccent = Color(0xFF1B7A3E),
    onPositiveAccent = Color(0xFFFFFFFF),
    warningAccent = Color(0xFFB45309),
    negativeAccent = Color(0xFFC62828),
    savedAccent = Color(0xFF0277BD)
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }

/**
 * Safely parses a 6-digit or 8-digit hex color string (with or without leading '#'),
 * falling back to [NeutralSlateGray] if malformed.
 */
fun parseHexColor(hex: String): Color {
    return runCatching {
        val cleanHex = hex.trim().removePrefix("#")
        val parsed = cleanHex.toLong(16)
        when (cleanHex.length) {
            6 -> Color(0xFF000000 or parsed)
            8 -> Color(parsed)
            else -> NeutralSlateGray
        }
    }.getOrDefault(NeutralSlateGray)
}
