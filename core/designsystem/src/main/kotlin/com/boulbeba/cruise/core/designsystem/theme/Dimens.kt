package com.boulbeba.cruise.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes that change with the layout class. Touch targets never go below the car minimums in SPEC §1. */
@Immutable
data class CruiseDimens(
    val gutter: Dp,
    val railWidth: Dp,
    val bottomBarHeight: Dp,
    val listRowHeight: Dp,
    val minTouchTarget: Dp,
    val primaryControl: Dp,
    val secondaryControl: Dp,
    val playButton: Dp,
    val cardRadius: Dp,
    val coverRadius: Dp,
) {
    /** Skins make shapes sharper or rounder (SPEC skins). */
    fun scaleCorners(factor: Float) = copy(cardRadius = cardRadius * factor, coverRadius = coverRadius * factor)
}

private val Regular = CruiseDimens(
    gutter = 24.dp,
    railWidth = 104.dp,
    bottomBarHeight = 80.dp,
    listRowHeight = 72.dp,
    minTouchTarget = 56.dp,
    primaryControl = 72.dp,
    secondaryControl = 56.dp,
    playButton = 96.dp,
    cardRadius = 20.dp,
    coverRadius = 28.dp,
)

fun dimensFor(layout: LayoutClass): CruiseDimens = when (layout) {
    LayoutClass.CompactLandscape -> Regular.copy(
        gutter = 16.dp,
        railWidth = 72.dp,
        listRowHeight = 64.dp,
        playButton = 80.dp,
    )
    LayoutClass.RegularLandscape, LayoutClass.UltraWide -> Regular
    LayoutClass.ExpandedLandscape -> Regular.copy(
        gutter = 32.dp,
        railWidth = 120.dp,
        listRowHeight = 80.dp,
        primaryControl = 88.dp,
        secondaryControl = 64.dp,
        playButton = 120.dp,
        coverRadius = 36.dp,
    )
    LayoutClass.CompactPortrait -> Regular.copy(gutter = 16.dp, listRowHeight = 64.dp, playButton = 88.dp)
    LayoutClass.RegularPortrait -> Regular
}

val LocalCruiseDimens = staticCompositionLocalOf { dimensFor(LayoutClass.RegularLandscape) }
