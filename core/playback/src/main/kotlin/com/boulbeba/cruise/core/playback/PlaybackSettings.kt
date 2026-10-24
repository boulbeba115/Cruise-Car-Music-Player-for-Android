package com.boulbeba.cruise.core.playback

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Car-specific playback behaviour (SPEC CAR-3, CAR-14, EX-1). On a head unit the screen going off means the ignition
 * went off; on a phone it only means the phone was locked, so the car defaults (pause at screen-off, resume at
 * screen-on and after boot) apply only on a head unit (CHANGE_REQUESTS.md CR-21). A choice made in Settings wins.
 */
@Singleton
class PlaybackSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("playback_settings", Context.MODE_PRIVATE)

    /**
     * A car head unit rather than a phone or tablet: a TW unit (the owner's), Android's car mode, or no battery — every
     * phone has one, head units run off the car's supply.
     */
    val isHeadUnit: Boolean = runCatching { Class.forName("android.tw.john.TWUtil"); true }.getOrDefault(false) ||
        context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_CAR ||
        runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) == false
        }.getOrDefault(false)

    /** Pause this long after the screen goes off (ignition off) — 0 disables. Avoids keeping a sleeping unit awake. */
    var sleepPauseDelayMs: Long
        get() = prefs.getLong("sleep_pause_delay", if (isHeadUnit) 10_000 else 0)
        set(value) = prefs.edit().putLong("sleep_pause_delay", value).apply()

    /** Resume when the unit wakes up if Cruise paused for sleep. */
    var resumeOnWake: Boolean
        get() = prefs.getBoolean("resume_on_wake", true)
        set(value) = prefs.edit().putBoolean("resume_on_wake", value).apply()

    /** Resume after a full shutdown + boot if music was playing. Off by default on a phone: it would start music in a pocket. */
    var resumeOnBoot: Boolean
        get() = prefs.getBoolean("resume_on_boot", isHeadUnit)
        set(value) = prefs.edit().putBoolean("resume_on_boot", value).apply()

    /** Whole-screen gestures on Now Playing: hold = play/pause, swipes = tracks and volume. */
    var screenGestures: Boolean
        get() = prefs.getBoolean("screen_gestures", true)
        set(value) = prefs.edit().putBoolean("screen_gestures", value).apply()

    internal var lastBootResumed: Long
        get() = prefs.getLong("last_boot_resumed", 0)
        set(value) = prefs.edit().putLong("last_boot_resumed", value).apply()
}
