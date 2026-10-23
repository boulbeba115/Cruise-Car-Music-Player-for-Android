package com.boulbeba.cruise.feature.library

import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.data.library.LibraryRepository
import com.boulbeba.cruise.core.playback.PlayerConnection
import com.boulbeba.cruise.core.ui.mediaId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The actions every list offers (SPEC Q-1, Q-2, Q-7), shared by Library, Search and Playlists. */
class TrackActions @Inject constructor(
    val player: PlayerConnection,
    val library: LibraryRepository,
) {
    val playlists = library.playlists

    fun play(tracks: List<TrackRow>, index: Int = 0, shuffle: Boolean = false) = player.playIds(tracks.map { it.mediaId() }, index, shuffle)
    fun playNext(tracks: List<TrackRow>) = player.playNext(tracks.map { it.mediaId() })
    fun addToQueue(tracks: List<TrackRow>) = player.addToQueue(tracks.map { it.mediaId() })

    fun addToPlaylist(scope: CoroutineScope, playlistId: Long, tracks: List<TrackRow>) =
        scope.launch { library.addToPlaylist(playlistId, tracks.map { it.path }) }

    fun newPlaylist(scope: CoroutineScope, name: String, tracks: List<TrackRow>) =
        scope.launch { library.createPlaylist(name, tracks.map { it.path }) }

    fun toggleFavorite(scope: CoroutineScope, track: TrackRow) = scope.launch { library.toggleFavorite(track.path) }
}
