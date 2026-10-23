package com.boulbeba.cruise.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DspTest {

    @After fun reset() {
        SoundEngine.settings = SoundSettings()
        SoundEngine.replayGainDb = 0f
        SoundEngine.replayGainPeak = 1f
        SoundEngine.volume = 0.6f
        SoundEngine.compareBypass = false
    }

    // ---- Filters ---------------------------------------------------------------------------------------------

    @Test fun peaking_filter_hits_its_gain_at_centre_only() {
        val b = Biquad.peaking(1000.0, 6.0, 1.41, 44_100)
        assertEquals(6.0, b.gainDb(1000.0, 44_100), 0.05)
        assertEquals(0.0, b.gainDb(60.0, 44_100), 0.2)
        assertEquals(0.0, b.gainDb(15_000.0, 44_100), 0.2)
    }

    @Test fun shelves_boost_their_side() {
        val low = Biquad.lowShelf(90.0, 6.0, 44_100)
        assertEquals(6.0, low.gainDb(20.0, 44_100), 0.3)
        assertEquals(0.0, low.gainDb(5000.0, 44_100), 0.1)
        val high = Biquad.highShelf(9000.0, 4.0, 48_000)
        assertEquals(4.0, high.gainDb(20_000.0, 48_000), 0.3)
        assertEquals(0.0, high.gainDb(200.0, 48_000), 0.1)
    }

    // ---- Engine ----------------------------------------------------------------------------------------------

    @Test fun headroom_cancels_every_boost() {
        val s = SoundSettings(eq = List(10) { 12f }, bassDb = 6f, loudness = false)
        val p = SoundEngine.build(s, 44_100, 0f, 1f, 1f, bypass = false)
        val preDb = 20 * kotlin.math.log10(p.preGain.toDouble())
        for (f in listOf(25.0, 31.0, 100.0, 1000.0, 8000.0, 16_000.0)) {
            assertTrue("clips at $f Hz", preDb + p.responseDb(f) <= 0.15)
        }
    }

    @Test fun flat_settings_are_unity() {
        val p = SoundEngine.build(SoundSettings(loudness = false), 44_100, 0f, 1f, 1f, bypass = false)
        assertTrue(p.filters.isEmpty())
        assertEquals(1f, p.preGain, 1e-6f)
    }

    @Test fun loudness_grows_as_volume_drops() {
        val quiet = SoundEngine.build(SoundSettings(loudness = true), 44_100, 0f, 1f, 0.1f, false)
        val loud = SoundEngine.build(SoundSettings(loudness = true), 44_100, 0f, 1f, 0.97f, false)
        assertTrue(quiet.responseDb(40.0) > 4.0)
        assertTrue(loud.filters.isEmpty())
    }

    @Test fun replaygain_boost_never_pushes_peak_over_full_scale() {
        val p = SoundEngine.build(SoundSettings(loudness = false), 44_100, rgDb = 9f, rgPeak = 0.8f, vol = 1f, bypass = false)
        assertTrue(0.8f * p.preGain <= 1.0001f)
        val cut = SoundEngine.build(SoundSettings(loudness = false), 44_100, rgDb = -7f, rgPeak = 1f, vol = 1f, bypass = false)
        assertEquals(-7.0, 20 * kotlin.math.log10(cut.preGain.toDouble()), 0.01)
    }

    @Test fun disabled_engine_is_bypassed() {
        val p = SoundEngine.build(SoundSettings(enabled = false, eq = List(10) { 5f }), 44_100, -6f, 1f, 0.2f, false)
        assertTrue(p.bypass)
        assertTrue(p.filters.isEmpty())
    }

    @Test fun driver_focus_delays_and_lowers_the_near_left_side() {
        val p = SoundEngine.build(SoundSettings(focus = Focus.Driver, loudness = false), 44_100, 0f, 1f, 1f, false)
        assertEquals(52, p.delayL)
        assertEquals(0, p.delayR)
        assertTrue(p.gainL < p.gainR)
    }

    // ---- Processor -------------------------------------------------------------------------------------------

    private fun process(settings: SoundSettings, input: ShortArray, channels: Int = 2): ShortArray {
        SoundEngine.settings = settings
        val processor = CruiseAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44_100, channels, C.ENCODING_PCM_16BIT))
        @Suppress("DEPRECATION") processor.flush()
        val out = ShortArray(input.size)
        var written = 0
        var read = 0
        while (read < input.size) {
            val n = minOf(4096, input.size - read)
            val buf = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder())
            for (i in 0 until n) buf.putShort(input[read + i])
            buf.flip()
            processor.queueInput(buf)
            val o = processor.output.order(ByteOrder.nativeOrder())
            while (o.hasRemaining()) out[written++] = o.short
            read += n
        }
        assertEquals(input.size, written)
        return out
    }

    private fun sine(freq: Double, amplitude: Double, seconds: Double, invertRight: Boolean = false): ShortArray {
        val frames = (44_100 * seconds).toInt()
        return ShortArray(frames * 2) { i ->
            val v = (amplitude * 32767 * sin(2 * PI * freq * (i / 2) / 44_100)).toInt()
            (if (invertRight && i % 2 == 1) -v else v).toShort()
        }
    }

    @Test fun disabled_engine_is_bit_exact() {
        val input = sine(997.0, 0.7, 0.2) + shortArrayOf(-32768, 32767, -32768, 32767, 1, -1)
        assertTrue(input.contentEquals(process(SoundSettings(enabled = false), input)))
    }

    @Test fun compare_bypass_is_bit_exact() {
        val input = sine(440.0, 1.0, 0.1)
        SoundEngine.compareBypass = true
        assertTrue(input.contentEquals(process(SoundSettings(eq = List(10) { 6f }, preampDb = 3f), input)))
    }

    @Test fun limiter_holds_the_ceiling_on_hot_input() {
        // +6 dB preamp on a full-scale sine: every peak is 6 dB over.
        val out = process(SoundSettings(preampDb = 6f, loudness = false), sine(440.0, 1.0, 0.5))
        val ceiling = SoundEngine.CEILING * 32768 + 2 // plus dither
        assertTrue("peak ${out.maxOf { abs(it.toInt()) }}", out.all { abs(it.toInt()) <= ceiling })
        assertTrue(out.maxOf { abs(it.toInt()) } > 0.93 * 32768) // and it does not over-compress
    }

    @Test fun wide_stereo_on_out_of_phase_music_does_not_clip() {
        val out = process(SoundSettings(width = 1.8f, loudness = false), sine(220.0, 0.95, 0.3, invertRight = true))
        assertTrue(out.all { abs(it.toInt()) <= SoundEngine.CEILING * 32768 + 2 })
    }

    @Test fun eq_boost_never_clips_without_the_limiter() {
        val eq = List(10) { if (it == 5) 12f else 0f } // +12 dB at 1 kHz
        val input = sine(1000.0, 0.9, 0.3)
        val out = process(SoundSettings(eq = eq, limiter = false, loudness = false), input)
        // Headroom takes the boost back: the 1 kHz tone comes out at its input level, nothing near full scale.
        assertTrue(out.maxOf { abs(it.toInt()) } <= input.maxOf { abs(it.toInt()) } * 1.02)
    }

    @Test fun silence_stays_quiet_with_dither() {
        val out = process(SoundSettings(loudness = false), ShortArray(44_100))
        assertTrue(out.all { abs(it.toInt()) <= 1 })
        assertFalse(out.all { it.toInt() == 0 }) // TPDF dither is present
    }

    @Test fun spectrum_peaks_at_the_played_frequency() {
        val frames = 4096
        val tone = FloatArray(frames) { (0.5 * sin(2 * PI * 1000.0 * it / 44_100)).toFloat() }
        AudioTap.push(tone, frames)
        val bands = FloatArray(48)
        assertTrue(AudioTap.spectrum(bands))
        val loudest = bands.indices.maxBy { bands[it] }
        val f0 = 40.0 * Math.pow(16000.0 / 40.0, loudest / 48.0)
        val f1 = 40.0 * Math.pow(16000.0 / 40.0, (loudest + 1) / 48.0)
        assertTrue("band $loudest = $f0–$f1 Hz", f0 <= 1100 && f1 >= 900)
        assertTrue(AudioTap.level() > 0.3f)
    }
}
