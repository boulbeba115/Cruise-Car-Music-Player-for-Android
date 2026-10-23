package com.boulbeba.cruise.core.car.calls

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.boulbeba.cruise.core.car.CarLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallState { Idle, Ringing, Active }

/** Phone-call control used by the steering-wheel router; [CallController] is the real implementation. */
interface CallControl {
    val state: StateFlow<CallState>
    fun start()
    fun answer(): Boolean
    fun reject(): Boolean
    fun hangUp(): Boolean
}

/**
 * Answers / rejects / ends Bluetooth phone calls through Android's hands-free client profile — the same calls
 * the unit's own Bluetooth app makes (`acceptCall(device, 0)`, `rejectCall(device)`, `terminateCall(device, null)`).
 * `BluetoothHeadsetClient` is a hidden class, reachable by reflection on Android 8.1 (no hidden-API checks before
 * API 28); units where it is missing simply report [CallState.Idle].
 */
@Singleton
class CallController @Inject constructor(@ApplicationContext private val context: Context) : CallControl {

    private val _state = MutableStateFlow(CallState.Idle)
    override val state: StateFlow<CallState> = _state.asStateFlow()

    private var proxy: BluetoothProfile? = null
    private var started = false

    @SuppressLint("MissingPermission") // BLUETOOTH is a normal permission on the target Android versions.
    override fun start() {
        if (started) return
        started = true
        @Suppress("DEPRECATION") // BluetoothManager.adapter is equivalent; this form also works on API 23–30 units.
        val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull() ?: return
        runCatching {
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, p: BluetoothProfile) {
                    proxy = p
                    CarLog.log("call", "hands-free client connected (${p.javaClass.name})")
                }

                override fun onServiceDisconnected(profile: Int) {
                    proxy = null
                }
            }, HEADSET_CLIENT)
        }.onFailure { CarLog.log("call", "no hands-free client: $it") }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_CALL_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val call: Any? = intent.getParcelableExtra(EXTRA_CALL)
            val callState = call?.let { runCatching { it.javaClass.getMethod("getState").invoke(it) as Int }.getOrNull() }
            _state.value = when (callState) {
                CALL_STATE_INCOMING, CALL_STATE_WAITING -> CallState.Ringing
                CALL_STATE_ACTIVE, CALL_STATE_HELD, CALL_STATE_DIALING, CALL_STATE_ALERTING -> CallState.Active
                else -> refreshFromCurrentCalls()
            }
            CarLog.log("call", "state=$callState -> ${_state.value}")
        }
    }

    /** After a call ends another one may still be active (call waiting). */
    private fun refreshFromCurrentCalls(): CallState {
        val device = device() ?: return CallState.Idle
        val calls = runCatching {
            proxy!!.javaClass.getMethod("getCurrentCalls", BluetoothDevice::class.java).invoke(proxy, device) as? List<*>
        }.getOrNull().orEmpty()
        val states = calls.mapNotNull { c -> runCatching { c!!.javaClass.getMethod("getState").invoke(c) as Int }.getOrNull() }
        return when {
            states.any { it == CALL_STATE_INCOMING || it == CALL_STATE_WAITING } -> CallState.Ringing
            states.any { it in setOf(CALL_STATE_ACTIVE, CALL_STATE_HELD, CALL_STATE_DIALING, CALL_STATE_ALERTING) } -> CallState.Active
            else -> CallState.Idle
        }
    }

    private fun device(): BluetoothDevice? = runCatching { proxy?.connectedDevices?.firstOrNull() }.getOrNull()

    override fun answer(): Boolean = invoke("acceptCall", arrayOf(BluetoothDevice::class.java, Int::class.javaPrimitiveType!!)) { d -> arrayOf(d, 0) }

    override fun reject(): Boolean = invoke("rejectCall", arrayOf(BluetoothDevice::class.java)) { d -> arrayOf(d) }

    override fun hangUp(): Boolean {
        val callClass = runCatching { Class.forName("android.bluetooth.BluetoothHeadsetClientCall") }.getOrNull()
        return if (callClass != null) {
            invoke("terminateCall", arrayOf(BluetoothDevice::class.java, callClass)) { d -> arrayOf(d, null) }
        } else {
            invoke("terminateCall", arrayOf(BluetoothDevice::class.java, Int::class.javaPrimitiveType!!)) { d -> arrayOf(d, 0) }
        }
    }

    private fun invoke(name: String, params: Array<Class<*>>, args: (BluetoothDevice) -> Array<Any?>): Boolean {
        val p = proxy ?: return false.also { CarLog.log("call", "$name: no hands-free client") }
        val device = device() ?: return false.also { CarLog.log("call", "$name: no phone connected") }
        return runCatching { p.javaClass.getMethod(name, *params).invoke(p, *args(device)) as? Boolean ?: false }
            .onFailure { CarLog.log("call", "$name failed: ${it.cause ?: it}") }
            .getOrDefault(false)
            .also { CarLog.log("call", "$name -> $it") }
    }

    companion object {
        private const val HEADSET_CLIENT = 16 // BluetoothProfile.HEADSET_CLIENT (hidden)
        private const val ACTION_CALL_CHANGED = "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED"
        private const val EXTRA_CALL = "android.bluetooth.headsetclient.extra.CALL"
        private const val CALL_STATE_ACTIVE = 0
        private const val CALL_STATE_HELD = 1
        private const val CALL_STATE_DIALING = 2
        private const val CALL_STATE_ALERTING = 3
        private const val CALL_STATE_INCOMING = 4
        private const val CALL_STATE_WAITING = 5
    }
}
