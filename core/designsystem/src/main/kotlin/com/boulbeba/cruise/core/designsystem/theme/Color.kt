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

val NeonColors = CruiseColors(
    background = Color(0xFF0B0420),
    surface = Color(0xFF180A31),
    surfaceElevated = Color(0xFF241044),
    accent = Color(0xFFFF2BD6),
    onAccent = Color(0xFF1A0016),
    textPrimary = Color(0xFFFBEFFF),
    textSecondary = Color(0xFFC3A6E6),
    divider = Color(0xFF34195C),
    success = Color(0xFF2EF2FF),
    warning = Color(0xFFFFE65C),
    error = Color(0xFFFF5470),
    isDark = true,
    accent2 = Color(0xFF2EF2FF),
)

val AmberColors = CruiseColors(
    background = Color(0xFF070605),
    surface = Color(0xFF14110C),
    surfaceElevated = Color(0xFF1F1A12),
    accent = Color(0xFFFFB000),
    onAccent = Color(0xFF1A1000),
    textPrimary = Color(0xFFFFE9BC),
    textSecondary = Color(0xFFB79566),
    divider = Color(0xFF2E2618),
    success = Color(0xFF9BE15D),
    warning = Color(0xFFFFB000),
    error = Color(0xFFFF5A1F),
    isDark = true,
    accent2 = Color(0xFFFF5A1F),
)

val VinylColors = CruiseColors(
    background = Color(0xFF150D09),
    surface = Color(0xFF23160F),
    surfaceElevated = Color(0xFF2F2016),
    accent = Color(0xFFE8A75D),
    onAccent = Color(0xFF241303),
    textPrimary = Color(0xFFF7ECDA),
    textSecondary = Color(0xFFC4A98B),
    divider = Color(0xFF3B2A1D),
    success = Color(0xFF9CC48A),
    warning = Color(0xFFE8A75D),
    error = Color(0xFFD9583A),
    isDark = true,
    accent2 = Color(0xFFC4572F),
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
