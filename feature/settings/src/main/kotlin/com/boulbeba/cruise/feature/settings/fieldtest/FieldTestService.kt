package com.boulbeba.cruise.feature.settings.fieldtest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.boulbeba.cruise.feature.settings.diagnostics.KeyEventLog
import com.boulbeba.cruise.feature.settings.diagnostics.MediaKeyProbe
import kotlin.concurrent.thread

/**
 * Background logger for the car field test (SPEC §10 Phase 0). While active it records what the head unit
 * does around the player — ignition/sleep, display, USB, audio activity, calls, GPS speed, steering keys in
 * the background — and copies everything to the USB stick every two minutes and on screen-off.
 *
 * It holds no wake lock: when the unit sleeps, the logger sleeps too, so it cannot drain the car battery.
 * It stops by itself after [MAX_RUN_MS].
 */
class FieldTestService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var lastBeatElapsed = 0L
    private var lastBeatUptime = 0L
    private var lastAudioMode = -1
    private var lastMusicActive: Boolean? = null
    private var lastExportElapsed = 0L
    private var gps: GpsTracker? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        FieldLog.init(this)
        startInForeground()
        FieldLog.log("logger", "started")
        registerSystemReceivers()
        registerMediaReceivers()
        registerDisplayListener()
        registerAudioWatchers()
        startMediaSession()
        claimMediaButtons()
        if (isScreenOn()) startGps()
        lastBeatElapsed = SystemClock.elapsedRealtime()
        lastBeatUptime = SystemClock.uptimeMillis()
        handler.post(heartbeat)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stop(this)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        FieldLog.log("logger", "stopping")
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(systemReceiver) }
        runCatching { unregisterReceiver(mediaReceiver) }
        runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                getSystemService(AudioManager::class.java)
                    .unregisterAudioPlaybackCallback(playbackCallback as AudioManager.AudioPlaybackCallback)
            }
        }
        stopGps()
        session?.release()
        session = null
        exportInBackground("logger stopped")
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Car test logger", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, FieldTestService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Cruise car test is recording")
            .setContentText("Logs ignition, keys and USB events to the USB stick. Tap Stop when done.")
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
        val locationAllowed = hasLocationPermission()
        var types = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (locationAllowed) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, types)
    }

    // ---- Heartbeat: detects sleep, mode changes, auto-export, auto-stop -------------------------------

    private val heartbeat = object : Runnable {
        override fun run() {
            val elapsed = SystemClock.elapsedRealtime()
            val uptime = SystemClock.uptimeMillis()
            val elapsedGap = elapsed - lastBeatElapsed
            if (elapsedGap > HEARTBEAT_MS * 3) {
                val deepSleep = elapsedGap - (uptime - lastBeatUptime)
                FieldLog.log("sleep", "woke after a gap of ${elapsedGap / 1000}s (deep sleep ${deepSleep / 1000}s)")
            }
            lastBeatElapsed = elapsed
            lastBeatUptime = uptime
            pollAudioState()
            if (elapsed - lastExportElapsed > EXPORT_INTERVAL_MS) exportInBackground("periodic")
            val startedAt = prefs(this@FieldTestService).getLong(KEY_STARTED_AT_WALL, System.currentTimeMillis())
            if (System.currentTimeMillis() - startedAt > MAX_RUN_MS) {
                FieldLog.log("logger", "auto-stop after ${MAX_RUN_MS / 3_600_000} h")
                stop(this@FieldTestService)
                return
            }
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private fun pollAudioState() {
        val audio = getSystemService(AudioManager::class.java)
        if (audio.mode != lastAudioMode) {
            FieldLog.log("audio", "mode=${audioModeName(audio.mode)}")
            lastAudioMode = audio.mode
        }
        val musicActive = audio.isMusicActive
        if (musicActive != lastMusicActive) {
            FieldLog.log("audio", "music active=$musicActive volume=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}")
            lastMusicActive = musicActive
        }
    }

    private fun exportInBackground(reason: String) {
        lastExportElapsed = SystemClock.elapsedRealtime()
        val context = applicationContext
        thread(name = "field-export") {
            val result = FieldExporter.exportAll(context)
            FieldLog.log("export", "$reason -> ${result.primary?.path ?: "failed: ${result.error}"}")
        }
    }

    // ---- System broadcasts -----------------------------------------------------------------------------

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val extras = intent.extras?.let { bundle -> bundle.keySet().joinToString { "$it=${bundle.get(it)}" } }.orEmpty()
            FieldLog.log("system", "${action.substringAfterLast('.')} $extras".trim())
            when (action) {
                Intent.ACTION_SCREEN_OFF -> {
                    stopGps()
                    exportInBackground("screen off")
                }
                Intent.ACTION_SCREEN_ON -> {
                    claimMediaButtons()
                    startGps()
                }
                Intent.ACTION_CONFIGURATION_CHANGED -> {
                    val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    FieldLog.log("system", "uiMode night=${night == Configuration.UI_MODE_NIGHT_YES}")
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun registerSystemReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SHUTDOWN)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            addAction("android.intent.action.PHONE_STATE")
        }
        ContextCompat.registerReceiver(this, systemReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            FieldLog.log("usb", "${intent.action?.substringAfterLast('.')} ${intent.data?.path ?: ""}")
            if (intent.action == Intent.ACTION_MEDIA_MOUNTED) exportInBackground("volume mounted")
        }
    }

    private fun registerMediaReceivers() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(Intent.ACTION_MEDIA_CHECKING)
            addAction(Intent.ACTION_MEDIA_SCANNER_STARTED)
            addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(this, mediaReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    // ---- Display and audio -----------------------------------------------------------------------------

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = FieldLog.log("display", "added $displayId")
        override fun onDisplayRemoved(displayId: Int) = FieldLog.log("display", "removed $displayId")
        override fun onDisplayChanged(displayId: Int) {
            val display = getSystemService(DisplayManager::class.java).getDisplay(displayId) ?: return
            FieldLog.log("display", "$displayId state=${displayStateName(display.state)}")
        }
    }

    private fun registerDisplayListener() {
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, handler)
    }

    private val playbackCallback: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                val players = configs.joinToString { c ->
                    "usage=${c.audioAttributes.usage} content=${c.audioAttributes.contentType}"
                }.ifEmpty { "none" }
                FieldLog.log("audio", "active players: $players")
            }
        }
    } else {
        null
    }

    private fun registerAudioWatchers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(AudioManager::class.java)
                .registerAudioPlaybackCallback(playbackCallback as AudioManager.AudioPlaybackCallback, handler)
        }
        pollAudioState()
    }

    // ---- Media session: steering keys while Cruise is in the background --------------------------------

    private fun startMediaSession() {
        session = MediaSession(this, "CruiseFieldTest").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    if (event != null) KeyEventLog.record("bg-session", event)
                    return true
                }
            })
            val receiver = Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(
                ComponentName(this@FieldTestService, MediaButtonLogReceiver::class.java),
            )
            setMediaButtonReceiver(
                PendingIntent.getBroadcast(this@FieldTestService, 2, receiver, PendingIntent.FLAG_IMMUTABLE),
            )
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or PlaybackState.ACTION_STOP,
                    )
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build(),
            )
            isActive = true
        }
    }

    /** Android routes media keys to the session that last played audio: play 1.5 s of silence to claim them. */
    private fun claimMediaButtons() {
        val probe = MediaKeyProbe(applicationContext)
        probe.start()
        handler.postDelayed({ probe.stop() }, 1_500)
    }

    // ---- GPS speed (offline) ---------------------------------------------------------------------------

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startGps() {
        if (gps != null || !hasLocationPermission()) return
        gps = GpsTracker(getSystemService(LocationManager::class.java), handler).also { it.start() }
    }

    private fun stopGps() {
        gps?.stop()
        gps = null
    }

    private fun isScreenOn(): Boolean =
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_ON

    companion object {
        private const val CHANNEL_ID = "field_test"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.boulbeba.cruise.fieldtest.STOP"
        private const val HEARTBEAT_MS = 20_000L
        private const val EXPORT_INTERVAL_MS = 120_000L
        private const val MAX_RUN_MS = 72L * 3_600_000
        private const val PREFS = "fieldtest"
        private const val KEY_ACTIVE = "logger_active"
        private const val KEY_STARTED_AT_WALL = "logger_started_at"

        fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun isActive(context: Context) = prefs(context).getBoolean(KEY_ACTIVE, false)

        fun start(context: Context) {
            prefs(context).edit().putBoolean(KEY_ACTIVE, true).putLong(KEY_STARTED_AT_WALL, System.currentTimeMillis()).apply()
            ContextCompat.startForegroundService(context, Intent(context, FieldTestService::class.java))
        }

        /** Restarts the logger after a reboot if the field test was still running. */
        fun resumeIfActive(context: Context) {
            if (isActive(context)) ContextCompat.startForegroundService(context, Intent(context, FieldTestService::class.java))
        }

        fun stop(context: Context) {
            prefs(context).edit().putBoolean(KEY_ACTIVE, false).apply()
            context.stopService(Intent(context, FieldTestService::class.java))
        }

        private fun audioModeName(mode: Int) = when (mode) {
            AudioManager.MODE_NORMAL -> "NORMAL"
            AudioManager.MODE_RINGTONE -> "RINGTONE"
            AudioManager.MODE_IN_CALL -> "IN_CALL"
            AudioManager.MODE_IN_COMMUNICATION -> "IN_COMMUNICATION"
            else -> "MODE_$mode"
        }

        private fun displayStateName(state: Int) = when (state) {
            Display.STATE_ON -> "ON"
            Display.STATE_OFF -> "OFF"
            Display.STATE_DOZE -> "DOZE"
            Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
            else -> "STATE_$state"
        }
    }
}

/** Logs GPS fixes as a 30-second summary: does GPS work offline, and how fast is the car going. */
private class GpsTracker(private val manager: LocationManager, private val handler: Handler) {
    private var fixes = 0
    private var maxSpeedKmh = 0f
    private var lastSpeedKmh = 0f
    private var lastAccuracy = 0f
    private var satellitesUsed = 0

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            fixes++
            lastSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else -1f
            maxSpeedKmh = maxOf(maxSpeedKmh, lastSpeedKmh)
            lastAccuracy = location.accuracy
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = FieldLog.log("gps", "provider enabled")
        override fun onProviderDisabled(provider: String) = FieldLog.log("gps", "provider disabled")
    }

    private val gnssCallback: Any? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                satellitesUsed = (0 until status.satelliteCount).count { status.usedInFix(it) }
            }
        }
    } else {
        null
    }

    private val summary = object : Runnable {
        override fun run() {
            FieldLog.log(
                "gps",
                "fixes=$fixes speed=%.0f km/h max=%.0f km/h accuracy=%.0f m satellites=%d".format(
                    lastSpeedKmh, maxSpeedKmh, lastAccuracy, satellitesUsed,
                ),
            )
            fixes = 0
            handler.postDelayed(this, 30_000)
        }
    }

    @SuppressLint("MissingPermission") // checked by FieldTestService before start()
    fun start() {
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            FieldLog.log("gps", "GPS provider disabled in system settings")
        }
        runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, listener, handler.looper) }
            .onFailure { FieldLog.log("gps", "cannot start: $it") }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { manager.registerGnssStatusCallback(gnssCallback as GnssStatus.Callback, handler) }
        }
        handler.postDelayed(summary, 30_000)
    }

    fun stop() {
        handler.removeCallbacks(summary)
        runCatching { manager.removeUpdates(listener) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { manager.unregisterGnssStatusCallback(gnssCallback as GnssStatus.Callback) }
        }
    }
}
