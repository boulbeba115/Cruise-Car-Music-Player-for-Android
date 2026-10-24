package com.boulbeba.cruise.core.car.steering

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.boulbeba.cruise.core.car.CarLog

/**
 * Remembers which vendor screen a remapped steering button is about to open (the head unit's service opens it
 * regardless), so [SteeringHelperService] can close it again.
 */
object VendorScreenSuppressor {
    @Volatile private var expectedPackage: String? = null
    @Volatile private var expectedUntil = 0L

    fun expect(packageName: String, windowMillis: Long = 2_500) {
        expectedPackage = packageName
        expectedUntil = SystemClock.elapsedRealtime() + windowMillis
    }

    fun consume(packageName: String): Boolean {
        val match = packageName == expectedPackage && SystemClock.elapsedRealtime() < expectedUntil
        if (match) expectedPackage = null
        return match
    }

    fun isHelperEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val me = ComponentName(context, SteeringHelperService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

/**
 * Optional accessibility service (SPEC SW-4). It only reacts to window changes of the vendor packages listed in
 * its configuration, and only right after Cruise handled a remapped button: it presses Back once to close the
 * vendor screen the head unit opened. It reads no window content.
 */
class SteeringHelperService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (VendorScreenSuppressor.consume(pkg)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            CarLog.log("steering", "closed $pkg opened by a remapped button")
        }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        instance = this
        CarLog.log("steering", "helper connected")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        /** The running helper, for actions only an accessibility service can do (the steering-wheel app's Back). */
        @Volatile var instance: SteeringHelperService? = null
            private set

        fun back(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_BACK) == true

        fun home(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
    }
}
