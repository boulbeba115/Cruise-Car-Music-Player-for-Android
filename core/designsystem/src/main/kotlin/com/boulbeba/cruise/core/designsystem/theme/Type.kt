package com.boulbeba.cruise.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Glanceable type scale (SPEC §5.3): nothing a driver reads is smaller than 14 sp. Fonts come from the skin. */
@Immutable
data class CruiseTypography(
    val display: TextStyle,
    val title: TextStyle,
    val subtitle: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val meta: TextStyle,
)

fun typographyFor(display: FontFamily, body: FontFamily, displayWeight: FontWeight = FontWeight.Bold) = CruiseTypography(
    display = TextStyle(fontFamily = display, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = displayWeight, letterSpacing = (-0.5).sp),
    title = TextStyle(fontFamily = display, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = displayWeight),
    subtitle = TextStyle(fontFamily = body, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    body = TextStyle(fontFamily = body, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    label = TextStyle(fontFamily = body, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    meta = TextStyle(fontFamily = body, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

val DefaultTypography = typographyFor(FontFamily.Default, FontFamily.Default)

val LocalCruiseTypography = staticCompositionLocalOf { DefaultTypography }
