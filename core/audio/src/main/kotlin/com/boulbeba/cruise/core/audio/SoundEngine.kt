package com.boulbeba.cruise.core.audio

import kotlin.math.max
import kotlin.math.pow

/**
 * Turns [SoundSettings] + playback context (ReplayGain of the current track, volume level) into [DspParams] for
 * the audio thread. A process-wide singleton: the processor lives inside ExoPlayer, the settings come from the UI.
 */
object SoundEngine {

    @Volatile var settings: SoundSettings = SoundSettings()
        set(value) { field = value; version++ }

    /** ReplayGain of the current track in dB (already chosen track/album by the player), 0 when unknown. */
    @Volatile var replayGainDb: Float = 0f
        set(value) { field = value; version++ }

    /** Peak of the current track (ReplayGain peak), for clip prevention. */
    @Volatile var replayGainPeak: Float = 1f
        set(value) { field = value; version++ }

    /** Media volume 0…1, drives loudness compensation (SPEC DSP-8). */
    @Volatile var volume: Float = 0.6f
        set(value) {
            if (kotlin.math.abs(field - value) > 0.02f) { field = value; version++ }
        }

    /** A/B compare: bypass while held (SPEC DSP-16). */
    @Volatile var compareBypass: Boolean = false
        set(value) { field = value; version++ }

    @Volatile var version: Int = 0
        private set

    fun params(sampleRate: Int): DspParams = build(settings, sampleRate, replayGainDb, replayGainPeak, volume, compareBypass)

    fun build(s: SoundSettings, rate: Int, rgDb: Float, rgPeak: Float, vol: Float, bypass: Boolean): DspParams {
        val filters = ArrayList<Biquad>()
        if (s.enabled) {
            SoundSettings.EQ_BANDS.forEachIndexed { i, f ->
                val g = s.eq.getOrElse(i) { 0f }
                if (kotlin.math.abs(g) > 0.05f && f < rate * 0.45) filters += Biquad.peaking(f, g.toDouble(), 1.41, rate)
            }
            if (kotlin.math.abs(s.bassDb) > 0.05f) filters += Biquad.lowShelf(90.0, s.bassDb.toDouble(), rate)
            if (s.loudness) {
                // Equal-loudness compensation: the quieter the volume, the more lows and some highs (ISO 226 shape).
                val quiet = (1f - vol).coerceIn(0f, 1f)
                if (quiet > 0.05f) {
                    filters += Biquad.lowShelf(100.0, (7.0 * quiet), rate, 0.8)
                    if (rate > 20_000) filters += Biquad.highShelf(9000.0, (3.5 * quiet), rate, 0.8)
                }
            }
        }
        // Headroom: never let EQ boosts clip; the limiter only catches what is left (SPEC §3).
        val maxBoost = if (filters.isEmpty()) 0.0 else
            (20..20000 step 37).maxOf { f -> filters.sumOf { it.gainDb(f.toDouble(), rate) } }.coerceAtLeast(0.0)
        val rg = if (s.enabled && s.replayGain != ReplayGainMode.Off) rgDb + s.rgPreampDb else 0f
        var gainDb = (if (s.enabled) s.preampDb else 0f) + rg - maxBoost.toFloat()
        if (s.enabled && rg > 0 && rgPeak > 0) gainDb = minOf(gainDb, (-20 * kotlin.math.log10(rgPeak.toDouble())).toFloat())
        val delay = (rate * 0.0012).toInt() // ≈ 1.2 ms: path difference between near and far door speakers
        val (gl, gr, dl, dr) = when (if (s.enabled) s.focus else Focus.Off) {
            Focus.Driver -> FocusSetup(0.88f, 1f, delay, 0)
            Focus.Passenger -> FocusSetup(1f, 0.88f, 0, delay)
            Focus.Off -> FocusSetup(1f, 1f, 0, 0)
        }
        val bal = if (s.enabled) s.balance.coerceIn(-1f, 1f) else 0f
        return DspParams(
            sampleRate = rate,
            bypass = bypass || !s.enabled,
            preGain = 10.0.pow(gainDb / 20.0).toFloat(),
            filters = filters,
            width = if (s.enabled) s.width.coerceIn(0f, 1.8f) else 1f,
            gainL = gl * (if (bal > 0) 1 - bal else 1f),
            gainR = gr * (if (bal < 0) 1 + bal else 1f),
            delayL = dl,
            delayR = dr,
            // Off when bypassed: the untouched signal cannot go over, and the engine must be transparent then.
            limiter = s.enabled && s.limiter && !bypass,
            ceiling = CEILING,
            dither = true,
        )
    }

    /** Limiter ceiling: −0.3 dBFS. Output is 16-bit PCM straight to the DAC, so no more margin is needed. */
    const val CEILING = 0.966f

    private data class FocusSetup(val gl: Float, val gr: Float, val dl: Int, val dr: Int)

    /** Highest boost of the current curve, for display ("headroom −x dB"). */
    fun headroomDb(rate: Int = 44_100): Float = max(0f, -20f * kotlin.math.log10(params(rate).preGain.coerceAtLeast(1e-6f)))
}

/**
 * Post-DSP mono samples for the visualizer and the animated music bar (SPEC VIS-4, VIS-6). Written by the audio
 * thread without locks; readers take a snapshot. Data older than [STALE_MS] reads as silence.
 */
object AudioTap {
    private const val SIZE = 4096
    private const val STALE_MS = 250L
    private val ring = FloatArray(SIZE)
    @Volatile private var write = 0
    @Volatile private var lastWrite = 0L

    internal fun push(samples: FloatArray, count: Int) {
        var w = write
        for (i in 0 until count) {
            ring[w] = samples[i]
            w = (w + 1) and (SIZE - 1)
        }
        write = w
        lastWrite = System.nanoTime()
    }

    private fun fresh() = System.nanoTime() - lastWrite < STALE_MS * 1_000_000

    /** RMS of the last ~20 ms, 0…1. */
    fun level(): Float {
        if (!fresh()) return 0f
        var sum = 0f
        val w = write
        for (i in 1..1024) { val v = ring[(w - i) and (SIZE - 1)]; sum += v * v }
        return kotlin.math.sqrt(sum / 1024).coerceIn(0f, 1f)
    }

    private val window = FloatArray(1024)
    private val magnitudes = FloatArray(512)

    /**
     * Fills [bands] with 0…1 levels on a logarithmic frequency scale (40 Hz … 16 kHz). Returns false when silent.
     * Not thread-safe: call from one UI loop.
     */
    fun spectrum(bands: FloatArray, sampleRate: Int = 44_100): Boolean {
        if (!fresh()) { bands.fill(0f); return false }
        val w = write
        for (i in 0 until 1024) window[i] = ring[(w - 1024 + i) and (SIZE - 1)]
        Fft.magnitudes(window, magnitudes)
        val n = bands.size
        val binHz = sampleRate / 1024.0
        for (b in 0 until n) {
            val f0 = 40.0 * (16000.0 / 40.0).pow(b.toDouble() / n)
            val f1 = 40.0 * (16000.0 / 40.0).pow((b + 1).toDouble() / n)
            val i0 = (f0 / binHz).toInt().coerceIn(1, 511)
            val i1 = (f1 / binHz).toInt().coerceIn(i0 + 1, 512)
            var peak = 0f
            for (i in i0 until i1) peak = maxOf(peak, magnitudes[i])
            val db = 20 * kotlin.math.log10(peak / 64f + 1e-6f)
            bands[b] = ((db + 48f) / 48f).coerceIn(0f, 1f)
        }
        return true
    }
}
