package com.boulbeba.cruise.feature.nowplaying

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import com.boulbeba.cruise.core.playback.PlayerUiState
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Covers drawn as objects (owner's request 2026-10-23, inspired by "song stickers"): a sleeve with its record, a
// living liquid shape, a die-cut sticker and a cassette. Everything is plain Canvas drawing — no blur or shaders —
// so it stays smooth on the owner's 4×Cortex-A7 unit; motion stops when the music is paused.

/** The cover image, or a generated one (gradient + initial) for files without art. Never null, for Canvas styles. */
@Composable
internal fun rememberCoverBitmap(image: ImageBitmap?, state: PlayerUiState): ImageBitmap {
    val seed = state.album.ifBlank { state.path ?: "" }
    val label = state.album.ifBlank { state.title }
    return image ?: remember(seed, label) { generatedCover(seed, label) }
}

private fun generatedCover(seed: String, label: String): ImageBitmap {
    val hue = Math.floorMod((File(seed).parent ?: seed).hashCode(), 360).toFloat()
    val a = Color.hsv(hue, 0.55f, 0.62f).toArgb()
    val b = Color.hsv((hue + 48f) % 360f, 0.65f, 0.24f).toArgb()
    val bmp = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 384f, 384f, a, b, Shader.TileMode.CLAMP) })
    val text = label.trim().take(1).uppercase(Locale.ROOT).ifEmpty { "♪" }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(220, 255, 255, 255); textSize = 190f; textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    c.drawText(text, 192f, 192f - (paint.descent() + paint.ascent()) / 2, paint)
    return bmp.asImageBitmap()
}

/** Draws [image] center-cropped into [dst]. */
private fun DrawScope.drawCropped(image: ImageBitmap, dst: Rect, alpha: Float = 1f) {
    val scale = maxOf(dst.width / image.width, dst.height / image.height)
    val sw = (dst.width / scale).toInt().coerceIn(1, image.width)
    val sh = (dst.height / scale).toInt().coerceIn(1, image.height)
    drawImage(
        image,
        srcOffset = IntOffset((image.width - sw) / 2, (image.height - sh) / 2), srcSize = IntSize(sw, sh),
        dstOffset = IntOffset(dst.left.toInt(), dst.top.toInt()), dstSize = IntSize(dst.width.toInt(), dst.height.toInt()),
        alpha = alpha,
    )
}

private fun roundRectPath(rect: Rect, radius: Float) = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(radius))) }

/** Holographic foil: an iridescent band sliding diagonally across [rect]. */
private fun DrawScope.foil(rect: Rect, radius: Float, t: Float, strength: Float = 1f) {
    val span = rect.width * 1.6f
    val x = rect.left - rect.width * 0.6f + ((t * 0.12f) % 1f) * span
    val brush = Brush.linearGradient(
        listOf(
            Color.Transparent, Color(0xFFFF0080).copy(alpha = 0.20f * strength), Color(0xFF00FFFF).copy(alpha = 0.24f * strength),
            Color(0xFFFFFF00).copy(alpha = 0.16f * strength), Color(0xFF8000FF).copy(alpha = 0.20f * strength), Color.Transparent,
        ),
        start = Offset(x, rect.top), end = Offset(x + rect.width * 0.7f, rect.bottom),
    )
    drawRoundRect(brush, rect.topLeft, rect.size, CornerRadius(radius))
}

/** Rotation that advances at [degreesPerSecond] while [running]; keeps its angle when paused. */
@Composable
internal fun rememberSpin(running: Boolean, degreesPerSecond: Float): Float {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, degreesPerSecond) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            angle = (angle + (now - last) / 1e9f * degreesPerSecond) % 360f
            last = now
        }
    }
    return angle
}

// ---- Sleeve ----------------------------------------------------------------------------------------------------

/** Album sleeve with holographic foil; its record slides out and spins while the music plays. */
@Composable
internal fun SleeveCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, accent2: Color, size: Dp, modifier: Modifier) {
    val art = rememberCoverBitmap(image, state)
    val moving = CruiseTheme.quality != VisualQuality.Saver
    val out by animateFloatAsState(if (state.isPlaying) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessVeryLow), label = "discOut")
    val spin = rememberSpin(state.isPlaying && moving, 200f)
    Canvas(modifier.size(size)) {
        audio.frame
        val s = this.size.minDimension
        val sleeve = s * 0.64f
        val sleeveRect = Rect(Offset(s * 0.02f, (s - sleeve) / 2), Size(sleeve, sleeve))
        val radius = s * 0.035f
        // Glow under everything, breathing with the music.
        val glowR = sleeve * (0.78f + 0.08f * audio.level)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.30f + 0.25f * audio.level), accent2.copy(alpha = 0.10f), Color.Transparent), center = sleeveRect.center, radius = glowR), glowR, sleeveRect.center)

        // The record, behind the sleeve.
        val discR = sleeve * 0.47f
        val discC = Offset(sleeveRect.center.x + s * (0.04f + 0.31f * out), sleeveRect.center.y)
        drawCircle(Color(0xFF0E0E12), discR, discC)
        for (k in 0 until 22) drawCircle(Color.White.copy(alpha = if (k % 4 == 0) 0.07f else 0.03f), discR * (0.40f + k * 0.027f), discC, style = Stroke(1.2f))
        rotate(spin, discC) {
            drawArc(
                Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent), discC),
                0f, 360f, true, Offset(discC.x - discR, discC.y - discR), Size(discR * 2, discR * 2),
            )
            val labelR = discR * 0.36f
            clipPath(Path().apply { addOval(Rect(discC, labelR)) }) { drawCropped(art, Rect(discC, labelR)) }
            drawCircle(Color.White.copy(alpha = 0.35f), labelR, discC, style = Stroke(2f))
        }
        drawCircle(Color(0xFF0E0E12), discR * 0.04f, discC)

        // The sleeve: drop shadow, art, foil, gloss, die-cut edge.
        for (i in 1..3) drawRoundRect(Color.Black.copy(alpha = 0.12f), sleeveRect.topLeft + Offset(i * 3f, i * 5f), sleeveRect.size, CornerRadius(radius))
        clipPath(roundRectPath(sleeveRect, radius)) {
            drawCropped(art, sleeveRect)
            if (moving) foil(sleeveRect, radius, audio.time, 0.8f + 0.6f * audio.level)
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), startY = sleeveRect.top, endY = sleeveRect.top + sleeve * 0.4f), sleeveRect.topLeft, sleeveRect.size)
        }
        drawRoundRect(Color.White.copy(alpha = 0.35f), sleeveRect.topLeft, sleeveRect.size, CornerRadius(radius), style = Stroke(2f))
    }
}

// ---- Liquid ----------------------------------------------------------------------------------------------------

private fun blobPath(center: Offset, radius: Float, t: Float, seed: Float, bands: FloatArray?, wobble: Float): Path {
    val n = 10
    val pts = Array(n) { i ->
        val a = (i.toFloat() / n) * 2f * PI.toFloat()
        val band = bands?.let { it[(i * it.size / n / 2).coerceIn(0, it.lastIndex)] } ?: 0f
        val r = radius * (1f + wobble * (0.055f * sin(t * 1.1f + i * 1.7f + seed) + 0.04f * sin(t * 0.63f + i * 2.9f + seed * 2f)) + 0.09f * band)
        Offset(center.x + cos(a) * r, center.y + sin(a) * r)
    }
    // Closed Catmull–Rom spline through the points.
    return Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 0 until n) {
            val p0 = pts[(i - 1 + n) % n]; val p1 = pts[i]; val p2 = pts[(i + 1) % n]; val p3 = pts[(i + 2) % n]
            cubicTo(
                p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                p2.x, p2.y,
            )
        }
        close()
    }
}

/** The cover inside a living blob; gooey colour blobs orbit behind it and the edge ripples with the spectrum. */
@Composable
internal fun LiquidCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, accent2: Color, size: Dp, modifier: Modifier) {
    val art = rememberCoverBitmap(image, state)
    val reactive = CruiseTheme.quality != VisualQuality.Saver
    Canvas(modifier.size(size)) {
        audio.frame
        val s = this.size.minDimension
        val c = center
        val t = audio.time
        val speed = if (state.isPlaying) 1f else 0.25f
        // Orbiting colour blobs.
        listOf(Triple(accent, 0f, 0.34f), Triple(accent2, 2.1f, 0.30f), Triple(Color.White.copy(alpha = 0.6f), 4.2f, 0.18f)).forEach { (col, phase, r) ->
            val o = Offset(c.x + cos(t * 0.35f * speed + phase) * s * 0.10f, c.y + sin(t * 0.42f * speed + phase) * s * 0.09f)
            drawPath(blobPath(o, s * r, t * speed, phase, null, 1.6f), Brush.radialGradient(listOf(col.copy(alpha = 0.55f), col.copy(alpha = 0.0f)), center = o, radius = s * r * 1.15f))
        }
        val radius = s * 0.37f
        val shape = blobPath(c, radius, t * speed, 0.7f, if (reactive) audio.bands else null, 1f)
        clipPath(shape) {
            drawCropped(art, Rect(c, radius * 1.25f))
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), start = Offset(c.x - radius, c.y - radius), end = c), Offset(c.x - radius * 1.3f, c.y - radius * 1.3f), Size(radius * 2.6f, radius * 2.6f))
        }
        drawPath(shape, Brush.sweepGradient(listOf(accent, accent2, accent), c), style = Stroke(3f + 3f * audio.level))
    }
}

// ---- Sticker ---------------------------------------------------------------------------------------------------

/** White die-cut sticker that tilts gently in 3D, with a foil sweep and a corner that peels up on the beat. */
@Composable
internal fun StickerCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, size: Dp, modifier: Modifier) {
    val art = rememberCoverBitmap(image, state)
    val moving = CruiseTheme.quality != VisualQuality.Saver
    val background = CruiseTheme.colors.background
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size * 0.82f).graphicsLayer {
                val t = audio.time
                if (moving) {
                    rotationX = 5f * sin(t * 0.55f)
                    rotationY = 7f * sin(t * 0.41f + 1f)
                    rotationZ = -2f + 1.2f * sin(t * 0.3f)
                }
                cameraDistance = 14f * density
                val beat = 1f + 0.018f * audio.level
                scaleX = beat; scaleY = beat
            },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                audio.frame
                val w = this.size.width
                val h = this.size.height
                val radius = w * 0.10f
                for (i in 1..4) drawRoundRect(Color.Black.copy(alpha = 0.06f), Offset(i * 2f, i * 5f), this.size, CornerRadius(radius))
                drawRoundRect(accent.copy(alpha = 0.18f + 0.2f * audio.level), Offset(-6f, -6f), Size(w + 12f, h + 12f), CornerRadius(radius + 6f))
                drawRoundRect(Color.White, Offset.Zero, this.size, CornerRadius(radius))
                val inner = Rect(Offset(w * 0.05f, h * 0.05f), Size(w * 0.90f, h * 0.90f))
                clipPath(roundRectPath(inner, radius * 0.7f)) {
                    drawCropped(art, inner)
                    if (moving) foil(inner, radius, audio.time, 1.2f)
                }
                // Peel: the corner lifts, showing the sticker's back and the surface under it.
                val lift = w * (0.11f + 0.05f * audio.level)
                val cut = Path().apply { moveTo(w - lift, h); lineTo(w, h - lift); lineTo(w, h); close() }
                drawPath(cut, background)
                fun flap(dx: Float, dy: Float) = Path().apply {
                    moveTo(w - lift + dx, h + dy); lineTo(w + dx, h - lift + dy); lineTo(w - lift * 0.92f + dx, h - lift * 0.92f + dy); close()
                }
                drawPath(flap(2f, 3f), Color.Black.copy(alpha = 0.18f))
                drawPath(flap(0f, 0f), Brush.linearGradient(listOf(Color(0xFFE9ECF1), Color(0xFFC3C9D3)), start = Offset(w - lift, h - lift), end = Offset(w, h)))
            }
        }
    }
}

// ---- Cassette --------------------------------------------------------------------------------------------------

/**
 * A cassette: the album art is the label, the tape winds from the left reel to the right one as the song plays, and
 * each reel turns faster the less tape it holds — like a real deck.
 */
@Composable
internal fun CassetteCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, accent2: Color, size: Dp, modifier: Modifier) {
    val art = rememberCoverBitmap(image, state)
    val text = rememberTextMeasurer()
    val colors = CruiseTheme.colors
    val title = state.title
    val artist = state.artist
    var leftAngle by remember { mutableFloatStateOf(0f) }
    var rightAngle by remember { mutableFloatStateOf(30f) }
    val running = state.isPlaying && CruiseTheme.quality != VisualQuality.Saver
    val progressNow = { if (state.durationMs > 0) (state.positionAt(android.os.SystemClock.elapsedRealtime()).toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            val dt = (now - last) / 1e9f
            last = now
            val p = progressNow()
            // Tape speed is constant, so angular speed ∝ 1 / pack radius.
            leftAngle = (leftAngle + dt * 90f / (0.35f + 0.65f * (1f - p))) % 360f
            rightAngle = (rightAngle + dt * 90f / (0.35f + 0.65f * p)) % 360f
        }
    }
    Canvas(modifier.size(size)) {
        audio.frame
        val s = this.size.minDimension
        val w = s * 0.98f
        val h = w * 0.64f
        val o = Offset((this.size.width - w) / 2, (this.size.height - h) / 2)
        val body = Rect(o, Size(w, h))
        val r = w * 0.045f
        // Shell.
        for (i in 1..3) drawRoundRect(Color.Black.copy(alpha = 0.14f), body.topLeft + Offset(i * 2f, i * 4f), body.size, CornerRadius(r))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF34302B), Color(0xFF1A1714)), startY = body.top, endY = body.bottom), body.topLeft, body.size, CornerRadius(r))
        drawRoundRect(Color.White.copy(alpha = 0.10f), body.topLeft, body.size, CornerRadius(r), style = Stroke(2f))
        listOf(Offset(0.04f, 0.06f), Offset(0.96f, 0.06f), Offset(0.04f, 0.94f), Offset(0.96f, 0.94f), Offset(0.5f, 0.94f)).forEach { f ->
            val p = Offset(body.left + w * f.x, body.top + h * f.y)
            drawCircle(Color(0xFF8F8A82), w * 0.014f, p)
            drawLine(Color(0xFF4A4640), p - Offset(w * 0.009f, 0f), p + Offset(w * 0.009f, 0f), 2f)
        }
        // Label: the album art fills it; a cream band carries the title, stripes run along the bottom.
        val label = Rect(Offset(body.left + w * 0.07f, body.top + h * 0.09f), Size(w * 0.86f, h * 0.56f))
        clipPath(roundRectPath(label, r * 0.6f)) {
            drawCropped(art, label)
            drawRect(Color(0xFFF3E9D6), label.topLeft, Size(label.width, label.height * 0.30f))
            listOf(Color(0xFFFFC145), accent, Color(0xFFE8503A), accent2).forEachIndexed { i, c ->
                drawRect(c, Offset(label.left, label.bottom - h * 0.10f + i * h * 0.025f), Size(label.width, h * 0.025f))
            }
        }
        val textLeft = label.left + w * 0.03f
        val sideLayout = text.measure("A", TextStyle(color = accent, fontSize = (s * 0.062f).toSp(), fontWeight = FontWeight.Bold))
        val textWidth = (label.width - sideLayout.size.width - w * 0.10f).toInt().coerceAtLeast(1)
        val line = "$title  ·  $artist"
        val titleLayout = text.measure(line, TextStyle(color = Color(0xFF1C1712), fontSize = (s * 0.044f).toSp(), fontWeight = FontWeight.Bold), overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = textWidth))
        val band = label.height * 0.30f
        drawText(titleLayout, topLeft = Offset(textLeft, label.top + (band - titleLayout.size.height) / 2))
        drawText(sideLayout, topLeft = Offset(label.right - sideLayout.size.width - w * 0.03f, label.top + (band - sideLayout.size.height) / 2))
        // VU strip in the band, next to the side letter.
        val vu = (audio.level * 14).toInt().coerceAtMost(8)
        val vuRight = label.right - sideLayout.size.width - w * 0.05f
        for (i in 0 until 8) drawRect(if (i < vu) (if (i > 5) Color(0xFFE8503A) else accent2) else Color(0x22000000), Offset(vuRight - (8 - i) * w * 0.018f, label.top + band * 0.38f), Size(w * 0.012f, band * 0.26f))

        // Window with the tape packs and reels.
        val win = Rect(Offset(body.left + w * 0.24f, label.top + label.height * 0.38f), Size(w * 0.52f, h * 0.22f))
        val p = progressNow()
        val core = w * 0.045f
        val maxPack = w * 0.13f
        val lc = Offset(win.left + win.width * 0.24f, win.center.y)
        val rc = Offset(win.right - win.width * 0.24f, win.center.y)
        val lr = core + (maxPack - core) * (1f - p)
        val rr = core + (maxPack - core) * p
        clipPath(roundRectPath(win, win.height * 0.3f)) {
            drawRect(Color(0xFF0D0B09), win.topLeft, win.size)
            drawCircle(Color(0xFF4A3426), lr, lc)
            drawCircle(Color(0xFF4A3426), rr, rc)
            for (k in 1..4) {
                drawCircle(Color.Black.copy(alpha = 0.18f), core + (lr - core) * k / 5f, lc, style = Stroke(1f))
                drawCircle(Color.Black.copy(alpha = 0.18f), core + (rr - core) * k / 5f, rc, style = Stroke(1f))
            }
        }
        listOf(lc to leftAngle, rc to rightAngle).forEach { (c, a) ->
            drawCircle(Color(0xFFEDE6DA), core, c)
            rotate(a, c) {
                for (k in 0 until 6) {
                    val ang = k * PI.toFloat() / 3f
                    drawLine(Color(0xFF6B655C), c, Offset(c.x + cos(ang) * core * 0.78f, c.y + sin(ang) * core * 0.78f), 3f, StrokeCap.Round)
                }
            }
            drawCircle(Color(0xFF1A1714), core * 0.35f, c)
        }
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), startY = win.top, endY = win.bottom), win.topLeft, win.size, CornerRadius(win.height * 0.3f))
        drawRoundRect(Color.White.copy(alpha = 0.25f), win.topLeft, win.size, CornerRadius(win.height * 0.3f), style = Stroke(2f))

        // Head area: trapezoid with the tape running across it.
        val top = body.bottom - h * 0.20f
        val trap = Path().apply {
            moveTo(body.left + w * 0.18f, body.bottom); lineTo(body.left + w * 0.24f, top)
            lineTo(body.right - w * 0.24f, top); lineTo(body.right - w * 0.18f, body.bottom); close()
        }
        drawPath(trap, Color(0xFF221E1A))
        drawLine(Color(0xFF4A3426), Offset(body.left + w * 0.22f, body.bottom - h * 0.05f), Offset(body.right - w * 0.22f, body.bottom - h * 0.05f), 4f)
        listOf(0.31f, 0.69f).forEach { f -> drawCircle(Color(0xFF0D0B09), w * 0.018f, Offset(body.left + w * f, top + h * 0.08f)) }
        drawRect(Color(0xFF0D0B09), Offset(body.left + w * 0.46f, top + h * 0.05f), Size(w * 0.08f, h * 0.07f))
        if (!colors.isDark) drawRoundRect(Color.Black.copy(alpha = 0.05f), body.topLeft, body.size, CornerRadius(r))
    }
}
