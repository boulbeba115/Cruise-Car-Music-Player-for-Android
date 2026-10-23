@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.boulbeba.cruise.core.playback

import android.os.Handler
import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer

/**
 * Fades in on play and out on pause (SPEC PB-10) so starts and stops never click — the session and every
 * controller (notification, steering wheel, UI) go through this wrapper.
 */
class FadingPlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {

    private val handler = Handler(exo.applicationLooper)
    private var ramp: Runnable? = null

    override fun play() {
        cancelRamp()
        val from = if (exo.isPlaying) exo.volume else 0f
        exo.volume = from
        super.play()
        rampVolume(from, 1f, FADE_IN_MS) {}
    }

    override fun pause() {
        if (!exo.isPlaying) {
            cancelRamp()
            exo.volume = 1f
            super.pause()
            return
        }
        cancelRamp()
        rampVolume(exo.volume, 0f, FADE_OUT_MS) {
            super.pause()
            exo.volume = 1f
        }
    }

    private fun rampVolume(from: Float, to: Float, durationMs: Long, then: () -> Unit) {
        val steps = (durationMs / STEP_MS).coerceAtLeast(1)
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step++
                val t = step.toFloat() / steps
                // Equal-power-ish curve: quieter changes are perceived as smoother.
                exo.volume = from + (to - from) * (t * t * (3 - 2 * t))
                if (step < steps) handler.postDelayed(this, STEP_MS) else {
                    ramp = null
                    then()
                }
            }
        }
        ramp = runnable
        handler.postDelayed(runnable, STEP_MS)
    }

    private fun cancelRamp() {
        ramp?.let(handler::removeCallbacks)
        ramp = null
    }

    private companion object {
        const val FADE_IN_MS = 300L
        const val FADE_OUT_MS = 220L
        const val STEP_MS = 16L
    }
}
