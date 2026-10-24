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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boulbeba.cruise.core.designsystem.theme.BarStyle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.playback.PlayerUiState
import com.boulbeba.cruise.core.ui.formatDuration
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
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
    val wave = remember { Path() }
    val reels = remember { Reels() }
    val tapePath = remember { TapePath() }
    val tapeTexts = if (skin.bar == BarStyle.Tape) rememberTapeTexts() else null
    val bar = skin.bar
    val height = when (bar) {
        BarStyle.Hairline -> 40.dp
        BarStyle.LedSegments -> 44.dp
        BarStyle.NeonTube -> 48.dp
        BarStyle.Tape -> 112.dp
        else -> 64.dp
    }
    // Where along the bar the finger is: across the whole width (the tape's lit part follows it round the reels).
    fun fractionAt(x: Float, w: Float): Float = (x / w).coerceIn(0f, 1f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(duration, bar) { detectTapGestures { onSeek((fractionAt(it.x, size.width.toFloat()) * duration).toLong()) } }
            .pointerInput(duration, bar) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = fractionAt(it.x, size.width.toFloat()) },
                    onDragEnd = { dragFraction?.let { onSeek((it * duration).toLong()) }; dragFraction = null },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ -> dragFraction = fractionAt(change.position.x, size.width.toFloat()) },
                )
            },
    ) {
        audio.frame // redraw on every audio frame
        val fraction = dragFraction ?: (state.positionAt(audio.now).toFloat() / duration).coerceIn(0f, 1f)
        when (bar) {
            BarStyle.Waveform -> waveform(fraction, pattern, audio, accent, accent2, colors.textSecondary)
            BarStyle.Wavy -> wavy(fraction, audio, accent, accent2, colors.textSecondary, colors.textPrimary, wave)
            BarStyle.NeonTube -> neonTube(fraction, audio, accent, colors.textSecondary)
            BarStyle.LedSegments -> ledSegments(fraction, audio, normal = accent2, hot = accent, off = colors.surfaceElevated)
            BarStyle.Groove -> groove(fraction, audio, accent, accent2, colors.textSecondary)
            BarStyle.Hairline -> hairline(fraction, audio, accent, colors.textSecondary)
            BarStyle.Tape -> {
                val position = fraction * duration
                tapeDeck(fraction, position / 1000f, audio, reels, tapePath, tapeTexts!!, state.isPlaying, accent, colors.textSecondary)
            }
        }
    }
    if (bar == BarStyle.Tape) return // its counter is part of the deck
    // The time text changes once a second: read the per-frame clock through derivedStateOf so only that recomposes.
    val shownSeconds by remember(state, duration) {
        derivedStateOf { (dragFraction?.let { (it * duration).toLong() } ?: state.positionAt(audio.now)) / 1000 }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(formatDuration(shownSeconds * 1000), style = CruiseTheme.typography.meta, color = if (dragFraction != null) accent else colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text("-" + formatDuration(duration - shownSeconds * 1000), style = CruiseTheme.typography.meta, color = colors.textSecondary)
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
    val r = 26f + 30f * audio.level
    drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.6f), Color.Transparent), center = Offset(x, size.height / 2), radius = r), radius = r, center = Offset(x, size.height / 2))
}

/**
 * Aurora (CHANGE_REQUESTS.md CR-07.2): the played part is a wave that swells with the music and keeps flowing; the
 * rest is a flat line; a round playhead joins them.
 */
private fun DrawScope.wavy(fraction: Float, audio: AudioLevels, a: Color, b: Color, idle: Color, head: Color, path: Path) {
    val mid = size.height / 2
    val x1 = size.width * fraction
    val stroke = 5.dp.toPx()
    drawLine(idle.copy(alpha = 0.32f), Offset(x1, mid), Offset(size.width, mid), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
    if (x1 > 1f) {
        val amp = size.height * 0.24f * (0.3f + 0.7f * (audio.level * 1.8f).coerceAtMost(1f))
        val wavelength = 38.dp.toPx()
        val phase = audio.time * 6.5f
        path.reset()
        var x = 0f
        val step = 3f
        while (x <= x1) {
            // Ease the wave in at the start and into the playhead, so both ends sit on the line.
            val ease = (x / (wavelength * 0.75f)).coerceAtMost(1f) * ((x1 - x) / (wavelength * 0.5f)).coerceIn(0f, 1f)
            val y = mid + amp * ease * sin(x / wavelength * 2f * PI.toFloat() - phase)
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += step
        }
        path.lineTo(x1, mid)
        drawPath(path, Brush.horizontalGradient(listOf(b, a), startX = 0f, endX = x1), style = Stroke(stroke, cap = StrokeCap.Round))
    }
    val r = 9.dp.toPx()
    drawCircle(Brush.radialGradient(listOf(a.copy(alpha = 0.55f), Color.Transparent), center = Offset(x1, mid), radius = r * (2.4f + audio.level)), radius = r * (2.4f + audio.level), center = Offset(x1, mid))
    drawCircle(head, radius = r, center = Offset(x1, mid))
    drawCircle(a, radius = r, center = Offset(x1, mid), style = Stroke(2.5.dp.toPx()))
}

/** Neon City (CR-07.4): a glass tube that fills with light as the song plays, glowing brighter with the music. */
private fun DrawScope.neonTube(fraction: Float, audio: AudioLevels, light: Color, glass: Color) {
    val mid = size.height / 2
    val tube = 14.dp.toPx()
    val inset = 10.dp.toPx()
    val start = inset
    val end = size.width - inset
    val x1 = start + (end - start) * fraction
    // The glass: faint body and outline, electrodes at both ends.
    drawRoundRect(glass.copy(alpha = 0.06f), Offset(start - tube / 2, mid - tube / 2), Size(end - start + tube, tube), CornerRadius(tube / 2))
    drawRoundRect(glass.copy(alpha = 0.28f), Offset(start - tube / 2, mid - tube / 2), Size(end - start + tube, tube), CornerRadius(tube / 2), style = Stroke(1.2.dp.toPx()))
    for (x in listOf(start - tube / 2 - 6.dp.toPx(), end + tube / 2)) drawRect(glass.copy(alpha = 0.5f), Offset(x, mid - 3.dp.toPx()), Size(6.dp.toPx(), 6.dp.toPx()))
    if (x1 > start) {
        val pulse = 0.8f + 0.2f * (audio.level * 2f).coerceAtMost(1f)
        val core = lerp(light, Color.White, 0.65f)
        for ((w, a) in listOf(tube * 2.2f to 0.10f, tube * 1.2f to 0.22f, tube * 0.55f to 0.65f)) {
            drawLine(light.copy(alpha = a * pulse), Offset(start, mid), Offset(x1, mid), strokeWidth = w, cap = StrokeCap.Round)
        }
        drawLine(core.copy(alpha = pulse), Offset(start, mid), Offset(x1, mid), strokeWidth = tube * 0.2f, cap = StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(core, light.copy(alpha = 0.4f), Color.Transparent), center = Offset(x1, mid), radius = tube * 1.6f), radius = tube * 1.6f, center = Offset(x1, mid))
    }
}

/** Sport dashboard LED meter: lit segments up to the position, a VU flicker just ahead of it, red zone at the end. */
private fun DrawScope.ledSegments(fraction: Float, audio: AudioLevels, normal: Color, hot: Color, off: Color) {
    val n = 48
    val gap = 4f
    val w = (size.width - gap * (n - 1)) / n
    val lit = (fraction * n).toInt()
    val vu = (audio.level * 10).toInt().coerceAtMost(8)
    for (i in 0 until n) {
        val x = i * (w + gap)
        val red = i >= n * 0.88f
        val on = i < lit
        val flicker = !on && i < lit + vu
        val c = when {
            on -> if (red) hot else normal
            flicker -> (if (red) hot else normal).copy(alpha = 0.35f)
            else -> off
        }
        drawRoundRect(c, Offset(x, 0f), Size(w, size.height), CornerRadius(3f))
        if (on) drawRoundRect(Color.White.copy(alpha = 0.18f), Offset(x, 0f), Size(w, size.height * 0.35f), CornerRadius(3f))
    }
}

/** Vinyl groove: fine line with a stylus that leaves a trail and swells with the music. */
private fun DrawScope.groove(fraction: Float, audio: AudioLevels, red: Color, teal: Color, idle: Color) {
    val mid = size.height / 2
    val x = size.width * fraction
    for (k in 0 until 3) drawLine(idle.copy(alpha = 0.18f), Offset(0f, mid - 6f + k * 6f), Offset(size.width, mid - 6f + k * 6f), strokeWidth = 1f)
    drawLine(Brush.horizontalGradient(listOf(teal.copy(alpha = 0.3f), red)), Offset(0f, mid), Offset(x, mid), strokeWidth = 4f + 8f * audio.level, cap = StrokeCap.Round)
    val wobble = sin(audio.time * PI.toFloat() * 2) * 2f
    drawCircle(red, radius = 10f + 6f * audio.level, center = Offset(x, mid + wobble))
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

// ---- Tape transport -------------------------------------------------------------------------------------------

/** Reel angles, advanced from frame to frame: the emptier a reel, the faster it turns (constant tape speed). */
private class Reels {
    var left = 0f
    var right = 40f
    var lastTime = -1f

    fun advance(time: Float, playing: Boolean, fraction: Float) {
        val dt = if (lastTime < 0f) 0f else (time - lastTime).coerceIn(0f, 0.2f)
        lastTime = time
        if (!playing) return
        left = (left + dt * 120f / (0.35f + 0.65f * (1f - fraction))) % 360f
        right = (right + dt * 120f / (0.35f + 0.65f * fraction)) % 360f
    }
}

/** The tape counter's digits and colon, measured once. */
private class TapeTexts(val digits: Array<TextLayoutResult>, val colon: TextLayoutResult)

@Composable
private fun rememberTapeTexts(): TapeTexts {
    val measurer = rememberTextMeasurer()
    val secondary = CruiseTheme.colors.textSecondary
    return remember(measurer, secondary) {
        val digit = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFFF3F6F8))
        TapeTexts(
            Array(10) { measurer.measure(it.toString(), digit) },
            measurer.measure(":", digit.copy(color = secondary, fontWeight = FontWeight.Normal)),
        )
    }
}

/** The tape's path, rebuilt only when the bar changes size, and a measure to cut the played part out of it. */
private class TapePath {
    val path = Path()
    val played = Path()
    val rest = Path()
    val measure = PathMeasure()
    private var builtFor = Size.Zero

    fun update(size: Size, left: Offset, right: Offset, radius: Float) {
        if (size == builtFor) return
        builtFor = size
        path.reset()
        // One closed loop round both reels, like a belt: across the top, round the right reel, back along the bottom,
        // round the left reel to where it started.
        path.moveTo(left.x, left.y - radius)
        path.lineTo(right.x, right.y - radius)
        path.arcTo(Rect(right, radius), -90f, 180f, false)
        path.lineTo(left.x, left.y + radius)
        path.arcTo(Rect(left, radius), 90f, 180f, false)
        path.close()
        measure.setPath(path, true)
    }
}

/**
 * Tape Deck (CR-07.6, fourth design after the owner's reviews on 2026-10-24): the tape itself is the progress bar — one
 * closed loop round both reels, like a belt; the part already played lights up in the skin's colour, going round the
 * whole loop from the top-left as the song plays, and visibly runs. No cursor: the lit tape is the position. The packs
 * inside the loop empty and fill, the hubs turn, and the mechanical tape counter sits in the centre.
 */
private fun DrawScope.tapeDeck(
    fraction: Float, seconds: Float, audio: AudioLevels, reels: Reels, tapePath: TapePath, texts: TapeTexts,
    playing: Boolean, teal: Color, idle: Color,
) {
    reels.advance(audio.time, playing, fraction)
    val w = size.width
    val h = size.height
    val radius = CornerRadius(h * 0.2f)
    // The panel: smoky glass with a lit top edge and four screws.
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF1E2631), Color(0xFF0C1016))), Offset.Zero, size, radius)
    drawRoundRect(Color.White.copy(alpha = 0.08f), Offset.Zero, size, radius, style = Stroke(1.5.dp.toPx()))
    for (p in listOf(Offset(h * 0.11f, h * 0.12f), Offset(w - h * 0.11f, h * 0.12f), Offset(h * 0.11f, h * 0.88f), Offset(w - h * 0.11f, h * 0.88f))) {
        drawCircle(Color(0xFF59636F), h * 0.022f, p)
        drawLine(Color(0xFF2A3038), p - Offset(h * 0.014f, 0f), p + Offset(h * 0.014f, 0f), 1.2f)
    }

    val r = h * 0.335f
    val track = r * 1.13f
    val pad = h * 0.12f + (track - r)
    val core = r * 0.38f
    val lc = Offset(pad + r, h * 0.5f)
    val rc = Offset(w - pad - r, h * 0.5f)
    val maxPack = r * 0.92f
    val lr = core + (maxPack - core) * (1f - fraction)
    val rr = core + (maxPack - core) * fraction
    val tape = Color(0xFF3A3F48)

    // Reels under the tape: pack, smoky flange, white hub with turning teeth.
    for ((c, packR, angle) in listOf(Triple(lc, lr, reels.left), Triple(rc, rr, reels.right))) {
        drawCircle(Color.Black.copy(alpha = 0.35f), r * 1.02f, c)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF3A3F48), Color(0xFF22262D)), center = c, radius = packR), packR, c)
        for (k in 1..4) drawCircle(Color.Black.copy(alpha = 0.22f), core + (packR - core) * k / 5f, c, style = Stroke(1f))
        drawCircle(Color.White.copy(alpha = 0.06f), r, c)
        rotate(angle, c) {
            for (k in 0 until 3) {
                val a = k * 120f + 30f
                drawArc(Color.White.copy(alpha = 0.10f), a, 46f, true, c - Offset(r * 0.86f, r * 0.86f), Size(r * 1.72f, r * 1.72f))
            }
        }
        drawCircle(Color(0xFFE9EEF3), core, c)
        rotate(angle, c) {
            for (k in 0 until 6) {
                val a = k * PI.toFloat() / 3f
                drawLine(Color(0xFF6B7480), Offset(c.x + cos(a) * core * 0.45f, c.y + sin(a) * core * 0.45f), Offset(c.x + cos(a) * core * 0.9f, c.y + sin(a) * core * 0.9f), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        drawCircle(Color(0xFF1A1F26), core * 0.3f, c)
    }

    // The tape, cut at the position: played part lit (with a soft glow and moving marks), the rest dark.
    tapePath.update(size, lc, rc, track)
    val total = tapePath.measure.length
    val at = total * fraction
    tapePath.played.reset()
    tapePath.rest.reset()
    tapePath.measure.getSegment(0f, at, tapePath.played, true)
    tapePath.measure.getSegment(at, total, tapePath.rest, true)
    val width = 4.dp.toPx()
    drawPath(tapePath.rest, tape, style = Stroke(width, cap = StrokeCap.Round))
    drawPath(tapePath.rest, idle.copy(alpha = 0.25f), style = Stroke(width * 0.35f, cap = StrokeCap.Round))
    if (at > 0f) {
        drawPath(tapePath.played, teal.copy(alpha = 0.22f), style = Stroke(width * 3f, cap = StrokeCap.Round))
        drawPath(tapePath.played, teal, style = Stroke(width, cap = StrokeCap.Round))
        val dash = 12.dp.toPx()
        drawPath(
            tapePath.played, Color.White.copy(alpha = 0.45f),
            style = Stroke(width * 0.45f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash * 0.15f, dash * 0.85f), -audio.time * 36f)),
        )
        // The lit end glows softly where the music is.
        val tip = tapePath.measure.getPosition(at)
        drawCircle(Brush.radialGradient(listOf(teal.copy(alpha = 0.7f), Color.Transparent), center = tip, radius = width * 3.5f), width * 3.5f, tip)
    }

    // Tape counter in the centre of the loop.
    val cell = Size(texts.digits[0].size.width + 8.dp.toPx(), h * 0.3f)
    val gap = 3.dp.toPx()
    val counterW = cell.width * 4 + texts.colon.size.width + gap * 4
    val origin = Offset((w - counterW) / 2, (h - cell.height) / 2)
    val frameTopLeft = origin - Offset(6.dp.toPx(), 4.dp.toPx())
    val frame = Size(counterW + 12.dp.toPx(), cell.height + 8.dp.toPx())
    drawRoundRect(Color(0xFF07090C), frameTopLeft, frame, CornerRadius(6.dp.toPx()))
    drawRoundRect(Color.White.copy(alpha = 0.10f), frameTopLeft, frame, CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
    counter(seconds, origin, cell, texts.digits, texts.colon, teal)
}

/** mm:ss on four digit wheels at [origin]. A wheel rolls during the last quarter-second before it changes. */
private fun DrawScope.counter(seconds: Float, origin: Offset, cell: Size, digits: Array<TextLayoutResult>, colon: TextLayoutResult, mark: Color) {
    val s = seconds.coerceAtLeast(0f)
    val total = s.toInt()
    val roll = ((s - total - 0.75f) / 0.25f).coerceIn(0f, 1f)
    // Each wheel: its digit, its base, and whether all lower wheels are about to wrap (then it rolls too).
    val sOnes = total % 10
    val sTens = (total / 10) % 6
    val mOnes = (total / 60) % 10
    val mTens = (total / 600) % 10
    val wheels = listOf(
        Triple(mTens, 10, sOnes == 9 && sTens == 5 && mOnes == 9),
        Triple(mOnes, 10, sOnes == 9 && sTens == 5),
        Triple(sTens, 6, sOnes == 9),
        Triple(sOnes, 10, true),
    )
    val gap = 3.dp.toPx()
    var x = origin.x
    wheels.forEachIndexed { i, (digit, base, rolls) ->
        if (i == 2) {
            drawText(colon, topLeft = Offset(x, origin.y + (cell.height - colon.size.height) / 2))
            x += colon.size.width + gap
        }
        val box = Rect(Offset(x, origin.y), cell)
        drawRoundRect(Color(0xFF12161C), box.topLeft, box.size, CornerRadius(3.dp.toPx()))
        clipRect(box.left, box.top, box.right, box.bottom) {
            val r = if (rolls) roll else 0f
            val cur = digits[digit]
            val next = digits[(digit + 1) % base]
            val baseY = box.top + (cell.height - cur.size.height) / 2
            drawText(cur, topLeft = Offset(box.left + (cell.width - cur.size.width) / 2, baseY - r * cell.height))
            if (r > 0f) drawText(next, topLeft = Offset(box.left + (cell.width - next.size.width) / 2, baseY + (1f - r) * cell.height))
        }
        // Glass shading over the wheel, and a thin index mark.
        drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.6f)), startY = box.top, endY = box.bottom), box.topLeft, box.size, CornerRadius(3.dp.toPx()))
        drawLine(mark.copy(alpha = 0.6f), Offset(box.left + 2f, box.bottom - 1f), Offset(box.right - 2f, box.bottom - 1f), 1.dp.toPx())
        x += cell.width + gap
    }
}
