package com.boulbeba.cruise.feature.nowplaying

import android.media.AudioManager
import android.os.SystemClock
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.ui.formatDuration
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the gesture layer shows while the finger is down or just after an action. */
sealed interface GestureCue {
    /** Finger resting: the ring fills until the hold triggers. */
    data class Hold(val at: Offset, val progress: Float) : GestureCue
    data class Scrub(val at: Offset, val positionMs: Long, val durationMs: Long) : GestureCue
    data class Volume(val level: Float) : GestureCue
    /** Swipe past the threshold: which way it will skip. */
    data class Skip(val forward: Boolean, val armed: Boolean) : GestureCue
    /** Confirmation of a finished action. */
    data class Done(@DrawableRes val icon: Int, val stamp: Long = SystemClock.uptimeMillis()) : GestureCue
}

@Stable
class GestureCues {
    var cue by mutableStateOf<GestureCue?>(null)
}

/** The actions gestures can trigger; reads the live player state through lambdas so a gesture never sees stale data. */
class GestureActions(
    val isPlaying: () -> Boolean,
    val positionMs: () -> Long,
    val durationMs: () -> Long,
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seekTo: (Long) -> Unit,
    val audio: AudioManager?,
)

private enum class Mode { Pending, Held, Scrub, SwipeH, SwipeV }

private const val HOLD_SHOW_MS = 140L
private const val HOLD_MS = 520L

/**
 * Whole-screen gestures for Now Playing (owner's request 2026-10-23) — no overlay, ordinary taps untouched:
 * hold = play/pause, hold + slide = scrub, swipe left/right = next/previous, swipe up/down = volume.
 *
 * Controls keep priority: the layer watches each event before the children (Initial pass) and again after them
 * (Final pass); if a child consumed it (seek bar drag, lyrics scroll) the layer steps aside. Once the layer claims a
 * gesture it consumes the events first, so the button under the finger cancels its click.
 */
fun Modifier.nowPlayingGestures(enabled: Boolean, cues: GestureCues, actions: GestureActions, haptics: HapticFeedback): Modifier =
    if (!enabled) this else pointerInput(actions) {
        val slop = viewConfiguration.touchSlop
        val skipDistance = 90.dp.toPx()
        val volumeStep = 38.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val id = down.id
            val start = down.position
            val t0 = SystemClock.uptimeMillis()
            var mode = Mode.Pending
            var scrubBase = 0L
            var scrubTarget = 0L
            var volumeSteps = 0
            fun holdNow() {
                mode = Mode.Held
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                cues.cue = GestureCue.Hold(start, 1f)
            }
            while (true) {
                val event = withTimeoutOrNull(32) { awaitPointerEvent(PointerEventPass.Initial) }
                val elapsed = SystemClock.uptimeMillis() - t0
                if (event == null) {
                    if (mode == Mode.Pending) {
                        if (elapsed >= HOLD_MS) holdNow()
                        else if (elapsed >= HOLD_SHOW_MS) cues.cue = GestureCue.Hold(start, ((elapsed - HOLD_SHOW_MS).toFloat() / (HOLD_MS - HOLD_SHOW_MS)).coerceIn(0f, 1f))
                    }
                    continue
                }
                if (event.changes.count { it.pressed } > 1) { // two fingers: not ours
                    if (mode == Mode.Pending || mode == Mode.Held) cues.cue = null
                    if (mode == Mode.Pending) return@awaitEachGesture
                }
                val change = event.changes.firstOrNull { it.id == id } ?: break
                val delta = change.position - start
                val claimed = mode != Mode.Pending
                if (claimed) event.changes.forEach { it.consume() }

                if (!change.pressed) {
                    when (mode) {
                        Mode.Held -> {
                            val wasPlaying = actions.isPlaying()
                            actions.playPause()
                            cues.cue = GestureCue.Done(if (wasPlaying) CruiseIcons.Pause else CruiseIcons.Play)
                        }
                        Mode.Scrub -> {
                            actions.seekTo(scrubTarget)
                            cues.cue = GestureCue.Done(CruiseIcons.Play)
                        }
                        Mode.SwipeH -> when {
                            delta.x <= -skipDistance -> { actions.next(); cues.cue = GestureCue.Done(CruiseIcons.Next) }
                            delta.x >= skipDistance -> { actions.previous(); cues.cue = GestureCue.Done(CruiseIcons.Previous) }
                            else -> cues.cue = null
                        }
                        Mode.SwipeV -> cues.cue = null
                        Mode.Pending -> cues.cue = null // an ordinary tap: the control under it handles it
                    }
                    break
                }

                when (mode) {
                    Mode.Pending -> {
                        // Let the children see this event, then check whether one of them took it.
                        val final = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == id } ?: break
                        if (final.isConsumed) { cues.cue = null; return@awaitEachGesture }
                        when {
                            delta.getDistance() > slop -> {
                                mode = if (abs(delta.x) > abs(delta.y)) Mode.SwipeH else Mode.SwipeV
                                final.consume()
                                cues.cue = null
                            }
                            elapsed >= HOLD_MS -> holdNow()
                            elapsed >= HOLD_SHOW_MS -> cues.cue = GestureCue.Hold(start, ((elapsed - HOLD_SHOW_MS).toFloat() / (HOLD_MS - HOLD_SHOW_MS)).coerceIn(0f, 1f))
                        }
                    }
                    Mode.Held -> if (abs(delta.x) > slop * 2) {
                        mode = Mode.Scrub
                        scrubBase = actions.positionMs()
                        scrubTarget = scrubBase
                    }
                    Mode.Scrub -> {
                        val duration = actions.durationMs().coerceAtLeast(1)
                        // Sliding across the whole screen covers the whole song.
                        scrubTarget = (scrubBase + delta.x / size.width * duration).toLong().coerceIn(0, duration)
                        cues.cue = GestureCue.Scrub(change.position, scrubTarget, duration)
                    }
                    Mode.SwipeH -> cues.cue = GestureCue.Skip(forward = delta.x < 0, armed = abs(delta.x) >= skipDistance)
                    Mode.SwipeV -> {
                        val am = actions.audio ?: continue
                        val steps = (-delta.y / volumeStep).toInt()
                        while (steps > volumeSteps) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0); volumeSteps++ }
                        while (steps < volumeSteps) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0); volumeSteps-- }
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        cues.cue = GestureCue.Volume(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max)
                    }
                }
            }
        }
    }

/** Draws the gesture cues over Now Playing. Never takes touches. */
@Composable
fun BoxScope.GestureCueLayer(cues: GestureCues, accent: Color) {
    val colors = CruiseTheme.colors
    val density = LocalDensity.current
    when (val cue = cues.cue) {
        is GestureCue.Hold -> Canvas(Modifier.fillMaxSize()) {
            val r = 46.dp.toPx()
            drawCircle(accent.copy(alpha = 0.14f * cue.progress + 0.06f), r, cue.at)
            drawArc(accent, -90f, 360f * cue.progress, false, Offset(cue.at.x - r, cue.at.y - r), Size(r * 2, r * 2), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        }
        is GestureCue.Scrub -> {
            val x = with(density) { cue.at.x.toDp() }
            val y = with(density) { cue.at.y.toDp() }
            Column(
                Modifier.offset(x - 70.dp, y - 110.dp).width(140.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceElevated.copy(alpha = 0.95f)).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(formatDuration(cue.positionMs), style = CruiseTheme.typography.title, color = accent)
                Text("/ " + formatDuration(cue.durationMs), style = CruiseTheme.typography.meta, color = colors.textSecondary)
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.divider)) {
                    Box(Modifier.fillMaxWidth(cue.positionMs.toFloat() / cue.durationMs).fillMaxHeight().background(accent))
                }
            }
        }
        is GestureCue.Volume -> Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 28.dp).width(56.dp).height(260.dp).clip(RoundedCornerShape(28.dp)).background(colors.surfaceElevated.copy(alpha = 0.92f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(cue.level.coerceIn(0.04f, 1f)).background(accent.copy(alpha = 0.85f)))
            Icon(painterResource(CruiseIcons.Speaker), null, tint = colors.textPrimary, modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(26.dp))
            Text("${(cue.level * 100).roundToInt()}", style = CruiseTheme.typography.label, color = colors.textPrimary, modifier = Modifier.padding(bottom = 12.dp))
        }
        is GestureCue.Skip -> Box(
            Modifier.align(if (cue.forward) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 32.dp).size(96.dp)
                .graphicsLayer { alpha = if (cue.armed) 1f else 0.45f; scaleX = if (cue.armed) 1f else 0.85f; scaleY = scaleX }
                .clip(CircleShape).background(if (cue.armed) accent else colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(if (cue.forward) CruiseIcons.Next else CruiseIcons.Previous), null, tint = if (cue.armed) colors.onAccent else colors.textPrimary, modifier = Modifier.size(52.dp))
        }
        is GestureCue.Done -> {
            val fade = remember(cue.stamp) { Animatable(1f) }
            LaunchedEffect(cue.stamp) {
                fade.animateTo(0f, tween(700, delayMillis = 250))
                if (cues.cue == cue) cues.cue = null
            }
            Box(
                Modifier.align(Alignment.Center).size(150.dp).graphicsLayer { alpha = fade.value; scaleX = 0.9f + 0.2f * (1f - fade.value); scaleY = scaleX }
                    .clip(CircleShape).background(accent.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(cue.icon), null, tint = colors.onAccent, modifier = Modifier.size(84.dp))
            }
        }
        null -> Unit
    }
}

