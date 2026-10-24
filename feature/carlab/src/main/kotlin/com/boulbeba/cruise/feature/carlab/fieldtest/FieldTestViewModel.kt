package com.boulbeba.cruise.feature.carlab.fieldtest

import com.boulbeba.cruise.core.model.StorageRoots

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.feature.carlab.diagnostics.DeviceReportCollector
import com.boulbeba.cruise.feature.carlab.diagnostics.KeyEventLog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FieldStep { Permissions, Logger, Report, Sound, Performance, Keys, UsbScan, VendorFiles, PowerCycle, Save }

enum class StepStatus { Pending, Running, Done, Failed }

data class StepState(val status: StepStatus = StepStatus.Pending, val summary: String = "")

/** Drives the one-visit car test: every step stores its result so nothing is lost across ignition cycles. */
@HiltViewModel
class FieldTestViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reportCollector: DeviceReportCollector,
) : ViewModel() {

    private val prefs = FieldTestService.prefs(context)

    private val _steps = MutableStateFlow(FieldStep.entries.associateWith(::load))
    val steps: StateFlow<Map<FieldStep, StepState>> = _steps.asStateFlow()

    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress.asStateFlow()

    private val _frameTestRunning = MutableStateFlow(false)
    val frameTestRunning: StateFlow<Boolean> = _frameTestRunning.asStateFlow()

    val loggerActive: Boolean get() = FieldTestService.isActive(context)

    private fun load(step: FieldStep): StepState {
        val raw = prefs.getString("step_${step.name}", null) ?: return StepState()
        val status = runCatching { StepStatus.valueOf(raw.substringBefore('|')) }.getOrDefault(StepStatus.Pending)
        // A step interrupted by a power cut is not running any more.
        return StepState(if (status == StepStatus.Running) StepStatus.Pending else status, raw.substringAfter('|', ""))
    }

    fun set(step: FieldStep, status: StepStatus, summary: String) {
        prefs.edit().putString("step_${step.name}", "${status.name}|$summary").apply()
        _steps.update { it + (step to StepState(status, summary)) }
        if (status != StepStatus.Running) FieldLog.log("step", "${step.name} -> $status: ${summary.lineSequence().firstOrNull().orEmpty()}")
    }

    fun permissionsChecked(summary: String, allGranted: Boolean) =
        set(FieldStep.Permissions, if (allGranted) StepStatus.Done else StepStatus.Pending, summary)

    fun toggleLogger() {
        if (loggerActive) {
            FieldTestService.stop(context)
            set(FieldStep.Logger, StepStatus.Pending, "stopped")
        } else {
            FieldTestService.start(context)
            set(FieldStep.Logger, StepStatus.Done, "recording")
        }
    }

    fun runReport(uiInfo: List<Pair<String, String>>) = launchStep(FieldStep.Report) {
        val report = reportCollector.collect(uiInfo)
        FieldLog.writeResult("device-report", report.toText(KeyEventLog.events.value))
        val real = report.sections.firstOrNull()?.entries?.toMap()?.get("Real Android version").orEmpty()
        "Saved. $real"
    }

    fun recordSound(answer: String) {
        FieldLog.writeResult("sound-check", "Left/right tone check: $answer")
        set(FieldStep.Sound, StepStatus.Done, answer)
    }

    fun startFrameTest() {
        set(FieldStep.Performance, StepStatus.Running, "measuring animation…")
        _frameTestRunning.value = true
    }

    fun frameTestFinished(stats: FrameStats) {
        _frameTestRunning.value = false
        launchStep(FieldStep.Performance) {
            _progress.value = "Measuring the CPU for the sound engine…"
            val dsp = withContext(Dispatchers.Default) { DspBenchmark.runAll() }
            val text = buildString {
                appendLine("Animation (Compose, animated test scene): ${stats.describe()}")
                appendLine()
                appendLine("DSP (Kotlin, single thread):")
                dsp.forEach { appendLine("  ${it.describe()}") }
            }
            FieldLog.writeResult("performance", text)
            "%.0f fps animation, 10-band EQ uses %.1f %% of a core".format(stats.fps, dsp[1].coreLoadPercent)
        }
    }

    fun keysDone() {
        val bySource = KeyEventLog.events.value.groupingBy { it.source }.eachCount().toMutableMap()
        // Raw steering keys from the TW MCU are logged by the head-unit layer, not as Android key events.
        val raw = FieldLog.lines.value.count { "[key] raw" in it }
        if (raw > 0) bySource["tw-raw"] = raw
        val summary = if (bySource.isEmpty()) "no key events received" else bySource.entries.joinToString { "${it.key}: ${it.value}" }
        FieldLog.writeResult("keys", KeyEventLog.events.value.joinToString("\n") { it.describe() })
        set(FieldStep.Keys, if (bySource.isEmpty()) StepStatus.Failed else StepStatus.Done, summary)
    }

    fun runUsbScan() = launchStep(FieldStep.UsbScan) {
        val roots = StorageRoots.removableRoots(context)
        val root = roots.firstOrNull() ?: error("No USB stick found. Is it plugged in, and is storage access granted?")
        val text = UsbScanner.scan(context, root) { _progress.value = it }
        FieldLog.writeResult("usb-scan", "All removable roots: ${roots.joinToString { it.path }}\n\n$text")
        text.lineSequence().firstOrNull { it.startsWith("Audio files") } ?: "Scanned ${root.path}"
    }

    fun runVendorExport() = launchStep(FieldStep.VendorFiles) {
        val target = StorageRoots.outputDirs(context).firstOrNull() ?: error("No writable storage found")
        val text = VendorExport.export(context, target) { _progress.value = it }
        FieldLog.writeResult("vendor-export", "Target: ${target.path}\n$text")
        text.lineSequence().lastOrNull { it.isNotBlank() } ?: "done"
    }

    fun powerCycleDone() {
        val lines = FieldLog.lines.value
        val summary = "screen-off ${lines.count { "SCREEN_OFF" in it }}, wake ${lines.count { "woke after" in it }}, " +
            "boot ${lines.count { "[boot]" in it }}, app restarts ${lines.count { "[process] started" in it }}"
        set(FieldStep.PowerCycle, StepStatus.Done, summary)
    }

    fun saveAll() = launchStep(FieldStep.Save) {
        FieldLog.writeResult("steps", _steps.value.entries.joinToString("\n") { "${it.key}: ${it.value.status} — ${it.value.summary}" })
        val result = withContext(Dispatchers.IO) { FieldExporter.exportAll(context) }
        result.primary?.path?.let { "Saved to $it" } ?: error(result.error ?: "export failed")
    }

    private fun launchStep(step: FieldStep, block: suspend () -> String) {
        set(step, StepStatus.Running, "")
        viewModelScope.launch {
            val outcome = runCatching { withContext(Dispatchers.IO) { block() } }
            _progress.value = null
            outcome.fold(
                onSuccess = { set(step, StepStatus.Done, it) },
                onFailure = { set(step, StepStatus.Failed, it.message ?: it.toString()) },
            )
        }
    }
}
