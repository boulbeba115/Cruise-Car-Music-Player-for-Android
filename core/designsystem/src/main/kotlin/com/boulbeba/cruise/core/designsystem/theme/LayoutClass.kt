package com.boulbeba.cruise.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive layout classes (SPEC §5.1). Chosen from the current window size, never from the device,
 * so rotation, split-screen and resizing are handled the same way as a different screen.
 */
enum class LayoutClass {
    CompactLandscape,
    RegularLandscape,
    ExpandedLandscape,
    UltraWide,
    CompactPortrait,
    RegularPortrait,
    ;

    val isPortrait: Boolean get() = this == CompactPortrait || this == RegularPortrait

    /** Compact portrait uses a bottom bar; every other class uses a navigation rail. */
    val usesBottomBar: Boolean get() = this == CompactPortrait

    /** Whether the rail has room for labels under its icons. */
    val railShowsLabels: Boolean get() = this != CompactLandscape

    companion object {
        fun from(width: Dp, height: Dp): LayoutClass = if (width >= height) {
            when {
                width / height > 2.4f -> UltraWide
                height < 480.dp -> CompactLandscape
                height <= 900.dp -> RegularLandscape
                else -> ExpandedLandscape
            }
        } else {
            if (width < 600.dp) CompactPortrait else RegularPortrait
        }
    }
}

val LocalLayoutClass = staticCompositionLocalOf { LayoutClass.RegularLandscape }
