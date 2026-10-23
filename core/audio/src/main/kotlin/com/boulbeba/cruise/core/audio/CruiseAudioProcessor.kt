@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.boulbeba.cruise.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The sound engine inside ExoPlayer's output chain (SPEC §4.3): 16-bit PCM in, float processing, dithered 16-bit
 * out. Chain: pre-gain (pre-amp + ReplayGain − EQ headroom) → biquad cascade (EQ, bass, loudness) → stereo width →
 * driver focus (level + delay) → look-ahead limiter → tap for visuals → TPDF dither.
 */
class CruiseAudioProcessor : BaseAudioProcessor() {

    private var channels = 2
    private var rate = 44_100
    private var params: DspParams? = null
    private var paramsVersion = -1

    // Filter state (transposed direct form II): [filter][channel].
    private var z1 = Array(0) { FloatArray(2) }
    private var z2 = Array(0) { FloatArray(2) }

    // Driver-focus delay lines.
    private val delayL = FloatArray(MAX_DELAY)
    private val delayR = FloatArray(MAX_DELAY)
    private var delayPos = 0

    // Look-ahead limiter.
    private val lookL = FloatArray(LOOKAHEAD)
    private val lookR = FloatArray(LOOKAHEAD)
    private var lookPos = 0
    private var limiterGain = 1f
    // Sliding minimum of the gain targets over the look-ahead window (monotonic queue, amortised O(1) per sample):
    // the gain applied to a sample leaving the window is never above what any sample still inside it needs.
    private val minTarget = FloatArray(WINDOW_CAP)
    private val minAt = LongArray(WINDOW_CAP)
    private var minHead = 0
    private var minSize = 0
    private var sampleIndex = 0L
    private var releaseCoef = 0.0005f

    private val rng = Xorshift()
    private var tap = FloatArray(4096)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount !in 1..2) {
            return AudioProcessor.AudioFormat.NOT_SET // other formats pass through untouched
        }
        channels = inputAudioFormat.channelCount
        rate = inputAudioFormat.sampleRate
        releaseCoef = (1.0 - kotlin.math.exp(-1.0 / (rate * 0.12))).toFloat() // ~120 ms release
        paramsVersion = -1
        return inputAudioFormat
    }

    private fun refreshParams(): DspParams {
        val v = SoundEngine.version
        val current = params
        if (current != null && v == paramsVersion && current.sampleRate == rate) return current
        val next = SoundEngine.params(rate)
        if (next.filters.size != z1.size) {
            z1 = Array(next.filters.size) { FloatArray(2) }
            z2 = Array(next.filters.size) { FloatArray(2) }
        }
        params = next
        paramsVersion = v
        return next
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val p = refreshParams()
        val out = replaceOutputBuffer(bytes)
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val frames = bytes / (2 * channels)
        if (tap.size < frames) tap = FloatArray(frames)

        val f = p.filters
        val count = f.size
        for (i in 0 until frames) {
            var l = input.short / 32768f
            var r = if (channels == 2) input.short / 32768f else l

            if (!p.bypass) {
                l *= p.preGain
                r *= p.preGain
                for (k in 0 until count) {
                    val b = f[k]
                    val zl1 = z1[k]; val zl2 = z2[k]
                    val yl = (b.b0 * l + zl1[0]).toFloat()
                    zl1[0] = (b.b1 * l - b.a1 * yl + zl2[0]).toFloat()
                    zl2[0] = (b.b2 * l - b.a2 * yl).toFloat()
                    l = yl
                    val yr = (b.b0 * r + zl1[1]).toFloat()
                    zl1[1] = (b.b1 * r - b.a1 * yr + zl2[1]).toFloat()
                    zl2[1] = (b.b2 * r - b.a2 * yr).toFloat()
                    r = yr
                }
                if (channels == 2 && p.width != 1f) {
                    val m = (l + r) * 0.5f
                    val s = (l - r) * 0.5f * p.width
                    l = m + s
                    r = m - s
                }
                l *= p.gainL
                r *= p.gainR
                if (p.delayL > 0 || p.delayR > 0) {
                    delayL[delayPos] = l
                    delayR[delayPos] = r
                    l = delayL[(delayPos - p.delayL + MAX_DELAY) % MAX_DELAY]
                    r = delayR[(delayPos - p.delayR + MAX_DELAY) % MAX_DELAY]
                    delayPos = (delayPos + 1) % MAX_DELAY
                }
            }

            if (p.limiter) {
                val peak = maxOf(abs(l), abs(r))
                val target = if (peak > p.ceiling) p.ceiling / peak else 1f
                val windowMin = slidingMin(target)
                // Attack at once; release slowly, never above what the window needs.
                limiterGain = if (windowMin < limiterGain) windowMin else limiterGain + (windowMin - limiterGain) * releaseCoef
                val dl = lookL[lookPos]
                val dr = lookR[lookPos]
                lookL[lookPos] = l
                lookR[lookPos] = r
                lookPos = (lookPos + 1) % LOOKAHEAD
                l = dl * limiterGain
                r = dr * limiterGain
            }

            tap[i] = (l + r) * 0.5f
            out.putShort(toPcm16(l, p.dither && !p.bypass))
            if (channels == 2) out.putShort(toPcm16(r, p.dither && !p.bypass))
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
        AudioTap.push(tap, frames)
    }

    private fun slidingMin(target: Float): Float {
        val now = sampleIndex++
        while (minSize > 0 && minTarget[(minHead + minSize - 1) and (WINDOW_CAP - 1)] >= target) minSize--
        val slot = (minHead + minSize) and (WINDOW_CAP - 1)
        minTarget[slot] = target
        minAt[slot] = now
        minSize++
        while (minAt[minHead] < now - LOOKAHEAD) { minHead = (minHead + 1) and (WINDOW_CAP - 1); minSize-- }
        return minTarget[minHead]
    }

    private fun toPcm16(x: Float, dither: Boolean): Short {
        val d = if (dither) rng.nextFloat() - rng.nextFloat() else 0f // TPDF, ±1 LSB
        return (x * 32768f + d).roundToInt().coerceIn(-32768, 32767).toShort()
    }

    override fun onFlush() {
        z1.forEach { it.fill(0f) }
        z2.forEach { it.fill(0f) }
        delayL.fill(0f); delayR.fill(0f); lookL.fill(0f); lookR.fill(0f)
        limiterGain = 1f
        minHead = 0
        minSize = 0
    }

    override fun onReset() {
        params = null
        paramsVersion = -1
    }

    private companion object {
        const val MAX_DELAY = 512
        const val LOOKAHEAD = 48 // ≈ 1 ms at 44.1/48 kHz
        const val WINDOW_CAP = 64 // ≥ LOOKAHEAD + 1, power of two
    }
}
