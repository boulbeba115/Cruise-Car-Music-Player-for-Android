package com.boulbeba.cruise.feature.nowplaying

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.boulbeba.cruise.core.audio.AudioTap
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import kotlinx.coroutines.delay

/**
 * One loop per screen feeding every animated element (music bar, cover bars, gauge needle): smoothed spectrum
 * bands, overall level, animation time and "now" for smooth position extrapolation.
 * Runs at display rate on Full, ~30 fps on Balanced, 4 fps on Saver (progress only), and stops while paused.
 */
@Stable
class AudioLevels(bandCount: Int) {
    val bands = FloatArray(bandCount)
    private val raw = FloatArray(bandCount)
    var level by mutableFloatStateOf(0f)
        private set
    var time by mutableFloatStateOf(0f)
        private set
    var now by mutableLongStateOf(SystemClock.elapsedRealtime())
        private set
    /** Bumps every update so Canvas blocks that read [bands] redraw. */
    var frame by mutableLongStateOf(0L)
        private set

    internal fun update(seconds: Float, reactive: Boolean) {
        time = seconds
        now = SystemClock.elapsedRealtime()
        if (reactive) {
            AudioTap.spectrum(raw)
            for (i in bands.indices) {
                // Fast attack, slow release: lively but not jittery.
                bands[i] = if (raw[i] > bands[i]) bands[i] + (raw[i] - bands[i]) * 0.6f else bands[i] * 0.86f + raw[i] * 0.14f
            }
            val l = AudioTap.level()
            level = if (l > level) level + (l - level) * 0.5f else level * 0.9f
        } else {
            bands.fill(0f)
            level = 0f
        }
        frame++
    }
}

@Composable
fun rememberAudioLevels(quality: VisualQuality, playing: Boolean, bandCount: Int = 48): AudioLevels {
    val levels = remember(bandCount) { AudioLevels(bandCount) }
    LaunchedEffect(quality, playing) {
        val start = withFrameNanos { it } - (levels.time * 1e9).toLong()
        if (!playing) {
            // Paused: settle once (bars at rest, exact position) and stop asking for frames.
            levels.update(levels.time, reactive = false)
            return@LaunchedEffect
        }
        val tickMs = when (quality) { VisualQuality.Full -> 0L; VisualQuality.Balanced -> 33L; VisualQuality.Saver -> 250L }
        while (true) {
            if (tickMs > 0) delay(tickMs)
            withFrameNanos { t -> levels.update((t - start) / 1e9f, reactive = quality != VisualQuality.Saver) }
        }
    }
    return levels
}
