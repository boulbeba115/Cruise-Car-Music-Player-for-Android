package com.boulbeba.cruise.core.designsystem.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Screen matrix from SPEC §5.1, in dp (pixels ÷ density). */
class LayoutClassTest {

    @Test fun `1024x600 head unit at mdpi is compact landscape when status bar takes height`() =
        assertEquals(LayoutClass.CompactLandscape, LayoutClass.from(1024.dp, 552.dp / 1.2f))

    @Test fun `1024x600 head unit at hdpi is compact landscape`() =
        assertEquals(LayoutClass.CompactLandscape, LayoutClass.from(683.dp, 400.dp))

    @Test fun `1280x720 head unit at mdpi is regular landscape (owner default)`() =
        assertEquals(LayoutClass.RegularLandscape, LayoutClass.from(1280.dp, 720.dp))

    @Test fun `1280x720 at 1_5x is still regular landscape`() =
        assertEquals(LayoutClass.RegularLandscape, LayoutClass.from(853.dp, 480.dp))

    @Test fun `2000x1200 tablet at mdpi is expanded landscape`() =
        assertEquals(LayoutClass.ExpandedLandscape, LayoutClass.from(2000.dp, 1200.dp))

    @Test fun `1920x720 bar screen is ultra-wide`() =
        assertEquals(LayoutClass.UltraWide, LayoutClass.from(1920.dp, 720.dp))

    @Test fun `phone in landscape is compact landscape`() =
        assertEquals(LayoutClass.CompactLandscape, LayoutClass.from(873.dp, 393.dp))

    @Test fun `phone in portrait is compact portrait`() =
        assertEquals(LayoutClass.CompactPortrait, LayoutClass.from(393.dp, 873.dp))

    @Test fun `portrait tablet is regular portrait`() =
        assertEquals(LayoutClass.RegularPortrait, LayoutClass.from(800.dp, 1280.dp))

    @Test fun `half of a 1280x720 split screen is compact portrait`() =
        assertEquals(LayoutClass.CompactPortrait, LayoutClass.from(636.dp / 1.1f, 720.dp))
}
