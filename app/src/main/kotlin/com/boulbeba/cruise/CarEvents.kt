package com.boulbeba.cruise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.boulbeba.cruise.core.model.CruiseLog
import com.boulbeba.cruise.core.data.library.LibraryScanner
import com.boulbeba.cruise.core.data.library.LibrarySettings
import com.boulbeba.cruise.core.data.library.Volumes
import com.boulbeba.cruise.core.designsystem.theme.Appearance
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * App-wide car signals: a USB stick plugged in or removed (refresh volumes, rescan the library — SPEC LIB-2) and the
 * headlights switching on or off (night skin — SPEC UI-6; the unit broadcasts them through its zlink service).
 */
@Singleton
class CarEvents @Inject constructor(
    @ApplicationContext private val context: Context,
    private val volumes: Volumes,
    private val scanner: LibraryScanner,
    private val settings: LibrarySettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start() {
        val storage = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                CruiseLog.log("storage", "${intent.action} ${intent.data}")
                volumes.refresh()
                if (intent.action == Intent.ACTION_MEDIA_MOUNTED && settings.autoScan) scanner.scan(background = true)
            }
        }
        val storageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, storage, storageFilter, ContextCompat.RECEIVER_EXPORTED)

        val lights = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val on = intent.action == HEADLIGHTS_ON
                CruiseLog.log("lights", if (on) "headlights on" else "headlights off")
                Appearance.setHeadlights(on)
            }
        }
        ContextCompat.registerReceiver(
            context, lights, IntentFilter().apply { addAction(HEADLIGHTS_ON); addAction(HEADLIGHTS_OFF) }, ContextCompat.RECEIVER_EXPORTED,
        )

        // Let the first frame and the playback resume go first; then bring the library up to date without a word on
        // screen: the cached library is shown meanwhile, and only new or changed files are read.
        if (settings.autoScan) scope.launch {
            delay(STARTUP_SCAN_DELAY_MS)
            scanner.scan(background = true)
        }
    }

    /** Storage access was just granted: the volumes are readable now, so scan straight away (CHANGE_REQUESTS.md CR-03). */
    fun onStorageGranted() {
        volumes.refresh()
        scanner.scan()
    }

    companion object {
        private const val STARTUP_SCAN_DELAY_MS = 8_000L
        const val HEADLIGHTS_ON = "com.zjinnova.zlink.action.OUT_DARK_START"
        const val HEADLIGHTS_OFF = "com.zjinnova.zlink.action.OUT_DARK_STOP"
    }
}
