package com.boulbeba.cruise.feature.nowplaying

import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.designsystem.component.CruiseIconButton
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.model.AudioFiles
import com.boulbeba.cruise.core.model.MediaIds
import com.boulbeba.cruise.core.playback.PlayerConnection
import com.boulbeba.cruise.core.playback.PlayerUiState
import com.boulbeba.cruise.core.ui.formatDuration
import com.boulbeba.cruise.core.ui.qualityLabel
import java.io.File
import kotlin.math.abs

/** Synced lyrics (SPEC ART-7): the current line large and highlighted, scrolling smoothly with the song. */
@Composable
fun LyricsPanel(lyrics: Lyrics, state: PlayerUiState, audio: AudioLevels, accent: Color, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val colors = CruiseTheme.colors
    if (lyrics.synced.isEmpty()) {
        LazyColumn(modifier, contentPadding = PaddingValues(vertical = 24.dp)) {
            item { Text(lyrics.plain.orEmpty(), style = CruiseTheme.typography.subtitle, color = colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
        return
    }
    val position = state.positionAt(audio.now)
    val current = lyrics.synced.indexOfLast { it.timeMs <= position }.coerceAtLeast(0)
    val list = rememberLazyListState()
    LaunchedEffect(current) { list.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    LazyColumn(modifier, state = list, verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 48.dp)) {
        itemsIndexed(lyrics.synced) { i, line ->
            val scale by animateFloatAsState(if (i == current) 1.12f else 1f, label = "lyricScale")
            Text(
                line.text.ifBlank { "♪" },
                style = if (i == current) CruiseTheme.typography.title else CruiseTheme.typography.subtitle,
                color = when {
                    i == current -> accent
                    i < current -> colors.textSecondary.copy(alpha = 0.5f)
                    else -> colors.textPrimary.copy(alpha = 0.8f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }.clickable { onSeek(line.timeMs) },
            )
        }
    }
}

/** Queue panel (SPEC Q-1): what plays next; tap to jump, remove, move. */
@Composable
fun QueuePanel(queue: List<Pair<String, TrackRow?>>, currentIndex: Int, player: PlayerConnection, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CruiseTheme.colors
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0))
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).background(colors.surface.copy(alpha = 0.96f)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.np_queue, queue.size), style = CruiseTheme.typography.subtitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
            CruisePill(stringResource(R.string.np_queue_clear), onClick = player::clearQueueExceptCurrent, icon = CruiseIcons.Delete)
            Spacer(Modifier.width(8.dp))
            CruiseIconButton(CruiseIcons.Back, stringResource(R.string.np_close), onClick = onClose)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(queue, key = { i, (id, _) -> "$i:$id" }) { i, (id, row) ->
                val active = i == currentIndex
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (active) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { player.jumpTo(i) }.padding(horizontal = 8.dp),
                ) {
                    Text((i + 1).toString(), style = CruiseTheme.typography.meta, color = colors.textSecondary, modifier = Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(row?.title ?: AudioFiles.displayName(File(MediaIds.filePath(id))), style = CruiseTheme.typography.body, color = if (active) colors.accent else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(row?.artist.orEmpty(), style = CruiseTheme.typography.meta, color = colors.textSecondary, maxLines = 1)
                    }
                    if (!active) {
                        CruiseIconButton(CruiseIcons.Chevron, stringResource(R.string.np_move_up), onClick = { player.moveInQueue(i, i - 1) }, size = 48.dp, modifier = Modifier.graphicsLayer { rotationZ = -90f })
                        CruiseIconButton(CruiseIcons.Delete, stringResource(R.string.np_remove), onClick = { player.removeFromQueue(i) }, size = 48.dp)
                    }
                }
            }
        }
    }
}

/** Codec, rate, depth, bitrate, file — SPEC ART-9. */
@Composable
fun TrackInfoPanel(track: TrackRow?, state: PlayerUiState, plays: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CruiseTheme.colors
    Column(modifier.clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).background(colors.surface.copy(alpha = 0.96f)).clickable(onClick = onClose).padding(20.dp)) {
        Text(stringResource(R.string.np_info), style = CruiseTheme.typography.subtitle, color = colors.textPrimary)
        Spacer(Modifier.height(10.dp))
        fun line(k: String, v: String) = k to v
        val lines = listOfNotNull(
            line(stringResource(R.string.np_info_format), track?.let { qualityLabel(it.codec, it.sampleRate, it.bitDepth, it.lossless) } ?: state.path?.substringAfterLast('.')?.uppercase().orEmpty()),
            track?.takeIf { it.sampleRate > 0 }?.let { line(stringResource(R.string.np_info_rate), "${it.sampleRate} Hz · ${if (it.bitDepth > 0) "${it.bitDepth}-bit" else "—"}") },
            line(stringResource(R.string.np_info_length), formatDuration(state.durationMs)),
            track?.takeIf { it.year > 0 }?.let { line(stringResource(R.string.np_info_year), it.year.toString()) },
            track?.takeIf { it.genre.isNotBlank() }?.let { line(stringResource(R.string.np_info_genre), it.genre) },
            line(stringResource(R.string.np_info_plays), plays.toString()),
            line(stringResource(R.string.np_info_file), state.path.orEmpty()),
        )
        lines.forEach { (k, v) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(k, style = CruiseTheme.typography.meta, color = colors.textSecondary, modifier = Modifier.width(120.dp))
                Text(v, style = CruiseTheme.typography.meta, color = colors.textPrimary)
            }
        }
    }
}

/**
 * Blind mode (SPEC CAR-9): the whole screen is a gesture pad — tap play/pause, swipe left/right next/previous,
 * swipe up/down volume. Big feedback, nothing to aim at. Exit with the button in the corner.
 */
@Composable
fun BlindMode(state: PlayerUiState, player: PlayerConnection, onExit: () -> Unit) {
    val colors = CruiseTheme.colors
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var feedback by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(feedback) { if (feedback != null) { kotlinx.coroutines.delay(700); feedback = null } }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.97f))
            .pointerInput(Unit) { detectTapGestures { player.playPause(); feedback = if (state.isPlaying) CruiseIcons.Pause else CruiseIcons.Play } }
            .pointerInput(Unit) {
                var total = androidx.compose.ui.geometry.Offset.Zero
                detectDragGestures(onDragStart = { total = androidx.compose.ui.geometry.Offset.Zero }, onDragEnd = {
                    val (dx, dy) = total
                    when {
                        abs(dx) > abs(dy) && dx < -120 -> { player.next(); feedback = CruiseIcons.Next }
                        abs(dx) > abs(dy) && dx > 120 -> { player.previous(); feedback = CruiseIcons.Previous }
                        abs(dy) > 120 -> {
                            audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (dy < 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                            feedback = CruiseIcons.Speaker
                        }
                    }
                }) { _, drag -> total += drag }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(feedback ?: if (state.isPlaying) CruiseIcons.Pause else CruiseIcons.Play), null, tint = colors.accent, modifier = Modifier.size(if (feedback != null) 200.dp else 140.dp))
            Spacer(Modifier.height(16.dp))
            Text(state.title, style = CruiseTheme.typography.display, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 48.dp))
            Text(stringResource(R.string.np_blind_hint), style = CruiseTheme.typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
        }
        CruisePill(stringResource(R.string.np_blind_exit), onClick = onExit, icon = CruiseIcons.Back, modifier = Modifier.align(Alignment.TopEnd).padding(24.dp))
    }
}
