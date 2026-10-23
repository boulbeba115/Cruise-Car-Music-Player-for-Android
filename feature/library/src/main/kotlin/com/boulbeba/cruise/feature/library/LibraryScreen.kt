package com.boulbeba.cruise.feature.library

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.data.library.AlbumSort
import com.boulbeba.cruise.core.data.library.Quality
import com.boulbeba.cruise.core.data.library.TrackSort
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.ui.AlbumCard
import com.boulbeba.cruise.core.ui.CoverImage
import com.boulbeba.cruise.core.ui.GroupItem
import com.boulbeba.cruise.core.ui.LetterScrubber
import com.boulbeba.cruise.core.ui.TrackItem
import com.boulbeba.cruise.core.ui.formatDuration
import com.boulbeba.cruise.core.ui.indexLetter
import com.boulbeba.cruise.core.ui.qualityLabel
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(modifier: Modifier = Modifier, viewModel: LibraryViewModel = hiltViewModel()) {
    val stack by viewModel.stack.collectAsStateWithLifecycle()
    val menu = rememberTrackMenu()
    val playing by viewModel.actions.player.state.collectAsStateWithLifecycle()
    BackHandler(enabled = stack.size > 1) { viewModel.back() }
    Box(modifier.fillMaxSize()) {
        AnimatedContent(stack.last(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "libRoute") { route ->
            when (route) {
                LibraryRoute.Home -> LibraryHome(viewModel, menu, playing.libraryPath)
                else -> DetailPage(route, viewModel, menu, playing.libraryPath)
            }
        }
        TrackMenuHost(menu, viewModel.actions)
    }
}

@Composable
private fun LibraryHome(vm: LibraryViewModel, menu: TrackMenuState, playingPath: String?) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val scan by vm.scan.collectAsStateWithLifecycle()
    val count by vm.trackCount.collectAsStateWithLifecycle()
    val dimens = CruiseTheme.dimens
    Column(Modifier.fillMaxSize().padding(horizontal = dimens.gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = dimens.gutter, bottom = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(skinTitle(stringResource(R.string.lib_title)), style = CruiseTheme.typography.title, color = CruiseTheme.colors.textPrimary)
                Text(
                    if (scan.running) stringResource(R.string.lib_scanning, scan.filesSeen) else stringResource(R.string.lib_tracks_count, count),
                    style = CruiseTheme.typography.meta, color = if (scan.running) CruiseTheme.colors.accent else CruiseTheme.colors.textSecondary,
                )
            }
            CruisePill(stringResource(R.string.lib_rescan), onClick = vm::rescan, icon = CruiseIcons.Repeat, enabled = !scan.running)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryTab.entries.forEach { t -> CruisePill(stringResource(t.label()), onClick = { vm.selectTab(t) }, selected = t == tab) }
        }
        Spacer(Modifier.height(12.dp))
        if (count == 0 && !scan.running) {
            Empty(R.string.lib_empty_title, R.string.lib_empty_body)
            return
        }
        when (tab) {
            LibraryTab.Albums -> AlbumsTab(vm)
            LibraryTab.Artists -> GroupsTab(vm.artists.collectAsStateWithLifecycle().value) { vm.open(LibraryRoute.Artist(it.key, it.name)) }
            LibraryTab.Genres -> GroupsTab(vm.genres.collectAsStateWithLifecycle().value) { vm.open(LibraryRoute.Genre(it.name)) }
            LibraryTab.Years -> GroupsTab(vm.years.collectAsStateWithLifecycle().value, letters = false) { vm.open(LibraryRoute.Year(it.name.toIntOrNull() ?: 0)) }
            else -> TracksTab(vm, tab, menu, playingPath)
        }
    }
}

@Composable
private fun AlbumsTab(vm: LibraryViewModel) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val sort by vm.albumSort.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val letterKey: (Int) -> String = { i -> if (sort == AlbumSort.Artist) albums[i].albumArtist else albums[i].album }
    SortRow(stringResource(sort.label())) { vm.setAlbumSort(AlbumSort.entries[(sort.ordinal + 1) % AlbumSort.entries.size]) }
    Row(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            GridCells.Adaptive(168.dp), state = grid, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(albums, key = { it.albumKey }) { album ->
                AlbumCard(
                    album,
                    onClick = { vm.open(LibraryRoute.Album(album.albumKey, album.album)) },
                    onLongClick = { vm.open(LibraryRoute.Album(album.albumKey, album.album)) },
                )
            }
        }
        if (sort == AlbumSort.Title || sort == AlbumSort.Artist) {
            val letters = remember(albums, sort) { albums.indices.map { indexLetter(letterKey(it)) }.distinct() }
            LetterScrubber(letters, onLetter = { c ->
                val i = albums.indices.firstOrNull { indexLetter(letterKey(it)) == c } ?: return@LetterScrubber
                scope.launch { grid.scrollToItem(i) }
            }, modifier = Modifier.fillMaxHeight().padding(start = 8.dp, bottom = 16.dp))
        }
    }
}

@Composable
private fun GroupsTab(groups: List<com.boulbeba.cruise.core.data.db.GroupRow>, letters: Boolean = true, onOpen: (com.boulbeba.cruise.core.data.db.GroupRow) -> Unit) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(groups, key = { it.key }) { g ->
                GroupItem(
                    title = g.name.ifBlank { stringResource(R.string.unknown_artist) },
                    subtitle = stringResource(R.string.lib_albums_count, g.albumCount, g.trackCount),
                    coverKey = g.sampleAlbumKey, coverPath = g.samplePath, coverFolder = g.sampleFolder,
                    onClick = { onOpen(g) },
                )
            }
        }
        if (letters) {
            val index = remember(groups) { groups.map { indexLetter(it.name.replace(Regex("^(the|le|la|les)\\s+", RegexOption.IGNORE_CASE), "")) } }
            LetterScrubber(index.distinct(), onLetter = { c -> index.indexOf(c).takeIf { it >= 0 }?.let { scope.launch { list.scrollToItem(it) } } }, modifier = Modifier.fillMaxHeight().padding(start = 8.dp, bottom = 16.dp))
        }
    }
}

@Composable
private fun TracksTab(vm: LibraryViewModel, tab: LibraryTab, menu: TrackMenuState, playingPath: String?) {
    val tracks by vm.tabTracks.collectAsStateWithLifecycle()
    val sort by vm.trackSort.collectAsStateWithLifecycle()
    val quality by vm.quality.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        CruisePill(stringResource(R.string.lib_play), onClick = { vm.actions.play(tracks) }, icon = CruiseIcons.Play, selected = true, enabled = tracks.isNotEmpty())
        CruisePill(stringResource(R.string.lib_shuffle), onClick = { vm.actions.play(tracks, shuffle = true) }, icon = CruiseIcons.Shuffle, enabled = tracks.isNotEmpty())
        if (tab == LibraryTab.Tracks) {
            CruisePill(stringResource(R.string.lib_sort, stringResource(sort.label())), onClick = { vm.setTrackSort(TrackSort.entries[(sort.ordinal + 1) % TrackSort.entries.size]) }, icon = CruiseIcons.Tune)
        }
        if (tab == LibraryTab.Quality) Quality.entries.forEach { q -> CruisePill(stringResource(q.label()), onClick = { vm.quality.value = q }, selected = q == quality) }
    }
    Spacer(Modifier.height(10.dp))
    if (tracks.isEmpty()) {
        Text(stringResource(R.string.lib_nothing_here), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textSecondary, modifier = Modifier.padding(top = 24.dp))
        return
    }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(tracks, key = { _, t -> t.path }) { i, t ->
                TrackItem(t, onClick = { vm.actions.play(tracks, i) }, onLongClick = { menu.forTrack(t) }, active = t.path == playingPath)
            }
        }
        if (tab == LibraryTab.Tracks && (sort == TrackSort.Title || sort == TrackSort.Artist)) {
            val index = remember(tracks, sort) { tracks.map { indexLetter(if (sort == TrackSort.Title) it.title else it.artist) } }
            LetterScrubber(index.distinct(), onLetter = { c -> index.indexOf(c).takeIf { it >= 0 }?.let { scope.launch { list.scrollToItem(it) } } }, modifier = Modifier.fillMaxHeight().padding(start = 8.dp, bottom = 16.dp))
        }
    }
}

@Composable
private fun DetailPage(route: LibraryRoute, vm: LibraryViewModel, menu: TrackMenuState, playingPath: String?) {
    val tracks by vm.detailTracks.collectAsStateWithLifecycle()
    val dimens = CruiseTheme.dimens
    val first = tracks.firstOrNull()
    val title = when (route) {
        is LibraryRoute.Album -> route.title.ifBlank { first?.album.orEmpty() }
        is LibraryRoute.Artist -> route.name.ifBlank { stringResource(R.string.unknown_artist) }
        is LibraryRoute.Genre -> route.name
        is LibraryRoute.Year -> route.year.toString()
        LibraryRoute.Home -> ""
    }
    val subtitle = when (route) {
        is LibraryRoute.Album -> listOfNotNull(first?.albumArtist?.takeIf { it.isNotBlank() }, first?.year?.takeIf { it > 0 }?.toString()).joinToString(" · ")
        else -> ""
    }
    val isAlbum = route is LibraryRoute.Album
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = dimens.gutter), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = dimens.gutter, bottom = 16.dp)) {
                CruisePill(stringResource(R.string.lib_back), onClick = { vm.back() }, icon = CruiseIcons.Back)
                Spacer(Modifier.width(20.dp))
                if (first != null) {
                    CoverImage(first.albumKey, first.path, first.folder, title, Modifier.size(if (isAlbum) 168.dp else 96.dp), RoundedCornerShape(dimens.coverRadius * 0.7f), sizePx = 480)
                    Spacer(Modifier.width(24.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(skinTitle(title), style = CruiseTheme.typography.title, color = CruiseTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) Text(subtitle, style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textSecondary, maxLines = 1)
                    Text(
                        stringResource(R.string.lib_detail_summary, tracks.size, formatDuration(tracks.sumOf { it.durationMs })) +
                            (first?.let { "  ·  " + qualityLabel(it.codec, it.sampleRate, it.bitDepth, it.lossless) } ?: ""),
                        style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        CruisePill(stringResource(R.string.lib_play), onClick = { vm.actions.play(tracks) }, icon = CruiseIcons.Play, selected = true)
                        CruisePill(stringResource(R.string.lib_shuffle), onClick = { vm.actions.play(tracks, shuffle = true) }, icon = CruiseIcons.Shuffle)
                        CruisePill(stringResource(R.string.act_play_next), onClick = { vm.actions.playNext(tracks) }, icon = CruiseIcons.PlayAll)
                        CruisePill(stringResource(R.string.act_add_playlist), onClick = {
                            menu.choosingPlaylist = MenuTarget(title, subtitle, tracks)
                        }, icon = CruiseIcons.Playlist)
                    }
                }
            }
        }
        val discs = tracks.map { it.discNo }.distinct()
        tracks.forEachIndexed { i, t ->
            if (isAlbum && discs.size > 1 && (i == 0 || tracks[i - 1].discNo != t.discNo)) {
                item(key = "disc${t.discNo}") {
                    Text(stringResource(R.string.lib_disc, t.discNo), style = CruiseTheme.typography.label, color = CruiseTheme.colors.accent, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
            }
            item(key = t.path) {
                TrackItem(
                    t,
                    onClick = { vm.actions.play(tracks, i) },
                    onLongClick = { menu.forTrack(t) },
                    active = t.path == playingPath,
                    showCover = !isAlbum,
                    showNumber = isAlbum,
                    subtitle = if (isAlbum) t.artist.takeIf { it != t.albumArtist }.orEmpty() else listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "),
                )
            }
        }
    }
}

@Composable
private fun SortRow(label: String, onClick: () -> Unit) {
    Row(Modifier.padding(bottom = 8.dp)) { CruisePill(stringResource(R.string.lib_sort, label), onClick = onClick, icon = CruiseIcons.Tune) }
}

@Composable
internal fun Empty(@StringRes title: Int, @StringRes body: Int) {
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp).clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).background(CruiseTheme.colors.surface.copy(alpha = 0.8f)).padding(24.dp),
    ) {
        Text(stringResource(title), style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(body), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textSecondary)
    }
}

@StringRes private fun LibraryTab.label() = when (this) {
    LibraryTab.Albums -> R.string.lib_tab_albums
    LibraryTab.Artists -> R.string.lib_tab_artists
    LibraryTab.Genres -> R.string.lib_tab_genres
    LibraryTab.Tracks -> R.string.lib_tab_tracks
    LibraryTab.Years -> R.string.lib_tab_years
    LibraryTab.Quality -> R.string.lib_tab_quality
    LibraryTab.Recent -> R.string.lib_tab_recent
    LibraryTab.MostPlayed -> R.string.lib_tab_most
    LibraryTab.Favorites -> R.string.lib_tab_favorites
}

@StringRes private fun AlbumSort.label() = when (this) {
    AlbumSort.Title -> R.string.sort_title
    AlbumSort.Artist -> R.string.sort_artist
    AlbumSort.Year -> R.string.sort_year
    AlbumSort.Added -> R.string.sort_added
}

@StringRes private fun TrackSort.label() = when (this) {
    TrackSort.Title -> R.string.sort_title
    TrackSort.Artist -> R.string.sort_artist
    TrackSort.Album -> R.string.sort_album
    TrackSort.Year -> R.string.sort_year
    TrackSort.Duration -> R.string.sort_duration
    TrackSort.Added -> R.string.sort_added
    TrackSort.FileName -> R.string.sort_filename
}

@StringRes private fun Quality.label() = when (this) {
    Quality.HiRes -> R.string.quality_hires
    Quality.Lossless -> R.string.quality_lossless
    Quality.Lossy -> R.string.quality_lossy
}

