package com.boulbeba.cruise.feature.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.designsystem.component.CruiseIconButton
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CoverStyle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.playback.PlayerConnection
import com.boulbeba.cruise.core.playback.PlayerUiState
import com.boulbeba.cruise.core.ui.qualityLabel
import java.util.Locale
import kotlin.math.abs

/** The current cover, decoded once by the app shell and shared with the backdrop and Now Playing. */
val LocalCoverArt = compositionLocalOf<DecodedArtwork?> { null }

private enum class Panel { None, Lyrics, Queue, Info }

/**
 * Now Playing (SPEC §5.2): the screen the driver sees most. Every part follows the active skin — cover presentation,
 * animated music bar, typefaces, glow. Responsive: side by side in landscape, stacked in portrait.
 */
@Composable
fun NowPlayingScreen(
    onOpenFolders: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val track by viewModel.track.collectAsStateWithLifecycle()
    val stat by viewModel.stat.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val player = viewModel.player
    val skin = CruiseTheme.skin
    val colors = CruiseTheme.colors

    val shared = LocalCoverArt.current
    val art = shared ?: rememberArtwork(state.artwork, state.artworkKey, state.path, with(LocalDensity.current) { 480.dp.roundToPx() })
    val accent by animateColorAsState(if (skin.dynamicAccent) art.accent ?: colors.accent else colors.accent, tween(600), label = "accent")
    val accent2 = colors.accent2
    val audio = rememberAudioLevels(CruiseTheme.quality, state.isPlaying)

    var panel by rememberSaveable { mutableStateOf(Panel.None) }
    var blind by rememberSaveable { mutableStateOf(false) }
    if (panel == Panel.Lyrics && lyrics == null) panel = Panel.None

    BoxWithConstraints(modifier.fillMaxSize()) {
        val maxW = maxWidth
        val maxH = maxHeight
        if (!state.hasMedia) {
            EmptyState(onOpenFolders)
            return@BoxWithConstraints
        }
        val portrait = CruiseTheme.layout.isPortrait
        val coverArea: @Composable (Dp) -> Unit = { size ->
            AnimatedContent(
                targetState = panel == Panel.Lyrics,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "coverOrLyrics",
            ) { showLyrics ->
                val l = lyrics
                if (showLyrics && l != null) {
                    LyricsPanel(l, state, audio, accent, onSeek = player::seekTo, modifier = Modifier.size(size))
                } else {
                    SwipeableCover(state, art, audio, accent, accent2, size, onSwipe = { if (it > 0) player.previous() else player.next() })
                }
            }
        }
        val details: @Composable (Modifier) -> Unit = { m ->
            TrackPanel(
                state, track, player, accent, accent2, audio,
                favorite = stat?.favorite == true,
                hasLyrics = lyrics != null,
                panel = panel,
                onPanel = { panel = if (panel == it) Panel.None else it },
                onFavorite = viewModel::toggleFavorite,
                onBlind = { blind = true },
                modifier = m,
            )
        }
        Box(Modifier.fillMaxSize().padding(CruiseTheme.dimens.gutter)) {
            if (portrait) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                    coverArea(minOf(maxW, maxH * 0.46f))
                    details(Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    coverArea(minOf(maxH - CruiseTheme.dimens.gutter * 2, maxW * 0.42f))
                    Spacer(Modifier.width(CruiseTheme.dimens.gutter * 1.5f))
                    details(Modifier.weight(1f, fill = false).widthIn(max = 760.dp))
                }
            }
        }

        // Queue slides in from the side; tap outside to close.
        AnimatedVisibility(panel == Panel.Queue || panel == Panel.Info, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)).clickable(remember { MutableInteractionSource() }, null) { panel = Panel.None })
        }
        AnimatedVisibility(
            panel == Panel.Queue,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            QueuePanel(
                queue, state.queueIndex, player, onClose = { panel = Panel.None },
                modifier = Modifier.width(if (portrait) maxWidth else maxWidth * 0.48f).padding(12.dp),
            )
        }
        AnimatedVisibility(panel == Panel.Info, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut() + scaleOut(targetScale = 0.94f), modifier = Modifier.align(Alignment.Center)) {
            TrackInfoPanel(track, state, stat?.playCount ?: 0, onClose = { panel = Panel.None }, modifier = Modifier.widthIn(max = 640.dp).padding(24.dp))
        }
        if (blind) BlindMode(state, player, onExit = { blind = false })
    }
}

@Composable
private fun SwipeableCover(state: PlayerUiState, art: DecodedArtwork, audio: AudioLevels, accent: Color, accent2: Color, size: Dp, onSwipe: (Float) -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    // Keyed on the decoded cover: the new art scales in over the old one once it is ready.
    AnimatedContent(
        targetState = art.image,
        transitionSpec = { (fadeIn(tween(320)) + scaleIn(tween(360), initialScale = 0.92f)) togetherWith fadeOut(tween(180)) },
        label = "cover",
        modifier = Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (abs(drag) > 80.dp.toPx()) onSwipe(drag)
                    drag = 0f
                },
                onHorizontalDrag = { _, amount -> drag += amount },
            )
        },
    ) { image ->
        SkinCover(state, image, audio, accent, accent2, size)
    }
}

@Composable
private fun TrackPanel(
    state: PlayerUiState,
    track: TrackRow?,
    player: PlayerConnection,
    accent: Color,
    accent2: Color,
    audio: AudioLevels,
    favorite: Boolean,
    hasLyrics: Boolean,
    panel: Panel,
    onPanel: (Panel) -> Unit,
    onFavorite: () -> Unit,
    onBlind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val skin = CruiseTheme.skin
    val colors = CruiseTheme.colors
    val type = CruiseTheme.typography
    val dimens = CruiseTheme.dimens
    val big = skin.cover == CoverStyle.Minimal
    val glow = if (skin.glow) Shadow(accent.copy(alpha = 0.8f), blurRadius = 24f) else null
    Column(modifier, verticalArrangement = Arrangement.Center) {
        val title = track?.title?.takeIf { it.isNotBlank() } ?: state.title
        AnimatedContent(title, transitionSpec = { (fadeIn(tween(300)) + slideInHorizontally { it / 10 }) togetherWith fadeOut(tween(150)) }, label = "title") { t ->
            Text(
                skinTitle(t),
                style = (if (big) type.display else type.title).copy(shadow = glow),
                color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        val artist = track?.artist?.takeIf { it.isNotBlank() } ?: state.artist
        if (artist.isNotBlank()) {
            Text(artist, style = type.subtitle, color = if (big) accent else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
        }
        val quality = track?.let { qualityLabel(it.codec, it.sampleRate, it.bitDepth, it.lossless) }
            ?: state.path?.substringAfterLast('.', "")?.uppercase(Locale.ROOT).orEmpty()
        Text(
            listOf(track?.album ?: state.album, quality, stringResource(R.string.np_track_of, state.queueIndex + 1, state.queueSize))
                .filter { it.isNotBlank() }.joinToString("  ·  "),
            style = type.meta, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(20.dp))
        AnimatedMusicBar(state, audio, accent, accent2, onSeek = player::seekTo)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            CruiseIconButton(
                CruiseIcons.Shuffle, stringResource(R.string.np_shuffle), onClick = player::toggleShuffle,
                size = dimens.secondaryControl, tint = if (state.shuffle) accent else colors.textSecondary,
            )
            CruiseIconButton(CruiseIcons.Previous, stringResource(R.string.np_previous), onClick = player::previous, size = dimens.primaryControl)
            CruiseIconButton(
                icon = if (state.isPlaying) CruiseIcons.Pause else CruiseIcons.Play,
                contentDescription = stringResource(if (state.isPlaying) R.string.np_pause else R.string.np_play),
                onClick = player::playPause,
                size = dimens.playButton,
                filled = true,
            )
            CruiseIconButton(CruiseIcons.Next, stringResource(R.string.np_next), onClick = player::next, size = dimens.primaryControl)
            CruiseIconButton(
                if (state.repeatMode == Player.REPEAT_MODE_ONE) CruiseIcons.RepeatOne else CruiseIcons.Repeat,
                stringResource(R.string.np_repeat), onClick = player::cycleRepeat, size = dimens.secondaryControl,
                tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) accent else colors.textSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val small = dimens.minTouchTarget
            CruiseIconButton(
                if (favorite) CruiseIcons.Favorite else CruiseIcons.FavoriteBorder, stringResource(R.string.np_favorite),
                onClick = onFavorite, size = small, tint = if (favorite) accent2 else colors.textSecondary,
            )
            if (hasLyrics) {
                CruiseIconButton(
                    CruiseIcons.Lyrics, stringResource(R.string.np_lyrics), onClick = { onPanel(Panel.Lyrics) }, size = small,
                    tint = if (panel == Panel.Lyrics) accent else colors.textSecondary,
                )
            }
            CruiseIconButton(CruiseIcons.Playlist, stringResource(R.string.np_queue_open), onClick = { onPanel(Panel.Queue) }, size = small, tint = colors.textSecondary)
            CruiseIconButton(CruiseIcons.Info, stringResource(R.string.np_info), onClick = { onPanel(Panel.Info) }, size = small, tint = colors.textSecondary)
            CruiseIconButton(CruiseIcons.Gesture, stringResource(R.string.np_blind), onClick = onBlind, size = small, tint = colors.textSecondary)
        }
    }
}

@Composable
private fun EmptyState(onOpenFolders: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(140.dp).clip(RoundedCornerShape(CruiseTheme.dimens.coverRadius)).background(CruiseTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(CruiseIcons.Note), contentDescription = null, tint = CruiseTheme.colors.accent, modifier = Modifier.size(72.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(skinTitle(stringResource(R.string.np_empty_title)), style = CruiseTheme.typography.title, color = CruiseTheme.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.np_empty_body), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textSecondary)
        Spacer(Modifier.height(24.dp))
        CruisePill(stringResource(R.string.np_open_folders), onClick = onOpenFolders, icon = CruiseIcons.Folder, selected = true)
    }
}
