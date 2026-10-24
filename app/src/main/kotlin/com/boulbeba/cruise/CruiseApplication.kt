package com.boulbeba.cruise

import android.app.Application
import android.content.Intent
import com.boulbeba.cruise.core.designsystem.theme.Appearance
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CruiseApplication : Application() {
    @Inject lateinit var carEvents: CarEvents

    override fun onCreate() {
        super.onCreate()
        Appearance.init(this)
        carEvents.start()
        wakeWheel()
    }

    /**
     * Cruise Wheel (the steering-wheel app, CHANGE_REQUESTS.md CR-10) is force-stopped with every other app when the
     * unit switches off, and a stopped app gets no broadcasts until something starts it. Cruise is started by the
     * MUSIC key, the resume and the driver, so it starts Cruise Wheel too. Nothing happens when it is not installed.
     */
    private fun wakeWheel() {
        runCatching {
            sendBroadcast(
                Intent(WHEEL_START)
                    .setClassName(WHEEL_PACKAGE, "$WHEEL_PACKAGE.service.BootReceiver")
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES),
            )
        }
    }

    private companion object {
        const val WHEEL_PACKAGE = "com.boulbeba.cruise.wheel"
        const val WHEEL_START = "com.boulbeba.cruise.wheel.action.START"
    }
}
