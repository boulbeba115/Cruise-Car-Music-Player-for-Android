package com.boulbeba.cruise.core.designsystem.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

val LocalVisualQuality = staticCompositionLocalOf { VisualQuality.Full }

object CruiseTheme {
    val colors: CruiseColors @Composable get() = LocalCruiseColors.current
    val typography: CruiseTypography @Composable get() = LocalCruiseTypography.current
    val dimens: CruiseDimens @Composable get() = LocalCruiseDimens.current
    val layout: LayoutClass @Composable get() = LocalLayoutClass.current
    val skin: Skin @Composable get() = LocalSkin.current
    val quality: VisualQuality @Composable get() = LocalVisualQuality.current
}

/**
 * Root theme. The [skin] supplies colours, fonts, shapes and styles; [uiScale] (0.8–1.4, SPEC §5.1) scales every dp
 * and sp, and the layout class is computed after scaling, so a larger UI on a small screen falls back to a more
 * compact layout instead of overflowing.
 */
@Composable
fun CruiseTheme(
    skin: Skin = Skins.Midnight,
    uiScale: Float = 1f,
    quality: VisualQuality = VisualQuality.Full,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    val scaled = Density(base.density * uiScale.coerceIn(0.8f, 1.4f), base.fontScale)
    val colors = skin.colors
    val typography = remember(skin) { typographyFor(skin.display, skin.body, skin.displayWeight) }
    CompositionLocalProvider(LocalDensity provides scaled) {
        BoxWithConstraints {
            val layout = LayoutClass.from(maxWidth, maxHeight)
            val material = if (colors.isDark) {
                darkColorScheme(
                    primary = colors.accent, onPrimary = colors.onAccent, secondary = colors.accent2,
                    background = colors.background, onBackground = colors.textPrimary,
                    surface = colors.surface, onSurface = colors.textPrimary, onSurfaceVariant = colors.textSecondary,
                    surfaceContainer = colors.surfaceElevated, surfaceContainerHigh = colors.surfaceElevated,
                    secondaryContainer = colors.surfaceElevated, onSecondaryContainer = colors.textPrimary,
                    outlineVariant = colors.divider, error = colors.error,
                )
            } else {
                lightColorScheme(
                    primary = colors.accent, onPrimary = colors.onAccent, secondary = colors.accent2,
                    background = colors.background, onBackground = colors.textPrimary,
                    surface = colors.surface, onSurface = colors.textPrimary, onSurfaceVariant = colors.textSecondary,
                    surfaceContainer = colors.surfaceElevated, surfaceContainerHigh = colors.surfaceElevated,
                    outlineVariant = colors.divider, error = colors.error,
                )
            }
            CompositionLocalProvider(
                LocalSkin provides skin,
                LocalVisualQuality provides quality,
                LocalCruiseColors provides colors,
                LocalCruiseTypography provides typography,
                LocalLayoutClass provides layout,
                LocalCruiseDimens provides dimensFor(layout).scaleCorners(skin.corners),
            ) {
                MaterialTheme(colorScheme = material, content = content)
            }
        }
    }
}
