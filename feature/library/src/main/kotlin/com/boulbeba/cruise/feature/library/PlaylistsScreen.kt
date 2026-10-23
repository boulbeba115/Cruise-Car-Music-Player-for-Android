package com.boulbeba.cruise.feature.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.data.db.PlaylistRow
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.model.MediaIds
import com.boulbeba.cruise.core.ui.ActionSheet
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistsViewModel @Inject constructor(val actions: TrackActions) : ViewModel() {
    private val repo = actions.library
    val playlists = repo.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val open = MutableStateFlow<PlaylistRow?>(null)
    val tracks = open.flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repo.playlistTracks(p.id).mapLatest { repo.rows(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val message = MutableStateFlow<String?>(null)

    fun create(name: String, paths: List<String> = emptyList()) = viewModelScope.launch { repo.createPlaylist(name, paths) }
    fun saveQueue(name: String) = create(name, actions.player.queue.value.map(MediaIds::libraryPath))
    fun rename(id: Long, name: String) = viewModelScope.launch { repo.renamePlaylist(id, name); open.value = open.value?.copy(name = name) }
    fun delete(id: Long) = viewModelScope.launch { repo.deletePlaylist(id); open.value = null }
    fun remove(id: Long, index: Int) = viewModelScope.launch { repo.removeFromPlaylist(id, index) }
    fun move(id: Long, from: Int, to: Int) = viewModelScope.launch { repo.movePlaylistItem(id, from, to) }
    fun export(id: Long) = viewModelScope.launch { message.value = repo.exportPlaylist(id)?.path }
}

/** Playlists (SPEC Q-2, Q-3): list, detail with reorder/remove, rename, delete, export as M3U8 to the USB stick. */
@Composable
fun PlaylistsScreen(modifier: Modifier = Modifier, viewModel: PlaylistsViewModel = hiltViewModel()) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val open by viewModel.open.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf<String?>(null) }
    val dateName = stringResource(R.string.pl_queue_name, SimpleDateFormat("d MMM HH:mm", ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: java.util.Locale.ROOT).format(Date()))
    BackHandler(enabled = open != null) { viewModel.open.value = null }
    Box(modifier.fillMaxSize()) {
        val current = open
        if (current != null) {
            PlaylistDetail(current, viewModel)
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = CruiseTheme.dimens.gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = CruiseTheme.dimens.gutter, bottom = 12.dp)) {
                    Text(skinTitle(stringResource(R.string.pl_title)), style = CruiseTheme.typography.title, color = CruiseTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        CruisePill(stringResource(R.string.pl_new), onClick = { naming = "new" }, icon = CruiseIcons.PlayAll, selected = true)
                        CruisePill(stringResource(R.string.pl_save_queue), onClick = { naming = "queue" }, icon = CruiseIcons.Save)
                    }
                }
                if (playlists.isEmpty()) Empty(R.string.pl_title, R.string.pl_empty)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        GroupItem(
                            title = p.name,
                            subtitle = stringResource(R.string.pl_count, p.trackCount) + (p.sourceFile?.let { "  ·  " + it.substringAfterLast('/') } ?: ""),
                            coverKey = p.firstPath?.let { "pl:${p.id}:$it" } ?: "pl:${p.id}",
                            coverPath = p.firstPath ?: "",
                            coverFolder = p.firstPath?.let { MediaIds.filePath(it).substringBeforeLast('/') } ?: "",
                            onClick = { viewModel.open.value = p },
                        )
                    }
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

@Composable
private fun PlaylistDetail(p: PlaylistRow, vm: PlaylistsViewModel) {
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val playing by vm.actions.player.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var itemMenu by remember { mutableStateOf<Pair<Int, TrackRow>?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = CruiseTheme.dimens.gutter), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(top = CruiseTheme.dimens.gutter, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CruisePill(stringResource(R.string.lib_back), onClick = { vm.open.value = null }, icon = CruiseIcons.Back)
                    Spacer(Modifier.width(16.dp))
                    Text(skinTitle(p.name), style = CruiseTheme.typography.title, color = CruiseTheme.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    CruisePill(stringResource(R.string.lib_play), onClick = { vm.actions.play(tracks) }, icon = CruiseIcons.Play, selected = true, enabled = tracks.isNotEmpty())
                    CruisePill(stringResource(R.string.lib_shuffle), onClick = { vm.actions.play(tracks, shuffle = true) }, icon = CruiseIcons.Shuffle, enabled = tracks.isNotEmpty())
                    CruisePill(stringResource(R.string.pl_rename), onClick = { renaming = true }, icon = CruiseIcons.Tune)
                    CruisePill(stringResource(R.string.pl_export), onClick = { vm.export(p.id) }, icon = CruiseIcons.Usb)
                    CruisePill(stringResource(R.string.pl_delete), onClick = { vm.delete(p.id) }, icon = CruiseIcons.Delete)
                }
                message?.let { Text(stringResource(R.string.pl_exported, it), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.success, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
        itemsIndexed(tracks, key = { i, t -> "$i:${t.path}" }) { i, t ->
            TrackItem(t, onClick = { vm.actions.play(tracks, i) }, onLongClick = { itemMenu = i to t }, active = t.path == playing.libraryPath)
        }
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
