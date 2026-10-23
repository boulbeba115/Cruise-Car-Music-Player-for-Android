package com.boulbeba.cruise.feature.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.designsystem.theme.BarStyle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.playback.PlayerUiState
import com.boulbeba.cruise.core.ui.formatDuration
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The animated music bar (owner's request 2026-10-23, SPEC VIS-6): progress moves smoothly every frame, the bar
 * reacts to the music, and each skin draws its own style. Tap or drag anywhere to seek; the time follows the finger.
 */
@Composable
fun AnimatedMusicBar(state: PlayerUiState, audio: AudioLevels, accent: Color, accent2: Color, onSeek: (Long) -> Unit) {
    val skin = CruiseTheme.skin
    val colors = CruiseTheme.colors
    val duration = state.durationMs.coerceAtLeast(1)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val pattern = remember(state.path) {
        val seed = (state.path ?: "").hashCode()
        FloatArray(72) { i -> 0.25f + 0.75f * abs(sin(i * 0.37f + seed % 97) * sin(i * 0.11f + (seed % 13))) }
    }
    val height = when (skin.bar) { BarStyle.Hairline -> 40.dp; BarStyle.LedSegments -> 44.dp; else -> 64.dp }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(duration) { detectTapGestures { onSeek((it.x / size.width * duration).toLong()) } }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragFraction?.let { onSeek((it * duration).toLong()) }; dragFraction = null },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) },
                )
            },
    ) {
        audio.frame // redraw on every audio frame
        val fraction = dragFraction ?: (state.positionAt(audio.now).toFloat() / duration).coerceIn(0f, 1f)
        when (skin.bar) {
            BarStyle.Waveform -> waveform(fraction, pattern, audio, accent, accent2, colors.textSecondary)
            BarStyle.NeonWave -> neonWave(fraction, audio, accent, accent2, colors.textSecondary)
            BarStyle.LedSegments -> ledSegments(fraction, audio, accent, accent2, colors.surfaceElevated)
            BarStyle.Groove -> groove(fraction, audio, accent, accent2, colors.textSecondary)
            BarStyle.Hairline -> hairline(fraction, audio, accent, colors.textSecondary)
        }
    }
    val shown = dragFraction?.let { (it * duration).toLong() } ?: state.positionAt(audio.now)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(formatDuration(shown), style = CruiseTheme.typography.meta, color = if (dragFraction != null) accent else colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text("-" + formatDuration(duration - shown), style = CruiseTheme.typography.meta, color = colors.textSecondary)
    }
}

/** Bars from a per-track pattern, each one dancing with its spectrum band; played part in a gradient. */
private fun DrawScope.waveform(fraction: Float, pattern: FloatArray, audio: AudioLevels, a: Color, b: Color, idle: Color) {
    val n = pattern.size
    val slot = size.width / n
    val w = slot * 0.6f
    val bands = audio.bands
    for (i in 0 until n) {
        val live = bands[(i * bands.size / n).coerceIn(0, bands.lastIndex)]
        val h = size.height * (pattern[i] * (0.55f + 0.45f * live)).coerceIn(0.08f, 1f)
        val played = (i + 0.5f) / n <= fraction
        val c = if (played) lerp(a, b, i.toFloat() / n) else idle.copy(alpha = 0.28f + 0.25f * live)
        drawRoundRect(c, Offset(i * slot + (slot - w) / 2, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
    }
    val x = size.width * fraction
    drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.6f), Color.Transparent), center = Offset(x, size.height / 2), radius = 26f + 30f * audio.level), radius = 26f + 30f * audio.level, center = Offset(x, size.height / 2))
}

/** Oscilloscope: the played part is a glowing wave shaped by the music, the rest a dim line. */
private fun DrawScope.neonWave(fraction: Float, audio: AudioLevels, a: Color, b: Color, idle: Color) {
    val mid = size.height / 2
    val x1 = size.width * fraction
    drawLine(idle.copy(alpha = 0.35f), Offset(x1, mid), Offset(size.width, mid), strokeWidth = 3f)
    val path = Path()
    val steps = 120
    for (s in 0..steps) {
        val x = x1 * s / steps
        val band = audio.bands[(s * audio.bands.size / (steps + 1)).coerceIn(0, audio.bands.lastIndex)]
        val amp = size.height * 0.42f * (0.12f + 0.88f * band) * (0.3f + 0.7f * s / steps)
        val y = mid + amp * sin(s * 0.42f + audio.time * 9f) * sin(s * 0.07f + audio.time * 2f)
        if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val brush = Brush.horizontalGradient(listOf(b, a))
    drawPath(path, brush, style = Stroke(width = 14f, cap = StrokeCap.Round), alpha = 0.18f)
    drawPath(path, brush, style = Stroke(width = 7f, cap = StrokeCap.Round), alpha = 0.35f)
    drawPath(path, brush, style = Stroke(width = 3f, cap = StrokeCap.Round))
    drawCircle(Brush.radialGradient(listOf(Color.White, a.copy(alpha = 0.6f), Color.Transparent), center = Offset(x1, mid), radius = 22f + 20f * audio.level), radius = 22f + 20f * audio.level, center = Offset(x1, mid))
}

/** Dashboard LED meter: lit segments up to the position, a VU flicker just ahead of it, red zone at the end. */
private fun DrawScope.ledSegments(fraction: Float, audio: AudioLevels, amber: Color, red: Color, off: Color) {
    val n = 48
    val gap = 4f
    val w = (size.width - gap * (n - 1)) / n
    val lit = (fraction * n).toInt()
    val vu = (audio.level * 10).toInt().coerceAtMost(8)
    for (i in 0 until n) {
        val x = i * (w + gap)
        val hot = i >= n * 0.9f
        val on = i < lit
        val flicker = !on && i < lit + vu
        val c = when {
            on -> if (hot) red else amber
            flicker -> (if (hot) red else amber).copy(alpha = 0.35f)
            else -> off
        }
        drawRoundRect(c, Offset(x, 0f), Size(w, size.height), CornerRadius(3f))
        if (on) drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(x, 0f), Size(w, size.height * 0.35f), CornerRadius(3f))
    }
}

/** Vinyl groove: fine line with a stylus that leaves a warm trail and swells with the music. */
private fun DrawScope.groove(fraction: Float, audio: AudioLevels, brass: Color, rust: Color, idle: Color) {
    val mid = size.height / 2
    val x = size.width * fraction
    for (k in 0 until 3) drawLine(idle.copy(alpha = 0.18f), Offset(0f, mid - 6f + k * 6f), Offset(size.width, mid - 6f + k * 6f), strokeWidth = 1f)
    drawLine(Brush.horizontalGradient(listOf(rust.copy(alpha = 0.2f), brass)), Offset(0f, mid), Offset(x, mid), strokeWidth = 4f + 8f * audio.level, cap = StrokeCap.Round)
    val wobble = sin(audio.time * PI.toFloat() * 2) * 2f
    drawCircle(brass, radius = 10f + 6f * audio.level, center = Offset(x, mid + wobble))
    drawCircle(Color.Black.copy(alpha = 0.6f), radius = 4f, center = Offset(x, mid + wobble))
}

/** Minimal: hairline with a breathing playhead. */
private fun DrawScope.hairline(fraction: Float, audio: AudioLevels, white: Color, idle: Color) {
    val mid = size.height / 2
    val x = size.width * fraction
    drawLine(idle.copy(alpha = 0.35f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2f)
    drawLine(white, Offset(0f, mid), Offset(x, mid), strokeWidth = 3f)
    val r = 7f + 9f * audio.level
    drawCircle(white.copy(alpha = 0.25f), radius = r * 2.2f, center = Offset(x, mid))
    drawCircle(white, radius = r, center = Offset(x, mid))
}
