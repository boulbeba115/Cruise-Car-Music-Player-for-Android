package com.boulbeba.cruise.core.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Car-specific playback behaviour (SPEC CAR-3, CAR-14, EX-1). */
@Singleton
class PlaybackSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("playback_settings", Context.MODE_PRIVATE)

    /** Pause this long after the screen goes off (ignition off) — 0 disables. Avoids keeping a sleeping unit awake. */
    var sleepPauseDelayMs: Long
        get() = prefs.getLong("sleep_pause_delay", 10_000)
        set(value) = prefs.edit().putLong("sleep_pause_delay", value).apply()

    /** Resume when the unit wakes up if Cruise paused for sleep. */
    var resumeOnWake: Boolean
        get() = prefs.getBoolean("resume_on_wake", true)
        set(value) = prefs.edit().putBoolean("resume_on_wake", value).apply()

    /** Resume after a full shutdown + boot if music was playing. */
    var resumeOnBoot: Boolean
        get() = prefs.getBoolean("resume_on_boot", true)
        set(value) = prefs.edit().putBoolean("resume_on_boot", value).apply()

    /** Whole-screen gestures on Now Playing: hold = play/pause, swipes = tracks and volume. */
    var screenGestures: Boolean
        get() = prefs.getBoolean("screen_gestures", true)
        set(value) = prefs.edit().putBoolean("screen_gestures", value).apply()

    internal var lastBootResumed: Long
        get() = prefs.getLong("last_boot_resumed", 0)
        set(value) = prefs.edit().putLong("last_boot_resumed", value).apply()
}
