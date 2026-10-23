package com.boulbeba.cruise.feature.folders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.ui.NavRequests
import com.boulbeba.cruise.core.model.FolderListing
import com.boulbeba.cruise.core.model.FolderScanner
import com.boulbeba.cruise.core.model.StorageRoots
import com.boulbeba.cruise.core.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageRoot(val dir: File, val isRemovable: Boolean)

data class FoldersUiState(
    val hasPermission: Boolean = false,
    val roots: List<StorageRoot> = emptyList(),
    /** Null while the volume list is shown. */
    val root: StorageRoot? = null,
    val listing: FolderListing? = null,
    val loading: Boolean = false,
)

@HiltViewModel
class FoldersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val player: PlayerConnection,
) : ViewModel() {

    private val _state = MutableStateFlow(FoldersUiState())
    val state: StateFlow<FoldersUiState> = _state.asStateFlow()
    val playback = player.state

    private var job: Job? = null

    init {
        refreshRoots()
        // "Go to folder" from a track menu anywhere in the app.
        viewModelScope.launch {
            NavRequests.folder.collect { path ->
                if (path != null) {
                    NavRequests.folder.value = null
                    val dir = File(path)
                    val roots = withContext(Dispatchers.IO) {
                        StorageRoots.removableRoots(context).map { StorageRoot(it, true) } + StorageRoot(Environment.getExternalStorageDirectory(), false)
                    }
                    roots.filter { path.startsWith(it.dir.path) }.maxByOrNull { it.dir.path.length }?.let { open(it, dir) }
                }
            }
        }
    }

    fun refreshRoots() {
        val permission = hasStoragePermission()
        viewModelScope.launch {
            val roots = withContext(Dispatchers.IO) {
                StorageRoots.removableRoots(context).map { StorageRoot(it, true) } +
                    StorageRoot(Environment.getExternalStorageDirectory(), false)
            }
            _state.value = _state.value.copy(hasPermission = permission, roots = roots)
            // One stick and nothing open yet: go straight into it, that is where the music is.
            if (permission && _state.value.root == null) roots.singleOrNull { it.isRemovable }?.let { open(it, it.dir) }
        }
    }

    fun open(root: StorageRoot, dir: File) {
        job?.cancel()
        _state.value = _state.value.copy(root = root, loading = true)
        job = viewModelScope.launch {
            val listing = withContext(Dispatchers.IO) { FolderScanner.list(dir) }
            _state.value = _state.value.copy(listing = listing, loading = false)
        }
    }

    fun openFolder(dir: File) = _state.value.root?.let { open(it, dir) }

    /** Up one level; from a volume's top goes back to the volume list. Returns false when already there. */
    fun up(): Boolean {
        val s = _state.value
        val root = s.root ?: return false
        val dir = s.listing?.dir ?: return false
        if (dir.path == root.dir.path) {
            job?.cancel()
            _state.value = s.copy(root = null, listing = null)
        } else {
            dir.parentFile?.let { open(root, it) }
        }
        return true
    }

    fun playFrom(index: Int) {
        val tracks = _state.value.listing?.tracks ?: return
        player.play(tracks, index)
    }

    fun playAll(shuffle: Boolean) {
        val dir = _state.value.listing?.dir ?: return
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) { FolderScanner.tracksRecursive(dir) }
            if (tracks.isNotEmpty()) player.play(tracks, 0, shuffle)
        }
    }

    fun playFolderTree(dir: File) {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) { FolderScanner.tracksRecursive(dir) }
            if (tracks.isNotEmpty()) player.play(tracks, 0)
        }
    }

    private fun hasStoragePermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}
