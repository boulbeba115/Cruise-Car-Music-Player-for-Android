package com.boulbeba.cruise.wheel.service

import android.annotation.SuppressLint
import com.boulbeba.cruise.core.car.CarLog
import com.boulbeba.cruise.core.car.tw.TwUtilBridge

/**
 * When the ignition goes off, the unit's key service (`com.tw.service`, `TWService.A()`) force-stops every app that
 * is not on its own list — Cruise and Cruise Wheel included — unless the system property `persist.tw.kill` is set.
 * A force-stopped app receives nothing (no boot, no broadcast) until it is opened by hand, so the wheel would stay
 * dead after every drive. The unit runs SELinux permissive, so an app may set the property itself (CHANGE_REQUESTS.md
 * CR-19); it is persistent and can be undone with [allowKill].
 */
object KillGuard {
    private const val PROPERTY = "persist.tw.kill"

    /** Only TW units have the killer. */
    val applies: Boolean get() = TwUtilBridge.isAvailable

    /** True when the unit leaves apps running at ignition-off. */
    fun isOn(): Boolean = !applies || (get(PROPERTY)?.toIntOrNull() ?: 0) != 0

    /** Asks the unit to stop killing apps at ignition-off; returns whether it took. */
    fun turnOn(): Boolean = set("1").also { CarLog.log("wheel", "keep apps at ignition-off: ${if (it) "on" else "refused"}") }

    /** Gives the unit its default behaviour back. */
    fun allowKill(): Boolean = set("0")

    private fun set(value: String): Boolean {
        if (!applies) return false
        runCatching {
            Runtime.getRuntime().exec(arrayOf("setprop", PROPERTY, value)).waitFor()
        }.onFailure { CarLog.log("wheel", "setprop failed: $it") }
        return get(PROPERTY) == value
    }

    // android.os.SystemProperties is hidden but stable on the unit's Android 8.1; failure reads as "not set".
    @SuppressLint("PrivateApi")
    private fun get(name: String): String? = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java).invoke(null, name) as String
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}
