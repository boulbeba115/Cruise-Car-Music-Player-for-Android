package com.boulbeba.cruise.feature.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.data.db.PlayStatEntity
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.data.library.LibraryRepository
import com.boulbeba.cruise.core.data.tags.LrcParser
import com.boulbeba.cruise.core.data.tags.LyricLine
import com.boulbeba.cruise.core.model.MediaIds
import com.boulbeba.cruise.core.playback.PlaybackSettings
import com.boulbeba.cruise.core.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lyrics of the current track: synced lines when an LRC is available, else plain text. */
data class Lyrics(val synced: List<LyricLine>, val plain: String?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    val player: PlayerConnection,
    private val library: LibraryRepository,
    private val playbackSettings: PlaybackSettings,
) : ViewModel() {
    /** Read when the screen opens, so a change in Settings applies the next time Now Playing is shown. */
    val gesturesEnabled: Boolean get() = playbackSettings.screenGestures

    val state = player.state

    private val currentPath = player.state.map { it.libraryPath }.distinctUntilChanged()

    /** Library row of the current track (titles of CUE tracks, quality details). */
    val track = currentPath.mapLatest { p -> p?.let { runCatching { library.rows(listOf(it)).firstOrNull() }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stat = currentPath.flatMapLatest { p -> if (p == null) flowOf(null) else library.stat(p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as PlayStatEntity?)

    val lyrics = currentPath.mapLatest { p -> p?.let { loadLyrics(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Queue with library details, for the queue panel. */
    val queue = player.queue.mapLatest { ids ->
        val rows = runCatching { library.rows(ids.map(MediaIds::libraryPath)) }.getOrDefault(emptyList()).associateBy { it.path }
        ids.map { id -> id to rows[MediaIds.libraryPath(id)] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Pair<String, TrackRow?>>())

    fun toggleFavorite() {
        val p = state.value.libraryPath ?: return
        viewModelScope.launch { library.toggleFavorite(p) }
    }

    private suspend fun loadLyrics(path: String): Lyrics? = withContext(Dispatchers.IO) {
        val audio = File(path.substringBefore('#'))
        val lrc = LrcParser.sidecar(audio)?.let { runCatching { LrcParser.parse(it.readText()) }.getOrNull() }.orEmpty()
        val embedded = runCatching { library.lyrics(path) }.getOrNull()
        val embeddedSynced = embedded?.let(LrcParser::parse).orEmpty()
        when {
            lrc.isNotEmpty() -> Lyrics(lrc, null)
            embeddedSynced.isNotEmpty() -> Lyrics(embeddedSynced, null)
            !embedded.isNullOrBlank() -> Lyrics(emptyList(), embedded)
            else -> null
        }
    }
}
