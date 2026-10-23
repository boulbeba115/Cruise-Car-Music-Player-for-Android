package com.boulbeba.cruise.core.car.tw

import android.content.Context
import android.content.Intent
import com.boulbeba.cruise.core.car.CarLog
import java.io.File

/**
 * The unit's "default app" files, read by its key service (com.tw.service): the MUSIC key opens the app named in
 * `/data/tw/music_name` (line 1 package, line 2 activity), falling back to the stock player. The unit runs SELinux
 * in permissive mode, so whether Cruise may write the file depends only on its permissions — tried, never assumed.
 */
object TwDefaultApps {
    private val musicFile = File("/data/tw/music_name")
    private const val PREFS = "tw_default_apps"
    private const val KEY_PREVIOUS = "previous_music"

    /** Package of the app the MUSIC key opens, or null when the unit uses its stock player. */
    fun musicApp(): String? = runCatching { musicFile.readLines().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()

    fun isCruise(context: Context) = musicApp() == context.packageName

    /** Points the MUSIC key at Cruise; remembers the previous app for [restore]. Returns whether it worked. */
    fun makeCruiseMusicApp(context: Context): Boolean {
        val activity = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.className ?: return false
        val previous = runCatching { musicFile.readText() }.getOrNull()
        val ok = runCatching {
            musicFile.writeText("${context.packageName}\n$activity\n")
            musicFile.setReadable(true, false)
            musicApp() == context.packageName
        }.getOrDefault(false)
        if (ok && previous != null && !previous.startsWith(context.packageName)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PREVIOUS, previous).apply()
        }
        CarLog.log("tw", "music key -> Cruise: ${if (ok) "done" else "not allowed (${musicFile.parentFile?.canWrite()}/${musicFile.canWrite()})"}")
        return ok
    }

    /** Gives the MUSIC key back to the app it opened before. */
    fun restore(context: Context): Boolean {
        val previous = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREVIOUS, null)
        return runCatching {
            if (previous != null) musicFile.writeText(previous) else musicFile.delete()
            true
        }.getOrDefault(false)
    }

    /** The unit's own steering-wheel learning screen, if installed. */
    fun steeringLearningIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage("com.tw.keypad")?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
