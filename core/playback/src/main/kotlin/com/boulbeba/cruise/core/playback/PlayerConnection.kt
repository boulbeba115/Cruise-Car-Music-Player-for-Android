@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.boulbeba.cruise.core.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
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
 *
 * Until the controller connects (a cold start takes a few seconds on the owner's unit) the state is the last song
 * shown, from a small snapshot, so Now Playing opens on it at once instead of a loading screen; play/pause, next,
 * previous and seek pressed meanwhile are carried out as soon as the controller is there.
 */
@Singleton
class PlayerConnection @Inject constructor(@ApplicationContext private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private val snapshot = context.getSharedPreferences("player_snapshot", Context.MODE_PRIVATE)
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(loadSnapshot())
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
            pending.forEach { it(c) }
            pending.clear()
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
        saveSnapshot(_state.value, force = true)
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

    fun playPause() = whenConnected { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun play() = whenConnected { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun pause() = whenConnected { it.pause() }

    /**
     * Playback speed and pitch together, like a record or a tape running faster or slower (the hands-on covers,
     * CHANGE_REQUESTS.md CR-24). Always set back to 1 when the hand lets go.
     */
    fun setSpeed(speed: Float, pitch: Float = speed) {
        controller?.playbackParameters = PlaybackParameters(speed.coerceIn(0.1f, 4f), pitch.coerceIn(0.1f, 4f))
    }

    fun next() = whenConnected { it.seekToNext() }

    fun previous() = whenConnected { it.seekToPrevious() }

    fun seekTo(positionMs: Long) {
        whenConnected { it.seekTo(positionMs) }
        _state.update { it.copy(positionMs = positionMs) }
    }

    /** Runs [action] now, or once the controller has connected (the snapshot is on screen meanwhile). */
    private fun whenConnected(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else if (enabled) pending += action
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
        val before = _state.value
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
        saveSnapshot(_state.value, force = before.libraryPath != _state.value.libraryPath || before.title != _state.value.title)
        if (_queue.value.size != c.mediaItemCount || _queue.value.getOrNull(c.currentMediaItemIndex) != path) {
            _queue.value = List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId }
        }
        ticker?.cancel()
        if (c.isPlaying) {
            // Screens extrapolate the position from positionSampledAt (PlayerUiState.positionAt), so a resync every few
            // seconds is enough; a faster tick would recompose every screen that watches the player (CR-09).
            ticker = scope.launch {
                while (isActive) {
                    delay(POSITION_RESYNC_MS)
                    _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0), positionSampledAt = android.os.SystemClock.elapsedRealtime()) }
                }
            }
        }
    }

    // ---- Snapshot ----------------------------------------------------------------------------------------

    /** Kept when the song changes and when the screen goes away; a few short strings, so cheap to write. */
    private fun saveSnapshot(s: PlayerUiState, force: Boolean) {
        if (!force || !enabled || !s.connected) return
        if (!s.hasMedia) {
            snapshot.edit().clear().apply()
            return
        }
        snapshot.edit()
            .putString(K_PATH, s.path).putString(K_LIBRARY_PATH, s.libraryPath)
            .putString(K_TITLE, s.title).putString(K_ARTIST, s.artist).putString(K_ALBUM, s.album)
            .putLong(K_POSITION, s.positionMs).putLong(K_DURATION, s.durationMs)
            .putBoolean(K_SHUFFLE, s.shuffle).putInt(K_REPEAT, s.repeatMode)
            .putInt(K_INDEX, s.queueIndex).putInt(K_SIZE, s.queueSize)
            .apply()
    }

    private fun loadSnapshot(): PlayerUiState {
        if (!enabled) return PlayerUiState()
        val path = snapshot.getString(K_PATH, null) ?: return PlayerUiState()
        return PlayerUiState(
            connected = false,
            hasMedia = true,
            path = path,
            title = snapshot.getString(K_TITLE, null).orEmpty(),
            artist = snapshot.getString(K_ARTIST, null).orEmpty(),
            album = snapshot.getString(K_ALBUM, null).orEmpty(),
            positionMs = snapshot.getLong(K_POSITION, 0),
            durationMs = snapshot.getLong(K_DURATION, 0),
            shuffle = snapshot.getBoolean(K_SHUFFLE, false),
            repeatMode = snapshot.getInt(K_REPEAT, Player.REPEAT_MODE_OFF),
            queueIndex = snapshot.getInt(K_INDEX, 0),
            queueSize = snapshot.getInt(K_SIZE, 0),
            libraryPath = snapshot.getString(K_LIBRARY_PATH, null),
        )
    }
}

private const val POSITION_RESYNC_MS = 5_000L
private const val K_PATH = "path"
private const val K_LIBRARY_PATH = "libraryPath"
private const val K_TITLE = "title"
private const val K_ARTIST = "artist"
private const val K_ALBUM = "album"
private const val K_POSITION = "position"
private const val K_DURATION = "duration"
private const val K_SHUFFLE = "shuffle"
private const val K_REPEAT = "repeat"
private const val K_INDEX = "index"
private const val K_SIZE = "size"
