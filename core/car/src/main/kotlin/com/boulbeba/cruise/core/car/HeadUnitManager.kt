package com.boulbeba.cruise.core.car

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import com.boulbeba.cruise.core.car.calls.CallControl
import com.boulbeba.cruise.core.car.calls.CallState
import com.boulbeba.cruise.core.car.steering.KeyAction
import com.boulbeba.cruise.core.car.steering.KeyMappingStore
import com.boulbeba.cruise.core.car.steering.MediaCommands
import com.boulbeba.cruise.core.car.steering.PressType
import com.boulbeba.cruise.core.car.steering.RawKey
import com.boulbeba.cruise.core.car.steering.VendorScreenSuppressor
import com.boulbeba.cruise.core.car.tw.TwProtocol
import com.boulbeba.cruise.core.car.tw.TwUtilBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Head-unit integration (SPEC §4.11). On "TW" units it listens to the MCU through [TwUtilBridge]:
 * raw steering keys go through the smart call button and the user's mapping; standard media keys
 * (which the unit sends once Cruise holds audio focus) are left to Media3.
 */
@Singleton
class HeadUnitManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapping: KeyMappingStore,
    private val calls: CallControl,
) {
    private var phoneClaimed = false
    private var screenReceiver: BroadcastReceiver? = null

    private var bridge: TwUtilBridge? = null
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) = onMessage(msg)
    }

    @Volatile var commands: MediaCommands? = null

    private val _lastKeys = MutableStateFlow<List<RawKey>>(emptyList())
    /** Most recent raw keys, newest last — for the mapping screen. */
    val lastKeys: StateFlow<List<RawKey>> = _lastKeys.asStateFlow()

    private val _source = MutableStateFlow<Int?>(null)
    /** Current head-unit source (0 = outside app such as Cruise, 3 = stock music, …). */
    val source: StateFlow<Int?> = _source.asStateFlow()

    val isTwUnit: Boolean get() = TwUtilBridge.isAvailable
    val isConnected: Boolean get() = bridge != null

    private var lastKey: RawKey? = null

    @Synchronized
    fun start() {
        calls.start()
        if (bridge != null || !TwUtilBridge.isAvailable) return
        val b = TwUtilBridge.create() ?: return
        val ids = TwProtocol.ids(TwProtocol.KEY, TwProtocol.KEY_LONG, TwProtocol.SOURCE, TwProtocol.SCREEN_STATE)
        if (!b.open(ids)) {
            CarLog.log("tw", "open failed")
            return
        }
        b.start()
        b.addHandler(HANDLER_NAME, handler)
        bridge = b
        CarLog.log("tw", "connected to the MCU")
        claimPhoneButton()
        // The vendor service forgets the hand-over when it restarts; renew it whenever the screen comes back.
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = claimPhoneButton()
        }.also { context.registerReceiver(it, IntentFilter(Intent.ACTION_SCREEN_ON)) }
    }

    /**
     * The unit's key service (com.tw.service) opens its Bluetooth screen for the phone button unless a HiCar phone is
     * "connected" — a flag set by the unprotected broadcast `action.hicar.onconnect` and used for nothing else there
     * (the Bluetooth app only stops auto-enabling Bluetooth while it is set). Calls are still answered by the unit.
     */
    fun claimPhoneButton() {
        if (!TwUtilBridge.isAvailable) return
        val own = mapping.ownPhoneButton
        if (own) {
            context.sendBroadcast(Intent(HICAR_CONNECTED))
            phoneClaimed = true
            CarLog.log("tw", "phone button claimed")
        } else if (phoneClaimed) {
            context.sendBroadcast(Intent(HICAR_DISCONNECTED))
            phoneClaimed = false
            CarLog.log("tw", "phone button released")
        }
    }

    @Synchronized
    fun stop() {
        screenReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        screenReceiver = null
        bridge?.run {
            removeHandler(HANDLER_NAME)
            stop()
            close()
        }
        bridge = null
    }

    private fun onMessage(msg: Message) {
        when (msg.what) {
            TwProtocol.KEY, TwProtocol.KEY_LONG -> {
                val press = when {
                    msg.what == TwProtocol.KEY_LONG -> PressType.Long
                    msg.arg1 == 1 -> PressType.Short
                    msg.arg1 == 2 -> PressType.Long
                    else -> null
                }
                CarLog.log("key", "raw what=${msg.what} code=${msg.arg2} arg1=${msg.arg1} -> $press")
                if (press != null) onKey(msg.arg2, press)
            }
            TwProtocol.SOURCE -> {
                _source.value = msg.arg1
                CarLog.log("tw", "source=${msg.arg1}")
            }
            else -> CarLog.log("tw", "msg what=${msg.what} arg1=${msg.arg1} arg2=${msg.arg2}")
        }
    }

    /** Public so the mapping screen and tests can drive it. */
    fun onKey(code: Int, press: PressType) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastKey
        if (previous != null && previous.code == code && previous.press == press && now - previous.timeMillis < DEBOUNCE_MS) return
        val key = RawKey(code, press, now)
        lastKey = key
        _lastKeys.update { (it + key).takeLast(20) }
        mapping.remember(code)

        if (mapping.smartCallButton && (code == KeyMappingStore.PHONE_KEY || code == KeyMappingStore.HANG_UP_KEY)) {
            when (calls.state.value) {
                CallState.Ringing -> {
                    if (code == KeyMappingStore.PHONE_KEY && press == PressType.Short) calls.answer() else calls.reject()
                    return
                }
                CallState.Active -> {
                    calls.hangUp()
                    return
                }
                CallState.Idle -> Unit
            }
        }

        // The unit already sends these as standard media keys; handling them here too would double them.
        if (code in TwProtocol.standardMediaCodes) return

        val action = mapping.actionFor(code, press)
        if (action == KeyAction.UnitDefault) return
        vendorScreenFor(code)?.let(VendorScreenSuppressor::expect)
        val target = commands
        CarLog.log("key", "code=$code $press -> $action${if (target == null) " (player not running)" else ""}")
        when (action) {
            KeyAction.PlayPause -> target?.playPause()
            KeyAction.Next -> target?.next()
            KeyAction.Previous -> target?.previous()
            KeyAction.NextFolder -> target?.nextFolder()
            KeyAction.PreviousFolder -> target?.previousFolder()
            KeyAction.ToggleShuffle -> target?.toggleShuffle()
            KeyAction.CycleRepeat -> target?.cycleRepeat()
            KeyAction.OpenCruise -> target?.openApp()
            KeyAction.Nothing, KeyAction.UnitDefault -> Unit
        }
    }

    private fun vendorScreenFor(code: Int): String? = when (code) {
        KeyMappingStore.PHONE_KEY -> "com.tw.bt"
        KeyMappingStore.MIXER_KEY -> "com.tw.eq"
        else -> null
    }

    private companion object {
        const val HICAR_CONNECTED = "action.hicar.onconnect"
        const val HICAR_DISCONNECTED = "action.hicar.ondisconnect"
        const val HANDLER_NAME = "CruiseHeadUnit"
        const val DEBOUNCE_MS = 250L
    }
}
