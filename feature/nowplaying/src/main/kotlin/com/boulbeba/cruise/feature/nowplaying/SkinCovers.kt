package com.boulbeba.cruise.feature.nowplaying

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.boulbeba.cruise.core.designsystem.theme.CoverStyle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import com.boulbeba.cruise.core.playback.PlayerUiState
import com.boulbeba.cruise.core.ui.PlaceholderCover
import com.boulbeba.cruise.core.ui.formatDuration
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The cover as the active skin presents it. */
@Composable
fun SkinCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, accent: Color, accent2: Color, size: Dp, modifier: Modifier = Modifier) {
    when (CruiseTheme.skin.cover) {
        CoverStyle.Sleeve -> SleeveCover(state, image, audio, accent, accent2, size, modifier)
        CoverStyle.Liquid -> LiquidCover(state, image, audio, accent, accent2, size, modifier)
        CoverStyle.Sticker -> StickerCover(state, image, audio, accent, size, modifier)
        CoverStyle.Cassette -> CassetteCover(state, image, audio, accent, accent2, size, modifier)
        CoverStyle.Vinyl -> VinylCover(state, image, audio, size, modifier)
        CoverStyle.Neon -> NeonCover(state, image, audio, accent, accent2, size, modifier)
        CoverStyle.Gauge -> GaugeCover(state, image, audio, accent, accent2, size, modifier)
        CoverStyle.Minimal -> Box(modifier.size(size * 0.72f).clip(RoundedCornerShape(CruiseTheme.dimens.coverRadius * 0.6f))) { Art(image, state) }
    }
}

@Composable
private fun Art(image: ImageBitmap?, state: PlayerUiState) {
    if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    else PlaceholderCover(state.path.orEmpty(), sizePx = 512)
}

// ---- Vinyl Lounge ---------------------------------------------------------------------------------------------

/** Where the tonearm is, carried from frame to frame so a new track glides it back to the outer groove. */
private class ArmState {
    var angle = Float.NaN
}

/**
 * A picture disc on a turntable (CHANGE_REQUESTS.md CR-07.7): the album art covers most of the record with the grooves
 * on top, the record turns at 33⅓ rpm under a fixed reflection, and the tonearm behaves like a real one — it rests off
 * the record, swings onto the outer groove when the music starts, moves toward the label as the song plays, lifts on
 * pause and glides back to the edge for the next track.
 */
@Composable
private fun VinylCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, size: Dp, modifier: Modifier) {
    val colors = CruiseTheme.colors
    val art = rememberCoverBitmap(image, state)
    val moving = CruiseTheme.quality != VisualQuality.Saver
    val spin = rememberSpin(state.isPlaying && moving, 200f) // 33⅓ rpm
    val onRecord by animateFloatAsState(if (state.isPlaying || state.positionMs > 0) 1f else 0f, tween(1100), label = "armOn")
    val lift by animateFloatAsState(if (state.isPlaying) 0f else 1f, tween(500), label = "armLift")
    val arm = remember { ArmState() }
    Canvas(modifier.size(size)) {
        audio.frame
        val s = this.size.minDimension
        val c = Offset(s * 0.46f, s * 0.54f)
        val r = s * 0.43f
        // Platter under the record.
        drawCircle(Color.Black.copy(alpha = 0.45f), r * 1.04f, c + Offset(0f, s * 0.014f))
        drawCircle(Color(0xFF1B1E24), r * 1.02f, c)
        // The record turns: art, grooves, label.
        rotate(spin.value, c) {
            clipPath(Path().apply { addOval(Rect(c, r)) }) { drawCropped(art, Rect(c, r)) }
            var k = 0
            var rr = r * 0.30f
            while (rr < r) {
                val gap = k % 9 == 8 // the gap between two songs
                drawCircle(Color.Black.copy(alpha = if (gap) 0.42f else 0.16f), rr, c, style = Stroke(if (gap) 2.2f else 1f))
                if (k % 2 == 0) drawCircle(Color.White.copy(alpha = 0.04f), rr + 1.2f, c, style = Stroke(0.8f))
                rr += r * 0.0115f
                k++
            }
            drawCircle(colors.accent, r * 0.2f, c)
            drawCircle(colors.textPrimary.copy(alpha = 0.85f), r * 0.2f, c, style = Stroke(r * 0.012f))
            drawCircle(colors.textPrimary.copy(alpha = 0.5f), r * 0.13f, c, style = Stroke(r * 0.006f))
            drawLine(colors.textPrimary.copy(alpha = 0.7f), c + Offset(-r * 0.1f, -r * 0.06f), c + Offset(r * 0.1f, -r * 0.06f), r * 0.012f)
        }
        // The reflection stays where the light is while the record turns under it.
        drawCircle(
            Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.13f), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.09f), Color.Transparent, Color.Transparent), c),
            r, c,
        )
        drawCircle(Color.Black.copy(alpha = 0.7f), r, c, style = Stroke(s * 0.008f))
        drawCircle(Color(0xFFD9DCE3), s * 0.011f, c)

        // Tonearm.
        val pivot = Offset(s * 0.93f, s * 0.10f)
        val length = s * 0.60f
        val rest = 88f
        val progress = if (state.durationMs > 0) (state.positionAt(audio.now).toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        val groove = r * (0.94f - 0.58f * progress)
        val onGroove = armAngle(pivot, c, length, groove) ?: rest
        val target = rest + (onGroove - rest) * onRecord
        // Glide when the target jumps (a new track sends the arm back to the edge); follow the groove otherwise.
        arm.angle = if (arm.angle.isNaN() || !state.isPlaying) target else arm.angle + (target - arm.angle) * 0.12f
        tonearm(pivot, length, arm.angle, lift * onRecord, s, colors.accent)
    }
}

/** Angle (degrees, screen coordinates) that puts the stylus [groove] away from the record's centre, if reachable. */
private fun armAngle(pivot: Offset, center: Offset, length: Float, groove: Float): Float? {
    val d = hypot(center.x - pivot.x, center.y - pivot.y)
    val cosDelta = (length * length + d * d - groove * groove) / (2 * length * d)
    if (cosDelta !in -1f..1f) return null
    val phi = atan2(center.y - pivot.y, center.x - pivot.x)
    return Math.toDegrees((phi - acos(cosDelta)).toDouble()).toFloat()
}

private fun DrawScope.tonearm(pivot: Offset, length: Float, angle: Float, lift: Float, s: Float, accent: Color) {
    val a = Math.toRadians(angle.toDouble())
    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
    val stylus = pivot + dir * length
    val shellStart = pivot + dir * (length - s * 0.075f)
    val weight = pivot - dir * (s * 0.075f)
    val shadow = Offset(s * 0.012f, s * 0.018f) * (1f + lift * 1.6f)
    // Shadow, larger when the arm is lifted.
    drawLine(Color.Black.copy(alpha = 0.35f - 0.1f * lift), weight + shadow, shellStart + shadow, s * 0.018f, StrokeCap.Round)
    drawCircle(Color.Black.copy(alpha = 0.3f), s * 0.06f, pivot + shadow * 0.5f)
    // Base and counterweight.
    drawCircle(Brush.radialGradient(listOf(Color(0xFF59606B), Color(0xFF22262D)), center = pivot, radius = s * 0.06f), s * 0.06f, pivot)
    drawCircle(Color(0xFFB9C0CA), s * 0.06f, pivot, style = Stroke(s * 0.006f))
    drawCircle(Brush.radialGradient(listOf(Color(0xFFCDD3DB), Color(0xFF5B626D)), center = weight, radius = s * 0.04f), s * 0.036f, weight)
    // Arm tube.
    drawLine(Color(0xFFCED4DC), weight, shellStart, s * 0.016f, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.5f), weight, shellStart, s * 0.005f, StrokeCap.Round)
    drawCircle(Color(0xFF2A2F37), s * 0.02f, pivot)
    // Headshell, angled like a real one, with the cartridge in the skin's accent.
    val offset = Math.toRadians(angle + 24.0)
    val shellDir = Offset(cos(offset).toFloat(), sin(offset).toFloat())
    val shellEnd = shellStart + shellDir * (s * 0.08f)
    drawLine(Color(0xFF2B3038), shellStart, shellEnd, s * 0.034f, StrokeCap.Round)
    drawLine(accent, shellStart + shellDir * (s * 0.035f), shellEnd, s * 0.018f, StrokeCap.Round)
    drawCircle(Color(0xFFE8ECF1), s * 0.006f, stylus)
}

// ---- Neon City ------------------------------------------------------------------------------------------------

/**
 * A rooftop LED billboard in the night city (CHANGE_REQUESTS.md CR-07.4, second design after the owner's review):
 * the album plays on an LED screen — pixel grid and a slow refresh sweep — turned a little in 3D, lit from above by a
 * lamp bar whose light cones pulse with the music, trimmed with a pink neon tube, standing on steel scaffolding over
 * a wet roof that mirrors it, under light rain. A slow red aviation light breathes on top. Without art the screen shows
 * a neon record-and-note drawing. The LED grid is drawn once per size; each frame only adds lights, rain and the sweep.
 */
@Composable
private fun NeonCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, cyan: Color, pink: Color, size: Dp, modifier: Modifier) {
    val flicker = rememberNeonFlicker(state.path)
    val moving = CruiseTheme.quality != VisualQuality.Saver
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = -12f
                    cameraDistance = 16f * density
                }
                .drawWithCache {
                    val s = this.size.minDimension
                    val board = Rect(Offset(s * 0.13f, s * 0.11f), Size(s * 0.74f, s * 0.62f))
                    val screen = board.deflate(s * 0.022f)
                    val grid = ledGrid(screen.size, s / 150f)
                    val roofY = s * 0.93f
                    onDrawBehind {
                        audio.frame
                        val t = audio.time
                        val level = (audio.level * 2f).coerceAtMost(1f)
                        val lit = flicker.value
                        scaffold(board, roofY, s)
                        roofReflection(image, screen, roofY, s, cyan, pink)
                        // Frame and screen.
                        drawRoundRect(Color(0xFF1A2032), board.topLeft - Offset(0f, 0f), board.size, CornerRadius(s * 0.012f))
                        drawRoundRect(Color(0xFF3A4560), board.topLeft, board.size, CornerRadius(s * 0.012f), style = Stroke(s * 0.006f))
                        if (image != null) drawCropped(image, screen) else neonPlaceholder(screen, cyan, pink, lit)
                        drawRect(Color.White.copy(alpha = 0.05f * level), screen.topLeft, screen.size)
                        drawImage(grid, topLeft = screen.topLeft)
                        if (moving) {
                            // The panel's refresh sweeping down, slowly.
                            val y = screen.top + ((t * 0.22f) % 1f) * screen.height
                            val band = screen.height * 0.09f
                            drawRect(
                                Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent), startY = y - band, endY = y + band),
                                Offset(screen.left, (y - band).coerceAtLeast(screen.top)), Size(screen.width, (band * 2).coerceAtMost(screen.bottom - (y - band).coerceAtLeast(screen.top))),
                            )
                        }
                        lampBar(board, s, level, lit)
                        // Neon trim: a pink tube under the frame, short cyan tubes up the sides.
                        neonLine(Offset(board.left + s * 0.02f, board.bottom + s * 0.018f), Offset(board.right - s * 0.02f, board.bottom + s * 0.018f), pink, s * 0.008f, lit * (0.85f + 0.15f * level))
                        neonLine(Offset(board.left - s * 0.02f, board.top + board.height * 0.2f), Offset(board.left - s * 0.02f, board.bottom - board.height * 0.2f), cyan, s * 0.006f, lit)
                        neonLine(Offset(board.right + s * 0.02f, board.top + board.height * 0.2f), Offset(board.right + s * 0.02f, board.bottom - board.height * 0.2f), cyan, s * 0.006f, lit)
                        // Aviation light on top, breathing slowly (no flashing).
                        val beacon = Offset(board.right - s * 0.03f, board.top - s * 0.075f)
                        val glow = 0.35f + 0.65f * ((sin(t * 1.6f) + 1f) / 2f)
                        drawLine(Color(0xFF3A4560), beacon, Offset(beacon.x, board.top - s * 0.03f), s * 0.006f)
                        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF3B3B).copy(alpha = 0.6f * glow), Color.Transparent), center = beacon, radius = s * 0.04f), s * 0.04f, beacon)
                        drawCircle(Color(0xFFFF5A5A).copy(alpha = 0.5f + 0.5f * glow), s * 0.009f, beacon)
                    }
                },
        )
        if (moving) Rain(audio, cyan)
    }
}

/** Light rain in front of the billboard: thin streaks falling at a slant, recycled from the top. */
@Composable
private fun Rain(audio: AudioLevels, tint: Color) {
    Canvas(Modifier.fillMaxSize()) {
        audio.frame
        val t = audio.time
        val w = size.width
        val h = size.height
        val len = h * 0.055f
        for (i in 0 until 34) {
            val lane = (i * 0.618034f) % 1f
            val speed = 0.55f + (i % 5) * 0.08f
            val y = ((t * speed + i * 0.137f) % 1f) * (h + len) - len
            val x = lane * w + y * 0.12f
            drawLine(tint.copy(alpha = 0.08f + (i % 3) * 0.05f), Offset(x, y), Offset(x + len * 0.12f, y + len), 1.2f, StrokeCap.Round)
        }
    }
}

/** The LED panel's pixel grid: dark gaps between the lit pixels, drawn once into a bitmap. */
private fun ledGrid(size: Size, pitch: Float): ImageBitmap {
    val w = size.width.toInt().coerceAtLeast(1)
    val h = size.height.toInt().coerceAtLeast(1)
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    // Whole pixels and one-pixel gaps: fractional lines would beat against the screen's own pixels (moiré).
    val step = pitch.toInt().coerceAtLeast(3)
    val p = android.graphics.Paint().apply { color = android.graphics.Color.argb(46, 0, 0, 0) }
    for (x in 0 until w step step) c.drawRect(x.toFloat(), 0f, x + 1f, h.toFloat(), p)
    for (y in 0 until h step step) c.drawRect(0f, y.toFloat(), w.toFloat(), y + 1f, p)
    // Slight vignette: LED panels are dimmer at the edges.
    p.shader = android.graphics.RadialGradient(w / 2f, h / 2f, maxOf(w, h) * 0.75f, android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(90, 0, 0, 0), android.graphics.Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    return bmp.asImageBitmap()
}

/** Steel legs with cross-bracing down to the roof slab. */
private fun DrawScope.scaffold(board: Rect, roofY: Float, s: Float) {
    val steel = Color(0xFF2A3146)
    val edge = Color(0xFF46516E)
    val legs = listOf(board.left + board.width * 0.16f, board.right - board.width * 0.16f)
    val w = s * 0.014f
    legs.forEach { x -> drawLine(steel, Offset(x, board.bottom), Offset(x, roofY), w); drawLine(edge, Offset(x - w / 3, board.bottom), Offset(x - w / 3, roofY), w / 4) }
    val levels = 3
    val step = (roofY - board.bottom) / levels
    for (k in 0 until levels) {
        val y0 = board.bottom + k * step
        val y1 = y0 + step
        drawLine(steel, Offset(legs[0], y0), Offset(legs[1], y1), w * 0.5f)
        drawLine(steel, Offset(legs[1], y0), Offset(legs[0], y1), w * 0.5f)
        drawLine(edge, Offset(legs[0], y1), Offset(legs[1], y1), w * 0.35f)
    }
    // Roof slab.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0D1222), Color(0xFF05070F)), startY = roofY, endY = s), Offset(s * 0.02f, roofY), Size(s * 0.96f, s - roofY))
    drawLine(Color(0xFF19E6FF).copy(alpha = 0.25f), Offset(s * 0.02f, roofY), Offset(s * 0.98f, roofY), s * 0.003f)
}

/** The wet roof mirrors the screen: flipped, squashed, faint. */
private fun DrawScope.roofReflection(image: ImageBitmap?, screen: Rect, roofY: Float, s: Float, cyan: Color, pink: Color) {
    clipRect(s * 0.02f, roofY, s * 0.98f, s) {
        if (image != null) {
            withTransform({ scale(1f, -0.22f, pivot = Offset(screen.center.x, roofY)) }) {
                drawCropped(image, screen.translate(Offset(0f, roofY - screen.bottom)), alpha = 0.22f)
            }
        }
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, pink.copy(alpha = 0.10f), cyan.copy(alpha = 0.08f), Color.Transparent)), Offset(s * 0.02f, roofY), Size(s * 0.96f, s - roofY))
    }
}

/** Four lamps on a bar above the board, throwing warm light cones onto the screen. */
private fun DrawScope.lampBar(board: Rect, s: Float, level: Float, lit: Float) {
    val barY = board.top - s * 0.045f
    drawLine(Color(0xFF3A4560), Offset(board.left + s * 0.03f, barY), Offset(board.right - s * 0.03f, barY), s * 0.008f)
    for (k in 0 until 4) {
        val x = board.left + board.width * (0.14f + 0.24f * k)
        drawLine(Color(0xFF3A4560), Offset(x, barY), Offset(x, barY + s * 0.02f), s * 0.005f)
        val head = Offset(x, barY + s * 0.025f)
        val cone = Path().apply {
            moveTo(x - s * 0.014f, head.y); lineTo(x + s * 0.014f, head.y)
            lineTo(x + s * 0.11f, board.top + board.height * 0.62f); lineTo(x - s * 0.11f, board.top + board.height * 0.62f); close()
        }
        drawPath(cone, Brush.verticalGradient(listOf(Color(0xFFFFF1C9).copy(alpha = (0.20f + 0.12f * level) * lit), Color.Transparent), startY = head.y, endY = board.top + board.height * 0.62f))
        drawRoundRect(Color(0xFF1A2032), Offset(x - s * 0.018f, barY + s * 0.012f), Size(s * 0.036f, s * 0.018f), CornerRadius(s * 0.006f))
        drawCircle(Color(0xFFFFF4D6).copy(alpha = 0.6f + 0.4f * lit), s * 0.006f, head)
    }
}

/** A straight glass tube: wide faint halo, mid glow, bright core. */
private fun DrawScope.neonLine(a: Offset, b: Offset, color: Color, width: Float, lit: Float) {
    for ((w, alpha) in listOf(width * 5f to 0.10f, width * 2.6f to 0.26f, width * 1.4f to 0.75f)) drawLine(color.copy(alpha = alpha * lit), a, b, w, StrokeCap.Round)
    drawLine(lerp(color, Color.White, 0.6f).copy(alpha = lit), a, b, width * 0.5f, StrokeCap.Round)
}

/** A record with a note, bent out of two neon tubes, on the screen of songs without art. */
private fun DrawScope.neonPlaceholder(screen: Rect, cyan: Color, pink: Color, lit: Float) {
    drawRect(Color(0xFF060914), screen.topLeft, screen.size)
    val s = minOf(screen.width, screen.height)
    val c = Offset(screen.left + screen.width * 0.42f, screen.top + screen.height * 0.55f)
    fun tube(color: Color, draw: (Color, Float) -> Unit) {
        for ((w, a) in listOf(s * 0.05f to 0.12f, s * 0.026f to 0.3f, s * 0.013f to 0.85f)) draw(color.copy(alpha = a * lit), w)
        draw(lerp(color, Color.White, 0.6f).copy(alpha = lit), s * 0.006f)
    }
    tube(pink) { col, w -> drawCircle(col, s * 0.30f, c, style = Stroke(w)) }
    tube(pink) { col, w -> drawCircle(col, s * 0.08f, c, style = Stroke(w)) }
    tube(cyan) { col, w ->
        val stem = Offset(screen.left + screen.width * 0.76f, screen.top + screen.height * 0.24f)
        drawLine(col, stem, stem + Offset(0f, s * 0.42f), w, StrokeCap.Round)
        drawLine(col, stem, stem + Offset(s * 0.13f, s * 0.07f), w, StrokeCap.Round)
        drawCircle(col, s * 0.07f, stem + Offset(-s * 0.07f, s * 0.42f), style = Stroke(w))
    }
}

// ---- Sport Cockpit ---------------------------------------------------------------------------------------------

/** The needle's position, carried from frame to frame so it moves with some inertia instead of jumping. */
private class NeedleState {
    var value = 0f
    var timeText: TextLayoutResult? = null
    var timeShown = -1L
}

/**
 * Rev counter (CR-07.1): the needle pivots on the gauge's centre under a hub cap and sweeps the same 270° as the ticks,
 * driven by the music with some inertia; the progress is the ice-blue arc that turns red in the red zone, and the
 * elapsed time sits in a digital window in the gap at the bottom. Every size is relative to the gauge, so it lines up
 * on any screen.
 */
@Composable
private fun GaugeCover(state: PlayerUiState, image: ImageBitmap?, audio: AudioLevels, red: Color, ice: Color, size: Dp, modifier: Modifier) {
    val colors = CruiseTheme.colors
    val needle = remember { NeedleState() }
    val measurer = rememberTextMeasurer()
    val digital = CruiseTheme.typography.meta.fontFamily ?: FontFamily.Monospace
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            audio.frame
            val r = this.size.minDimension / 2
            val c = center
            // Bezel and face.
            drawCircle(Brush.radialGradient(listOf(colors.surfaceElevated, colors.background), center = c, radius = r), r, c)
            drawCircle(Color.White.copy(alpha = 0.10f), r * 0.985f, c, style = Stroke(r * 0.012f))
            // Progress arc: ice blue, red in the last 15 %.
            val arcR = r * 0.9f
            val arcStroke = r * 0.045f
            val arcBox = Rect(c, arcR)
            val fraction = if (state.durationMs > 0) (state.positionAt(audio.now).toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            drawArc(colors.divider, START, SWEEP, false, arcBox.topLeft, arcBox.size, style = Stroke(arcStroke))
            drawArc(ice, START, SWEEP * minOf(fraction, RED_ZONE), false, arcBox.topLeft, arcBox.size, style = Stroke(arcStroke))
            if (fraction > RED_ZONE) drawArc(red, START + SWEEP * RED_ZONE, SWEEP * (fraction - RED_ZONE), false, arcBox.topLeft, arcBox.size, style = Stroke(arcStroke))
            // Ticks, inside the arc.
            for (i in 0..TICKS) {
                val a = Math.toRadians((START + SWEEP * i / TICKS).toDouble())
                val major = i % 6 == 0
                val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
                val outer = r * 0.82f
                val inner = r * if (major) 0.71f else 0.76f
                val hot = i.toFloat() / TICKS >= RED_ZONE
                drawLine(
                    if (hot) red else colors.textSecondary.copy(alpha = if (major) 1f else 0.6f),
                    c + dir * inner, c + dir * outer, strokeWidth = r * if (major) 0.016f else 0.008f, cap = StrokeCap.Round,
                )
            }
        }
        Box(Modifier.size(size * 0.6f).clip(CircleShape)) { Art(image, state) }
        Canvas(Modifier.fillMaxSize()) {
            audio.frame
            val r = this.size.minDimension / 2
            val c = center
            // Needle with inertia: rises fast, falls slowly, rests at zero when the music stops.
            val target = if (state.isPlaying) 0.05f + 0.9f * (audio.level * 2.2f).coerceIn(0f, 1f) else 0f
            needle.value += (target - needle.value) * if (target > needle.value) 0.35f else 0.12f
            val a = Math.toRadians((START + SWEEP * needle.value).toDouble())
            val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
            val tip = c + dir * (r * 0.8f)
            val tail = c - dir * (r * 0.14f)
            drawLine(red.copy(alpha = 0.25f), tail, tip, r * 0.05f, StrokeCap.Round)
            drawLine(red, tail, tip, r * 0.018f, StrokeCap.Round)
            // Hub cap.
            drawCircle(Brush.radialGradient(listOf(Color(0xFF3A414B), Color(0xFF14171C)), center = c, radius = r * 0.1f), r * 0.095f, c)
            drawCircle(red, r * 0.095f, c, style = Stroke(r * 0.012f))
            drawCircle(Color(0xFFDDE3EA), r * 0.025f, c)
            // Digital time window in the gap at the bottom.
            val seconds = state.positionAt(audio.now) / 1000
            if (seconds != needle.timeShown || needle.timeText == null) {
                needle.timeShown = seconds
                needle.timeText = measurer.measure(formatDuration(seconds * 1000), TextStyle(fontFamily = digital, color = ice, fontSize = (r * 0.11f).toSp()))
            }
            val text = needle.timeText!!
            val window = Rect(Offset(c.x - text.size.width / 2f - r * 0.06f, c.y + r * 0.66f), Size(text.size.width + r * 0.12f, text.size.height + r * 0.03f))
            drawRoundRect(Color(0xFF07090C), window.topLeft, window.size, CornerRadius(r * 0.03f))
            drawRoundRect(ice.copy(alpha = 0.35f), window.topLeft, window.size, CornerRadius(r * 0.03f), style = Stroke(r * 0.006f))
            drawText(text, topLeft = Offset(window.left + r * 0.06f, window.top + r * 0.015f))
        }
    }
}

private const val START = 135f
private const val SWEEP = 270f
private const val TICKS = 54
private const val RED_ZONE = 0.85f
