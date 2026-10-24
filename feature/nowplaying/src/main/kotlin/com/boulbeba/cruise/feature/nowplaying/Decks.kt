package com.boulbeba.cruise.feature.nowplaying

import android.os.SystemClock
import com.boulbeba.cruise.core.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Set while a finger is on a hands-on cover: the whole-screen gestures step aside meanwhile ([nowPlayingGestures]). */
internal object CoverTouch {
    @Volatile var active = false
}

/**
 * Hands on the music (CHANGE_REQUESTS.md CR-24): what a turntable or a tape deck does to the sound when you touch it.
 *
 * - [grab]: a hand stops the record or the tape — the pitch slides down to [grab]'s floor, then silence.
 * - [scrub] (record): moving it plays from where it was grabbed at the speed and pitch of the hand; backward rewinds
 *   (silently: the player cannot play audio backwards); holding still is silence.
 * - [shuttle] (tape): fast-forward cueing at a raised speed and pitch, or rewinding, while the finger stays pulled.
 * - [release]: it starts again with the pitch rising to normal, if it was playing when grabbed.
 *
 * Speed and pitch are always set back to normal at the end, whatever interrupts an effect.
 */
internal class DeckFx(private val player: PlayerConnection, private val scope: CoroutineScope) {
    private var effect: Job? = null
    private var shuttleLoop: Job? = null
    private var cursor = 0L
    private var lastSeekAt = 0L
    private var lastTarget = -1L
    private var shuttleRate = 0f
    private var speedNow = 1f
    private var playingNow = false

    /** It was playing when grabbed, so it starts again on [release]. */
    var resumes = false
        private set

    private val durationMs get() = player.state.value.durationMs.coerceAtLeast(1)

    fun grab(stopMs: Long, floor: Float) {
        val s = player.state.value
        resumes = s.isPlaying
        playingNow = s.isPlaying
        cursor = s.positionAt(SystemClock.elapsedRealtime())
        lastTarget = -1L
        effect?.cancel()
        if (!resumes) return
        effect = scope.launch {
            try {
                ramp(1f, floor, stopMs)
            } finally {
                pause()
                speed(1f)
            }
        }
    }

    /** The record moved [offsetMs] of music from where it was grabbed, at [rate] times normal speed (negative: back). */
    fun scrub(offsetMs: Long, rate: Float) {
        effect?.cancel()
        val target = (cursor + offsetMs).coerceIn(0, durationMs)
        val now = SystemClock.uptimeMillis()
        when {
            rate > MIN_RATE -> {
                speed(rate.coerceIn(0.3f, 2.5f))
                // Playing advances by itself; re-align with the hand only when it has drifted.
                val playing = player.state.value.positionAt(SystemClock.elapsedRealtime())
                if (kotlin.math.abs(playing - target) > 350 && now - lastSeekAt > 250) seek(target, now)
                play()
            }
            else -> {
                pause()
                if (now - lastSeekAt > 110 && kotlin.math.abs(target - lastTarget) > 40) seek(target, now)
            }
        }
    }

    /** Tape: [rate] in −1…1 from how far the finger pulled the cassette (positive: forward). */
    fun shuttle(rate: Float) {
        effect?.cancel()
        shuttleRate = rate
        if (shuttleLoop?.isActive == true) return
        shuttleLoop = scope.launch {
            try {
                while (isActive) {
                    val r = shuttleRate
                    when {
                        r > MIN_RATE -> {
                            // Cueing: faster and higher, the chirp of a deck held on fast-forward.
                            speed(1f + r * 1.6f)
                            play()
                        }
                        r < -MIN_RATE -> {
                            pause()
                            cursor = (cursor + (r * 1400).toLong()).coerceIn(0, durationMs)
                            player.seekTo(cursor)
                        }
                        else -> pause()
                    }
                    delay(100)
                    if (r > MIN_RATE) cursor = player.state.value.positionAt(SystemClock.elapsedRealtime())
                }
            } finally {
                speed(1f)
            }
        }
    }

    fun release(startSpeed: Float, rampMs: Long) {
        effect?.cancel()
        shuttleLoop?.cancel()
        shuttleLoop = null
        shuttleRate = 0f
        if (!resumes) {
            pause()
            speed(1f)
            return
        }
        effect = scope.launch {
            try {
                speed(startSpeed)
                play()
                ramp(startSpeed, 1f, rampMs)
            } finally {
                speed(1f)
            }
        }
    }

    private fun seek(target: Long, now: Long) {
        player.seekTo(target)
        lastSeekAt = now
        lastTarget = target
    }

    // Only what changes reaches the player: a drag sends dozens of moves a second.
    private fun speed(value: Float) {
        if (value != 1f && kotlin.math.abs(value - speedNow) < 0.04f) return
        speedNow = value
        player.setSpeed(value)
    }

    private fun play() {
        if (playingNow) return
        playingNow = true
        player.play()
    }

    private fun pause() {
        if (!playingNow) return
        playingNow = false
        player.pause()
    }

    private suspend fun ramp(from: Float, to: Float, ms: Long) {
        val steps = (ms / STEP_MS).coerceAtLeast(1)
        for (i in 1..steps) {
            speed(from + (to - from) * i / steps)
            delay(STEP_MS)
        }
    }

    private companion object {
        const val MIN_RATE = 0.12f
        const val STEP_MS = 40L
    }
}
