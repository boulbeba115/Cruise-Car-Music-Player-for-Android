package com.boulbeba.cruise.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every skin must stay readable at a glance in a moving car (SPEC §5.1: WCAG AAA for primary text). */
class SkinContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb).toDouble()
    }

    private fun check(skin: Skin, what: String, fg: Color, bg: Color, min: Double) {
        val c = contrast(fg.compositeOver(bg), bg)
        assertTrue("${skin.id}: $what contrast %.2f < $min".format(c), c >= min)
    }

    @Test fun every_skin_is_readable() {
        for (skin in Skins.all) {
            val c = skin.colors
            check(skin, "primary text / background", c.textPrimary, c.background, 7.0)
            check(skin, "primary text / surface", c.textPrimary, c.surface, 7.0)
            check(skin, "primary text / elevated", c.textPrimary, c.surfaceElevated, 7.0)
            check(skin, "secondary text / background", c.textSecondary, c.background, 4.5)
            check(skin, "secondary text / elevated", c.textSecondary, c.surfaceElevated, 4.5)
            check(skin, "on-accent / accent", c.onAccent, c.accent, 4.5)
            check(skin, "accent / background", c.accent, c.background, 3.0)
            check(skin, "accent2 / background", c.accent2, c.background, 3.0)
        }
    }

    @Test fun skins_have_unique_ids_and_lookup_falls_back() {
        assertEquals(Skins.all.size, Skins.all.map { it.id }.toSet().size)
        Skins.all.forEach { assertEquals(it, Skins.byId(it.id)) }
        assertEquals(Skins.Midnight, Skins.byId("gone"))
        assertEquals(Skins.Midnight, Skins.byId(null))
    }

    @Test fun skins_really_differ() {
        // A skin must change the whole look, not just a colour.
        val looks = Skins.all.map { listOf(it.cover, it.background, it.bar, it.colors.background) }
        assertEquals(looks.size, looks.toSet().size)
    }

    @Test fun night_skin_follows_the_headlights() {
        val s = AppearanceState(skinId = Skins.Daylight.id, nightSkinId = Skins.OledPure.id)
        assertEquals(Skins.Daylight, s.activeSkin)
        assertEquals(Skins.OledPure, s.copy(headlightsOn = true).activeSkin)
        assertEquals(Skins.Daylight, s.copy(nightSkinId = null, headlightsOn = true).activeSkin)
    }

    @Test fun low_end_devices_get_balanced_animations() {
        assertEquals(VisualQuality.Balanced, AppearanceState(lowEndDevice = true).quality)
        assertEquals(VisualQuality.Full, AppearanceState(lowEndDevice = false).quality)
        assertEquals(VisualQuality.Saver, AppearanceState(performance = PerformanceMode.Saver).quality)
    }
}
