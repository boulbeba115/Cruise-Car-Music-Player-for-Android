package com.boulbeba.cruise.feature.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.data.db.AlbumRow
import com.boulbeba.cruise.core.data.db.GroupRow
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.data.library.AlbumSort
import com.boulbeba.cruise.core.data.library.LibraryScanner
import com.boulbeba.cruise.core.ui.NavRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The Library's tabs (CHANGE_REQUESTS.md CR-02). Folders first: the music lives on the stick as folders. */
enum class LibraryTab { Folders, Albums, Artists, Genres, Playlists }

/** A detail page opened from the Albums, Artists or Genres tab (SPEC §5.2). */
sealed interface LibraryRoute {
    data object Home : LibraryRoute
    data class Album(val key: String, val title: String) : LibraryRoute
    data class Artist(val key: String, val name: String) : LibraryRoute
    data class Genre(val name: String) : LibraryRoute
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    val actions: TrackActions,
    private val scanner: LibraryScanner,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("library_ui", Context.MODE_PRIVATE)
    private val repo = actions.library
    private fun <T> Flow<T>.state(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val tab = MutableStateFlow(runCatching { LibraryTab.valueOf(prefs.getString("tab2", "Folders")!!) }.getOrDefault(LibraryTab.Folders))
    val albumSort = MutableStateFlow(runCatching { AlbumSort.valueOf(prefs.getString("albumSort", "Artist")!!) }.getOrDefault(AlbumSort.Artist))

    private val _stack = MutableStateFlow<List<LibraryRoute>>(listOf(LibraryRoute.Home))
    val stack: StateFlow<List<LibraryRoute>> = _stack.asStateFlow()

    val scan = scanner.state
    val trackCount = repo.trackCount.state(0)
    val albums: StateFlow<List<AlbumRow>> = albumSort.flatMapLatest { repo.albums(it) }.state(emptyList())
    val artists: StateFlow<List<GroupRow>> = repo.artists().state(emptyList())
    val genres: StateFlow<List<GroupRow>> = repo.genres().state(emptyList())

    /** Tracks of the detail page on top of the stack. */
    val detailTracks: StateFlow<List<TrackRow>> = _stack.flatMapLatest { s ->
        when (val r = s.last()) {
            is LibraryRoute.Album -> repo.albumTracks(r.key)
            is LibraryRoute.Artist -> repo.artistTracks(r.key)
            is LibraryRoute.Genre -> repo.genreTracks(r.name)
            LibraryRoute.Home -> flowOf(emptyList())
        }
    }.state(emptyList())

    init {
        // "Go to album / open folder" from a long-press menu anywhere: switch to the matching tab.
        viewModelScope.launch {
            NavRequests.album.collect { key ->
                if (key != null) {
                    NavRequests.album.value = null
                    show(LibraryTab.Albums, LibraryRoute.Album(key, ""))
                }
            }
        }
        viewModelScope.launch {
            // The Folders tab's own view model opens the folder and clears the request.
            NavRequests.folder.collect { path -> if (path != null) selectTab(LibraryTab.Folders) }
        }
    }

    fun selectTab(t: LibraryTab) {
        tab.value = t
        _stack.value = listOf(LibraryRoute.Home)
        prefs.edit().putString("tab2", t.name).apply()
    }

    /** Opens [route] as a detail page of tab [t] (from search results and menus). */
    fun show(t: LibraryTab, route: LibraryRoute) {
        selectTab(t)
        _stack.value = listOf(LibraryRoute.Home, route)
    }

    fun setAlbumSort(s: AlbumSort) { albumSort.value = s; prefs.edit().putString("albumSort", s.name).apply() }

    fun open(route: LibraryRoute) { _stack.value = _stack.value + route }

    fun back(): Boolean {
        if (_stack.value.size <= 1) return false
        _stack.value = _stack.value.dropLast(1)
        return true
    }

    fun rescan() = scanner.scan()
}
