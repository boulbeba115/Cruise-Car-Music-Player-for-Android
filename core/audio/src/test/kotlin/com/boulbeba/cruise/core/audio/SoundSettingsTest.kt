package com.boulbeba.cruise.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SoundSettingsTest {
    @Test fun json_round_trip() {
        val s = SoundSettings(
            enabled = false, preset = "megane", eq = List(10) { it - 4.5f }, customEq = List(10) { 1f }, preampDb = -2f, bassDb = 3f,
            trebleDb = -2.5f, loudness = false,
            width = 1.3f, focus = Focus.Passenger, balance = -0.25f, limiter = false, replayGain = ReplayGainMode.Track, rgPreampDb = 1.5f,
        )
        assertEquals(s, SoundSettings.fromJson(s.toJson()))
    }

    @Test fun broken_json_gives_defaults() = assertEquals(SoundSettings(), SoundSettings.fromJson("not json"))

    @Test fun settings_from_1_1_keep_the_curve_and_drop_hidden_controls() {
        val old = """{"enabled":true,"preset":"custom","eq":[1,2,3,4,5,6,7,8,9,10],"bass":8,"width":1.7,"balance":-0.5,"limiter":false,"focus":"Driver"}"""
        val s = SoundSettings.fromJson(old)
        assertEquals("custom", s.preset)
        assertEquals(List(10) { it + 1f }, s.eq)
        assertEquals(s.eq, s.customEq)
        assertEquals(Focus.Driver, s.focus)
        assertEquals(0f, s.bassDb)
        assertEquals(1f, s.width)
        assertEquals(0f, s.balance)
        assertEquals(true, s.limiter)
    }

    @Test fun flat_with_the_engine_on_becomes_natural() {
        val s = SoundSettings.fromJson("""{"enabled":true,"preset":"flat","eq":[0,0,0,0,0,0,0,0,0,0]}""")
        assertEquals(false, s.enabled)
    }

    @Test fun fresh_install_starts_on_the_megane_profile() {
        val s = SoundSettings.fromJson("{}")
        assertEquals(SoundPreset.Megane.id, s.preset)
        assertEquals(SoundPreset.Megane.eq, s.eq)
    }

    @Test fun presets_stay_inside_the_eq_range() {
        SoundPreset.entries.forEach { p ->
            assertEquals(SoundSettings.EQ_BANDS.size, p.eq.size)
            p.eq.forEach { assert(kotlin.math.abs(it) <= SoundSettings.EQ_RANGE_DB) }
        }
    }
}
