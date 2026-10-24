package com.boulbeba.cruise.feature.carlab.diagnostics

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.KeyEvent

/**
 * Makes the app the target of media buttons while the key tester is open.
 *
 * Android routes media keys to the session that most recently played audio, so the probe plays
 * looped silence and keeps an active session that reports "playing". Keys that arrive through the
 * session (rather than the activity) are recorded with source "media-session".
 */
class MediaKeyProbe(private val context: Context) {
    private var session: MediaSession? = null
    private var silence: AudioTrack? = null

    fun start() {
        if (session != null) return
        silence = createSilence().also { it.play() }
        session = MediaSession(context, "CruiseKeyProbe").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (event != null) KeyEventLog.record("media-session", event)
                    return true
                }
            })
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_FAST_FORWARD or
                            PlaybackState.ACTION_REWIND or PlaybackState.ACTION_STOP,
                    )
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build(),
            )
            isActive = true
        }
    }

    fun stop() {
        session?.run {
            isActive = false
            release()
        }
        session = null
        silence?.run {
            runCatching { stop() }
            release()
        }
        silence = null
    }

    private fun createSilence(): AudioTrack {
        val sampleRate = 48_000
        val frames = sampleRate / 2
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(frames * 4)
            .build()
        track.write(ShortArray(frames * 2), 0, frames * 2)
        track.setLoopPoints(0, frames, -1)
        return track
    }
}
