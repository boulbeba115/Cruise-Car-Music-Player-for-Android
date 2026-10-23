package com.boulbeba.cruise.core.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Normalised biquad coefficients (a0 = 1), RBJ audio-EQ cookbook. */
data class Biquad(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double) {

    /** Magnitude response in dB at [freq] — used by the curve editor and the tests. */
    fun gainDb(freq: Double, sampleRate: Int): Double {
        val w = 2 * PI * freq / sampleRate
        val cw = cos(w); val sw = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * cw + b2 * c2; val ni = -(b1 * sw + b2 * s2)
        val dr = 1 + a1 * cw + a2 * c2; val di = -(a1 * sw + a2 * s2)
        return 10 * kotlin.math.log10((nr * nr + ni * ni) / (dr * dr + di * di))
    }

    companion object {
        val Identity = Biquad(1.0, 0.0, 0.0, 0.0, 0.0)

        fun peaking(freq: Double, gainDb: Double, q: Double, rate: Int): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w = 2 * PI * freq / rate
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha / a
            return Biquad((1 + alpha * a) / a0, -2 * cos(w) / a0, (1 - alpha * a) / a0, -2 * cos(w) / a0, (1 - alpha / a) / a0)
        }

        fun lowShelf(freq: Double, gainDb: Double, rate: Int, slope: Double = 1.0): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w = 2 * PI * freq / rate
            val cw = cos(w)
            val alpha = sin(w) / 2 * sqrt((a + 1 / a) * (1 / slope - 1) + 2)
            val sa = 2 * sqrt(a) * alpha
            val a0 = (a + 1) + (a - 1) * cw + sa
            return Biquad(
                a * ((a + 1) - (a - 1) * cw + sa) / a0,
                2 * a * ((a - 1) - (a + 1) * cw) / a0,
                a * ((a + 1) - (a - 1) * cw - sa) / a0,
                -2 * ((a - 1) + (a + 1) * cw) / a0,
                ((a + 1) + (a - 1) * cw - sa) / a0,
            )
        }

        fun highShelf(freq: Double, gainDb: Double, rate: Int, slope: Double = 1.0): Biquad {
            val a = 10.0.pow(gainDb / 40)
            val w = 2 * PI * freq / rate
            val cw = cos(w)
            val alpha = sin(w) / 2 * sqrt((a + 1 / a) * (1 / slope - 1) + 2)
            val sa = 2 * sqrt(a) * alpha
            val a0 = (a + 1) - (a - 1) * cw + sa
            return Biquad(
                a * ((a + 1) + (a - 1) * cw + sa) / a0,
                -2 * a * ((a - 1) + (a + 1) * cw) / a0,
                a * ((a + 1) + (a - 1) * cw - sa) / a0,
                2 * ((a - 1) - (a + 1) * cw) / a0,
                ((a + 1) - (a - 1) * cw - sa) / a0,
            )
        }
    }
}

/**
 * Everything the audio thread needs, precomputed for one sample rate. Immutable: the UI builds a new instance and
 * swaps it in, so the audio thread never takes a lock.
 */
class DspParams(
    val sampleRate: Int,
    val bypass: Boolean,
    val preGain: Float,
    val filters: List<Biquad>,
    val width: Float,
    val gainL: Float,
    val gainR: Float,
    val delayL: Int,
    val delayR: Int,
    val limiter: Boolean,
    val ceiling: Float,
    val dither: Boolean,
) {
    /** Combined response of the filter cascade, in dB. */
    fun responseDb(freq: Double): Double = filters.sumOf { it.gainDb(freq, sampleRate) }
}

/** Fast RNG for dither (xorshift32). */
internal class Xorshift(seed: Int = 0x2545F491) {
    private var s = seed
    fun nextFloat(): Float {
        s = s xor (s shl 13); s = s xor (s ushr 17); s = s xor (s shl 5)
        return (s ushr 8) * (1f / 16_777_216f)
    }
}

/** Radix-2 FFT magnitude spectrum, for the visualizer tap. */
internal object Fft {
    fun magnitudes(input: FloatArray, out: FloatArray) {
        val n = input.size
        val re = DoubleArray(n) { i -> input[i] * (0.5 - 0.5 * cos(2 * PI * i / (n - 1))) }
        val im = DoubleArray(n)
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k; val b = a + len / 2
                    val vr = re[b] * cr - im[b] * ci
                    val vi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - vr; im[b] = im[a] - vi
                    re[a] += vr; im[a] += vi
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
        for (k in out.indices) out[k] = sqrt(re[k] * re[k] + im[k] * im[k]).toFloat()
    }
}
