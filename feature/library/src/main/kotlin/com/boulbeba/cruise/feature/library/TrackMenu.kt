package com.boulbeba.cruise.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.ui.ActionSheet
import com.boulbeba.cruise.core.ui.NavRequests
import com.boulbeba.cruise.core.ui.SheetAction
import com.boulbeba.cruise.core.ui.TextEntryDialog

/** What a long-press opened: one track, or a whole album / group. */
data class MenuTarget(val title: String, val subtitle: String?, val tracks: List<TrackRow>, val albumKey: String? = null, val folder: String? = null)

/** State + dialogs of the long-press menu, shared by every list screen. */
class TrackMenuState {
    var target by mutableStateOf<MenuTarget?>(null)
    var choosingPlaylist by mutableStateOf<MenuTarget?>(null)
    var naming by mutableStateOf<MenuTarget?>(null)

    fun open(t: MenuTarget) { target = t }

    fun forTrack(t: TrackRow) = open(MenuTarget(t.title, listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "), listOf(t), t.albumKey, t.folder))
}

@Composable
fun rememberTrackMenu() = remember { TrackMenuState() }

@Composable
fun TrackMenuHost(state: TrackMenuState, actions: TrackActions, onNavigate: (folder: String?, albumKey: String?) -> Unit = { f, a -> NavRequests.folder.value = f; NavRequests.album.value = a }) {
    val scope = rememberCoroutineScope()
    val playlists by actions.playlists.collectAsStateWithLifecycle(initialValue = emptyList())
    state.target?.let { t ->
        val list = buildList {
            add(SheetAction(stringResource(R.string.act_play_now), CruiseIcons.Play) { actions.play(t.tracks) })
            add(SheetAction(stringResource(R.string.act_play_next), CruiseIcons.PlayAll) { actions.playNext(t.tracks) })
            add(SheetAction(stringResource(R.string.act_add_queue), CruiseIcons.Playlist) { actions.addToQueue(t.tracks) })
            add(SheetAction(stringResource(R.string.act_add_playlist), CruiseIcons.Library) { state.choosingPlaylist = t })
            if (t.tracks.size == 1) add(SheetAction(stringResource(R.string.act_favorite), CruiseIcons.Ok) { actions.toggleFavorite(scope, t.tracks.first()) })
            if (t.albumKey != null && t.tracks.size == 1) add(SheetAction(stringResource(R.string.act_go_album), CruiseIcons.Album) { onNavigate(null, t.albumKey) })
            if (t.folder != null) add(SheetAction(stringResource(R.string.act_go_folder), CruiseIcons.Folder) { onNavigate(t.folder, null) })
        }
        ActionSheet(t.title, t.subtitle, list, onDismiss = { state.target = null })
    }
    state.choosingPlaylist?.let { t ->
        val list = buildList {
            add(SheetAction(stringResource(R.string.act_new_playlist), CruiseIcons.PlayAll) { state.naming = t })
            playlists.forEach { p -> add(SheetAction(p.name, CruiseIcons.Playlist) { actions.addToPlaylist(scope, p.id, t.tracks) }) }
        }
        ActionSheet(stringResource(R.string.act_choose_playlist), t.title, list, onDismiss = { state.choosingPlaylist = null })
    }
    state.naming?.let { t ->
        TextEntryDialog(
            title = stringResource(R.string.pl_name),
            initial = t.title,
            confirm = stringResource(R.string.pl_create),
            onConfirm = { actions.newPlaylist(scope, it, t.tracks) },
            onDismiss = { state.naming = null },
        )
    }
}
