package com.boulbeba.cruise.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.component.CruiseBottomBar
import com.boulbeba.cruise.core.designsystem.component.CruiseNavRail
import com.boulbeba.cruise.core.designsystem.component.NavItem
import com.boulbeba.cruise.core.designsystem.skin.SkinBackground
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.ui.NavRequests
import com.boulbeba.cruise.core.ui.PlayRequests
import com.boulbeba.cruise.core.ui.VoiceRequests
import com.boulbeba.cruise.feature.voice.VoiceOverlay
import com.boulbeba.cruise.feature.voice.VoiceViewModel
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boulbeba.cruise.feature.library.LibraryScreen
import com.boulbeba.cruise.feature.library.PlaylistsScreen
import com.boulbeba.cruise.feature.library.SearchScreen
import com.boulbeba.cruise.feature.nowplaying.LocalCoverArt
import com.boulbeba.cruise.feature.nowplaying.rememberArtwork
import com.boulbeba.cruise.feature.sound.SoundScreen
import com.boulbeba.cruise.core.playback.PlayerConnection
import com.boulbeba.cruise.feature.folders.FoldersScreen
import com.boulbeba.cruise.feature.nowplaying.NowPlayingScreen
import com.boulbeba.cruise.feature.settings.diagnostics.DiagnosticsScreen

@Composable
fun CruiseApp(player: PlayerConnection) {
    var destination by rememberSaveable { mutableStateOf(TopDestination.NowPlaying) }
    val playback by player.state.collectAsStateWithLifecycle()
    // One decode of the current cover feeds the backdrop, Now Playing and the mini player.
    val art = rememberArtwork(playback.artwork, playback.artworkKey, playback.path, with(LocalDensity.current) { 480.dp.roundToPx() })

    // Voice: the overlay sits above every screen; requests come from the wheel, mic buttons and other apps.
    val voice: VoiceViewModel = hiltViewModel()
    val voiceUi by voice.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voice.toggle()
    }
    val listen = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.toggle()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val listenCount by VoiceRequests.listen.collectAsStateWithLifecycle()
    LaunchedEffect(listenCount) { if (listenCount > 0) listen() }
    LaunchedEffect(Unit) {
        VoiceRequests.query.collect { q -> if (q != null) { VoiceRequests.query.value = null; voice.run(q) } }
    }
    // An audio file opened from a file manager: play it as soon as the player is connected.
    val pendingFile by PlayRequests.path.collectAsStateWithLifecycle()
    LaunchedEffect(pendingFile, playback.connected) {
        val path = pendingFile ?: return@LaunchedEffect
        if (!playback.connected) return@LaunchedEffect
        PlayRequests.path.value = null
        player.play(listOf(java.io.File(path)))
        destination = TopDestination.NowPlaying
    }

    // "Go to folder / album" from any screen switches destination; the target screen consumes the request.
    LaunchedEffect(Unit) { NavRequests.folder.collect { if (it != null) destination = TopDestination.Folders } }
    LaunchedEffect(Unit) { NavRequests.album.collect { if (it != null) destination = TopDestination.Library } }
    val items = TopDestination.entries.map { NavItem(it.name, it.icon, stringResource(it.label)) }
    val onSelect: (NavItem) -> Unit = { destination = TopDestination.valueOf(it.key) }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            DestinationContent(destination, onNavigate = { destination = it }, modifier = Modifier.weight(1f))
            AnimatedVisibility(
                visible = playback.hasMedia && destination != TopDestination.NowPlaying,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                MiniPlayer(playback, player, onOpen = { destination = TopDestination.NowPlaying })
            }
        }
    }

    CompositionLocalProvider(LocalCoverArt provides art) {
    Box(Modifier.fillMaxSize()) {
    SkinBackground(Modifier.fillMaxSize(), coverAccent = art.accent, coverDeep = art.deep, moving = playback.isPlaying)
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (CruiseTheme.layout.usesBottomBar) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthPerItem = maxWidth / items.size
                Column(Modifier.fillMaxSize()) {
                    content(Modifier.weight(1f))
                    CruiseBottomBar(items, destination.name, onSelect, showLabels = widthPerItem >= 64.dp)
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                CruiseNavRail(items, destination.name, onSelect)
                content(Modifier.weight(1f))
            }
        }
    }
    VoiceOverlay(voiceUi, onCancel = voice::cancel, onRetry = { voice.cancel(); listen() })
    }
    }
}

@Composable
private fun DestinationContent(destination: TopDestination, onNavigate: (TopDestination) -> Unit, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(280)) { it / 24 }) togetherWith fadeOut(tween(120))
        },
        label = "destination",
        modifier = modifier.fillMaxSize(),
    ) { current ->
        when (current) {
            TopDestination.NowPlaying -> NowPlayingScreen(onOpenFolders = { onNavigate(TopDestination.Folders) })
            TopDestination.Library -> LibraryScreen()
            TopDestination.Folders -> FoldersScreen()
            TopDestination.Playlists -> PlaylistsScreen()
            TopDestination.Search -> SearchScreen(onOpenLibrary = { onNavigate(TopDestination.Library) })
            TopDestination.Sound -> SoundScreen()
            TopDestination.Settings -> DiagnosticsScreen()
        }
    }
}
