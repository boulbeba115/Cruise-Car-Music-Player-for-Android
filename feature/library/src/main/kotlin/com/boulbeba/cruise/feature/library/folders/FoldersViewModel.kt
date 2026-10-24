package com.boulbeba.cruise.feature.library.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.data.library.FolderNode
import com.boulbeba.cruise.core.data.library.FolderTree
import com.boulbeba.cruise.core.ui.NavRequests
import com.boulbeba.cruise.feature.library.TrackActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Folders tab shows: the volume list (when [current] is null and several volumes have music) or a folder. */
data class FoldersUi(val tree: FolderTree? = null, val current: FolderNode? = null) {
    val loading: Boolean get() = tree == null
    val isTop: Boolean get() = tree == null || current == null || current == tree.start()
}

/**
 * Folder browser built from the library (CHANGE_REQUESTS.md CR-04): only folders that contain music, single-path
 * chains collapsed. The open folder is kept as a path so the view survives the tree being rebuilt while a scan runs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoldersViewModel @Inject constructor(val actions: TrackActions) : ViewModel() {
    private val repo = actions.library

    /** Path of the open folder; null = the start (single volume) or the volume list. */
    private val openPath = MutableStateFlow<String?>(null)

    private val tree: StateFlow<FolderTree?> = repo.folderTree().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val ui: StateFlow<FoldersUi> = combine(tree, openPath) { t, path ->
        if (t == null) FoldersUi() else FoldersUi(t, path?.let(t::node) ?: t.start())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUi())

    val tracks: StateFlow<List<TrackRow>> = ui.flatMapLatest { u ->
        val node = u.current
        if (node?.row == null) flowOf(emptyList()) else repo.folderTracks(node.path)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // "Open folder" from a track menu or a search result.
        viewModelScope.launch {
            NavRequests.folder.collect { path ->
                if (path != null) {
                    NavRequests.folder.value = null
                    openPath.value = path
                }
            }
        }
    }

    fun open(node: FolderNode) {
        val t = ui.value.tree ?: return
        openPath.value = t.collapse(node).path
    }

    /** Up one level. Returns false when already at the top. */
    fun up(): Boolean {
        val u = ui.value
        val t = u.tree ?: return false
        val current = u.current ?: return false
        if (current == t.start()) return false
        openPath.value = t.up(current)?.path
        return true
    }

    fun play(index: Int) = actions.play(tracks.value, index)

    fun playTree(node: FolderNode, shuffle: Boolean = false) {
        viewModelScope.launch {
            val all = repo.folderTreeTracks(node.path)
            if (all.isNotEmpty()) actions.play(all, 0, shuffle)
        }
    }
}
