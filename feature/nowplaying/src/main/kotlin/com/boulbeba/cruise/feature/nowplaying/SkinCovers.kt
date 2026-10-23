package com.boulbeba.cruise.feature.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.designsystem.theme.CoverStyle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import com.boulbeba.cruise.core.playback.PlayerUiState
import com.boulbeba.cruise.core.ui.GeneratedCover
import kotlin.math.cos
import kotlin.math.sin

/** The cover as the active skin presents it. */
@Composable
fun SkinCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, accent2: Color, size: Dp, modifier: Modifier = Modifier) {
    when (CruiseTheme.skin.cover) {
        CoverStyle.Rounded -> RingCover(state, image, audio, accent, accent2, size, modifier)
        CoverStyle.Vinyl -> VinylCover(state, image, audio, size, modifier)
        CoverStyle.Neon -> NeonCover(state, image, audio, accent, accent2, size, modifier)
        CoverStyle.Gauge -> GaugeCover(state, image, audio, accent, accent2, size, modifier)
        CoverStyle.Minimal -> Box(modifier.size(size * 0.72f).clip(RoundedCornerShape(CruiseTheme.dimens.coverRadius * 0.6f))) { Art(image, state) }
    }
}

@Composable
private fun Art(image: ImageBitmap?, state: PlayerUiState) {
    if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    else GeneratedCover(state.album.ifBlank { state.path ?: "" }, state.album.ifBlank { state.title })
}

/** Rounded cover framed by spectrum bars along its edges that breathe with the music (SPEC VIS-4). */
@Composable
private fun RingCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, accent2: Color, size: Dp, modifier: Modifier) {
    val reactive = CruiseTheme.quality != VisualQuality.Saver
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (reactive) Canvas(Modifier.fillMaxSize()) {
            audio.frame
            val perSide = 16
            val half = this.size.minDimension * 0.41f
            val gap = this.size.minDimension * 0.025f
            for (side in 0 until 4) for (k in 0 until perSide) {
                val u = (k + 0.5f) / perSide * 2f - 1f
                if (kotlin.math.abs(u) > 0.84f) continue // the rounded corners
                val i = side * perSide + k
                val band = audio.bands[(i * 7) % audio.bands.size]
                val len = this.size.minDimension * (0.012f + 0.045f * band)
                val (nx, ny) = when (side) { 0 -> 0f to -1f; 1 -> 1f to 0f; 2 -> 0f to 1f; else -> -1f to 0f }
                val (px, py) = when (side) { 0 -> u * half to -half; 1 -> half to u * half; 2 -> -u * half to half; else -> -half to -u * half }
                val start = Offset(center.x + px + nx * gap, center.y + py + ny * gap)
                val end = Offset(start.x + nx * len, start.y + ny * len)
                drawLine(if (i % 2 == 0) accent else accent2, start, end, strokeWidth = 5f, cap = StrokeCap.Round, alpha = 0.55f + 0.45f * band)
            }
        }
        val scale = 1f + 0.015f * audio.level
        Box(
            Modifier.size(size * 0.82f).graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(CruiseTheme.dimens.coverRadius)),
        ) { Art(image, state) }
    }
}

/** A record: grooves, the album as the label, a tonearm that lowers while playing; the disc spins at 33⅓ rpm. */
@Composable
private fun VinylCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, size: Dp, modifier: Modifier) {
    val colors = CruiseTheme.colors
    var angle by remember { mutableFloatStateOf(0f) }
    val spins = state.isPlaying && CruiseTheme.quality != VisualQuality.Saver
    LaunchedEffect(spins) {
        if (!spins) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            angle = (angle + (now - last) / 1e9f * 200f) % 360f // 33⅓ rpm
            last = now
        }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 0.9f).graphicsLayer { rotationZ = angle }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = this.size.minDimension / 2
                drawCircle(Color(0xFF0D0D0D), r)
                for (k in 0 until 26) drawCircle(Color.White.copy(alpha = if (k % 3 == 0) 0.07f else 0.03f), r * (0.42f + k * 0.022f), style = Stroke(1.2f))
                // Light reflection band.
                rotate(-angle + 30f) {
                    drawArc(Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent)), -20f, 40f, true)
                }
            }
            Box(Modifier.size(size * 0.36f).clip(CircleShape)) { Art(image, state) }
            Canvas(Modifier.size(10.dp)) { drawCircle(colors.background) }
        }
        // Tonearm: swings onto the outer grooves when playing, back to its rest beside the record when paused.
        val armAngle by animateFloatAsState(if (state.isPlaying) 12f else -5f, tween(900), label = "tonearm")
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val pivot = Offset(this.size.width * 0.93f, this.size.height * 0.08f)
            val arm = this.size.height * 0.46f
            drawCircle(colors.surfaceElevated, radius = 26f, center = pivot)
            drawCircle(colors.accent, radius = 16f, center = pivot)
            rotate(armAngle, pivot) {
                drawLine(colors.accent, pivot, Offset(pivot.x, pivot.y + arm), strokeWidth = 8f, cap = StrokeCap.Round)
                drawLine(colors.accent2, Offset(pivot.x, pivot.y + arm), Offset(pivot.x - 20f, pivot.y + arm + 34f), strokeWidth = 16f, cap = StrokeCap.Round)
            }
        }
    }
}

/** Neon frame pulsing with the beat, colours sweeping between the two neon tubes. */
@Composable
private fun NeonCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, accent2: Color, size: Dp, modifier: Modifier) {
    val corner = CruiseTheme.dimens.coverRadius
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            audio.frame
            val inset = this.size.minDimension * 0.06f
            val glow = 0.5f + 0.5f * audio.level
            val brush = Brush.sweepGradient(listOf(accent, accent2, accent), center)
            val r = androidx.compose.ui.geometry.CornerRadius(corner.toPx())
            for ((w, a) in listOf(34f to 0.10f, 20f to 0.22f, 9f to 0.55f, 3f to 1f)) {
                drawRoundRect(brush, Offset(inset, inset), Size(this.size.width - inset * 2, this.size.height - inset * 2), r, style = Stroke(w), alpha = a * glow + (1 - glow) * a * 0.5f)
            }
        }
        Box(Modifier.size(size * 0.86f).clip(RoundedCornerShape(corner))) { Art(image, state) }
    }
}

/** Tachometer: tick marks, the progress as a sweeping arc, and a needle driven by the music's level. */
@Composable
private fun GaugeCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, red: Color, size: Dp, modifier: Modifier) {
    val colors = CruiseTheme.colors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            audio.frame
            val r = this.size.minDimension / 2 * 0.96f
            val start = 135f
            val sweep = 270f
            drawArc(colors.surfaceElevated, start, sweep, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(18f, cap = StrokeCap.Butt))
            val fraction = if (state.durationMs > 0) (state.positionAt(audio.now).toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            drawArc(Brush.sweepGradient(listOf(accent, accent, red, accent), center), start, sweep * fraction, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(18f))
            for (i in 0..54) {
                val a = Math.toRadians((start + sweep * i / 54).toDouble())
                val major = i % 6 == 0
                val inner = r - if (major) 42f else 28f
                drawLine(if (i > 46) red else colors.textSecondary, Offset(center.x + cos(a).toFloat() * inner, center.y + sin(a).toFloat() * inner), Offset(center.x + cos(a).toFloat() * (r - 16f), center.y + sin(a).toFloat() * (r - 16f)), strokeWidth = if (major) 4f else 2f)
            }
        }
        Box(Modifier.size(size * 0.56f).clip(CircleShape)) { Art(image, state) }
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2 * 0.80f
            val a = Math.toRadians((135f + 270f * (0.05f + 0.9f * audio.level.coerceIn(0f, 1f) * 2.2f).coerceAtMost(1f)).toDouble())
            val tip = Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r)
            val tail = Offset(center.x + cos(a).toFloat() * r * 0.62f, center.y + sin(a).toFloat() * r * 0.62f)
            drawLine(red, tail, tip, strokeWidth = 7f, cap = StrokeCap.Round)
        }
    }
}
