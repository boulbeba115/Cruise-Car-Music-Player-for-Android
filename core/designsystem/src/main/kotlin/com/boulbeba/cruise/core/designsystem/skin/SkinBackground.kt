package com.boulbeba.cruise.core.designsystem.skin

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.asImageBitmap
import com.boulbeba.cruise.core.designsystem.theme.BackgroundStyle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Seconds of animation time, advanced at a rate the device can afford (SPEC VIS-10): every frame on Full, ~20 fps on
 * Balanced (one frame request per tick, so the display is not kept busy in between), frozen on Saver or when not
 * [running]. Decorative motion stays slow (≥ 4 s cycles), so the lower rate is not visible.
 */
@Composable
fun rememberAnimationTime(quality: VisualQuality = CruiseTheme.quality, running: Boolean = true): Float {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(quality, running) {
        if (quality == VisualQuality.Saver || !running) return@LaunchedEffect
        val start = withFrameNanos { it } - (time * 1e9).toLong()
        while (true) {
            if (quality == VisualQuality.Balanced) delay(BALANCED_TICK_MS)
            withFrameNanos { now -> time = (now - start) / 1e9f }
        }
    }
    return time
}

private const val BALANCED_TICK_MS = 50L

/**
 * The skin's backdrop behind every screen. [coverAccent]/[coverDeep] tint skins that follow the album cover.
 * It moves only while [moving] (music playing): parked with the music paused, nothing redraws.
 */
@Composable
fun SkinBackground(modifier: Modifier = Modifier, coverAccent: Color? = null, coverDeep: Color? = null, moving: Boolean = true) {
    val skin = CruiseTheme.skin
    val colors = CruiseTheme.colors
    val t = rememberAnimationTime(running = moving)
    val accent = if (skin.dynamicAccent && coverAccent != null) coverAccent else colors.accent
    val deep = if (skin.dynamicAccent && coverDeep != null) coverDeep else colors.accent2
    val carbon = remember { carbonTile() }
    val grain = remember { grainTile() }
    Canvas(modifier) {
        drawRect(colors.background)
        when (skin.background) {
            BackgroundStyle.CoverGlow -> coverGlow(t, accent, deep)
            BackgroundStyle.RetroGrid -> retroGrid(t, colors.accent, colors.accent2, colors.background)
            BackgroundStyle.Carbon -> carbonDash(t, carbon, colors.accent)
            BackgroundStyle.WarmGrain -> warmGrain(t, grain, colors.accent, colors.accent2)
            BackgroundStyle.Aurora -> aurora(t, colors.accent, colors.accent2)
            BackgroundStyle.Stripes -> stripes(t, colors.accent, colors.accent2)
            BackgroundStyle.Plain -> if (!colors.isDark) {
                drawRect(Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.06f), Color.Transparent)))
            }
        }
    }
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

/** Synthwave: gradient sky, striped sun on the horizon, perspective floor grid scrolling toward the viewer. */
private fun DrawScope.retroGrid(t: Float, pink: Color, cyan: Color, bg: Color) {
    val horizon = size.height * 0.58f
    drawRect(Brush.verticalGradient(listOf(bg, Color(0xFF2A0B4A), pink.copy(alpha = 0.55f)), endY = horizon), size = Size(size.width, horizon))
    // Sun with horizontal cut-outs.
    // Low and off to the side, half-transparent: it must never compete with the text in front of it.
    val sunR = size.height * 0.2f
    val sunC = Offset(size.width * 0.9f, horizon)
    clipRect(bottom = horizon) {
        drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFE65C), pink), startY = sunC.y - sunR, endY = sunC.y), sunR, sunC, alpha = 0.55f)
        for (i in 0 until 6) {
            val y = horizon - sunR * 0.12f - i * sunR * 0.13f
            drawRect(Color(0xFF2A0B4A).copy(alpha = 0.9f), Offset(sunC.x - sunR, y), Size(sunR * 2, sunR * 0.035f * (6 - i) / 2f))
        }
    }
    // Floor.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF14052B), bg), startY = horizon), Offset(0f, horizon), Size(size.width, size.height - horizon))
    val vanishing = Offset(size.width * 0.5f, horizon)
    val lineColor = cyan.copy(alpha = 0.55f)
    for (i in -14..14) {
        val x = size.width * 0.5f + i * size.width * 0.11f
        drawLine(lineColor, vanishing, Offset(x + (x - vanishing.x) * 1.6f, size.height), strokeWidth = 1.5f)
    }
    val phase = (t * 0.35f) % 1f
    for (i in 0 until 14) {
        val z = ((i + phase) / 14f)
        val y = horizon + (size.height - horizon) * z * z
        drawLine(pink.copy(alpha = 0.25f + 0.55f * z), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f + 2f * z)
    }
    drawRect(Brush.verticalGradient(listOf(cyan.copy(alpha = 0.35f), Color.Transparent), startY = horizon - 2f, endY = horizon + 40f), Offset(0f, horizon - 2f), Size(size.width, 42f))
}

/** Dashboard: carbon weave, amber vignette and a slow scanline sweep. */
private fun DrawScope.carbonDash(t: Float, tile: ImageBitmap, amber: Color) {
    drawRect(ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)), alpha = 0.55f)
    drawRect(Brush.radialGradient(listOf(amber.copy(alpha = 0.10f), Color.Transparent), center = Offset(size.width * 0.5f, size.height * 1.1f), radius = size.maxDimension * 0.8f))
    val sweep = (t * 0.12f) % 1.4f - 0.2f
    val y = size.height * sweep
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, amber.copy(alpha = 0.05f), Color.Transparent), startY = y - 60f, endY = y + 60f), Offset(0f, y - 60f), Size(size.width, 120f))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center = center, radius = size.maxDimension * 0.75f))
}

/** Lounge: warm lamp light breathing slowly over grain. */
private fun DrawScope.warmGrain(t: Float, tile: ImageBitmap, brass: Color, rust: Color) {
    val breathe = 0.85f + 0.15f * sin(t * 0.5f)
    drawRect(Brush.radialGradient(listOf(brass.copy(alpha = 0.20f * breathe), rust.copy(alpha = 0.10f), Color.Transparent), center = Offset(size.width * 0.25f, size.height * 0.2f), radius = size.maxDimension * 0.85f))
    drawRect(Brush.radialGradient(listOf(rust.copy(alpha = 0.12f), Color.Transparent), center = Offset(size.width * 0.9f, size.height), radius = size.maxDimension * 0.5f))
    drawRect(ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)), alpha = 0.35f)
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

/** Tape Deck: four 80s stripes rising out of the lower-right corner (clear of the navigation), a warm vignette. */
private fun DrawScope.stripes(t: Float, orange: Color, teal: Color) {
    val bands = listOf(Color(0xFFFFC145), orange, Color(0xFFE8503A), teal)
    val w = size.height * 0.045f
    val drift = (sin(t * 0.25f) + 1f) / 2f * size.height * 0.03f
    rotate(-22f, pivot = Offset(size.width, size.height)) {
        bands.forEachIndexed { i, c ->
            val y = size.height * 0.90f + i * w * 1.2f + drift
            drawRect(c.copy(alpha = 0.80f), Offset(size.width * 0.62f, y), Size(size.width * 0.6f, w))
        }
    }
    drawRect(Brush.radialGradient(listOf(orange.copy(alpha = 0.10f), Color.Transparent), center = Offset(size.width * 0.3f, size.height * 0.4f), radius = size.maxDimension * 0.6f))
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent, Color.Black.copy(alpha = 0.30f))))
}

private fun carbonTile(): ImageBitmap {
    val n = 12
    val bmp = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
    for (x in 0 until n) for (y in 0 until n) {
        val cell = ((x / 3) + (y / 3)) % 2 == 0
        val shade = if (cell) 0x2A + (x % 3) * 6 else 0x1A + (y % 3) * 6
        bmp.setPixel(x, y, android.graphics.Color.rgb(shade, shade, shade))
    }
    return bmp.asImageBitmap()
}

private fun grainTile(): ImageBitmap {
    val n = 64
    val bmp = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
    val r = java.util.Random(7)
    for (x in 0 until n) for (y in 0 until n) {
        val a = r.nextInt(40)
        bmp.setPixel(x, y, android.graphics.Color.argb(a, 255, 230, 200))
    }
    return bmp.asImageBitmap()
}
