package com.boulbeba.cruise.core.data.library

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("library_settings", Context.MODE_PRIVATE)

    /** Shorter files (notification sounds, jingles) are not music (SPEC LIB-7). 10 s keeps album interludes. */
    var minDurationMs: Long
        get() = prefs.getLong("min_duration", 10_000)
        set(value) = prefs.edit().putLong("min_duration", value).apply()

    /** Rescan automatically when the app starts and when a USB stick is plugged in. */
    var autoScan: Boolean
        get() = prefs.getBoolean("auto_scan", true)
        set(value) = prefs.edit().putBoolean("auto_scan", value).apply()
}
