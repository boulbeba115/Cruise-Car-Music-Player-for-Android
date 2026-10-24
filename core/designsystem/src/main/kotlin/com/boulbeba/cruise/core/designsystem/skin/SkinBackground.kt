package com.boulbeba.cruise.core.designsystem.skin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.boulbeba.cruise.core.designsystem.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.boulbeba.cruise.core.designsystem.theme.BackgroundStyle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import java.util.Random
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Seconds of animation time, advanced at a rate the device can afford (SPEC VIS-10): every frame on Full, ~20 fps on
 * Balanced (one frame request per tick, so the display is not kept busy in between), frozen on Saver or when not
 * [running]. Decorative motion stays slow (≥ 4 s cycles), so the lower rate is not visible.
 *
 * Returned as a [State]: read it while drawing, not while composing, so only the drawing repeats each frame.
 */
@Composable
fun rememberAnimationTime(quality: VisualQuality = CruiseTheme.quality, running: Boolean = true): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(quality, running) {
        if (quality == VisualQuality.Saver || !running) return@LaunchedEffect
        val start = withFrameNanos { it } - (time.floatValue * 1e9).toLong()
        while (true) {
            if (quality == VisualQuality.Balanced) delay(BALANCED_TICK_MS)
            withFrameNanos { now -> time.floatValue = (now - start) / 1e9f }
        }
    }
    return time
}

private const val BALANCED_TICK_MS = 50L

/**
 * The skin's backdrop behind every screen. [coverAccent]/[coverDeep] tint skins that follow the album cover.
 * It moves only while [moving] (music playing): parked with the music paused, nothing redraws. Fixed parts (the Neon
 * City skyline, textures) are drawn once per size and reused.
 */
@Composable
fun SkinBackground(modifier: Modifier = Modifier, coverAccent: Color? = null, coverDeep: Color? = null, moving: Boolean = true) {
    val skin = CruiseTheme.skin
    val colors = CruiseTheme.colors
    val time = rememberAnimationTime(running = moving)
    val accent = if (skin.dynamicAccent && coverAccent != null) coverAccent else colors.accent
    val deep = if (skin.dynamicAccent && coverDeep != null) coverDeep else colors.accent2
    val style = skin.background
    val texture = rememberSkinTexture(skin.id)
    if (style == BackgroundStyle.NeonCity) LaunchedEffect(Unit) { withContext(Dispatchers.Default) { NeonFog.prepare() } }
    Spacer(
        modifier.drawWithCache {
            // With its painted backdrop (CR-22) Neon City only adds the moving lights; the drawn city is the fallback.
            val city = if (style == BackgroundStyle.NeonCity && texture == null) NeonCity(size, colors.accent, colors.accent2, colors.warning) else null
            val tile = when (style) {
                BackgroundStyle.Carbon -> ShaderBrush(ImageShader(carbonTile(), TileMode.Repeated, TileMode.Repeated))
                BackgroundStyle.Felt -> ShaderBrush(ImageShader(feltTile(), TileMode.Repeated, TileMode.Repeated))
                else -> null
            }
            onDrawBehind {
                val t = time.value
                drawRect(colors.background)
                if (texture != null) {
                    // A generated texture (CR-13), toned with the skin's own background so text keeps its contrast.
                    drawCover(texture)
                    // Neon City's painted night is dark already; a lighter veil keeps its glow.
                    if (colors.isDark) drawRect(colors.background.copy(alpha = if (style == BackgroundStyle.NeonCity) 0.22f else 0.35f))
                }
                when (style) {
                    BackgroundStyle.CoverGlow -> coverGlow(t, accent, deep)
                    BackgroundStyle.NeonCity -> if (city != null) city.draw(this, t) else if (texture != null) neonNight(t, texture)
                    BackgroundStyle.Carbon -> carbonDash(t, tile.takeIf { texture == null }, colors.accent, colors.accent2)
                    BackgroundStyle.Felt -> felt(t, tile.takeIf { texture == null }, colors.textPrimary, colors.accent2)
                    BackgroundStyle.Aurora -> aurora(t, colors.accent, colors.accent2)
                    BackgroundStyle.Stripes -> stripes(t, colors.accent, colors.accent2, colors.textPrimary)
                    BackgroundStyle.Plain -> if (!colors.isDark) {
                        drawRect(Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.06f), Color.Transparent)))
                    }
                }
            }
        },
    )
}

private fun DrawScope.coverGlow(t: Float, accent: Color, deep: Color) {
    val drift = (sin(t * 0.35f) + 1f) / 2f
    drawRect(
        Brush.radialGradient(
            listOf(accent.copy(alpha = 0.22f), deep.copy(alpha = 0.12f), Color.Transparent),
            center = Offset(size.width * (0.2f + 0.45f * drift), size.height * 0.25f),
            radius = size.maxDimension * 0.9f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            listOf(deep.copy(alpha = 0.14f), Color.Transparent),
            center = Offset(size.width * (0.85f - 0.3f * drift), size.height * 0.95f),
            radius = size.maxDimension * 0.6f,
        ),
    )
}

// ---- Neon City ----------------------------------------------------------------------------------------------------

/**
 * A city at night (CHANGE_REQUESTS.md CR-07.4): sky with the city's glow, two layers of buildings with lit windows,
 * a wet street mirroring them. All of that is painted once into a half-resolution bitmap; each frame only adds a few
 * neon signs (one short dip every several seconds, far below the 3 flashes/s limit) and their shimmering reflections.
 */
private class NeonCity(size: Size, private val cyan: Color, private val pink: Color, private val amber: Color) {
    private val horizon = size.height * NEON_HORIZON
    private val scene: ImageBitmap
    private val signs = ArrayList<Sign>()

    private class Sign(val x: Float, val y: Float, val w: Float, val h: Float, val color: Color, val bar: Boolean, val phase: Float)

    init {
        val scale = 0.5f
        val w = (size.width * scale).roundToInt().coerceAtLeast(2)
        val h = (size.height * scale).roundToInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val hz = horizon * scale
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Sky: deep blue-black to the city's glow at the horizon.
        p.shader = LinearGradient(0f, 0f, 0f, hz, Color(0xFF04060D).toArgb(), Color(0xFF0D1A3D).toArgb(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), hz, p)
        p.shader = RadialGradient(w * 0.35f, hz, w * 0.55f, pink.copy(alpha = 0.22f).toArgb(), Color.Transparent.toArgb(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), hz, p)
        p.shader = RadialGradient(w * 0.8f, hz, w * 0.45f, cyan.copy(alpha = 0.16f).toArgb(), Color.Transparent.toArgb(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), hz, p)
        p.shader = null
        // Street.
        p.shader = LinearGradient(0f, hz, 0f, h.toFloat(), Color(0xFF080C1A).toArgb(), Color(0xFF020309).toArgb(), Shader.TileMode.CLAMP)
        c.drawRect(0f, hz, w.toFloat(), h.toFloat(), p)
        p.shader = null

        val rnd = Random(42)
        val warm = Color(0xFFFFD58A).toArgb()
        val cool = Color(0xFF9DEBFF).toArgb()
        fun skyline(minH: Float, maxH: Float, body: Int, windowAlpha: Float, collectSigns: Boolean) {
            var x = -rnd.nextFloat() * w * 0.03f
            while (x < w) {
                val bw = w * (0.035f + rnd.nextFloat() * 0.06f)
                val bh = hz * (minH + rnd.nextFloat() * (maxH - minH))
                val top = hz - bh
                p.color = body
                c.drawRect(x, top, x + bw, hz, p)
                // Windows: a grid, about a third lit, warm or cool.
                val cell = (w / 160f).coerceAtLeast(2f)
                var wy = top + cell * 1.5f
                while (wy < hz - cell * 2) {
                    var wx = x + cell
                    while (wx < x + bw - cell * 1.5f) {
                        if (rnd.nextFloat() < 0.32f) {
                            p.color = if (rnd.nextFloat() < 0.75f) warm else cool
                            p.alpha = ((windowAlpha * (0.45f + 0.55f * rnd.nextFloat())) * 255).toInt()
                            c.drawRect(wx, wy, wx + cell * 0.7f, wy + cell, p)
                            p.alpha = 255
                        }
                        wx += cell * 1.6f
                    }
                    wy += cell * 2.1f
                }
                // Only at the screen's edges and, on the right, only high up: never behind the music bar or the times.
                val signTop = top + bh * 0.12f
                val nearEdge = x < w * 0.16f || (x + bw > w * 0.9f && signTop < h * 0.3f)
                if (collectSigns && nearEdge && bh > hz * 0.28f && rnd.nextFloat() < 0.7f && signs.size < 4) {
                    val bar = rnd.nextBoolean()
                    val sw = if (bar) bw * 0.18f else bw * 0.7f
                    val sh = if (bar) bh * 0.32f else bh * 0.07f
                    val colors = listOf(pink, cyan, amber)
                    signs += Sign(
                        (x + (bw - sw) / 2) / scale, (signTop + bh * rnd.nextFloat() * 0.06f) / scale, sw / scale, sh / scale,
                        colors[signs.size % 3], bar, rnd.nextFloat() * 20f,
                    )
                }
                x += bw + w * rnd.nextFloat() * 0.012f
            }
        }
        skyline(0.18f, 0.42f, Color(0xFF0B1430).toArgb(), 0.35f, collectSigns = false)
        skyline(0.25f, 0.62f, Color(0xFF060A17).toArgb(), 0.65f, collectSigns = true)
        // Wet street: the lit skyline mirrored, squashed and faint.
        c.save()
        c.clipRect(0f, hz, w.toFloat(), h.toFloat())
        c.scale(1f, -0.55f, 0f, hz)
        p.alpha = 70
        val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
        c.drawBitmap(copy, 0f, 0f, p)
        copy.recycle()
        c.restore()
        p.alpha = 255
        // A soft fade at the horizon so the street reads as wet asphalt, not a mirror.
        p.shader = LinearGradient(0f, hz, 0f, h.toFloat(), Color(0x55070B18).toArgb(), Color(0xEE020309).toArgb(), Shader.TileMode.CLAMP)
        c.drawRect(0f, hz, w.toFloat(), h.toFloat(), p)
        scene = bmp.asImageBitmap()
    }

    fun draw(scope: DrawScope, t: Float) = with(scope) {
        drawImage(scene, dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
        val stroke = size.minDimension * 0.004f
        for (s in signs) {
            // One short dip every 8–14 s per sign.
            val cycle = 8f + (s.phase % 6f)
            val local = (t + s.phase) % cycle
            val lit = if (local < 0.14f) 0.25f else 1f
            sign(s, lit, stroke, mirrored = false)
            sign(s, lit * 0.35f, stroke, mirrored = true, wobble = sin(t * 1.7f + s.phase) * stroke * 2.5f)
        }
        neonLights(t, cyan, pink, horizon)
    }

    private fun DrawScope.sign(s: Sign, lit: Float, stroke: Float, mirrored: Boolean, wobble: Float = 0f) {
        val y = if (mirrored) horizon + (horizon - s.y - s.h) * 0.55f else s.y
        val h = if (mirrored) s.h * 0.55f else s.h
        val topLeft = Offset(s.x + wobble, y)
        val r = CornerRadius(minOf(s.w, h) * 0.3f)
        for ((width, alpha) in listOf(stroke * 5f to 0.12f, stroke * 2.4f to 0.3f, stroke to 1f)) {
            drawRoundRect(s.color.copy(alpha = alpha * lit), topLeft, Size(s.w, h), r, style = Stroke(width))
        }
        if (!s.bar) {
            // A line of "letters" inside the frame.
            val n = 4
            val gap = s.w / (n + 1)
            for (i in 1..n) drawLine(s.color.copy(alpha = 0.8f * lit), Offset(topLeft.x + gap * i, topLeft.y + h * 0.3f), Offset(topLeft.x + gap * i, topLeft.y + h * 0.7f), stroke)
        }
    }
}

private const val NEON_HORIZON = 0.72f

/** Neon City's moving lights: streaks drifting on the wet street below [horizon], the city's glow breathing over it. */
private fun DrawScope.neonLights(t: Float, cyan: Color, pink: Color, horizon: Float) {
    val stroke = size.minDimension * 0.004f
    for (i in 0 until 5) {
        val y = horizon + (size.height - horizon) * (0.18f + i * 0.17f)
        val x = ((t * (12f + i * 5f) + i * 211f) % (size.width + 300f)) - 150f
        drawLine(
            Brush.horizontalGradient(listOf(Color.Transparent, (if (i % 2 == 0) cyan else pink).copy(alpha = 0.18f), Color.Transparent), startX = x, endX = x + 260f),
            Offset(x, y), Offset(x + 260f, y), strokeWidth = stroke * 0.8f,
        )
    }
    val breathe = 0.85f + 0.15f * sin(t * 0.3f)
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, pink.copy(alpha = 0.10f * breathe), Color.Transparent), startY = horizon - size.height * 0.2f, endY = horizon + size.height * 0.05f),
    )
}

/**
 * The painted city alive (CR-22): searchlights on the tower tops sweeping the sky slowly, a scan of light gliding up the
 * holographic column, red aviation lights breathing on the antennas, fog drifting across in two layers. Everything is placed
 * in the picture's own coordinates through the same crop as [drawCover], so it sits on the painted towers on any screen
 * shape. Slow, soft and never flashing; it only moves while the music plays.
 */
private fun DrawScope.neonNight(t: Float, image: ImageBitmap) {
    val scale = maxOf(size.width / image.width, size.height / image.height)
    val ox = (size.width - image.width * scale) / 2f
    val oy = (size.height - image.height * scale) / 2f
    fun at(u: Float, v: Float) = Offset(ox + u * image.width * scale, oy + v * image.height * scale)

    // Searchlights: a narrow cone from each light, swaying a few degrees each way on its own slow period.
    val beam = Color(0xFF9BE8EA)
    for ((i, src) in NEON_BEAMS.withIndex()) {
        val p = at(src.x, src.y)
        val len = p.y + size.height * 0.15f
        val angle = sin(t * (0.10f + 0.017f * i) + i * 1.9f) * 0.19f
        val dir = Offset(sin(angle), -cos(angle))
        val side = Offset(-dir.y, dir.x)
        val tip = p + dir * len
        val wide = len * 0.05f
        val cone = Path().apply {
            moveTo(p.x + side.x * wide * 0.08f, p.y + side.y * wide * 0.08f)
            lineTo(tip.x + side.x * wide, tip.y + side.y * wide)
            lineTo(tip.x - side.x * wide, tip.y - side.y * wide)
            lineTo(p.x - side.x * wide * 0.08f, p.y - side.y * wide * 0.08f)
            close()
        }
        drawPath(cone, Brush.linearGradient(listOf(beam.copy(alpha = 0.12f), beam.copy(alpha = 0.04f), Color.Transparent), start = p, end = tip))
        val r = size.minDimension * 0.02f
        drawCircle(Brush.radialGradient(listOf(beam.copy(alpha = 0.22f), Color.Transparent), center = p, radius = r), r, p)
    }

    // The holographic column: a soft lens of light gliding up it every few seconds, kept inside the painted tower — its
    // lit body measured on the picture, with the rounded top of its cylinder — so nothing shows beyond it.
    val colTop = at(0.630f, 0.17f)
    val colBottom = at(0.694f, 0.53f)
    val colW = colBottom.x - colTop.x
    val colH = colBottom.y - colTop.y
    val column = Path().apply {
        addRoundRect(RoundRect(colTop.x, colTop.y, colBottom.x, colBottom.y, CornerRadius(colW / 2, colW * 0.35f)))
    }
    clipPath(column) {
        val y = colBottom.y - ((t * 0.14f) % 1.25f) * colH
        val lens = Offset(colTop.x + colW / 2, y)
        scale(1f, 0.55f, pivot = lens) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFBDF4F6).copy(alpha = 0.26f), Color.Transparent), center = lens, radius = colW * 0.75f), colW * 0.75f, lens)
        }
    }

    // Aviation lights on the antennas, breathing out of step.
    for ((i, light) in NEON_AVIATION.withIndex()) {
        val p = at(light.x, light.y)
        val glow = (sin(t * 1.3f + i * 2.1f) + 1f) / 2f
        val r = size.minDimension * 0.016f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF4A4A).copy(alpha = 0.55f * glow), Color.Transparent), center = p, radius = r), r, p)
        drawCircle(Color(0xFFFF7070).copy(alpha = 0.35f + 0.6f * glow), size.minDimension * 0.0028f, p)
    }

    // Fog: three layers drifting one way at different depths, each breathing slowly in density (see NeonFog).
    val fog = NeonFog.textures ?: return
    for (layer in NeonFog.layers) {
        val w = (size.width * layer.width).roundToInt()
        val h = (size.height * layer.height).roundToInt()
        val y = (size.height * layer.top).roundToInt()
        val x = -((t * size.width * layer.speed + layer.phase * w) % w).roundToInt()
        val breathe = 0.72f + 0.28f * sin(t * 2f * Math.PI.toFloat() / layer.breathS + layer.phase * 6f)
        val tex = fog[layer.texture]
        for (k in 0..1) {
            drawImage(tex, srcOffset = IntOffset.Zero, srcSize = IntSize(tex.width, tex.height), dstOffset = IntOffset(x + k * w, y), dstSize = IntSize(w, h), alpha = layer.alpha * breathe)
        }
    }
}

/**
 * Neon City's fog (CR-22, owner: "moving slowly like a real fog, not a clear loop"). Two fog textures are made once, in
 * the background: fractal value noise warped through itself, so it forms wisps and curls rather than blobs, repeating
 * seamlessly across its width, thick low down and thinning upwards. Three layers drift one way only — far haze barely
 * moving, nearer wisps a little faster — each at its own size, so together they never show a repeat, and each slowly
 * thickens and thins over about a minute like a fog bank.
 */
internal object NeonFog {
    class Layer(val texture: Int, val top: Float, val height: Float, val width: Float, val speed: Float, val alpha: Float, val breathS: Float, val phase: Float)

    /** Speeds are fractions of the screen width per second. */
    val layers = listOf(
        Layer(texture = 0, top = 0.30f, height = 0.50f, width = 2.3f, speed = 0.0045f, alpha = 0.6f, breathS = 47f, phase = 0.0f),
        Layer(texture = 1, top = 0.45f, height = 0.50f, width = 1.9f, speed = 0.0085f, alpha = 0.48f, breathS = 61f, phase = 0.37f),
        Layer(texture = 0, top = 0.62f, height = 0.48f, width = 1.45f, speed = 0.014f, alpha = 0.38f, breathS = 73f, phase = 0.71f),
    )

    // Snapshot state: the backdrop redraws by itself once the textures are ready, even with the music paused.
    private val ready = mutableStateOf<List<ImageBitmap>?>(null)
    val textures: List<ImageBitmap>? get() = ready.value

    /** Builds the textures off the main thread; until then the city simply has no moving fog. */
    fun prepare() {
        if (ready.value != null) return
        ready.value = listOf(make(7L), make(31L))
    }

    private class Noise(seed: Long, val cells: Int, val rows: Int) {
        private val grid = Random(seed).let { r -> Array(rows + 1) { FloatArray(cells) { r.nextFloat() } } }

        /** [u] wraps round (0..1 is one period); [v] runs 0..1 over the rows. */
        fun at(u: Float, v: Float): Float {
            val fx = ((u % 1f + 1f) % 1f) * cells
            val fy = v.coerceIn(0f, 0.9999f) * rows
            val x0 = fx.toInt() % cells
            val x1 = (x0 + 1) % cells
            val y0 = fy.toInt()
            val tx = smooth(fx - fx.toInt())
            val ty = smooth(fy - y0)
            val a = grid[y0][x0] + (grid[y0][x1] - grid[y0][x0]) * tx
            val b = grid[y0 + 1][x0] + (grid[y0 + 1][x1] - grid[y0 + 1][x0]) * tx
            return a + (b - a) * ty
        }

        private fun smooth(x: Float) = x * x * (3f - 2f * x)
    }

    private fun make(seed: Long): ImageBitmap {
        val w = 512
        val h = 128
        fun fbm(seedOffset: Long): (Float, Float) -> Float {
            val octaves = listOf(Noise(seed + seedOffset, 5, 2) to 0.5f, Noise(seed + seedOffset + 1, 10, 4) to 0.27f, Noise(seed + seedOffset + 2, 20, 8) to 0.15f, Noise(seed + seedOffset + 3, 40, 16) to 0.08f)
            return { u, v -> octaves.sumOf { (n, a) -> (n.at(u, v) * a).toDouble() }.toFloat() }
        }
        val shape = fbm(0)
        val warpX = fbm(100)
        val warpY = fbm(200)
        fun smoothstep(e0: Float, e1: Float, x: Float): Float {
            val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val v = y.toFloat() / (h - 1)
            // Thick low down, thinning upwards, soft at the very bottom so layers overlap without a seam.
            val envelope = smoothstep(0f, 0.45f, v) * (1f - smoothstep(0.82f, 1f, v))
            for (x in 0 until w) {
                val u = x.toFloat() / w
                val du = (warpX(u, v) - 0.5f) * 0.09f
                val dv = (warpY(u, v) - 0.5f) * 0.35f
                val n = shape(u + du, v + dv)
                val a = smoothstep(0.30f, 0.78f, n) * envelope
                px[y * w + x] = android.graphics.Color.argb((a * 235).roundToInt(), 0x7F, 0xA7, 0xB0)
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** Searchlights on the painted tower tops, as fractions of the backdrop picture (bg_neon, measured from it). */
private val NEON_BEAMS = listOf(Offset(0.0955f, 0.493f), Offset(0.237f, 0.337f), Offset(0.278f, 0.334f), Offset(0.636f, 0.355f), Offset(0.853f, 0.446f), Offset(0.880f, 0.444f))

/** Red aviation lights on the antennas of the backdrop. */
private val NEON_AVIATION = listOf(Offset(0.9359f, 0.3967f), Offset(0.8383f, 0.7715f))

// ---- Other skins --------------------------------------------------------------------------------------------------

/** Sport dashboard: carbon weave, a red light from below, a slow ice-blue scan line, vignette. */
private fun DrawScope.carbonDash(t: Float, tile: Brush?, red: Color, ice: Color) {
    if (tile != null) drawRect(tile, alpha = 0.55f)
    drawRect(Brush.radialGradient(listOf(red.copy(alpha = 0.09f), Color.Transparent), center = Offset(size.width * 0.5f, size.height * 1.1f), radius = size.maxDimension * 0.8f))
    val sweep = (t * 0.12f) % 1.4f - 0.2f
    val y = size.height * sweep
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, ice.copy(alpha = 0.05f), Color.Transparent), startY = y - 60f, endY = y + 60f), Offset(0f, y - 60f), Size(size.width, 120f))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center = center, radius = size.maxDimension * 0.75f))
}

/** Vinyl Lounge: felt under a soft spotlight that breathes slowly, a cool fill light, vignette. */
private fun DrawScope.felt(t: Float, tile: Brush?, cream: Color, teal: Color) {
    if (tile != null) drawRect(tile, alpha = 0.6f)
    val breathe = 0.9f + 0.1f * sin(t * 0.4f)
    drawRect(Brush.radialGradient(listOf(cream.copy(alpha = 0.13f * breathe), cream.copy(alpha = 0.04f), Color.Transparent), center = Offset(size.width * 0.3f, -size.height * 0.1f), radius = size.maxDimension * 0.85f))
    drawRect(Brush.radialGradient(listOf(teal.copy(alpha = 0.07f), Color.Transparent), center = Offset(size.width * 0.95f, size.height), radius = size.maxDimension * 0.5f))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), center = center, radius = size.maxDimension * 0.8f))
}

/** Northern lights: soft colour curtains drifting on slow Lissajous paths. */
private fun DrawScope.aurora(t: Float, mint: Color, violet: Color) {
    val blobs = listOf(
        Triple(mint, 0.13f, 0f), Triple(violet, 0.09f, 2.1f), Triple(mint.copy(red = 0.35f), 0.07f, 4.2f), Triple(violet.copy(blue = 1f), 0.11f, 1.3f),
    )
    for ((c, speed, phase) in blobs) {
        val x = size.width * (0.5f + 0.38f * sin(t * speed + phase))
        val y = size.height * (0.35f + 0.25f * cos(t * speed * 1.3f + phase))
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.30f), Color.Transparent), center = Offset(x, y), radius = size.maxDimension * 0.42f), size.maxDimension * 0.42f, Offset(x, y))
    }
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))))
}

/** Tape Deck: four stripes rising out of the lower-right corner (clear of the navigation), a cool vignette. */
private fun DrawScope.stripes(t: Float, teal: Color, coral: Color, white: Color) {
    val bands = listOf(white, teal, Color(0xFF5AB8FF), coral)
    val w = size.height * 0.045f
    val drift = (sin(t * 0.25f) + 1f) / 2f * size.height * 0.03f
    rotate(-22f, pivot = Offset(size.width, size.height)) {
        bands.forEachIndexed { i, c ->
            val y = size.height * 0.90f + i * w * 1.2f + drift
            drawRect(c.copy(alpha = 0.75f), Offset(size.width * 0.62f, y), Size(size.width * 0.6f, w))
        }
    }
    drawRect(Brush.radialGradient(listOf(teal.copy(alpha = 0.08f), Color.Transparent), center = Offset(size.width * 0.3f, size.height * 0.4f), radius = size.maxDimension * 0.6f))
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.30f))))
}

private fun carbonTile(): ImageBitmap {
    val n = 12
    val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    for (x in 0 until n) for (y in 0 until n) {
        val cell = ((x / 3) + (y / 3)) % 2 == 0
        val shade = if (cell) 0x2A + (x % 3) * 6 else 0x1A + (y % 3) * 6
        bmp.setPixel(x, y, android.graphics.Color.rgb(shade, shade, shade + 4))
    }
    return bmp.asImageBitmap()
}

/** Felt: fine fibres, light and dark specks. */
private fun feltTile(): ImageBitmap {
    val n = 96
    val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val r = Random(7)
    for (x in 0 until n) for (y in 0 until n) {
        val light = r.nextFloat() < 0.5f
        val a = r.nextInt(if (light) 22 else 40)
        bmp.setPixel(x, y, if (light) android.graphics.Color.argb(a, 220, 225, 240) else android.graphics.Color.argb(a, 0, 0, 0))
    }
    return bmp.asImageBitmap()
}

/**
 * Generated backdrop textures (CR-13): Vinyl Lounge's art-deco wallpaper, Sport Cockpit's carbon, Tape Deck's brushed
 * metal, Daylight's paper; Neon City's painted rainy city at night (CR-22).
 */
private fun textureFor(skinId: String): Int? = when (skinId) {
    "neon" -> R.drawable.bg_neon
    "vinyl" -> R.drawable.bg_vinyl
    "amber" -> R.drawable.bg_amber
    "tape" -> R.drawable.bg_tape
    "daylight" -> R.drawable.bg_daylight
    else -> null
}

// One decode per texture for the whole app (screen and Settings previews share it). RGB_565: half the memory, no alpha needed.
private val textures = object : android.util.LruCache<Int, ImageBitmap>(2) {}

@Composable
private fun rememberSkinTexture(skinId: String): ImageBitmap? {
    val context = LocalContext.current
    val res = textureFor(skinId) ?: return null
    // State per texture: switching between two textured skins must not keep the previous skin's bitmap.
    val image = remember(res) { mutableStateOf(textures.get(res)) }
    LaunchedEffect(res) {
        if (image.value == null) image.value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565; inScaled = false }
            BitmapFactory.decodeResource(context.resources, res, options)?.asImageBitmap()?.also { textures.put(res, it) }
        }
    }
    return image.value
}

/** [image] scaled to cover the whole area, centred. */
private fun DrawScope.drawCover(image: ImageBitmap) {
    val scale = maxOf(size.width / image.width, size.height / image.height)
    val sw = (size.width / scale).roundToInt().coerceIn(1, image.width)
    val sh = (size.height / scale).roundToInt().coerceIn(1, image.height)
    drawImage(
        image, srcOffset = IntOffset((image.width - sw) / 2, (image.height - sh) / 2), srcSize = IntSize(sw, sh),
        dstOffset = IntOffset.Zero, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
    )
}
