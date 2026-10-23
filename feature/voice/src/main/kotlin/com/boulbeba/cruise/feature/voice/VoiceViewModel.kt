package com.boulbeba.cruise.feature.voice

import android.content.Context
import android.media.AudioManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.car.CarLog
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.data.library.LibraryRepository
import com.boulbeba.cruise.core.data.library.TrackSort
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.playback.PlayerConnection
import com.boulbeba.cruise.core.ui.mediaId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the voice overlay shows. */
sealed interface VoiceUi {
    data object Idle : VoiceUi
    data class Preparing(val installing: Boolean) : VoiceUi
    data class Listening(val partial: String) : VoiceUi
    data class Thinking(val heard: String) : VoiceUi
    /** Done: [message] already formatted; closes by itself. */
    data class Done(val heard: String, @StringRes val message: Int, val arg: String, @DrawableRes val icon: Int) : VoiceUi
    data class Failed(val heard: String?, @StringRes val message: Int, val arg: String = "") : VoiceUi
    data object NoPack : VoiceUi
}

/**
 * Voice commands (owner's request 2026-10-23): listen, understand, act. Music ducks while listening; whatever is
 * not a control word is matched against the library by sound ([VoiceMatcher]) and played.
 */
@HiltViewModel
class VoiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: VoiceEngine,
    private val player: PlayerConnection,
    private val library: LibraryRepository,
) : ViewModel() {
    private val _ui = MutableStateFlow<VoiceUi>(VoiceUi.Idle)
    val ui = _ui.asStateFlow()
    private var job: Job? = null

    val isActive get() = _ui.value != VoiceUi.Idle

    /** Starts listening (the caller has the microphone permission). Pressing again while listening cancels. */
    fun toggle() {
        if (_ui.value is VoiceUi.Listening || _ui.value is VoiceUi.Preparing) { cancel(); return }
        job?.cancel()
        job = viewModelScope.launch {
            if (!engine.hasPack()) { _ui.value = VoiceUi.NoPack; return@launch }
            _ui.value = VoiceUi.Preparing(installing = false)
            val ready = engine.prepare(onStage = { _ui.value = VoiceUi.Preparing(it == VoiceEngine.Stage.Installing) })
            if (!ready) { _ui.value = VoiceUi.Failed(null, R.string.voice_failed_engine); return@launch }
            _ui.value = VoiceUi.Listening("")
            player.setPlayerVolume(DUCKED)
            var heard: List<String> = emptyList()
            try {
                engine.listen().collect { h ->
                    when (h) {
                        is Hearing.Partial -> _ui.value = VoiceUi.Listening(h.text)
                        is Hearing.Final -> heard = h.alternatives
                        Hearing.Silence -> Unit
                        is Hearing.Failed -> CarLog.log("voice", "listen failed: ${h.error}")
                    }
                }
            } finally {
                player.setPlayerVolume(1f)
            }
            if (heard.isEmpty()) { _ui.value = VoiceUi.Failed(null, R.string.voice_failed_heard_nothing); autoClose(); return@launch }
            act(heard)
        }
    }

    /** A request that already carries its words (another app's "play …", or a typed query). */
    fun run(text: String) {
        job?.cancel()
        job = viewModelScope.launch { act(listOf(text)) }
    }

    fun cancel() {
        job?.cancel()
        player.setPlayerVolume(1f)
        _ui.value = VoiceUi.Idle
    }

    private suspend fun act(alternatives: List<String>) {
        val first = alternatives.first()
        _ui.value = VoiceUi.Thinking(first)
        CarLog.log("voice", "heard: ${alternatives.joinToString(" | ")}")
        // Opened cold by another app's "play …": the player may not be connected yet.
        withTimeoutOrNull(5_000) { player.state.first { it.connected } }
        val tracks = library.tracks(TrackSort.Album).first()
        val candidates = candidatesOf(tracks)
        // The recogniser's best guess is not always the right one: take the first alternative that makes sense.
        var chosen: Pair<String, VoiceCommand>? = null
        var match: Match? = null
        for (text in alternatives) {
            val command = VoiceCommands.parse(text)
            if (command is VoiceCommand.Play) {
                val m = VoiceMatcher.best(command, candidates)
                if (m != null && (match == null || m.score > match.score)) { match = m; chosen = text to command }
            } else if (command != VoiceCommand.Unknown && chosen == null) {
                chosen = text to command
                break
            }
        }
        val (text, command) = chosen ?: (first to VoiceCommands.parse(first))
        _ui.value = when (command) {
            VoiceCommand.Next -> { player.next(); VoiceUi.Done(text, R.string.voice_did_next, "", CruiseIcons.Next) }
            VoiceCommand.Previous -> { player.previous(); VoiceUi.Done(text, R.string.voice_did_previous, "", CruiseIcons.Previous) }
            VoiceCommand.Pause -> { if (player.state.value.isPlaying) player.playPause(); VoiceUi.Done(text, R.string.voice_did_pause, "", CruiseIcons.Pause) }
            VoiceCommand.Resume -> { if (!player.state.value.isPlaying) player.playPause(); VoiceUi.Done(text, R.string.voice_did_resume, "", CruiseIcons.Play) }
            VoiceCommand.VolumeUp -> { volume(AudioManager.ADJUST_RAISE); VoiceUi.Done(text, R.string.voice_did_louder, "", CruiseIcons.Speaker) }
            VoiceCommand.VolumeDown -> { volume(AudioManager.ADJUST_LOWER); VoiceUi.Done(text, R.string.voice_did_quieter, "", CruiseIcons.Speaker) }
            VoiceCommand.Favorites -> {
                val favs = library.favorites().first()
                if (favs.isEmpty()) VoiceUi.Failed(text, R.string.voice_failed_no_favorites)
                else { player.playIds(favs.map { it.mediaId() }, 0, shuffle = true); VoiceUi.Done(text, R.string.voice_did_favorites, "", CruiseIcons.Favorite) }
            }
            VoiceCommand.ShuffleAll -> {
                if (tracks.isEmpty()) VoiceUi.Failed(text, R.string.voice_failed_empty_library)
                else { player.playIds(tracks.map { it.mediaId() }, 0, shuffle = true); VoiceUi.Done(text, R.string.voice_did_shuffle_all, "", CruiseIcons.Shuffle) }
            }
            is VoiceCommand.Play -> {
                val m = match
                if (m == null) VoiceUi.Failed(text, R.string.voice_failed_not_found, command.query) else play(text, command, m, tracks)
            }
            VoiceCommand.Unknown -> VoiceUi.Failed(text, R.string.voice_failed_heard_nothing)
        }
        autoClose()
    }

    private suspend fun play(heard: String, command: VoiceCommand.Play, m: Match, all: List<TrackRow>): VoiceUi {
        val c = m.candidate
        return when (c.kind) {
            VoiceCommand.Kind.Artist -> {
                val rows = library.artistTracks(c.key).first().ifEmpty { all.filter { it.artistKey == c.key } }
                player.playIds(rows.map { it.mediaId() }, 0, shuffle = true)
                VoiceUi.Done(heard, R.string.voice_did_artist, c.text, CruiseIcons.Library)
            }
            VoiceCommand.Kind.Album -> {
                val rows = library.albumTracks(c.key).first()
                player.playIds(rows.map { it.mediaId() }, 0, shuffle = command.shuffle)
                VoiceUi.Done(heard, R.string.voice_did_album, c.text, CruiseIcons.Album)
            }
            else -> {
                // The song, then the rest of its album.
                val track = all.firstOrNull { it.path == c.key }
                val album = track?.let { library.albumTracks(it.albumKey).first() }.orEmpty()
                val index = album.indexOfFirst { it.path == c.key }
                if (index >= 0) player.playIds(album.map { it.mediaId() }, index, shuffle = command.shuffle)
                else track?.let { player.playIds(listOf(it.mediaId()), 0) }
                VoiceUi.Done(heard, R.string.voice_did_track, listOfNotNull(c.text, c.artist.takeIf { it.isNotBlank() }).joinToString(" · "), CruiseIcons.Play)
            }
        }
    }

    private fun candidatesOf(tracks: List<TrackRow>): List<Candidate> {
        val out = ArrayList<Candidate>(tracks.size + tracks.size / 4)
        tracks.mapTo(out) { Candidate(VoiceCommand.Kind.Track, it.title, it.path, it.artist) }
        tracks.groupBy { it.artistKey }.forEach { (key, rows) ->
            val name = rows.first().let { it.albumArtist.ifBlank { it.artist } }
            if (key.isNotBlank() && name.isNotBlank()) out += Candidate(VoiceCommand.Kind.Artist, name, key)
        }
        tracks.groupBy { it.albumKey }.forEach { (key, rows) ->
            val r = rows.first()
            if (r.album.isNotBlank()) out += Candidate(VoiceCommand.Kind.Album, r.album, key, r.albumArtist)
        }
        return out
    }

    private fun volume(direction: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI) }
    }

    private fun autoClose() {
        val shown = _ui.value
        viewModelScope.launch {
            delay(if (shown is VoiceUi.Failed) 3_500 else 2_200)
            if (_ui.value == shown) _ui.value = VoiceUi.Idle
        }
    }

    private companion object {
        const val DUCKED = 0.15f
    }
}
