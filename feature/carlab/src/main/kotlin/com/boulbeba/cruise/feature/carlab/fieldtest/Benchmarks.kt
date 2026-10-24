package com.boulbeba.cruise.feature.carlab.fieldtest

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CPU budget for the sound engine (SPEC §4.3): how much of one core a realistic DSP chain needs on this
 * unit. Runs on the calling thread; call it off the main thread.
 */
object DspBenchmark {

    data class Result(val name: String, val realtimeFactor: Double) {
        /** Share of one core needed to keep up with real time. */
        val coreLoadPercent: Double get() = 100.0 / realtimeFactor
        fun describe() = "%-34s %7.1fx real time  -> %5.1f %% of one core".format(name, realtimeFactor, coreLoadPercent)
    }

    fun runAll(): List<Result> {
        // Warm-up so the JIT/AOT state does not skew the first measurement.
        biquads(48_000, 10, seconds = 1.0)
        return listOf(
            Result("10-band EQ, stereo, 44.1 kHz", biquads(44_100, 10, seconds = 5.0)),
            Result("10-band EQ, stereo, 48 kHz", biquads(48_000, 10, seconds = 5.0)),
            Result("16-band param EQ, stereo, 48 kHz", biquads(48_000, 16, seconds = 5.0)),
            Result("limiter (look-ahead), stereo, 48k", limiter(48_000, seconds = 5.0)),
            Result("FFT 2048 every 1024 samples, 48k", fft(48_000, seconds = 5.0)),
        )
    }

    /** Biquad cascade, transposed direct form II, float — the core of the EQ. Returns × real time. */
    private fun biquads(rate: Int, bands: Int, seconds: Double): Double {
        val block = 1024
        val buffer = FloatArray(block * 2) { sin(it * 0.01).toFloat() * 0.5f }
        val b0 = FloatArray(bands); val b1 = FloatArray(bands); val b2 = FloatArray(bands)
        val a1 = FloatArray(bands); val a2 = FloatArray(bands)
        for (i in 0 until bands) {
            val w = 2 * PI * (31.0 * Math.pow(2.0, i.toDouble())) / rate
            val alpha = sin(w) / 2.0
            val a0 = 1 + alpha
            b0[i] = ((1 + alpha * 1.2) / a0).toFloat(); b1[i] = (-2 * cos(w) / a0).toFloat(); b2[i] = ((1 - alpha * 1.2) / a0).toFloat()
            a1[i] = (-2 * cos(w) / a0).toFloat(); a2[i] = ((1 - alpha) / a0).toFloat()
        }
        val z1 = FloatArray(bands * 2); val z2 = FloatArray(bands * 2)
        val frames = (rate * seconds).toLong()
        val start = System.nanoTime()
        var done = 0L
        while (done < frames) {
            for (ch in 0..1) {
                for (b in 0 until bands) {
                    val s = b * 2 + ch
                    var s1 = z1[s]; var s2 = z2[s]
                    val c0 = b0[b]; val c1 = b1[b]; val c2 = b2[b]; val d1 = a1[b]; val d2 = a2[b]
                    var i = ch
                    while (i < buffer.size) {
                        val x = buffer[i]
                        val y = c0 * x + s1
                        s1 = c1 * x - d1 * y + s2
                        s2 = c2 * x - d2 * y
                        buffer[i] = y
                        i += 2
                    }
                    z1[s] = s1; z2[s] = s2
                }
            }
            done += block
        }
        return seconds / ((System.nanoTime() - start) / 1e9)
    }

    /** Look-ahead peak limiter (5 ms window) on stereo float. */
    private fun limiter(rate: Int, seconds: Double): Double {
        val block = 1024
        val look = rate / 200
        val delay = FloatArray(look * 2)
        var pos = 0
        var gain = 1f
        val buffer = FloatArray(block * 2) { sin(it * 0.02).toFloat() * 1.3f }
        val frames = (rate * seconds).toLong()
        val start = System.nanoTime()
        var done = 0L
        while (done < frames) {
            var i = 0
            while (i < buffer.size) {
                val l = buffer[i]; val r = buffer[i + 1]
                val peak = maxOf(kotlin.math.abs(l), kotlin.math.abs(r))
                val target = if (peak > 0.89f) 0.89f / peak else 1f
                gain = if (target < gain) target else gain + (target - gain) * 0.0005f
                val outL = delay[pos * 2]; val outR = delay[pos * 2 + 1]
                delay[pos * 2] = l; delay[pos * 2 + 1] = r
                pos = (pos + 1) % look
                buffer[i] = outL * gain; buffer[i + 1] = outR * gain
                i += 2
            }
            done += block
        }
        return seconds / ((System.nanoTime() - start) / 1e9)
    }

    /** Radix-2 FFT of 2048 points, hop 1024 — what the spectrum visualizer needs. */
    private fun fft(rate: Int, seconds: Double): Double {
        val n = 2048
        val re = DoubleArray(n); val im = DoubleArray(n)
        val ffts = (rate * seconds / 1024).toInt()
        val start = System.nanoTime()
        repeat(ffts) { k ->
            for (i in 0 until n) { re[i] = sin(i * 0.05 + k); im[i] = 0.0 }
            fftInPlace(re, im)
            var energy = 0.0
            for (i in 0 until n / 2) energy += sqrt(re[i] * re[i] + im[i] * im[i])
            if (energy < 0) error("unreachable")
        }
        return seconds / ((System.nanoTime() - start) / 1e9)
    }

    private fun fftInPlace(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { var t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                    val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** Frame pacing collected while the animated test scene runs. */
data class FrameStats(val intervalsMs: List<Double>) {
    private val sorted = intervalsMs.sorted()
    private fun percentile(p: Double) = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * p).toInt()]
    val fps: Double get() = if (intervalsMs.isEmpty()) 0.0 else 1000.0 / intervalsMs.average()
    fun describe(): String = if (intervalsMs.isEmpty()) "no frames" else
        "frames=%d avg %.1f fps | p50 %.1f ms p90 %.1f ms p99 %.1f ms | >17 ms: %.0f %% | >33 ms: %.0f %%".format(
            intervalsMs.size, fps, percentile(0.5), percentile(0.9), percentile(0.99),
            100.0 * intervalsMs.count { it > 17.5 } / intervalsMs.size,
            100.0 * intervalsMs.count { it > 33.5 } / intervalsMs.size,
        )
}
