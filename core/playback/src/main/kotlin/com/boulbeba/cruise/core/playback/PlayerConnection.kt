@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.boulbeba.cruise.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.boulbeba.cruise.core.model.AudioFiles
import com.boulbeba.cruise.core.model.MediaIds
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Everything a screen needs to draw playback. Immutable; positions update twice a second while playing. */
data class PlayerUiState(
    val connected: Boolean = false,
    val hasMedia: Boolean = false,
    val path: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: ByteArray? = null,
    val artworkKey: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    /** When [positionMs] was sampled (elapsedRealtime), so the bar can move smoothly between samples. */
    val positionSampledAt: Long = 0,
    /** Library key of the current track ("file" or "file#N" for CUE tracks). */
    val libraryPath: String? = null,
) {
    /** Position now, extrapolated from the last sample while playing. */
    fun positionAt(nowElapsed: Long): Long =
        if (isPlaying) (positionMs + (nowElapsed - positionSampledAt)).coerceIn(0, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE) else positionMs
}

/**
 * The UI's handle on the playback service: a [MediaController] exposed as a [StateFlow]. Connect while the
 * activity is visible; playback itself lives in the service and continues without it.
 */
@Singleton
class PlayerConnection @Inject constructor(@ApplicationContext private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<String>>(emptyList())
    /** Media ids of the queue, in play order. */
    val queue: StateFlow<List<String>> = _queue.asStateFlow()

    fun connect() {
        if (controller != null || !enabled) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            refresh()
        }, MoreExecutors.directExecutor())
    }

    /** Screenshot tests: show [state] and [queue] as if they were playing (only while the connection is disabled). */
    @VisibleForTesting
    fun preview(state: PlayerUiState, queue: List<String> = listOfNotNull(state.path)) {
        if (enabled) return
        _state.value = state
        _queue.value = queue
    }

    fun disconnect() {
        ticker?.cancel()
        controller?.run {
            removeListener(listener)
            release()
        }
        controller = null
        _state.update { it.copy(connected = false) }
    }

    // ---- Actions -----------------------------------------------------------------------------------------

    /** Replaces the queue with [files] and plays from [startIndex]. */
    fun play(files: List<File>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(files.map { MediaItems.fromPath(it.path) }, if (shuffle) 0 else startIndex, 0)
        c.prepare()
        c.play()
    }

    /** Replaces the queue with library tracks (CUE tracks keep their clip) and plays from [startIndex]. */
    fun playIds(ids: List<String>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (ids.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(ids.map(MediaItems::fromPath), if (shuffle) 0 else startIndex.coerceIn(0, ids.lastIndex), 0)
        c.prepare()
        c.play()
    }

    /** Inserts after the current track (SPEC Q-1 "play next"). */
    fun playNext(ids: List<String>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playIds(ids)
        c.addMediaItems((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), ids.map(MediaItems::fromPath))
    }

    fun addToQueue(ids: List<String>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playIds(ids)
        c.addMediaItems(ids.map(MediaItems::fromPath))
    }

    fun removeFromQueue(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) } }

    fun moveInQueue(from: Int, to: Int) { controller?.let { if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) } }

    fun jumpTo(index: Int) {
        controller?.let {
            if (index in 0 until it.mediaItemCount) {
                it.seekTo(index, 0)
                it.play()
            }
        }
    }

    fun clearQueueExceptCurrent() {
        val c = controller ?: return
        val current = c.currentMediaItemIndex
        if (current + 1 < c.mediaItemCount) c.removeMediaItems(current + 1, c.mediaItemCount)
        if (current > 0) c.removeMediaItems(0, current)
    }

    fun playPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNext() }

    /** Player volume 0…1 (not the system volume): used to duck the music while listening to a voice command. */
    fun setPlayerVolume(volume: Float) { controller?.volume = volume.coerceIn(0f, 1f) }

    fun previous() { controller?.seekToPrevious() }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ---- State -------------------------------------------------------------------------------------------

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    companion object {
        /** Off in JVM screenshot tests, where binding to the playback service is not supported. */
        @VisibleForTesting @Volatile var enabled = true
    }

    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val meta: MediaMetadata = c.mediaMetadata
        val path = item?.mediaId
        val artwork = meta.artworkData
        _state.value = PlayerUiState(
            connected = true,
            hasMedia = item != null,
            path = path?.let(MediaIds::filePath),
            title = meta.title?.toString() ?: path?.let { AudioFiles.displayName(File(MediaIds.filePath(it))) }.orEmpty(),
            artist = (meta.artist ?: meta.albumArtist)?.toString().orEmpty(),
            album = meta.albumTitle?.toString() ?: path?.let { File(MediaIds.filePath(it)).parentFile?.name }.orEmpty(),
            artwork = artwork,
            artworkKey = artwork?.let { "${path}#${it.size}" },
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: 0,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queueIndex = c.currentMediaItemIndex,
            queueSize = c.mediaItemCount,
            positionSampledAt = android.os.SystemClock.elapsedRealtime(),
            libraryPath = path?.let(MediaIds::libraryPath),
        )
        if (_queue.value.size != c.mediaItemCount || _queue.value.getOrNull(c.currentMediaItemIndex) != path) {
            _queue.value = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId }
        }
        ticker?.cancel()
        if (c.isPlaying) {
            ticker = scope.launch {
                while (isActive) {
                    delay(500)
                    _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0), positionSampledAt = android.os.SystemClock.elapsedRealtime()) }
                }
            }
        }
    }
}
