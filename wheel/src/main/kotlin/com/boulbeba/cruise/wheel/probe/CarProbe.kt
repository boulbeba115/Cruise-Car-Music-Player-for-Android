package com.boulbeba.cruise.wheel.probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.boulbeba.cruise.core.car.CarLog
import com.boulbeba.cruise.core.car.tw.TwProtocol
import com.boulbeba.cruise.core.car.tw.TwUtilBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A car feature the probe looks for, with what the driver does to make it show (CHANGE_REQUESTS.md CR-10.3). */
enum class Feature(val title: String, val instruction: String, val needsAction: Boolean = true) {
    WheelKeys("Steering-wheel buttons", "Press every button on the steering wheel once, then hold one of them."),
    Lights("Headlights", "Switch the headlights on, wait two seconds, switch them off."),
    Dimmer("Dashboard brightness", "Turn the dashboard brightness wheel up and down."),
    Reverse("Reverse gear", "Parked, foot on the brake: engage reverse for three seconds, then neutral."),
    Handbrake("Handbrake", "Release the handbrake, then apply it again."),
    Doors("Doors", "Open the driver's door, then close it."),
    SeatBelt("Seat belt", "Unbuckle the driver's seat belt, then buckle it again."),
    Climate("Climate", "Change the fan speed, then the temperature."),
    ParkingSensors("Parking sensors", "In reverse, put a hand near a rear parking sensor."),
    Rpm("Engine speed", "Parked: rev the engine gently twice."),
    Speed("Vehicle speed", "Drive slowly for a few seconds (or record this step on the road)."),
    Volume("Volume knob", "Turn the unit's volume knob up and down."),
    Ignition("Ignition", "If the unit stays on in the accessory position: switch the engine off, then on again."),
    Background("Values the car sends by itself", "Do nothing for ten seconds: outside temperature, fuel, trip data…", needsAction = false),
}

/** What showed up while a feature was being tested. */
data class StepResult(
    val feature: Feature,
    val mcu: List<String>,
    val broadcasts: List<String>,
    val settings: List<String>,
    val properties: List<String>,
) {
    val detected: Boolean get() = mcu.isNotEmpty() || broadcasts.isNotEmpty() || settings.isNotEmpty() || properties.isNotEmpty()
}

data class ProbeStats(val mcuIds: Int = 0, val mcuMessages: Int = 0, val broadcasts: Int = 0)

/**
 * Car integration probe (CHANGE_REQUESTS.md CR-10.3): listens to everything the head unit exposes — MCU messages over
 * the vendor TWUtil link (where the CAN-bus data arrives on TW units), vendor broadcasts, system settings and
 * properties — and, step by step, matches each car feature to the signals that change while the driver uses it.
 */
@Singleton
class CarProbe @Inject constructor(@ApplicationContext private val context: Context) {

    private class McuLog(var count: Int = 0, val values: LinkedHashSet<String> = LinkedHashSet(), var lastValue: String = "", var changedAt: Long = 0)

    private val mcu = HashMap<Int, McuLog>()
    private val broadcastLog = ArrayList<Pair<Long, String>>()
    private var bridge: TwUtilBridge? = null
    private var receiver: BroadcastReceiver? = null
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) = onMcu(msg)
    }

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _stats = MutableStateFlow(ProbeStats())
    val stats: StateFlow<ProbeStats> = _stats.asStateFlow()
    private val _results = MutableStateFlow<Map<Feature, StepResult>>(emptyMap())
    val results: StateFlow<Map<Feature, StepResult>> = _results.asStateFlow()

    /** MCU link opened for the wide id range; false when the unit refused it (then only known ids are watched). */
    var wideRange = false
        private set

    private var step: Step? = null

    private class Step(
        val feature: Feature,
        val startedAt: Long,
        val mcuValues: Map<Int, Set<String>>,
        val settings: Map<String, String>,
        val properties: Map<String, String>,
    )

    @Synchronized
    fun start() {
        if (_running.value) return
        _running.value = true
        if (TwUtilBridge.isAvailable) TwUtilBridge.create()?.let { b ->
            wideRange = b.open(WIDE_IDS)
            if (wideRange || b.open(KNOWN_IDS)) {
                b.start()
                b.addHandler(HANDLER_NAME, handler)
                bridge = b
            }
            CarLog.log("probe", "MCU link ${if (bridge == null) "refused" else if (wideRange) "open, ${WIDE_IDS.size} ids" else "open, known ids only"}")
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val extras = intent.extras?.keySet()?.joinToString { k -> "$k=${runCatching { intent.extras?.get(k) }.getOrNull()}" }.orEmpty()
                synchronized(broadcastLog) { broadcastLog += SystemClock.elapsedRealtime() to "${intent.action} $extras".trim() }
                bumpStats()
            }
        }.also { r ->
            ContextCompat.registerReceiver(context, r, IntentFilter().apply { BROADCASTS.forEach(::addAction) }, ContextCompat.RECEIVER_EXPORTED)
        }
    }

    @Synchronized
    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        bridge?.run { removeHandler(HANDLER_NAME); stop(); close() }
        bridge = null
        _running.value = false
    }

    private fun onMcu(msg: Message) {
        val value = "${msg.arg1}:${msg.arg2}${msg.obj?.let { ":" + describe(it) }.orEmpty()}"
        val log = mcu.getOrPut(msg.what) { McuLog() }
        log.count++
        if (value != log.lastValue) log.changedAt = SystemClock.elapsedRealtime()
        log.lastValue = value
        if (log.values.size < MAX_VALUES) log.values += value
        bumpStats()
    }

    private fun describe(obj: Any): String = when (obj) {
        is ByteArray -> obj.take(32).joinToString("") { "%02x".format(it) }
        is IntArray -> obj.take(16).joinToString(",")
        is ShortArray -> obj.take(16).joinToString(",")
        is String -> obj.take(40)
        else -> obj.toString().take(40)
    }

    private fun bumpStats() {
        _stats.value = ProbeStats(mcu.size, mcu.values.sumOf { it.count }, synchronized(broadcastLog) { broadcastLog.size })
    }

    // ---- Guided steps ----------------------------------------------------------------------------------------

    fun beginStep(feature: Feature) {
        if (!_running.value) start()
        step = Step(feature, SystemClock.elapsedRealtime(), mcu.mapValues { it.value.values.toSet() }, settingsSnapshot(), propertiesSnapshot())
    }

    /** Ends the current step: every MCU id that carried a new value, every broadcast, every changed setting or property. */
    fun endStep(): StepResult? {
        val s = step ?: return null
        step = null
        // A new value, or a value that changed during the step (busy ids like speed fill their value list early).
        val mcuChanges = mcu.mapNotNull { (id, log) ->
            val fresh = log.values - s.mcuValues[id].orEmpty()
            when {
                fresh.isNotEmpty() -> "0x%04X (%d): %s".format(id, id, fresh.take(6).joinToString(" | "))
                log.changedAt >= s.startedAt -> "0x%04X (%d): changing, now %s".format(id, id, log.lastValue)
                else -> null
            }
        }.sorted()
        val bc = synchronized(broadcastLog) { broadcastLog.filter { it.first >= s.startedAt }.map { it.second }.distinct() }
        val settings = diff(s.settings, settingsSnapshot())
        val props = diff(s.properties, propertiesSnapshot())
        return StepResult(s.feature, mcuChanges, bc, settings, props).also { r -> _results.value = _results.value + (s.feature to r) }
    }

    val stepRunning: Feature? get() = step?.feature

    private fun diff(before: Map<String, String>, after: Map<String, String>): List<String> =
        (before.keys + after.keys).filter { before[it] != after[it] }.sorted().map { "$it: ${before[it] ?: "—"} → ${after[it] ?: "—"}" }

    private fun settingsSnapshot(): Map<String, String> = runCatching {
        context.contentResolver.query(Settings.System.CONTENT_URI, arrayOf("name", "value"), null, null, null)?.use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0) ?: continue, c.getString(1).orEmpty()) }
        }.orEmpty()
    }.getOrDefault(emptyMap())

    private fun propertiesSnapshot(): Map<String, String> = runCatching {
        Runtime.getRuntime().exec("getprop").inputStream.bufferedReader().useLines { lines ->
            lines.mapNotNull { Regex("""^\[(.+?)]: \[(.*)]$""").find(it) }.associate { it.groupValues[1] to it.groupValues[2] }
        }
    }.getOrDefault(emptyMap())

    // ---- Report ----------------------------------------------------------------------------------------------

    /** Writes the report (JSON for tools, HTML for people) and returns its folder. */
    fun saveReport(): File? {
        val report = ProbeReport(
            device = deviceInfo(),
            linkOpen = bridge != null || mcu.isNotEmpty(),
            wideRange = wideRange,
            results = Feature.entries.map { f -> _results.value[f] ?: StepResult(f, emptyList(), emptyList(), emptyList(), emptyList()) },
            tested = _results.value.keys,
            mcu = mcu.toSortedMap().map { (id, log) -> McuSummary(id, log.count, log.values.toList()) },
            broadcasts = synchronized(broadcastLog) { broadcastLog.map { it.second }.distinct() },
            vendorSettings = settingsSnapshot().filterKeys { k -> VENDOR_HINTS.any { k.contains(it, ignoreCase = true) } },
            vendorProperties = propertiesSnapshot().filterKeys { k -> VENDOR_HINTS.any { k.contains(it, ignoreCase = true) } },
            sensors = sensors(),
            sysNodes = sysNodes(),
        )
        val dir = outputDir() ?: return null
        val folder = File(dir, "report-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(java.util.Date())).apply { mkdirs() }
        return runCatching {
            File(folder, "report.json").writeText(report.toJson())
            File(folder, "report.html").writeText(report.toHtml())
            CarLog.log("probe", "report saved to $folder")
            folder
        }.getOrNull()
    }

    private fun outputDir(): File? {
        val candidates = com.boulbeba.cruise.core.model.StorageRoots.removableRoots(context).map { File(it, "cruise-wheel") } +
            listOfNotNull(context.getExternalFilesDir(null))
        return candidates.firstOrNull { d -> runCatching { d.mkdirs(); File(d, ".w").apply { writeText("ok") }.delete() }.getOrDefault(false) }
    }

    private fun deviceInfo(): Map<String, String> = linkedMapOf(
        "manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL, "device" to Build.DEVICE, "board" to Build.BOARD,
        "hardware" to Build.HARDWARE, "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "fingerprint" to Build.FINGERPRINT, "tw platform" to TwUtilBridge.isAvailable.toString(),
    )

    private fun sensors(): List<String> = runCatching {
        context.getSystemService(SensorManager::class.java).getSensorList(Sensor.TYPE_ALL).map { "${it.name} (${it.vendor}, type ${it.type})" }
    }.getOrDefault(emptyList())

    private fun sysNodes(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val dirs = listOf("/sys/class/backlight", "/sys/class/power_supply", "/sys/class/leds", "/sys/class/switch", "/sys/class/input")
        for (d in dirs) File(d).listFiles()?.forEach { node ->
            for (leaf in listOf("brightness", "online", "status", "voltage_now", "capacity", "state", "name")) {
                val f = File(node, leaf)
                if (f.canRead()) runCatching { out["${node.path}/$leaf"] = f.readText().trim().take(60) }
            }
        }
        return out
    }

    companion object {
        private const val HANDLER_NAME = "CruiseWheelProbe"
        private const val MAX_VALUES = 24

        /** Message ids seen in the vendor apps (docs/unit-findings.md). */
        val KNOWN_IDS: ShortArray = TwProtocol.ids(
            TwProtocol.KEY, TwProtocol.KEY_LONG, TwProtocol.SOURCE, 771, 1282, TwProtocol.SCREEN_STATE,
            40448, 40449, 40451, 40452, 40456, 40457, 40458, 40465, 40704,
        )

        /**
         * A wide sweep: the low command ranges (0x0100–0x07FF), their long-press/second-page variants (0x8100–0x87FF)
         * and the 0x9E00–0x9FFF block the vendor apps use. The CAN box's data reaches apps through ids in these ranges.
         */
        val WIDE_IDS: ShortArray = (0x0100..0x07FF).plus(0x8100..0x87FF).plus(0x9E00..0x9FFF).let { ids -> ShortArray(ids.size) { ids[it].toShort() } }

        /** Broadcasts found in the vendor apps (com.tw.*, zlink, Dofun) plus the standard car-relevant ones. */
        val BROADCASTS = listOf(
            "com.android.action_acc_on", "com.android.action_acc_off", "com.tw.carchoose.broadcast.UART",
            "com.zjinnova.zlink.action.OUT_DARK_START", "com.zjinnova.zlink.action.OUT_DARK_STOP",
            "action.hicar.onconnect", "action.hicar.ondisconnect", "action.tw.open.hicarvoice",
            "action.intent.AIOS_ACCEPT", "action.intent.AIOS_DIAL", "action.intent.AIOS_REJECT",
            "com.tw.core.DESTROY_MAINSERVICE", "com.unisound.unicar.action.mute", "txz.action.START_SMART_ADAPTER",
            Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_HEADSET_PLUG, "android.intent.action.PHONE_STATE",
            "android.bluetooth.device.action.ACL_CONNECTED", "android.bluetooth.device.action.ACL_DISCONNECTED",
        )

        /** Substrings that mark vendor settings and properties worth reporting. */
        val VENDOR_HINTS = listOf("tw", "can", "car", "mcu", "acc", "reverse", "back", "ill", "light", "steer", "swc", "zlink", "dofun")
    }
}
