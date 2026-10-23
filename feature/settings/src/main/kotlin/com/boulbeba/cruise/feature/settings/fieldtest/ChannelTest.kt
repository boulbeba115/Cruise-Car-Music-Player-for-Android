package com.boulbeba.cruise.feature.settings.fieldtest

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Plays short tones on the left, right or both channels to check wiring and balance in the car. */
object ChannelTest {

    enum class Side { Left, Right, Both }

    private var track: AudioTrack? = null

    fun play(side: Side, frequency: Double = 660.0, seconds: Double = 1.6) {
        stop()
        val rate = 48_000
        val frames = (rate * seconds).toInt()
        val fade = rate / 50
        val samples = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val envelope = min(1.0, min(i, frames - 1 - i) / fade.toDouble())
            val v = (sin(2 * PI * frequency * i / rate) * envelope * 0.35 * Short.MAX_VALUE).toInt().toShort()
            samples[i * 2] = if (side != Side.Right) v else 0
            samples[i * 2 + 1] = if (side != Side.Left) v else 0
        }
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .also {
                it.write(samples, 0, samples.size)
                it.play()
            }
        FieldLog.log("sound", "playing $side tone")
    }

    fun stop() {
        track?.run {
            runCatching { stop() }
            release()
        }
        track = null
    }
}
