package com.boulbeba.cruise.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.designsystem.skin.rememberAnimationTime
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Neon City's track sign (CHANGE_REQUESTS.md CR-15): a neon-outlined prop plane tows an advertising banner across the
 * night sky, the title in pink tubes and the artist in cyan. On a new track the plane flies off to the left and the
 * next one comes in from the right, slowing to a cruise; the banner's tubes light up from the leading edge, blink once
 * and stay lit, while the cloth flutters — a wave running to its free end — and the plane bobs. Names too long for the
 * banner scroll along it.
 *
 * Cheap on the unit: each line of text is drawn once per track, glow included, into a bitmap; each frame slices it into
 * narrow strips shifted by the wave, so nothing is laid out again and no blur runs per frame. Paused, it holds still.
 */
@Composable
internal fun NeonBannerPlane(title: String, artist: String, pink: Color, cyan: Color, playing: Boolean, modifier: Modifier = Modifier) {
    val type = CruiseTheme.typography
    val quality = CruiseTheme.quality
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val time = rememberAnimationTime(quality, running = playing)
    var shown by remember { mutableStateOf(title to artist) }
    val fly = remember { Animatable(1f) } // 1 = off to the right, 0 = cruising, -1 = gone to the left
    val reveal = remember { Animatable(0f) }
    val lit = remember { Animatable(1f) }
    val marqueeFrom = remember { mutableFloatStateOf(Float.MAX_VALUE) }
    LaunchedEffect(title, artist) {
        if (quality == VisualQuality.Saver) {
            shown = title to artist
            fly.snapTo(0f)
            reveal.snapTo(1f)
            marqueeFrom.floatValue = Float.MAX_VALUE
            return@LaunchedEffect
        }
        if (shown != (title to artist) && fly.value == 0f) fly.animateTo(-1f, tween(520, easing = FastOutLinearInEasing))
        shown = title to artist
        marqueeFrom.floatValue = Float.MAX_VALUE
        reveal.snapTo(0f)
        fly.snapTo(1f)
        fly.animateTo(0f, tween(1400, easing = LinearOutSlowInEasing))
        reveal.animateTo(1f, tween(650, easing = LinearEasing))
        // One blink as the sign settles: a single flash per track (SPEC §5.4).
        lit.snapTo(0.35f)
        delay(80)
        lit.animateTo(1f, tween(240))
        marqueeFrom.floatValue = time.value + MARQUEE_PAUSE_S
    }

    val titleStyle = type.display
    val titleLine = remember(shown.first, titleStyle, pink, density) { NeonLine.of(measurer, shown.first, titleStyle, pink, density) }
    val artistLine = remember(shown.second, type.subtitle, cyan, density) {
        shown.second.takeIf { it.isNotBlank() }?.let { NeonLine.of(measurer, it, type.subtitle, cyan, density) }
    }
    val bannerHeightPx = titleLine.textHeight + (artistLine?.textHeight ?: 0) + with(density) { BANNER_PAD_Y.toPx() } * 2
    val height = with(density) { max(bannerHeightPx, PLANE_W.toPx() * PLANE_RATIO).toDp() } + 30.dp

    Spacer(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { text = AnnotatedString(listOf(title, artist).filter { it.isNotBlank() }.joinToString("\n")) }
            .graphicsLayer {
                val f = fly.value
                translationX = if (f >= 0f) f * (size.width + 40.dp.toPx()) else f * (size.width + 80.dp.toPx())
                alpha = if (f >= 0f) min(1f, (1f - f) * 2.5f) else min(1f, (1f + f) * 2f)
            }
            .drawWithCache {
                val planeW = PLANE_W.toPx()
                val planeH = planeW * PLANE_RATIO
                val plane = PlanePaths(planeW, planeH)
                val bannerLeft = planeW + ROPE.toPx()
                val padX = BANNER_PAD_X.toPx()
                val padY = BANNER_PAD_Y.toPx()
                val textW = max(titleLine.textWidth, artistLine?.textWidth ?: 0)
                val room = size.width - bannerLeft - 4.dp.toPx()
                val bannerW = min(room, max(textW + padX * 2, room * 0.62f))
                val bannerH = bannerHeightPx
                val innerW = bannerW - padX * 2
                val strips = (bannerW / STRIP.toPx()).toInt().coerceIn(12, 96)
                val xs = FloatArray(strips + 1) { bannerLeft + bannerW * it / strips }
                val dys = FloatArray(strips + 1)
                val slopes = FloatArray(strips + 1)
                val cloth = Path()
                val quad = Path()
                val amp = 5.dp.toPx()
                val bob = 3.dp.toPx()
                val gap = MARQUEE_GAP.toPx()
                val speed = MARQUEE_SPEED.toPx()
                val bodyPen = NeonPen(1.6.dp.toPx())
                val thinPen = NeonPen(1.1.dp.toPx())
                val ropeStroke = 1.dp.toPx()
                val dark = Color(0xFF0B1224)
                onDrawBehind {
                    val t = time.value
                    val glow = lit.value
                    val shownReveal = reveal.value
                    // The cloth: a wave travelling to the free end, growing towards it.
                    for (i in 0..strips) {
                        val u = i.toFloat() / strips
                        val phase = u * 2f * PI.toFloat() * 1.4f - t * 4.5f
                        val grow = 0.15f + 0.85f * u
                        dys[i] = amp * grow * sin(phase)
                        slopes[i] = grow * cos(phase)
                    }
                    val top = (size.height - bannerH) / 2f + bob * 0.6f * sin(t * 1.15f)
                    cloth.reset()
                    cloth.moveTo(xs[0], top + dys[0])
                    for (i in 1..strips) cloth.lineTo(xs[i], top + dys[i])
                    for (i in strips downTo 0) cloth.lineTo(xs[i], top + bannerH + dys[i])
                    cloth.close()

                    // Tow rope: tail hook → ring → both ends of the banner's pole.
                    val planeTop = (size.height - bannerH) / 2f + bannerH * 0.42f - planeH / 2f + bob * sin(t * 1.15f + 0.7f)
                    val hook = Offset(planeW * 0.97f, planeTop + planeH * 0.47f)
                    val poleTop = Offset(bannerLeft, top + dys[0])
                    val poleBottom = Offset(bannerLeft, top + bannerH + dys[0])
                    val ring = Offset(planeW + (bannerLeft - planeW) * 0.45f, (hook.y + (poleTop.y + poleBottom.y) / 2f) / 2f)
                    val rope = Color(0xFF8A96B8).copy(alpha = 0.75f)
                    drawLine(rope, hook, ring, ropeStroke)
                    drawLine(rope, ring, poleTop, ropeStroke)
                    drawLine(rope, ring, poleBottom, ropeStroke)

                    drawPath(cloth, Color(0xE60A0E1C))
                    for (i in 0 until strips) {
                        val m = (slopes[i] + slopes[i + 1]) / 2f
                        quad.reset()
                        quad.moveTo(xs[i], top + dys[i])
                        quad.lineTo(xs[i + 1], top + dys[i + 1])
                        quad.lineTo(xs[i + 1], top + bannerH + dys[i + 1])
                        quad.lineTo(xs[i], top + bannerH + dys[i])
                        quad.close()
                        drawPath(quad, if (m > 0f) Color.Black.copy(alpha = 0.22f * m) else Color.White.copy(alpha = -0.06f * m))
                    }
                    neonPath(cloth, cyan, thinPen, glow * (0.4f + 0.6f * shownReveal))
                    drawLine(Color(0xFF9AA6C6), poleTop, poleBottom, 2.2.dp.toPx(), StrokeCap.Round)

                    // The two lines of tubes, sliced along the wave.
                    val marquee = max(0f, t - marqueeFrom.floatValue) * speed
                    val lines = listOfNotNull(titleLine to top + padY, artistLine?.let { it to top + padY + titleLine.textHeight })
                    for ((line, lineTop) in lines) {
                        val scrolls = line.textWidth > innerW
                        val period = line.textWidth + gap
                        for (i in 0 until strips) {
                            val x0 = xs[i].roundToInt()
                            val x1 = xs[i + 1].roundToInt()
                            val u = (i + 0.5f) / strips
                            val dy = (dys[i] + dys[i + 1]) / 2f
                            val cx = x0 - (bannerLeft + padX)
                            val on = ((shownReveal * 1.15f - u) * 10f).coerceIn(0f, 1f)
                            var a = (0.10f + 0.90f * on) * glow
                            val y = (lineTop - line.pad + dy).roundToInt()
                            if (scrolls) {
                                val edge = min((x0 - bannerLeft) / padX, (bannerLeft + bannerW - x1) / padX).coerceIn(0f, 1f)
                                a *= edge
                                val c = if (line.rtl) cx + (line.textWidth - innerW) - marquee else cx + marquee
                                val wrapped = c - period * kotlin.math.floor(c / period)
                                line.slice(this, wrapped, x0, x1, y, a)
                                line.slice(this, wrapped - period, x0, x1, y, a)
                            } else {
                                line.slice(this, cx - (innerW - line.textWidth) / 2f, x0, x1, y, a)
                            }
                        }
                    }

                    // The plane, nose to the left, pitching gently with its bob.
                    translate(0f, planeTop) {
                        rotate(1.2f * cos(t * 1.15f + 0.7f), Offset(planeW * 0.5f, planeH * 0.5f)) {
                            drawPath(plane.body, dark)
                            drawPath(plane.wing, dark)
                            drawPath(plane.window, cyan.copy(alpha = 0.16f * glow))
                            neonPath(plane.fin, cyan, bodyPen, glow)
                            neonPath(plane.body, cyan, bodyPen, glow)
                            neonPath(plane.wing, cyan, thinPen, glow)
                            neonPath(plane.window, cyan, thinPen, glow * 0.8f)
                            neonPath(plane.stripe, pink, thinPen, glow)
                            neonPath(plane.gear, cyan, thinPen, glow * 0.8f)
                            // Propeller: a faint disc and a blade turning in it.
                            val hub = Offset(planeW * 0.06f, planeH * 0.56f)
                            drawOval(cyan.copy(alpha = 0.12f), Offset(hub.x - planeW * 0.018f, hub.y - planeH * 0.45f), Size(planeW * 0.036f, planeH * 0.9f))
                            val blade = planeH * 0.45f * cos(t * 40f)
                            drawLine(lerp(cyan, Color.White, 0.5f).copy(alpha = 0.7f), Offset(hub.x, hub.y - blade), Offset(hub.x, hub.y + blade), 1.4.dp.toPx(), StrokeCap.Round)
                            drawCircle(lerp(cyan, Color.White, 0.6f), planeW * 0.022f, hub)
                            // Red light on the fin, breathing slowly (no flashing).
                            val beacon = Offset(planeW * 0.915f, planeH * 0.04f)
                            val breath = (sin(t * 2.2f) + 1f) / 2f
                            drawCircle(Color(0xFFFF3B3B).copy(alpha = 0.18f + 0.3f * breath), 6.dp.toPx(), beacon)
                            drawCircle(Color(0xFFFF6B6B), 2.dp.toPx(), beacon)
                        }
                    }
                }
            },
    )
}

/** One line of the banner, lit: glow and tube drawn once into a bitmap with [pad] of room for the glow around the text. */
private class NeonLine(val image: ImageBitmap, val pad: Int, val textWidth: Int, val textHeight: Int, val rtl: Boolean) {

    /** Draws the part of the text that starts at text coordinate [from] into the columns [x0]–[x1], clipped to the bitmap. */
    fun slice(scope: DrawScope, from: Float, x0: Int, x1: Int, y: Int, alpha: Float) {
        if (alpha <= 0.01f) return
        var src = (from + pad).roundToInt()
        var dst = x0
        var w = x1 - x0
        if (src < 0) { dst -= src; w += src; src = 0 }
        if (src + w > image.width) w = image.width - src
        if (w <= 0) return
        scope.drawImage(image, IntOffset(src, 0), IntSize(w, image.height), IntOffset(dst, y), IntSize(w, image.height), alpha = alpha)
    }

    companion object {
        fun of(measurer: TextMeasurer, text: String, style: TextStyle, color: Color, density: Density): NeonLine {
            val layout = measurer.measure(
                AnnotatedString(text), style, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
                constraints = Constraints(maxWidth = MAX_LINE_PX), density = density,
            )
            val pad = with(density) { 14.dp.roundToPx() }
            val w = layout.size.width + pad * 2
            val h = layout.size.height + pad * 2
            val image = ImageBitmap(w, h)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
                val at = Offset(pad.toFloat(), pad.toFloat())
                drawText(layout, color.copy(alpha = 0.85f), at, shadow = Shadow(color, blurRadius = 11.dp.toPx()))
                drawText(layout, lerp(color, Color.White, 0.72f), at, shadow = Shadow(color, blurRadius = 4.dp.toPx()))
            }
            return NeonLine(image, pad, layout.size.width, layout.size.height, layout.getParagraphDirection(0) == ResolvedTextDirection.Rtl)
        }
    }
}

/** A neon tube's three strokes: wide faint glow, closer glow, near-white core. */
private class NeonPen(width: Float) {
    val wide = Stroke(width * 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val mid = Stroke(width * 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val core = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
}

private fun DrawScope.neonPath(path: Path, color: Color, pen: NeonPen, lit: Float) {
    drawPath(path, color.copy(alpha = 0.16f * lit), style = pen.wide)
    drawPath(path, color.copy(alpha = 0.38f * lit), style = pen.mid)
    drawPath(path, lerp(color, Color.White, 0.55f).copy(alpha = 0.35f + 0.65f * lit), style = pen.core)
}

/** A small prop plane seen from the side, nose to the left, in a [w] × [h] box. */
private class PlanePaths(w: Float, h: Float) {
    val body = Path().apply {
        moveTo(0.07f * w, 0.56f * h)
        cubicTo(0.10f * w, 0.36f * h, 0.22f * w, 0.32f * h, 0.30f * w, 0.32f * h)
        cubicTo(0.34f * w, 0.14f * h, 0.46f * w, 0.12f * h, 0.54f * w, 0.30f * h)
        lineTo(0.86f * w, 0.36f * h)
        lineTo(0.97f * w, 0.40f * h)
        lineTo(0.96f * w, 0.52f * h)
        lineTo(0.30f * w, 0.76f * h)
        cubicTo(0.18f * w, 0.78f * h, 0.09f * w, 0.72f * h, 0.07f * w, 0.56f * h)
        close()
    }
    val fin = Path().apply {
        moveTo(0.78f * w, 0.37f * h)
        lineTo(0.88f * w, 0.04f * h)
        lineTo(0.95f * w, 0.04f * h)
        lineTo(0.96f * w, 0.41f * h)
        moveTo(0.84f * w, 0.47f * h)
        lineTo(1.0f * w, 0.45f * h)
    }
    val wing = Path().apply {
        moveTo(0.33f * w, 0.58f * h)
        lineTo(0.62f * w, 0.52f * h)
        lineTo(0.66f * w, 0.58f * h)
        lineTo(0.36f * w, 0.68f * h)
        close()
    }
    val window = Path().apply {
        moveTo(0.33f * w, 0.31f * h)
        cubicTo(0.37f * w, 0.20f * h, 0.45f * w, 0.19f * h, 0.50f * w, 0.30f * h)
        close()
    }
    val stripe = Path().apply {
        moveTo(0.15f * w, 0.57f * h)
        lineTo(0.92f * w, 0.46f * h)
    }
    val gear = Path().apply {
        moveTo(0.40f * w, 0.74f * h)
        lineTo(0.40f * w, 0.88f * h)
        addOval(Rect(Offset(0.40f * w, 0.93f * h), 0.06f * h))
    }
}

private val PLANE_W = 136.dp
private const val PLANE_RATIO = 0.42f
private val ROPE = 30.dp
private val BANNER_PAD_X = 18.dp
private val BANNER_PAD_Y = 8.dp
private val STRIP = 6.dp
private val MARQUEE_GAP = 56.dp
private val MARQUEE_SPEED = 34.dp
private const val MARQUEE_PAUSE_S = 1.5f
private const val MAX_LINE_PX = 4000
