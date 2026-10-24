package com.boulbeba.cruise.feature.library

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.data.db.PlaylistRow
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.designsystem.component.CruiseIconButton
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.model.MediaIds
import com.boulbeba.cruise.core.ui.ActionSheet
import com.boulbeba.cruise.core.ui.FastScroller
import com.boulbeba.cruise.core.ui.scrollLetter
import com.boulbeba.cruise.core.ui.GroupItem
import com.boulbeba.cruise.core.ui.SheetAction
import com.boulbeba.cruise.core.ui.TextEntryDialog
import com.boulbeba.cruise.core.ui.TrackItem
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Playlists that fill themselves (CHANGE_REQUESTS.md CR-02: former Library tabs). */
enum class SmartList { Favorites, MostPlayed, Recent }

/** The playlist open in the Playlists tab. */
sealed interface OpenList {
    data class User(val row: PlaylistRow) : OpenList
    data class Smart(val kind: SmartList) : OpenList
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistsViewModel @Inject constructor(val actions: TrackActions) : ViewModel() {
    private val repo = actions.library
    val playlists = repo.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val open = MutableStateFlow<OpenList?>(null)
    val tracks = open.flatMapLatest { o ->
        when (o) {
            null -> flowOf(emptyList())
            is OpenList.User -> repo.playlistTracks(o.row.id).mapLatest { repo.rows(it) }
            is OpenList.Smart -> when (o.kind) {
                SmartList.Favorites -> repo.favorites()
                SmartList.MostPlayed -> repo.mostPlayed()
                SmartList.Recent -> repo.recentlyAdded()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val message = MutableStateFlow<String?>(null)

    fun create(name: String, paths: List<String> = emptyList()) = viewModelScope.launch { repo.createPlaylist(name, paths) }
    fun saveQueue(name: String) = create(name, actions.player.queue.value.map(MediaIds::libraryPath))
    fun rename(id: Long, name: String) = viewModelScope.launch {
        repo.renamePlaylist(id, name)
        (open.value as? OpenList.User)?.let { open.value = OpenList.User(it.row.copy(name = name)) }
    }
    fun delete(id: Long) = viewModelScope.launch { repo.deletePlaylist(id); open.value = null }
    fun remove(id: Long, index: Int) = viewModelScope.launch { repo.removeFromPlaylist(id, index) }
    fun move(id: Long, from: Int, to: Int) = viewModelScope.launch { repo.movePlaylistItem(id, from, to) }
    fun export(id: Long) = viewModelScope.launch { message.value = repo.exportPlaylist(id)?.path }
}

/** Playlists tab (SPEC Q-2, Q-3): automatic lists, the user's playlists, detail with reorder/remove, M3U8 export. */
@Composable
fun PlaylistsTab(viewModel: PlaylistsViewModel, modifier: Modifier = Modifier) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf<String?>(null) }
    val dateName = stringResource(R.string.pl_queue_name, SimpleDateFormat("d MMM HH:mm", ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: java.util.Locale.ROOT).format(Date()))
    BackHandler(enabled = open != null) { viewModel.open.value = null }
    Box(modifier.fillMaxSize()) {
        when (val current = open) {
            is OpenList.User -> PlaylistDetail(current.row, viewModel)
            is OpenList.Smart -> SmartDetail(current.kind, viewModel)
            null -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        SmartList.entries.forEach { kind -> SmartCard(kind) { viewModel.open.value = OpenList.Smart(kind) } }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).horizontalScroll(rememberScrollState())) {
                        CruisePill(stringResource(R.string.pl_new), onClick = { naming = "new" }, icon = CruiseIcons.PlayAll, selected = true)
                        CruisePill(stringResource(R.string.pl_save_queue), onClick = { naming = "queue" }, icon = CruiseIcons.Save)
                    }
                }
                if (playlists.isEmpty()) item { Empty(R.string.pl_title, R.string.pl_empty) }
                items(playlists, key = { it.id }) { p ->
                    GroupItem(
                        title = p.name,
                        subtitle = stringResource(R.string.pl_count, p.trackCount) + (p.sourceFile?.let { "  ·  " + it.substringAfterLast('/') } ?: ""),
                        coverKey = p.firstPath?.let { "pl:${p.id}:$it" } ?: "pl:${p.id}",
                        coverPath = p.firstPath ?: "",
                        coverFolder = p.firstPath?.let { MediaIds.filePath(it).substringBeforeLast('/') } ?: "",
                        onClick = { viewModel.open.value = OpenList.User(p) },
                    )
                }
            }
        }
        naming?.let { mode ->
            TextEntryDialog(
                title = stringResource(R.string.pl_name),
                initial = if (mode == "queue") dateName else "",
                confirm = stringResource(R.string.pl_create),
                onConfirm = { if (mode == "queue") viewModel.saveQueue(it) else viewModel.create(it) },
                onDismiss = { naming = null },
            )
        }
    }
}

/** A large tile for an automatic playlist. */
@Composable
private fun SmartCard(kind: SmartList, onClick: () -> Unit) {
    val colors = CruiseTheme.colors
    Column(
        Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
            .background(Brush.linearGradient(listOf(colors.accent.copy(alpha = 0.30f), colors.accent2.copy(alpha = 0.14f))))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(painterResource(kind.icon()), contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(18.dp))
        Text(skinTitle(stringResource(kind.label())), style = CruiseTheme.typography.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.pl_smart_hint), style = CruiseTheme.typography.meta, color = colors.textSecondary)
    }
}

@Composable
private fun DetailHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit) {
    Column(Modifier.padding(top = CruiseTheme.dimens.gutter, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CruiseIconButton(CruiseIcons.Back, stringResource(R.string.lib_back), onClick = onBack, size = CruiseTheme.dimens.minTouchTarget, tint = CruiseTheme.colors.textPrimary)
            Spacer(Modifier.width(12.dp))
            Text(skinTitle(title), style = CruiseTheme.typography.title, color = CruiseTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) { actions() }
    }
}

@Composable
private fun SmartDetail(kind: SmartList, vm: PlaylistsViewModel) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val playing by vm.actions.player.state.collectAsStateWithLifecycle()
    val menu = rememberTrackMenu()
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = FAST_SCROLL_GUTTER), state = list, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                DetailHeader(stringResource(kind.label()), onBack = { vm.open.value = null }) {
                    CruisePill(stringResource(R.string.lib_play), onClick = { vm.actions.play(tracks) }, icon = CruiseIcons.Play, selected = true, enabled = tracks.isNotEmpty())
                    CruisePill(stringResource(R.string.lib_shuffle), onClick = { vm.actions.play(tracks, shuffle = true) }, icon = CruiseIcons.Shuffle, enabled = tracks.isNotEmpty())
                }
            }
            if (tracks.isEmpty()) item { Text(stringResource(R.string.lib_nothing_here), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textSecondary) }
            itemsIndexed(tracks, key = { _, t -> t.path }) { i, t ->
                TrackItem(t, onClick = { vm.actions.play(tracks, i) }, onLongClick = { menu.forTrack(t) }, active = t.path == playing.libraryPath)
            }
        }
        FastScroller(list, tracks.size + 1, label = { if (it == 0) "" else scrollLetter(tracks[it - 1].title) }, modifier = Modifier.align(Alignment.TopEnd))
        TrackMenuHost(menu, vm.actions)
    }
}

@Composable
private fun PlaylistDetail(p: PlaylistRow, vm: PlaylistsViewModel) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val playing by vm.actions.player.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<Pair<Int, TrackRow>?>(null) }
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().padding(end = FAST_SCROLL_GUTTER), state = list, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            DetailHeader(p.name, onBack = { vm.open.value = null }) {
                CruisePill(stringResource(R.string.lib_play), onClick = { vm.actions.play(tracks) }, icon = CruiseIcons.Play, selected = true, enabled = tracks.isNotEmpty())
                CruisePill(stringResource(R.string.lib_shuffle), onClick = { vm.actions.play(tracks, shuffle = true) }, icon = CruiseIcons.Shuffle, enabled = tracks.isNotEmpty())
                CruisePill(stringResource(R.string.pl_rename), onClick = { renaming = true }, icon = CruiseIcons.Tune)
                CruisePill(stringResource(R.string.pl_export), onClick = { vm.export(p.id) }, icon = CruiseIcons.Usb)
                CruisePill(stringResource(R.string.pl_delete), onClick = { vm.delete(p.id) }, icon = CruiseIcons.Delete)
            }
            message?.let { Text(stringResource(R.string.pl_exported, it), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.success, modifier = Modifier.padding(bottom = 8.dp)) }
        }
        itemsIndexed(tracks, key = { i, t -> "$i:${t.path}" }) { i, t ->
            TrackItem(t, onClick = { vm.actions.play(tracks, i) }, onLongClick = { itemMenu = i to t }, active = t.path == playing.libraryPath)
        }
    }
    FastScroller(list, tracks.size + 1, label = { if (it == 0) "" else scrollLetter(tracks[it - 1].title) }, modifier = Modifier.align(Alignment.TopEnd))
    }
    if (renaming) TextEntryDialog(stringResource(R.string.pl_rename), p.name, stringResource(R.string.pl_rename), { vm.rename(p.id, it) }, { renaming = false })
    itemMenu?.let { (i, t) ->
        ActionSheet(
            t.title, t.artist,
            listOf(
                SheetAction(stringResource(R.string.act_play_next), CruiseIcons.PlayAll) { vm.actions.playNext(listOf(t)) },
                SheetAction(stringResource(R.string.act_add_queue), CruiseIcons.Playlist) { vm.actions.addToQueue(listOf(t)) },
                SheetAction(stringResource(R.string.pl_move_up), CruiseIcons.Chevron) { vm.move(p.id, i, i - 1) },
                SheetAction(stringResource(R.string.pl_move_down), CruiseIcons.Chevron) { vm.move(p.id, i, i + 1) },
                SheetAction(stringResource(R.string.pl_remove), CruiseIcons.Delete) { vm.remove(p.id, i) },
            ),
            onDismiss = { itemMenu = null },
        )
    }
}

@StringRes private fun SmartList.label() = when (this) {
    SmartList.Favorites -> R.string.pl_smart_favorites
    SmartList.MostPlayed -> R.string.pl_smart_most
    SmartList.Recent -> R.string.pl_smart_recent
}

@DrawableRes private fun SmartList.icon() = when (this) {
    SmartList.Favorites -> CruiseIcons.Favorite
    SmartList.MostPlayed -> CruiseIcons.Equalizer
    SmartList.Recent -> CruiseIcons.Refresh
}
