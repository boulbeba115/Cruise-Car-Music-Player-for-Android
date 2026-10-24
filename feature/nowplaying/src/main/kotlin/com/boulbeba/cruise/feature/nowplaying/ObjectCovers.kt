package com.boulbeba.cruise.feature.nowplaying

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import com.boulbeba.cruise.core.playback.PlayerUiState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Covers drawn as objects (owner's request 2026-10-23, inspired by "song stickers"): a sleeve with its record, a
// living liquid shape, a die-cut sticker and a cassette. Plain Canvas drawing; the only soft shadows are blurred once
// into a bitmap per size, so every frame stays cheap on the owner's 4×Cortex-A7 unit. Motion stops on pause.

/**
 * The cover for Canvas styles: the track's art or its folder's placeholder (both decoded by [rememberArtwork]); a plain
 * tile in the skin's surface colour for the moment before either is ready.
 */
@Composable
internal fun rememberCoverBitmap(image: ImageBitmap?, @Suppress("UNUSED_PARAMETER") state: PlayerUiState): ImageBitmap {
    val surface = CruiseTheme.colors.surfaceElevated
    return image ?: remember(surface) {
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(surface.toArgb()) }.asImageBitmap()
    }
}

/** Draws [image] center-cropped into [dst]. */
internal fun DrawScope.drawCropped(image: ImageBitmap, dst: Rect, alpha: Float = 1f) {
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

/**
 * A soft shadow for a rounded rectangle, blurred once into a bitmap ([BlurMaskFilter] works in a software canvas on
 * every Android version). The bitmap is [pad] larger on each side than the canvas; draw it at (−pad, −pad).
 */
private class SoftShadow(canvas: Size, rect: Rect, radius: Float, blur: Float, alpha: Float) {
    val pad = blur * 2
    val image: ImageBitmap = run {
        val w = (canvas.width + pad * 2).roundToInt().coerceAtLeast(1)
        val h = (canvas.height + pad * 2).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((alpha * 255).toInt(), 0, 0, 0)
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        android.graphics.Canvas(bmp).drawRoundRect(RectF(rect.left + pad, rect.top + pad, rect.right + pad, rect.bottom + pad), radius, radius, paint)
        bmp.asImageBitmap()
    }

    fun DrawScope.draw() = drawImage(image, topLeft = Offset(-pad, -pad))
}

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

/** Rotation that advances at [degreesPerSecond] while [running]; keeps its angle when paused. Read it while drawing. */
@Composable
internal fun rememberSpin(running: Boolean, degreesPerSecond: Float): State<Float> {
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, degreesPerSecond) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            angle.floatValue = (angle.floatValue + (now - last) / 1e9f * degreesPerSecond) % 360f
            last = now
        }
    }
    return angle
}

/** Grooves and the fixed light on a black record of radius [r] at [c]. */
private fun DrawScope.blackRecord(c: Offset, r: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFF1A1A20), Color(0xFF09090C)), center = c, radius = r), r, c)
    var k = 0
    var rr = r * 0.40f
    while (rr < r * 0.97f) {
        drawCircle(Color.White.copy(alpha = if (k % 7 == 6) 0.09f else 0.035f), rr, c, style = Stroke(1f))
        rr += r * 0.016f
        k++
    }
    // The reflection stays where the light is; only the label shows the record turning.
    drawCircle(
        Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Transparent), c),
        r, c,
    )
}

// ---- Sleeve (Midnight) -------------------------------------------------------------------------------------------

/**
 * Album sleeve with holographic foil (CR-07.3): soft shadow, a bevel and the open spine on the left edge, a glossy
 * highlight; its record slides out and turns while the music plays, under a reflection that stays put.
 */
@Composable
internal fun SleeveCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, accent2: Color, size: Dp, modifier: Modifier) {
    val art = rememberCoverBitmap(image, state)
    val moving = CruiseTheme.quality != VisualQuality.Saver
    val out = animateFloatAsState(if (state.isPlaying) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessVeryLow), label = "discOut")
    val spin = rememberSpin(state.isPlaying && moving, 200f)
    Spacer(
        modifier.size(size).drawWithCache {
            val s = this.size.minDimension
            val sleeve = s * 0.68f
            val sleeveRect = Rect(Offset(s * 0.01f, (s - sleeve) / 2), Size(sleeve, sleeve))
            val radius = s * 0.022f
            val shadow = SoftShadow(this.size, sleeveRect.translate(Offset(s * 0.012f, s * 0.03f)), radius, s * 0.035f, 0.6f)
            val clip = roundRectPath(sleeveRect, radius)
            onDrawBehind {
                audio.frame
                val level = audio.level
                // Glow under everything, breathing with the music.
                val glowR = sleeve * (0.8f + 0.08f * level)
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f + 0.22f * level), accent2.copy(alpha = 0.10f), Color.Transparent), center = sleeveRect.center, radius = glowR), glowR, sleeveRect.center)

                // The record, behind the sleeve.
                val discR = sleeve * 0.47f
                val discC = Offset(sleeveRect.center.x + s * (0.04f + 0.27f * out.value), sleeveRect.center.y)
                drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent), center = discC + Offset(0f, s * 0.02f), radius = discR * 1.12f), discR * 1.12f, discC + Offset(0f, s * 0.02f))
                blackRecord(discC, discR)
                rotate(spin.value, discC) {
                    val labelR = discR * 0.34f
                    clipPath(Path().apply { addOval(Rect(discC, labelR)) }) { drawCropped(art, Rect(discC, labelR)) }
                    drawCircle(Color.White.copy(alpha = 0.4f), labelR, discC, style = Stroke(2f))
                }
                drawCircle(Color(0xFFCFD3DA), discR * 0.035f, discC)

                // The sleeve.
                with(shadow) { draw() }
                clipPath(clip) {
                    drawCropped(art, sleeveRect)
                    if (moving) foil(sleeveRect, radius, audio.time, 0.7f + 0.5f * level)
                    // Gloss from the top-left, and the open spine on the left edge.
                    drawRect(
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent), start = sleeveRect.topLeft, end = sleeveRect.center),
                        sleeveRect.topLeft, sleeveRect.size,
                    )
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), startX = sleeveRect.left, endX = sleeveRect.left + sleeve * 0.05f),
                        sleeveRect.topLeft, Size(sleeve * 0.05f, sleeve),
                    )
                }
                // Bevel: light on the top and left edges, shade on the bottom and right.
                val bevel = s * 0.004f
                drawLine(Color.White.copy(alpha = 0.45f), Offset(sleeveRect.left + radius, sleeveRect.top + bevel / 2), Offset(sleeveRect.right - radius, sleeveRect.top + bevel / 2), bevel)
                drawLine(Color.White.copy(alpha = 0.25f), Offset(sleeveRect.left + bevel / 2, sleeveRect.top + radius), Offset(sleeveRect.left + bevel / 2, sleeveRect.bottom - radius), bevel)
                drawLine(Color.Black.copy(alpha = 0.45f), Offset(sleeveRect.left + radius, sleeveRect.bottom - bevel / 2), Offset(sleeveRect.right - radius, sleeveRect.bottom - bevel / 2), bevel)
                drawLine(Color.Black.copy(alpha = 0.35f), Offset(sleeveRect.right - bevel / 2, sleeveRect.top + radius), Offset(sleeveRect.right - bevel / 2, sleeveRect.bottom - radius), bevel)
                drawRoundRect(Color.White.copy(alpha = 0.18f), sleeveRect.topLeft, sleeveRect.size, CornerRadius(radius), style = Stroke(1.5f))
            }
        },
    )
}

// ---- Liquid (Aurora) ---------------------------------------------------------------------------------------------

/**
 * Fills [path] with a closed blob around [center]. Besides its slow idle wobble the edge follows the music
 * (CR-07.2): the bass makes the whole shape pulse, the mids and highs send ripples running round the edge, and each
 * point also follows its own spectrum band.
 */
private fun blobPath(path: Path, center: Offset, radius: Float, t: Float, seed: Float, wobble: Float, music: Music?) {
    val n = 18
    val pts = Array(n) { i ->
        val a = (i.toFloat() / n) * 2f * PI.toFloat()
        var k = 1f + wobble * (0.05f * sin(t * 1.1f + i * 1.7f + seed) + 0.035f * sin(t * 0.63f + i * 2.9f + seed * 2f))
        if (music != null) {
            k += 0.10f * music.bass +
                0.11f * music.mid * sin(3f * a - t * 2.6f) +
                0.07f * music.high * sin(5f * a + t * 3.4f) +
                0.12f * music.bands[(i * music.bands.size / n).coerceIn(0, music.bands.lastIndex)]
        }
        Offset(center.x + cos(a) * radius * k, center.y + sin(a) * radius * k)
    }
    // Closed Catmull–Rom spline through the points.
    path.reset()
    path.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until n) {
        val p0 = pts[(i - 1 + n) % n]; val p1 = pts[i]; val p2 = pts[(i + 1) % n]; val p3 = pts[(i + 2) % n]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    path.close()
}

/** The music split for the blob: bass, mids, highs and the raw bands. */
private class Music(val bands: FloatArray) {
    var bass = 0f
    var mid = 0f
    var high = 0f

    fun update() {
        fun avg(from: Int, to: Int) = (from until to).sumOf { bands[it].toDouble() }.toFloat() / (to - from)
        val n = bands.size
        bass = avg(0, n / 8)
        mid = avg(n / 8, n / 2)
        high = avg(n / 2, n)
    }
}

/** The cover inside a living blob; colour blobs orbit behind it, faster and bigger as the music gets louder. */
@Composable
internal fun LiquidCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, accent2: Color, size: Dp, modifier: Modifier) {
    val art = rememberCoverBitmap(image, state)
    val reactive = CruiseTheme.quality != VisualQuality.Saver
    val paths = remember { Array(4) { Path() } }
    val music = remember(audio) { Music(audio.bands) }
    Canvas(modifier.size(size)) {
        audio.frame
        music.update()
        val s = this.size.minDimension
        val c = center
        val t = audio.time
        val speed = if (state.isPlaying) 1f else 0.25f
        val level = audio.level
        val m = if (reactive) music else null
        // Orbiting colour blobs.
        listOf(Triple(accent, 0f, 0.34f), Triple(accent2, 2.1f, 0.30f), Triple(Color.White.copy(alpha = 0.6f), 4.2f, 0.18f)).forEachIndexed { i, (col, phase, r) ->
            val reach = s * (0.10f + 0.06f * level)
            val o = Offset(c.x + cos(t * 0.35f * speed * (1f + level) + phase) * reach, c.y + sin(t * 0.42f * speed * (1f + level) + phase) * reach * 0.9f)
            val rr = s * r * (1f + 0.25f * music.bass)
            blobPath(paths[i + 1], o, rr, t * speed, phase, 1.6f, null)
            drawPath(paths[i + 1], Brush.radialGradient(listOf(col.copy(alpha = 0.55f), col.copy(alpha = 0.0f)), center = o, radius = rr * 1.15f))
        }
        val radius = s * 0.35f
        val shape = paths[0]
        blobPath(shape, c, radius, t * speed, 0.7f, 1f, m)
        clipPath(shape) {
            drawCropped(art, Rect(c, radius * 1.35f))
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), start = Offset(c.x - radius, c.y - radius), end = c), Offset(c.x - radius * 1.4f, c.y - radius * 1.4f), Size(radius * 2.8f, radius * 2.8f))
        }
        // Rim: a soft glow that swells with the music, then the coloured edge.
        drawPath(shape, accent.copy(alpha = 0.18f + 0.25f * level), style = Stroke(10f + 22f * level))
        drawPath(shape, Brush.sweepGradient(listOf(accent, accent2, accent), c), style = Stroke(3f + 5f * level))
    }
}

// ---- Sticker (Daylight) ------------------------------------------------------------------------------------------

/**
 * White die-cut sticker (CR-07.3): a thick white border, a soft contact shadow, a gentle 3D tilt, a foil sweep and a
 * corner peeled up.
 */
@Composable
internal fun StickerCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, size: Dp, modifier: Modifier) {
    val art = rememberCoverBitmap(image, state)
    val moving = CruiseTheme.quality != VisualQuality.Saver
    val background = CruiseTheme.colors.background
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Spacer(
            Modifier
                .size(size * 0.86f)
                .graphicsLayer {
                    val t = audio.time
                    if (moving) {
                        rotationX = 4f * sin(t * 0.55f)
                        rotationY = 6f * sin(t * 0.41f + 1f)
                        rotationZ = -2f + 1f * sin(t * 0.3f)
                    }
                    cameraDistance = 14f * density
                    val beat = 1f + 0.012f * audio.level
                    scaleX = beat; scaleY = beat
                }
                .drawWithCache {
                    val w = this.size.width
                    val h = this.size.height
                    val radius = w * 0.09f
                    val shadow = SoftShadow(this.size, Rect(Offset(w * 0.03f, h * 0.05f), Size(w * 0.94f, h * 0.97f)), radius, w * 0.04f, 0.32f)
                    val inner = Rect(Offset(w * 0.07f, h * 0.07f), Size(w * 0.86f, h * 0.86f))
                    val innerClip = roundRectPath(inner, radius * 0.6f)
                    onDrawBehind {
                        audio.frame
                        with(shadow) { draw() }
                        drawRoundRect(accent.copy(alpha = 0.10f + 0.12f * audio.level), Offset(-5f, -5f), Size(w + 10f, h + 10f), CornerRadius(radius + 5f))
                        drawRoundRect(Color.White, Offset.Zero, this.size, CornerRadius(radius))
                        drawRoundRect(Color(0xFFD5DBE3), Offset.Zero, this.size, CornerRadius(radius), style = Stroke(1.5f))
                        clipPath(innerClip) {
                            drawCropped(art, inner)
                            if (moving) foil(inner, radius, audio.time, 1.1f)
                        }
                        // Peel: the corner curls up, showing the sticker's back and the surface under it.
                        val lift = w * 0.12f
                        val cut = Path().apply { moveTo(w - lift, h); lineTo(w, h - lift); lineTo(w, h); close() }
                        drawPath(cut, background)
                        fun flap(dx: Float, dy: Float) = Path().apply {
                            moveTo(w - lift + dx, h + dy); lineTo(w + dx, h - lift + dy)
                            quadraticTo(w - lift * 0.55f + dx, h - lift * 0.55f + dy, w - lift + dx, h + dy)
                            close()
                        }
                        drawPath(flap(3f, 4f), Color.Black.copy(alpha = 0.16f))
                        drawPath(flap(0f, 0f), Brush.linearGradient(listOf(Color(0xFFF4F6F9), Color(0xFFBCC4CF)), start = Offset(w - lift, h - lift), end = Offset(w, h)))
                    }
                },
        )
    }
}

// ---- Cassette (Tape Deck) ----------------------------------------------------------------------------------------

/**
 * A chrome cassette (CR-07.6): smoky translucent shell, the album art is the label, the tape winds from the left reel
 * to the right one as the song plays, and each reel turns faster the less tape it holds — like a real deck.
 */
@Composable
internal fun CassetteCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, accent2: Color, size: Dp, modifier: Modifier) {
    val art = rememberCoverBitmap(image, state)
    val text = rememberTextMeasurer()
    val title = state.title
    val artist = state.artist
    val leftAngle = remember { mutableFloatStateOf(0f) }
    val rightAngle = remember { mutableFloatStateOf(30f) }
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
            leftAngle.floatValue = (leftAngle.floatValue + dt * 90f / (0.35f + 0.65f * (1f - p))) % 360f
            rightAngle.floatValue = (rightAngle.floatValue + dt * 90f / (0.35f + 0.65f * p)) % 360f
        }
    }
    val sky = Color(0xFF5AB8FF)
    val ink = Color(0xFF141A22)
    val tape = Color(0xFF2B2E36)
    Canvas(modifier.size(size)) {
        audio.frame
        val s = this.size.minDimension
        val w = s * 0.98f
        val h = w * 0.64f
        val o = Offset((this.size.width - w) / 2, (this.size.height - h) / 2)
        val body = Rect(o, Size(w, h))
        val r = w * 0.045f
        // Smoky translucent shell.
        for (i in 1..3) drawRoundRect(Color.Black.copy(alpha = 0.14f), body.topLeft + Offset(i * 2f, i * 4f), body.size, CornerRadius(r))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xF03A4654), Color(0xF01C232C)), startY = body.top, endY = body.bottom), body.topLeft, body.size, CornerRadius(r))
        drawRoundRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), start = body.topLeft, end = body.center), body.topLeft, body.size, CornerRadius(r))
        drawRoundRect(Color.White.copy(alpha = 0.14f), body.topLeft, body.size, CornerRadius(r), style = Stroke(2f))
        listOf(Offset(0.04f, 0.06f), Offset(0.96f, 0.06f), Offset(0.04f, 0.94f), Offset(0.96f, 0.94f), Offset(0.5f, 0.94f)).forEach { f ->
            val p = Offset(body.left + w * f.x, body.top + h * f.y)
            drawCircle(Color(0xFFB8C2CC), w * 0.014f, p)
            drawLine(Color(0xFF5A6470), p - Offset(w * 0.009f, 0f), p + Offset(w * 0.009f, 0f), 2f)
        }
        // Label: the album art fills it; a white band carries the title, stripes run along the bottom.
        val label = Rect(Offset(body.left + w * 0.07f, body.top + h * 0.09f), Size(w * 0.86f, h * 0.56f))
        clipPath(roundRectPath(label, r * 0.6f)) {
            drawCropped(art, label)
            drawRect(Color(0xFFF1F4F6), label.topLeft, Size(label.width, label.height * 0.30f))
            listOf(accent, sky, accent2, ink).forEachIndexed { i, c ->
                drawRect(c, Offset(label.left, label.bottom - h * 0.10f + i * h * 0.025f), Size(label.width, h * 0.025f))
            }
        }
        val textLeft = label.left + w * 0.03f
        val sideLayout = text.measure("A", TextStyle(color = accent2, fontSize = (s * 0.062f).toSp(), fontWeight = FontWeight.Bold))
        val band = label.height * 0.30f
        // VU strip just left of the side letter; the names stop before it and end with "…" when too long.
        val vuW = 8 * w * 0.018f
        val vuRight = label.right - sideLayout.size.width - w * 0.05f
        val textWidth = (vuRight - vuW - w * 0.03f - textLeft).toInt().coerceAtLeast(1)
        val titleLayout = text.measure(
            title, TextStyle(color = ink, fontSize = (s * 0.040f).toSp(), fontWeight = FontWeight.Bold),
            overflow = TextOverflow.Ellipsis, maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = textWidth),
        )
        val artistLayout = text.measure(
            artist, TextStyle(color = ink.copy(alpha = 0.65f), fontSize = (s * 0.028f).toSp()),
            overflow = TextOverflow.Ellipsis, maxLines = 1, softWrap = false, constraints = Constraints(maxWidth = textWidth),
        )
        val textTop = label.top + (band - titleLayout.size.height - artistLayout.size.height) / 2
        drawText(titleLayout, topLeft = Offset(textLeft, textTop))
        drawText(artistLayout, topLeft = Offset(textLeft, textTop + titleLayout.size.height))
        drawText(sideLayout, topLeft = Offset(label.right - sideLayout.size.width - w * 0.03f, label.top + (band - sideLayout.size.height) / 2))
        val vu = (audio.level * 14).toInt().coerceAtMost(8)
        for (i in 0 until 8) drawRect(if (i < vu) (if (i > 5) accent2 else accent) else Color(0x22000000), Offset(vuRight - (8 - i) * w * 0.018f, label.top + band * 0.38f), Size(w * 0.012f, band * 0.26f))

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
            drawRect(Color(0xFF0A0D12), win.topLeft, win.size)
            drawCircle(tape, lr, lc)
            drawCircle(tape, rr, rc)
            for (k in 1..4) {
                drawCircle(Color.Black.copy(alpha = 0.25f), core + (lr - core) * k / 5f, lc, style = Stroke(1f))
                drawCircle(Color.Black.copy(alpha = 0.25f), core + (rr - core) * k / 5f, rc, style = Stroke(1f))
            }
        }
        listOf(lc to leftAngle.floatValue, rc to rightAngle.floatValue).forEach { (c, a) ->
            drawCircle(Color(0xFFE6EBF0), core, c)
            rotate(a, c) {
                for (k in 0 until 6) {
                    val ang = k * PI.toFloat() / 3f
                    drawLine(Color(0xFF6B7480), c, Offset(c.x + cos(ang) * core * 0.78f, c.y + sin(ang) * core * 0.78f), 3f, StrokeCap.Round)
                }
            }
            drawCircle(Color(0xFF1A1F26), core * 0.35f, c)
        }
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), startY = win.top, endY = win.bottom), win.topLeft, win.size, CornerRadius(win.height * 0.3f))
        drawRoundRect(Color.White.copy(alpha = 0.25f), win.topLeft, win.size, CornerRadius(win.height * 0.3f), style = Stroke(2f))

        // Head area: trapezoid with the tape running across it.
        val top = body.bottom - h * 0.20f
        val trap = Path().apply {
            moveTo(body.left + w * 0.18f, body.bottom); lineTo(body.left + w * 0.24f, top)
            lineTo(body.right - w * 0.24f, top); lineTo(body.right - w * 0.18f, body.bottom); close()
        }
        drawPath(trap, Color(0xFF1A2029))
        drawLine(tape, Offset(body.left + w * 0.22f, body.bottom - h * 0.05f), Offset(body.right - w * 0.22f, body.bottom - h * 0.05f), 4f)
        listOf(0.31f, 0.69f).forEach { f -> drawCircle(Color(0xFF0A0D12), w * 0.018f, Offset(body.left + w * f, top + h * 0.08f)) }
        drawRect(Color(0xFF0A0D12), Offset(body.left + w * 0.46f, top + h * 0.05f), Size(w * 0.08f, h * 0.07f))
    }
}
