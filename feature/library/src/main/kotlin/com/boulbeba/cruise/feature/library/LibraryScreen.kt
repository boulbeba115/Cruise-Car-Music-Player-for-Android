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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.data.db.GroupRow
import com.boulbeba.cruise.core.data.library.AlbumSort
import com.boulbeba.cruise.core.designsystem.component.CruiseIconButton
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.ui.AlbumCard
import com.boulbeba.cruise.core.ui.CoverImage
import com.boulbeba.cruise.core.ui.GroupItem
import com.boulbeba.cruise.core.ui.FastScroller
import com.boulbeba.cruise.core.ui.NavRequests
import com.boulbeba.cruise.core.ui.TrackItem
import com.boulbeba.cruise.core.ui.formatDuration
import com.boulbeba.cruise.core.ui.scrollLetter
import com.boulbeba.cruise.core.data.library.LibraryRepository
import com.boulbeba.cruise.core.ui.qualityLabel
import com.boulbeba.cruise.feature.library.folders.FoldersTab
import com.boulbeba.cruise.feature.library.folders.FoldersViewModel
import kotlinx.coroutines.launch

/**
 * The Library (CHANGE_REQUESTS.md CR-02): one section for every way to reach the music — Folders, Albums, Artists,
 * Genres, Playlists — with search in its header. Detail pages (album, artist, genre, playlist) take the whole area.
 */
@Composable
fun LibraryScreen(modifier: Modifier = Modifier, viewModel: LibraryViewModel = hiltViewModel()) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val stack by viewModel.stack.collectAsStateWithLifecycle()
    val playlists: PlaylistsViewModel = hiltViewModel()
    val openList by playlists.open.collectAsStateWithLifecycle()
    val search: SearchViewModel = hiltViewModel()
    val query by search.query.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    val menu = rememberTrackMenu()
    val playing by viewModel.actions.player.state.collectAsStateWithLifecycle()

    val closeSearch = { searching = false; search.query.value = "" }
    BackHandler(enabled = stack.size > 1) { viewModel.back() }
    val detail = (tab != LibraryTab.Playlists && stack.size > 1) || (tab == LibraryTab.Playlists && openList != null)

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = CruiseTheme.dimens.gutter)) {
            if (!detail) {
                LibraryHeader(viewModel, searching, query, onQuery = { search.query.value = it }, onSearch = { searching = true }, onCloseSearch = closeSearch)
                if (!searching || query.isBlank()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LibraryTab.entries.forEach { t -> CruisePill(stringResource(t.label()), onClick = { viewModel.selectTab(t) }, selected = t == tab, icon = t.icon()) }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            Box(Modifier.weight(1f)) {
                if (searching && query.isNotBlank()) {
                    val folders: FoldersViewModel = hiltViewModel()
                    val tree by folders.ui.collectAsStateWithLifecycle()
                    SearchResults(
                        search, menu, playing.libraryPath,
                        folders = remember(tree.tree, query) { tree.tree?.search(query).orEmpty() },
                        onFolder = { path -> closeSearch(); viewModel.selectTab(LibraryTab.Folders); NavRequests.folder.value = path },
                        onAlbum = { key, title -> closeSearch(); viewModel.show(LibraryTab.Albums, LibraryRoute.Album(key, title)) },
                        onArtist = { key, name -> closeSearch(); viewModel.show(LibraryTab.Artists, LibraryRoute.Artist(key, name)) },
                    )
                } else {
                    AnimatedContent(tab to stack.last(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "libRoute") { (t, route) ->
                        when {
                            t == LibraryTab.Folders -> FoldersTab(menu, playing.libraryPath)
                            t == LibraryTab.Playlists -> PlaylistsTab(playlists)
                            route != LibraryRoute.Home -> DetailPage(route, viewModel, menu, playing.libraryPath)
                            else -> LibraryHome(t, viewModel)
                        }
                    }
                }
            }
        }
        BackHandler(enabled = searching) { closeSearch() }
        TrackMenuHost(menu, viewModel.actions)
    }
}

@Composable
private fun LibraryHeader(
    vm: LibraryViewModel,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val count by vm.trackCount.collectAsStateWithLifecycle()
    val colors = CruiseTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = CruiseTheme.dimens.gutter, bottom = 10.dp)) {
        if (searching) {
            SearchField(query, onQuery, onClose = onCloseSearch, modifier = Modifier.weight(1f))
        } else {
            // Background refreshes stay silent: the cached library is what the driver sees.
            val shownScanning = scan.running && !scan.quiet
            Column(Modifier.weight(1f)) {
                Text(skinTitle(stringResource(R.string.lib_title)), style = CruiseTheme.typography.title, color = colors.textPrimary)
                Text(
                    if (shownScanning) stringResource(R.string.lib_scanning, scan.filesSeen) else stringResource(R.string.lib_tracks_count, count),
                    style = CruiseTheme.typography.meta, color = if (shownScanning) colors.accent else colors.textSecondary,
                )
            }
            CruiseIconButton(CruiseIcons.Search, stringResource(R.string.lib_search), onClick = onSearch, size = CruiseTheme.dimens.minTouchTarget, tint = colors.textPrimary)
            Spacer(Modifier.width(8.dp))
            CruiseIconButton(CruiseIcons.Refresh, stringResource(R.string.lib_rescan), onClick = vm::rescan, size = CruiseTheme.dimens.minTouchTarget, tint = if (shownScanning) colors.accent else colors.textSecondary)
        }
    }
}

@Composable
private fun LibraryHome(tab: LibraryTab, vm: LibraryViewModel) {
    val scan by vm.scan.collectAsStateWithLifecycle()
    val count by vm.trackCount.collectAsStateWithLifecycle()
    if (count == 0 && !scan.running) {
        Empty(R.string.lib_empty_title, R.string.lib_empty_body)
        return
    }
    when (tab) {
        LibraryTab.Albums -> AlbumsTab(vm)
        LibraryTab.Artists -> GroupsTab(vm.artists.collectAsStateWithLifecycle().value) { vm.open(LibraryRoute.Artist(it.key, it.name)) }
        LibraryTab.Genres -> GroupsTab(vm.genres.collectAsStateWithLifecycle().value) { vm.open(LibraryRoute.Genre(it.name)) }
        LibraryTab.Folders, LibraryTab.Playlists -> Unit
    }
}

@Composable
private fun AlbumsTab(vm: LibraryViewModel) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val sort by vm.albumSort.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val letterKey: (Int) -> String = { i -> if (sort == AlbumSort.Artist) albums[i].albumArtist else albums[i].album }
    Column {
        SortRow(stringResource(sort.label())) { vm.setAlbumSort(AlbumSort.entries[(sort.ordinal + 1) % AlbumSort.entries.size]) }
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Adaptive(168.dp), state = grid, modifier = Modifier.fillMaxSize().padding(end = FAST_SCROLL_GUTTER),
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
            FastScroller(grid, albums.size, label = { i -> if (sort == AlbumSort.Year) albums[i].year.takeIf { it > 0 }?.toString() ?: "#" else scrollLetter(letterKey(i)) }, modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun GroupsTab(groups: List<GroupRow>, onOpen: (GroupRow) -> Unit) {
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(end = FAST_SCROLL_GUTTER), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(groups, key = { it.key }) { g ->
                GroupItem(
                    title = g.name.ifBlank { stringResource(R.string.unknown_artist) },
                    subtitle = stringResource(R.string.lib_albums_count, g.albumCount, g.trackCount),
                    coverKey = g.sampleAlbumKey, coverPath = g.samplePath, coverFolder = g.sampleFolder,
                    onClick = { onOpen(g) },
                )
            }
        }
        FastScroller(list, groups.size, label = { scrollLetter(LibraryRepository.stripArticle(groups[it].name)) }, modifier = Modifier.align(Alignment.TopEnd))
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
        LibraryRoute.Home -> ""
    }
    val subtitle = when (route) {
        is LibraryRoute.Album -> listOfNotNull(first?.albumArtist?.takeIf { it.isNotBlank() }, first?.year?.takeIf { it > 0 }?.toString()).joinToString(" · ")
        else -> ""
    }
    val isAlbum = route is LibraryRoute.Album
    val discs = tracks.map { it.discNo }.distinct()
    // Bubble text per list row (header, disc titles, tracks), for the fast scroller.
    val labels = remember(tracks, isAlbum) {
        buildList {
            add("")
            tracks.forEachIndexed { i, t ->
                if (isAlbum && discs.size > 1 && (i == 0 || tracks[i - 1].discNo != t.discNo)) add(t.discNo.toString())
                add(if (isAlbum) (if (t.trackNo > 0) t.trackNo.toString() else "#") else scrollLetter(t.title))
            }
        }
    }
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().padding(end = FAST_SCROLL_GUTTER), state = list, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = dimens.gutter, bottom = 16.dp)) {
                CruiseIconButton(CruiseIcons.Back, stringResource(R.string.lib_back), onClick = { vm.back() }, size = dimens.minTouchTarget, tint = CruiseTheme.colors.textPrimary)
                Spacer(Modifier.width(16.dp))
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
    FastScroller(list, labels.size, label = { labels[it] }, modifier = Modifier.align(Alignment.TopEnd))
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
    LibraryTab.Folders -> R.string.lib_tab_folders
    LibraryTab.Albums -> R.string.lib_tab_albums
    LibraryTab.Artists -> R.string.lib_tab_artists
    LibraryTab.Genres -> R.string.lib_tab_genres
    LibraryTab.Playlists -> R.string.lib_tab_playlists
}

private fun LibraryTab.icon() = when (this) {
    LibraryTab.Folders -> CruiseIcons.Folder
    LibraryTab.Albums -> CruiseIcons.Album
    LibraryTab.Artists -> CruiseIcons.Library
    LibraryTab.Genres -> CruiseIcons.Equalizer
    LibraryTab.Playlists -> CruiseIcons.Playlist
}

@StringRes private fun AlbumSort.label() = when (this) {
    AlbumSort.Title -> R.string.sort_title
    AlbumSort.Artist -> R.string.sort_artist
    AlbumSort.Year -> R.string.sort_year
    AlbumSort.Added -> R.string.sort_added
}

/** Room kept free on the right of a list for the fast scroller's handle (CR-14). */
internal val FAST_SCROLL_GUTTER = 28.dp
