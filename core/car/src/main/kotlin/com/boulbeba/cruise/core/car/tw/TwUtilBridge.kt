package com.boulbeba.cruise.core.car.tw

import android.os.Handler
import com.boulbeba.cruise.core.car.CarLog
import java.lang.reflect.Method

/**
 * Reflection bridge to `android.tw.john.TWUtil`, the vendor class "TW" head units use to talk to their MCU
 * (see docs/unit-findings.md). Not part of the SDK, so every call is looked up at runtime and every failure is
 * logged instead of thrown. Usage mirrors the vendor apps: `open(ids)` → `start()` → `addHandler(name, handler)`;
 * messages arrive with `what` = message id, `arg1`/`arg2` = payload.
 */
class TwUtilBridge private constructor(private val instance: Any, private val type: Class<*>) {

    private fun method(name: String, vararg params: Class<*>): Method? =
        runCatching { type.getMethod(name, *params) }.getOrNull()

    private fun call(name: String, params: Array<Class<*>>, vararg args: Any?): Any? {
        val m = method(name, *params) ?: run {
            CarLog.log("tw", "missing method $name(${params.joinToString { it.simpleName }})")
            return null
        }
        return runCatching { m.invoke(instance, *args) }
            .onFailure { CarLog.log("tw", "$name failed: ${it.cause ?: it}") }
            .getOrNull()
    }

    /**
     * Subscribes to message ids; returns true on success (the vendor API returns 0). Call it once per instance, with
     * at most [MAX_IDS] ids: the vendor apps never pass more than 23, and the native side crashed the process (no
     * exception to catch) when Cruise Wheel's probe passed 4,096. Use more instances for more ids.
     */
    fun open(ids: ShortArray): Boolean {
        if (ids.isEmpty() || ids.size > MAX_IDS) {
            CarLog.log("tw", "open refused: ${ids.size} ids (max $MAX_IDS)")
            return false
        }
        return call("open", arrayOf(ShortArray::class.java), ids) == 0
    }

    fun start() { call("start", emptyArray()) }

    fun stop() { call("stop", emptyArray()) }

    fun close() { call("close", emptyArray()) }

    fun addHandler(name: String, handler: Handler) {
        call("addHandler", arrayOf(String::class.java, Handler::class.java), name, handler)
    }

    fun removeHandler(name: String) {
        call("removeHandler", arrayOf(String::class.java), name)
    }

    fun write(what: Int, arg1: Int) {
        call("write", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!), what, arg1)
    }

    fun write(what: Int, arg1: Int, arg2: Int) {
        call("write", arrayOf(Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!), what, arg1, arg2)
    }

    companion object {
        private const val CLASS_NAME = "android.tw.john.TWUtil"

        /** Largest id list per [open]; the vendor apps' largest is 23. */
        const val MAX_IDS = 24

        val isAvailable: Boolean by lazy { runCatching { Class.forName(CLASS_NAME) }.isSuccess }

        /** Creates a TWUtil instance, or null on units without the vendor framework. */
        fun create(): TwUtilBridge? {
            if (!isAvailable) return null
            return runCatching {
                val type = Class.forName(CLASS_NAME)
                val bridge = TwUtilBridge(type.getConstructor().newInstance(), type)
                CarLog.log("tw", "TWUtil methods: " + type.methods.filter { it.declaringClass == type }
                    .joinToString { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" })
                bridge
            }.onFailure { CarLog.log("tw", "cannot create TWUtil: $it") }.getOrNull()
        }
    }
}

/** Message ids and commands of the TW protocol (from the stock apps; docs/unit-findings.md). */
object TwProtocol {
    const val KEY = 513 // 0x0201: steering / panel key, arg2 = key code, arg1 == 1 short press
    const val KEY_LONG = 33281 // 0x8201: long-press variant
    const val SOURCE = 769 // 0x0301: current source in arg1
    const val MUSIC_COMMAND = 40451 // 0x9E03: commands to the source-3 music app
    const val SCREEN_STATE = 40732 // 0x9F1C: MCU state (dark screen / standby), logged to learn it

    /** Message ids as the signed shorts TWUtil.open expects. */
    fun ids(vararg ids: Int): ShortArray = ShortArray(ids.size) { ids[it].toShort() }

    /** Unit key codes (com.tw.keypad key_config.json + com.tw.service). */
    val keyNames: Map<Int, String> = mapOf(
        1 to "Volume +", 2 to "Volume −", 3 to "Mute", 4 to "Power",
        5 to "Next", 6 to "Previous", 9 to "Left / seek −", 10 to "Right / seek +",
        16 to "Home", 17 to "Menu", 18 to "Back", 19 to "Next track", 20 to "Play / Pause", 21 to "Previous track",
        22 to "Fast forward", 23 to "Rewind", 24 to "Phone / Answer", 25 to "Hang up", 26 to "Screen",
        32 to "Navigation", 63 to "Enter", 64 to "Car help", 66 to "Sound mixer (EQ)",
    )

    /** Codes the unit itself turns into standard media keys when an outside app holds audio focus. */
    val standardMediaCodes: Set<Int> = setOf(5, 6, 19, 20, 21)
}
