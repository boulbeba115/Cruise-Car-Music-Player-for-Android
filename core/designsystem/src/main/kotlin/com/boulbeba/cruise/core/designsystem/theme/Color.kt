package com.boulbeba.cruise.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour tokens of a skin (SPEC §5.3). [accent2] is the second colour of gradients and glows. */
@Immutable
data class CruiseColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val accent: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val isDark: Boolean,
    val accent2: Color = accent,
)

val MidnightColors = CruiseColors(
    background = Color(0xFF0A0C10),
    surface = Color(0xFF14171D),
    surfaceElevated = Color(0xFF1C2028),
    accent = Color(0xFF4FC3F7),
    onAccent = Color(0xFF00141F),
    textPrimary = Color(0xFFF2F4F8),
    textSecondary = Color(0xFFA8B0BD),
    divider = Color(0xFF262B34),
    success = Color(0xFF66D19E),
    warning = Color(0xFFFFC857),
    error = Color(0xFFFF6B6B),
    isDark = true,
    accent2 = Color(0xFF7C8CFF),
)

val DaylightColors = CruiseColors(
    background = Color(0xFFF4F6F9),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE8ECF2),
    accent = Color(0xFF0061A4),
    onAccent = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0B0E13),
    textSecondary = Color(0xFF465061),
    divider = Color(0xFFD9DEE6),
    success = Color(0xFF1E8A57),
    warning = Color(0xFF9A6700),
    error = Color(0xFFC62828),
    isDark = false,
    accent2 = Color(0xFF6A4DD6),
)

/** Neon City: night blue-black, electric cyan and neon red-pink, an amber sign as third colour. */
val NeonColors = CruiseColors(
    background = Color(0xFF05070F),
    surface = Color(0xFF0C1020),
    surfaceElevated = Color(0xFF141A30),
    accent = Color(0xFF19E6FF),
    onAccent = Color(0xFF001A1F),
    textPrimary = Color(0xFFF2FBFF),
    textSecondary = Color(0xFF93A9C9),
    divider = Color(0xFF1C2440),
    success = Color(0xFF19E6FF),
    warning = Color(0xFFFFB000),
    error = Color(0xFFFF2A6D),
    isDark = true,
    accent2 = Color(0xFFFF2A6D),
)

/** Sport Cockpit: graphite, cool white type, signal red for needle and red zone, ice blue for the LEDs. */
val AmberColors = CruiseColors(
    background = Color(0xFF0B0D10),
    surface = Color(0xFF15181D),
    surfaceElevated = Color(0xFF1E232A),
    accent = Color(0xFFFF3B3B),
    onAccent = Color(0xFF1A0000),
    textPrimary = Color(0xFFF1F4F7),
    textSecondary = Color(0xFF9AA4B1),
    divider = Color(0xFF2A3038),
    success = Color(0xFF5ED8FF),
    warning = Color(0xFFFFC857),
    error = Color(0xFFFF3B3B),
    isDark = true,
    accent2 = Color(0xFF5ED8FF),
)

/** Vinyl Lounge: ink navy and felt, cream type, record-label red and a soft hi-fi teal. */
val VinylColors = CruiseColors(
    background = Color(0xFF0B1020),
    surface = Color(0xFF131A2E),
    surfaceElevated = Color(0xFF1B2440),
    accent = Color(0xFFE84855),
    onAccent = Color(0xFF1A0508),
    textPrimary = Color(0xFFF5EEDC),
    textSecondary = Color(0xFFB3B8C9),
    divider = Color(0xFF242E4A),
    success = Color(0xFF6EC3D4),
    warning = Color(0xFFF2C14E),
    error = Color(0xFFE84855),
    isDark = true,
    accent2 = Color(0xFF6EC3D4),
)

val AuroraColors = CruiseColors(
    background = Color(0xFF04111A),
    surface = Color(0xCC0C2130),
    surfaceElevated = Color(0xFF123247),
    accent = Color(0xFF5CFFC8),
    onAccent = Color(0xFF00241A),
    textPrimary = Color(0xFFEDFFF9),
    textSecondary = Color(0xFFA0C7C4),
    divider = Color(0xFF1B3D52),
    success = Color(0xFF5CFFC8),
    warning = Color(0xFFFFD66B),
    error = Color(0xFFFF7A90),
    isDark = true,
    accent2 = Color(0xFF8A7CFF),
)

val OledColors = CruiseColors(
    background = Color(0xFF000000),
    surface = Color(0xFF0B0B0B),
    surfaceElevated = Color(0xFF161616),
    accent = Color(0xFFFFFFFF),
    onAccent = Color(0xFF000000),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8C8C8C),
    divider = Color(0xFF1F1F1F),
    success = Color(0xFFB8FFB0),
    warning = Color(0xFFFFE08A),
    error = Color(0xFFFF8A80),
    isDark = true,
    accent2 = Color(0xFF7A7A7A),
)

val LocalCruiseColors = staticCompositionLocalOf { MidnightColors }

/** Tape Deck, "chrome tape": blue-black, smoky shell, cool greys, teal and coral-pink stripes. */
val TapeColors = CruiseColors(
    background = Color(0xFF0E1218),
    surface = Color(0xFF161C24),
    surfaceElevated = Color(0xFF1F2731),
    accent = Color(0xFF2DE1C2),
    onAccent = Color(0xFF00211B),
    textPrimary = Color(0xFFEAF2F7),
    textSecondary = Color(0xFF9AA9B7),
    divider = Color(0xFF27313D),
    success = Color(0xFF2DE1C2),
    warning = Color(0xFFFFC857),
    error = Color(0xFFFF6B8B),
    isDark = true,
    accent2 = Color(0xFFFF6B8B),
)
