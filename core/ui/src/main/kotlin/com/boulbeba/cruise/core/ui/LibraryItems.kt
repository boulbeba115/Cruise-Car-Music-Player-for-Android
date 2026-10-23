package com.boulbeba.cruise.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.data.db.AlbumRow
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import java.util.Locale

/** "FLAC 24/96", "MP3", "ALAC 16/44" — SPEC ART-9 badge. */
fun qualityLabel(codec: String, sampleRate: Int, bitDepth: Int, lossless: Boolean): String = when {
    lossless && sampleRate > 0 && bitDepth > 0 -> "$codec $bitDepth/${formatRate(sampleRate)}"
    else -> codec
}

private fun formatRate(rate: Int): String = if (rate % 1000 == 0) "${rate / 1000}" else String.format(Locale.ROOT, "%.1f", rate / 1000f)

fun formatDuration(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

/** One track in any list: cover (optional), number, title, artist · album, quality, duration; long-press for actions. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackItem(
    track: TrackRow,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    showCover: Boolean = true,
    showNumber: Boolean = false,
    subtitle: String = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · "),
) {
    val colors = CruiseTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(CruiseTheme.dimens.listRowHeight)
            .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius * 0.7f))
            .background(if (active) colors.accent.copy(alpha = 0.16f) else colors.surface.copy(alpha = 0.78f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp),
    ) {
        if (showCover) {
            CoverImage(
                key = track.albumKey, trackPath = track.path, folder = track.folder, label = track.album,
                modifier = Modifier.size(48.dp), shape = RoundedCornerShape(CruiseTheme.dimens.cardRadius * 0.4f),
            )
            Spacer(Modifier.width(14.dp))
        } else if (showNumber) {
            Text(
                if (track.trackNo > 0) track.trackNo.toString() else "–",
                style = CruiseTheme.typography.label,
                color = if (active) colors.accent else colors.textSecondary,
                textAlign = TextAlign.End,
                modifier = Modifier.width(36.dp),
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(track.title, style = CruiseTheme.typography.body, color = if (active) colors.accent else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = CruiseTheme.typography.meta, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatDuration(track.durationMs), style = CruiseTheme.typography.meta, color = colors.textSecondary)
            Text(qualityLabel(track.codec, track.sampleRate, track.bitDepth, track.lossless), style = CruiseTheme.typography.meta, color = if (track.lossless) colors.accent else colors.textSecondary, maxLines = 1)
        }
    }
}

/** Album tile for grids. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumCard(album: AlbumRow, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CruiseTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        CoverImage(
            key = album.albumKey, trackPath = album.firstPath, folder = album.folder, label = album.album,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f), shape = RoundedCornerShape(CruiseTheme.dimens.cardRadius * 0.8f),
        )
        Spacer(Modifier.height(8.dp))
        Text(skinTitle(album.album), style = CruiseTheme.typography.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            listOf(album.albumArtist, if (album.year > 0) album.year.toString() else "").filter { it.isNotBlank() }.joinToString(" · "),
            style = CruiseTheme.typography.meta, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Generic group row (artist, genre, year, playlist): cover of a sample album, name, counts. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupItem(
    title: String,
    subtitle: String,
    coverKey: String,
    coverPath: String,
    coverFolder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
) {
    val colors = CruiseTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(CruiseTheme.dimens.listRowHeight + 8.dp)
            .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius * 0.7f))
            .background(colors.surface.copy(alpha = 0.78f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp),
    ) {
        CoverImage(coverKey, coverPath, coverFolder, title, Modifier.size(56.dp), RoundedCornerShape(CruiseTheme.dimens.cardRadius * 0.5f))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(skinTitle(title), style = CruiseTheme.typography.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = CruiseTheme.typography.meta, color = colors.textSecondary, maxLines = 1)
        }
    }
}

/**
 * Big A–Z scrubber on the right edge (SPEC §4.5 fast navigation): drag or tap a letter to jump; the current letter is
 * shown large while touching.
 */
@Composable
fun LetterScrubber(letters: List<Char>, onLetter: (Char) -> Unit, modifier: Modifier = Modifier) {
    if (letters.size < 2) return
    var active by remember { mutableStateOf<Char?>(null) }
    fun pick(y: Float, height: Int) {
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (letters[i] != active) {
            active = letters[i]
            onLetter(letters[i])
        }
    }
    Box(modifier) {
        Column(
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(CruiseTheme.colors.surface.copy(alpha = 0.7f))
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { pick(it.y, size.height) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, _ -> pick(change.position.y, size.height) }
                }
                .pointerInput(letters) { detectTapGestures { pick(it.y, size.height); active = null } },
        ) {
            letters.forEach { c ->
                Text(c.toString(), style = CruiseTheme.typography.meta, color = if (c == active) CruiseTheme.colors.accent else CruiseTheme.colors.textSecondary)
            }
        }
        active?.let {
            Box(
                Modifier.align(Alignment.Center).padding(end = 72.dp).size(88.dp).clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).background(CruiseTheme.colors.accent),
                contentAlignment = Alignment.Center,
            ) { Text(it.toString(), style = CruiseTheme.typography.display, color = CruiseTheme.colors.onAccent) }
        }
    }
}

/** First letter used for scrubbing; digits and symbols go under '#'. */
fun indexLetter(s: String): Char {
    val c = s.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    val folded = when (c) { 'À', 'Á', 'Â', 'Ä' -> 'A'; 'É', 'È', 'Ê', 'Ë' -> 'E'; 'Î', 'Ï' -> 'I'; 'Ô', 'Ö' -> 'O'; 'Ù', 'Û', 'Ü' -> 'U'; 'Ç' -> 'C'; else -> c }
    return if (folded in 'A'..'Z') folded else '#'
}
