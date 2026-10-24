package com.boulbeba.cruise.wheel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.boulbeba.cruise.core.car.CarLog
import com.boulbeba.cruise.core.car.calls.CallControl
import com.boulbeba.cruise.core.car.calls.CallState
import com.boulbeba.cruise.core.car.steering.VendorScreenSuppressor
import com.boulbeba.cruise.core.car.tw.TwProtocol
import com.boulbeba.cruise.core.car.tw.TwUtilBridge
import com.boulbeba.cruise.wheel.MainActivity
import com.boulbeba.cruise.wheel.R
import com.boulbeba.cruise.wheel.engine.ActionRunner
import com.boulbeba.cruise.wheel.engine.KeyEngine
import com.boulbeba.cruise.wheel.engine.KeyInput
import com.boulbeba.cruise.wheel.engine.Press
import com.boulbeba.cruise.wheel.engine.RuleStore
import com.boulbeba.cruise.wheel.engine.Trigger
import com.boulbeba.cruise.wheel.engine.WheelKeys
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the wheel service sees, for the screens: the latest buttons and whether the MCU link is up. */
object WheelState {
    private val _keys = MutableStateFlow<List<KeyInput>>(emptyList())
    /** Most recent buttons, newest last. */
    val keys: StateFlow<List<KeyInput>> = _keys.asStateFlow()
    val connected = MutableStateFlow(false)
    val lastTrigger = MutableStateFlow<Trigger?>(null)

    internal fun record(input: KeyInput) = _keys.update { (it + input).takeLast(12) }
}

/**
 * Cruise Wheel's foreground service (CHANGE_REQUESTS.md CR-10.1): listens to the wheel buttons through the unit's MCU
 * link, recognises taps, holds, double taps and combinations, and runs the user's rules. Buttons a rule handles stop
 * doing the unit's own thing: the phone button through the unit's HiCar hand-over flag, the others by closing the
 * vendor screen they open (accessibility helper). Re-sends what it learns on the [CarBus].
 */
@AndroidEntryPoint
class WheelService : Service() {

    @Inject lateinit var rules: RuleStore
    @Inject lateinit var runner: ActionRunner
    @Inject lateinit var calls: CallControl

    private var bridge: TwUtilBridge? = null
    private val engine by lazy { KeyEngine { rules.rules.value.mapTo(HashSet()) { it.trigger } } }
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) = onMcu(msg)
    }
    private val tick = Runnable { handle(engine.tick(SystemClock.elapsedRealtime())) }
    private val receivers = ArrayList<BroadcastReceiver>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification())
        calls.start()
        connect()
        listen(Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, ACC_ON, ACC_OFF, LIGHTS_ON, LIGHTS_OFF) { intent ->
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> { claimPhoneButton(); CarBus.send(this, CarBus.TYPE_IGNITION, "on") }
                Intent.ACTION_SCREEN_OFF -> CarBus.send(this, CarBus.TYPE_IGNITION, "off")
                ACC_ON -> CarBus.send(this, CarBus.TYPE_IGNITION, "on")
                ACC_OFF -> CarBus.send(this, CarBus.TYPE_IGNITION, "off")
                LIGHTS_ON -> CarBus.send(this, CarBus.TYPE_LIGHTS, "on")
                LIGHTS_OFF -> CarBus.send(this, CarBus.TYPE_LIGHTS, "off")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        claimPhoneButton()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        receivers.forEach { runCatching { unregisterReceiver(it) } }
        bridge?.run {
            removeHandler(HANDLER_NAME)
            stop()
            close()
        }
        bridge = null
        WheelState.connected.value = false
        super.onDestroy()
    }

    private fun connect() {
        if (!TwUtilBridge.isAvailable) {
            CarLog.log("wheel", "not a TW head unit: no MCU link")
            return
        }
        val b = TwUtilBridge.create() ?: return
        if (!b.open(TwProtocol.ids(TwProtocol.KEY, TwProtocol.KEY_LONG, TwProtocol.SOURCE))) {
            CarLog.log("wheel", "MCU link refused")
            return
        }
        b.start()
        b.addHandler(HANDLER_NAME, handler)
        bridge = b
        WheelState.connected.value = true
        CarLog.log("wheel", "listening to the wheel")
        claimPhoneButton()
    }

    private fun onMcu(msg: Message) {
        when (msg.what) {
            TwProtocol.KEY, TwProtocol.KEY_LONG -> {
                val press = when {
                    msg.what == TwProtocol.KEY_LONG || msg.arg1 == 2 -> Press.Long
                    msg.arg1 == 1 -> Press.Short
                    else -> return
                }
                onKey(KeyInput(msg.arg2, press, SystemClock.elapsedRealtime()))
            }
            TwProtocol.SOURCE -> CarBus.send(this, CarBus.TYPE_SOURCE, msg.arg1.toString())
        }
    }

    /** Public entry for tests and the screens' "try it" button. */
    fun onKey(input: KeyInput) {
        WheelState.record(input)
        CarBus.send(this, CarBus.TYPE_KEY, if (input.press == Press.Long) "long" else "short", input.code)
        // A ringing or active call comes first on the phone button, whatever the rules say.
        if (rules.smartCalls && (input.code == WheelKeys.PHONE || input.code == WheelKeys.HANG_UP)) {
            when (calls.state.value) {
                CallState.Ringing -> { if (input.code == WheelKeys.PHONE) calls.answer() else calls.reject(); return }
                CallState.Active -> { calls.hangUp(); return }
                CallState.Idle -> Unit
            }
        }
        handle(engine.onInput(input))
    }

    private fun handle(triggers: List<Trigger>) {
        for (t in triggers) {
            WheelState.lastTrigger.value = t
            CarBus.send(this, CarBus.TYPE_TRIGGER, describe(t), codes(t).first())
            val rule = rules.ruleFor(t) ?: continue
            codes(t).firstNotNullOfOrNull(WheelKeys::vendorScreen)?.let(VendorScreenSuppressor::expect)
            runner.run(rule)
        }
        handler.removeCallbacks(tick)
        engine.nextDeadline()?.let { handler.postDelayed(tick, (it - SystemClock.elapsedRealtime()).coerceAtLeast(0)) }
    }

    /**
     * The unit's key service opens its Bluetooth screen for the phone button unless a HiCar phone is "connected"; the
     * flag is set by an unprotected broadcast and used for nothing else (docs/unit-findings.md). Claimed while a rule
     * uses the phone button; renewed on every screen-on because the service forgets it when it restarts.
     */
    private fun claimPhoneButton() {
        if (!TwUtilBridge.isAvailable) return
        val usesPhone = rules.rules.value.any { WheelKeys.PHONE in codes(it.trigger) }
        sendBroadcast(Intent(if (usesPhone) HICAR_CONNECTED else HICAR_DISCONNECTED))
    }

    private fun listen(vararg actions: String, onReceive: (Intent) -> Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) = onReceive(intent)
        }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_EXPORTED)
        receivers += r
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.wheel_channel), NotificationManager.IMPORTANCE_MIN))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder
            .setSmallIcon(R.drawable.ic_wheel_notification)
            .setContentTitle(getString(R.string.wheel_running))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "wheel"
        private const val NOTIFICATION_ID = 7
        private const val HANDLER_NAME = "CruiseWheel"
        private const val HICAR_CONNECTED = "action.hicar.onconnect"
        private const val HICAR_DISCONNECTED = "action.hicar.ondisconnect"
        // Found in the unit's vendor apps (com.tw.*): ignition, and the day/night signal it sends for CarPlay.
        const val ACC_ON = "com.android.action_acc_on"
        const val ACC_OFF = "com.android.action_acc_off"
        const val LIGHTS_ON = "com.zjinnova.zlink.action.OUT_DARK_START"
        const val LIGHTS_OFF = "com.zjinnova.zlink.action.OUT_DARK_STOP"

        fun codes(t: Trigger): List<Int> = when (t) {
            is Trigger.Tap -> listOf(t.code)
            is Trigger.Hold -> listOf(t.code)
            is Trigger.DoubleTap -> listOf(t.code)
            is Trigger.Combo -> listOf(t.first, t.second)
        }

        fun describe(t: Trigger): String = when (t) {
            is Trigger.Tap -> "tap"
            is Trigger.Hold -> "hold"
            is Trigger.DoubleTap -> "double"
            is Trigger.Combo -> "combo:${t.second}"
        }

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, WheelService::class.java))
    }
}

/** Starts the service with the unit. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in START_ACTIONS) return
        runCatching { WheelService.start(context) }
    }

    private companion object {
        val START_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON", Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
