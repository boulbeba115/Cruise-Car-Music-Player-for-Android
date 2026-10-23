package com.boulbeba.cruise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.boulbeba.cruise.core.car.CarLog
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
                CarLog.log("storage", "${intent.action} ${intent.data}")
                volumes.refresh()
                if (intent.action == Intent.ACTION_MEDIA_MOUNTED && settings.autoScan) scanner.scan()
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
                CarLog.log("lights", if (on) "headlights on" else "headlights off")
                Appearance.setHeadlights(on)
            }
        }
        ContextCompat.registerReceiver(
            context, lights, IntentFilter().apply { addAction(HEADLIGHTS_ON); addAction(HEADLIGHTS_OFF) }, ContextCompat.RECEIVER_EXPORTED,
        )

        // Let the first frame and the playback resume go first; then bring the library up to date.
        if (settings.autoScan) scope.launch {
            delay(2_500)
            scanner.scan()
        }
    }

    companion object {
        const val HEADLIGHTS_ON = "com.zjinnova.zlink.action.OUT_DARK_START"
        const val HEADLIGHTS_OFF = "com.zjinnova.zlink.action.OUT_DARK_STOP"
    }
}
