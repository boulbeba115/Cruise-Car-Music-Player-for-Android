package com.boulbeba.cruise.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SoundSettingsTest {
    @Test fun json_round_trip() {
        val s = SoundSettings(
            enabled = false, preset = "megane", eq = List(10) { it - 4.5f }, preampDb = -2f, bassDb = 3f, loudness = false,
            width = 1.3f, focus = Focus.Passenger, balance = -0.25f, limiter = false, replayGain = ReplayGainMode.Track, rgPreampDb = 1.5f,
        )
        assertEquals(s, SoundSettings.fromJson(s.toJson()))
    }

    @Test fun broken_json_gives_defaults() = assertEquals(SoundSettings(), SoundSettings.fromJson("not json"))

    @Test fun presets_stay_inside_the_eq_range() {
        SoundPreset.entries.forEach { p ->
            assertEquals(SoundSettings.EQ_BANDS.size, p.eq.size)
            p.eq.forEach { assert(kotlin.math.abs(it) <= SoundSettings.EQ_RANGE_DB) }
        }
    }
}
