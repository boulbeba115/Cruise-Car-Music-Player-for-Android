@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.boulbeba.cruise.core.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.media.AudioManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.boulbeba.cruise.core.audio.CruiseAudioProcessor
import com.boulbeba.cruise.core.audio.ReplayGainMode
import com.boulbeba.cruise.core.audio.SoundEngine
import com.boulbeba.cruise.core.audio.SoundSettingsStore
import com.boulbeba.cruise.core.data.library.LibraryRepository
import com.boulbeba.cruise.core.data.library.LibraryScanner
import com.boulbeba.cruise.core.data.db.LibraryDao
import com.boulbeba.cruise.core.model.MediaIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.boulbeba.cruise.core.model.CruiseLog
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Background playback (SPEC §4.1). One ExoPlayer behind a [MediaSession]: the notification, media keys,
 * Bluetooth and the UI are all controllers of the same session.
 *
 * Car behaviour: requests audio focus (which makes TW head units send their steering keys to Cruise as media keys), saves the
 * position every 5 s, pauses shortly after the screen turns off so a sleeping unit is never kept awake, and
 * resumes when it wakes.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var store: QueueStore
    @Inject lateinit var settings: PlaybackSettings
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var dao: LibraryDao
    @Inject lateinit var sound: SoundSettingsStore
    @Inject lateinit var scanner: LibraryScanner

    private lateinit var exo: ExoPlayer
    private lateinit var player: FadingPlayer
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var pausedForSleep = false
    private var lastPlayingAt = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Play statistics of the item being played (SPEC §4.5 most played, Q-7).
    private var statId: String? = null
    private var statMaxPositionMs = 0L
    private var statDurationMs = 0L

    override fun onCreate() {
        super.onCreate()
        exo = ExoPlayer.Builder(this, renderersFactory())
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            // The unit reports its line output as a "headset"; spurious unplug events must not pause the music.
            .setHandleAudioBecomingNoisy(false)
            // Keeps the CPU awake only while playing; Cruise pauses when the unit goes to sleep (see sleepPause).
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player = FadingPlayer(exo)
        exo.addListener(listener)
        exo.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                // Gapless: the next track's decoder starts before the transition, so name the item the event is for.
                val item = eventTime.timeline.takeIf { eventTime.windowIndex < it.windowCount }
                    ?.getWindow(eventTime.windowIndex, Timeline.Window())?.mediaItem ?: exo.currentMediaItem
                CruiseLog.log("player", "decoder $decoderName for ${item?.mediaId?.substringAfterLast('/')}")
            }
        })
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .apply { appLaunchIntent()?.let(::setSessionActivity) }
            .build()

        restoreQueue(play = false)
        if (exo.mediaItemCount == 0) startFreshQueue()
        pausedForSleep = store.loadPosition()?.pausedForSleep == true

        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        handler.postDelayed(saveTicker, SAVE_INTERVAL_MS)
        CruiseLog.log("player", "service created")
    }

    /**
     * Platform decoders first; FFmpeg for what the unit cannot decode (FLAC on the owner's unit, ALAC…). The audio sink
     * carries Cruise's sound engine (SPEC §4.3).
     */
    private fun renderersFactory() = object : DefaultRenderersFactory(this) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioOutputPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(CruiseAudioProcessor()))
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .build()
    }
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        .setEnableDecoderFallback(true)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        if (!exo.playWhenReady || exo.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        savePosition()
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenReceiver) }
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        io.shutdown()
        super.onDestroy()
    }

    // ---- Persistence ---------------------------------------------------------------------------------------

    private fun restoreQueue(play: Boolean) {
        if (exo.mediaItemCount > 0) return
        val paths = store.loadQueue()
        if (paths.isEmpty()) return
        val position = store.loadPosition()
        val index = position?.index?.coerceIn(0, paths.lastIndex) ?: 0
        exo.setMediaItems(paths.map(MediaItems::fromPath), index, position?.positionMs ?: 0)
        exo.shuffleModeEnabled = position?.shuffle == true
        exo.repeatMode = position?.repeatMode ?: Player.REPEAT_MODE_OFF
        exo.prepare()
        if (play) player.play()
    }

    /**
     * Fresh install or empty queue (CHANGE_REQUESTS.md CR-03): as soon as the library has music, queue all of it in
     * shuffle from a random track, paused, so Now Playing always shows a song. During the very first scan it waits for
     * the scan to finish (at most [FRESH_SCAN_WAIT_MS]) so the queue holds the whole stick, not just the first files
     * found; a library known from before is used at once.
     */
    private fun startFreshQueue() {
        scope.launch {
            library.trackCount.first { it > 0 }
            if (!scanner.state.value.quiet) withTimeoutOrNull(FRESH_SCAN_WAIT_MS) { scanner.state.first { !it.running } }
            if (exo.mediaItemCount > 0) return@launch
            val tracks = withContext(Dispatchers.IO) { library.allTracks().sortedWith(LibraryRepository.FOLDER_ORDER) }
            if (tracks.isEmpty() || exo.mediaItemCount > 0) return@launch
            exo.shuffleModeEnabled = true
            exo.setMediaItems(tracks.map { MediaItems.fromPath(MediaIds.of(it.path, it.cueStartMs, it.cueEndMs)) }, Random.nextInt(tracks.size), 0)
            exo.prepare()
            CruiseLog.log("player", "fresh start: ${tracks.size} tracks in shuffle")
        }
    }

    private fun saveQueue() {
        val paths = List(exo.mediaItemCount) { exo.getMediaItemAt(it).mediaId }
        io.execute { store.saveQueue(paths) }
    }

    private fun savePosition() {
        if (exo.mediaItemCount == 0) return
        store.savePosition(
            PlaybackPosition(
                index = exo.currentMediaItemIndex,
                positionMs = exo.currentPosition,
                shuffle = exo.shuffleModeEnabled,
                repeatMode = exo.repeatMode,
                wasPlaying = exo.isPlaying,
                pausedForSleep = pausedForSleep,
            ),
        )
    }

    private val saveTicker = object : Runnable {
        override fun run() {
            if (exo.isPlaying) {
                lastPlayingAt = SystemClock.elapsedRealtime()
                savePosition()
                statMaxPositionMs = maxOf(statMaxPositionMs, exo.currentPosition)
            }
            updateVolume()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(p: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) saveQueue()
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) onItemChanged()
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) {
                if (p.isPlaying) {
                    lastPlayingAt = SystemClock.elapsedRealtime()
                    pausedForSleep = false
                }
                savePosition()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            CruiseLog.log("player", "error ${error.errorCodeName} on ${exo.currentMediaItem?.mediaId}: ${error.message}")
            // Skip unplayable files instead of stopping the drive's music; after the last one, behave like the end of
            // the queue (back to the first track, paused) rather than staying stuck in the error state.
            if (exo.hasNextMediaItem()) {
                exo.seekToNextMediaItem()
                exo.prepare()
                exo.play()
            } else {
                exo.seekTo(0, 0)
                exo.pause()
                exo.prepare()
            }
        }
    }

    // ---- Per-track: statistics and ReplayGain -----------------------------------------------------------------

    /** Records the previous track (played ≥ 50 % or 4 min counts as a play, else a skip) and loads the new gain. */
    private fun onItemChanged() {
        val previous = statId
        val listened = maxOf(statMaxPositionMs, if (previous == exo.currentMediaItem?.mediaId) exo.currentPosition else 0)
        if (previous != null && statDurationMs > 0) {
            val completed = listened >= statDurationMs / 2 || listened >= 240_000
            val path = MediaIds.libraryPath(previous)
            scope.launch(Dispatchers.IO) { runCatching { library.recordPlay(path, completed) } }
        }
        val current = exo.currentMediaItem?.mediaId
        statId = current
        statMaxPositionMs = 0
        statDurationMs = exo.duration.takeIf { it > 0 } ?: 0
        applyReplayGain(current)
    }

    /** ReplayGain (SPEC DSP-2): album gain when playing in order (albums, folders), track gain when shuffling. */
    private fun applyReplayGain(id: String?) {
        if (id == null) return
        scope.launch {
            val row = runCatching { kotlinx.coroutines.withContext(Dispatchers.IO) { dao.gain(MediaIds.libraryPath(id)) } }.getOrNull()
            val mode = sound.settings.value.replayGain
            val useAlbum = mode == ReplayGainMode.Album || (mode == ReplayGainMode.Auto && !exo.shuffleModeEnabled)
            val gain = if (useAlbum) row?.rgAlbumGain ?: row?.rgTrackGain else row?.rgTrackGain ?: row?.rgAlbumGain
            val peak = if (useAlbum) row?.rgAlbumPeak ?: row?.rgTrackPeak else row?.rgTrackPeak ?: row?.rgAlbumPeak
            SoundEngine.replayGainDb = gain ?: 0f
            SoundEngine.replayGainPeak = peak ?: 1f
        }
    }

    private fun updateVolume() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        SoundEngine.volume = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    // ---- Ignition: sleep and wake ---------------------------------------------------------------------------

    private val sleepPause = Runnable {
        if (exo.isPlaying) {
            pausedForSleep = true
            player.pause()
            savePosition()
            CruiseLog.log("player", "paused for sleep")
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val delay = settings.sleepPauseDelayMs
                    if (exo.isPlaying && delay > 0) {
                        handler.postDelayed(sleepPause, delay)
                    } else if (settings.isHeadUnit && !exo.isPlaying && SystemClock.elapsedRealtime() - lastPlayingAt < RECENT_PLAY_MS) {
                        // The unit itself paused us just before switching off (ignition): remember to resume. On a
                        // phone this is the user pausing and then locking it, which must not resume at unlock.
                        pausedForSleep = true
                        savePosition()
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    handler.removeCallbacks(sleepPause)
                    if (pausedForSleep && settings.resumeOnWake && exo.mediaItemCount > 0) {
                        CruiseLog.log("player", "resuming after wake")
                        player.play()
                    }
                }
            }
        }
    }

    private fun appLaunchIntentRaw(): Intent? =
        packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun appLaunchIntent(): PendingIntent? = appLaunchIntentRaw()?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems.map(MediaItems::resolve).toMutableList())

        /** "Play" from a media key or a reboot while Cruise was not running: continue where it stopped. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val paths = store.loadQueue()
            if (paths.isEmpty()) return Futures.immediateFailedFuture(UnsupportedOperationException("nothing to resume"))
            val position = store.loadPosition()
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    paths.map(MediaItems::fromPath),
                    position?.index?.coerceIn(0, paths.lastIndex) ?: 0,
                    position?.positionMs ?: 0,
                ),
            )
        }
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 5_000L
        const val RECENT_PLAY_MS = 15_000L
        const val FRESH_SCAN_WAIT_MS = 10_000L
    }
}
