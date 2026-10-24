package com.boulbeba.cruise.wheel.ui

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.car.steering.VendorScreenSuppressor
import com.boulbeba.cruise.core.car.tw.TwUtilBridge
import com.boulbeba.cruise.core.model.StorageRoots
import com.boulbeba.cruise.wheel.engine.Action
import com.boulbeba.cruise.wheel.engine.ActionRunner
import com.boulbeba.cruise.wheel.engine.Rule
import com.boulbeba.cruise.wheel.engine.RuleStore
import com.boulbeba.cruise.wheel.engine.Trigger
import com.boulbeba.cruise.wheel.probe.CarProbe
import com.boulbeba.cruise.wheel.probe.Feature
import com.boulbeba.cruise.wheel.service.KillGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An installed app that can be opened from the wheel. */
data class LaunchableApp(val packageName: String, val label: String)

/** Messages for the screen after an export, import or report. */
sealed interface WheelMessage {
    data class Exported(val path: String) : WheelMessage
    data class Imported(val count: Int) : WheelMessage
    data object NoFile : WheelMessage
    data class ReportSaved(val path: String) : WheelMessage
    data object ReportFailed : WheelMessage
}

/** What still keeps the wheel from working on every drive (CHANGE_REQUESTS.md CR-19). */
data class SetupState(
    /** The unit kills apps at ignition-off (TW units) … */
    val keepAliveApplies: Boolean = false,
    /** … and has been told not to. */
    val keepAliveOn: Boolean = true,
    /** The unit refused the request. */
    val keepAliveRefused: Boolean = false,
    val helperOn: Boolean = false,
) {
    val keepAliveNeeded: Boolean get() = keepAliveApplies && !keepAliveOn && !keepAliveRefused
    val complete: Boolean get() = !keepAliveNeeded && helperOn
}

@HiltViewModel
class WheelViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: RuleStore,
    private val runner: ActionRunner,
    val probe: CarProbe,
) : ViewModel() {
    val rules = store.rules
    val buttons = store.buttons
    val smartCalls = MutableStateFlow(store.smartCalls)
    val musicTarget = MutableStateFlow(store.musicTarget)
    val apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val message = MutableStateFlow<WheelMessage?>(null)
    val setup = MutableStateFlow(SetupState())
    val isTwUnit = TwUtilBridge.isAvailable
    val cruiseInstalled: Boolean get() = runner.cruisePackage() != null
    val version: String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** No speech service on the owner's unit: the picker leaves the voice assistant out unless something answers it. */
    val voiceAvailable: Boolean = listOf(Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE), Intent(Intent.ACTION_VOICE_COMMAND))
        .any { context.packageManager.resolveActivity(it, 0) != null }

    /** The car-report step being recorded. */
    val currentStep = MutableStateFlow<Feature?>(null)

    init {
        refreshSetup()
        viewModelScope.launch {
            apps.value = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo.packageName != context.packageName }
                    .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }

    /** Called when the screen comes back (the driver may have just turned the helper on). */
    fun refreshSetup() {
        setup.update {
            it.copy(keepAliveApplies = KillGuard.applies, keepAliveOn = KillGuard.isOn(), helperOn = VendorScreenSuppressor.isHelperEnabled(context))
        }
    }

    fun setKeepAlive(on: Boolean) = viewModelScope.launch {
        val took = withContext(Dispatchers.IO) { if (on) KillGuard.turnOn() else KillGuard.allowKill() }
        store.keepAlive = on && took
        setup.update { it.copy(keepAliveOn = KillGuard.isOn(), keepAliveRefused = on && !took) }
    }

    fun appLabel(packageName: String): String =
        apps.value.firstOrNull { it.packageName == packageName }?.label ?: runner.appLabel(packageName) ?: packageName

    fun setAction(trigger: Trigger, action: Action, packageName: String? = null) = store.put(Rule(trigger, action, packageName))

    fun clear(trigger: Trigger) = store.clear(trigger)

    fun addButton(code: Int) = store.addButton(code)

    fun forgetButton(code: Int) = store.forgetButton(code)

    fun setSmartCalls(on: Boolean) {
        store.smartCalls = on
        smartCalls.value = on
    }

    /** null: Cruise, "": whichever app plays, else a package. */
    fun setMusicTarget(target: String?) {
        store.musicTarget = target
        musicTarget.value = target
    }

    fun export() = viewModelScope.launch {
        message.value = withContext(Dispatchers.IO) {
            val dir = folder() ?: return@withContext WheelMessage.ReportFailed
            val f = File(dir, MAPPING_FILE)
            runCatching { f.writeText(Rule.listToJson(rules.value)) }.fold({ WheelMessage.Exported(f.path) }, { WheelMessage.ReportFailed })
        }
    }

    fun import() = viewModelScope.launch {
        message.value = withContext(Dispatchers.IO) {
            val f = StorageRoots.removableRoots(context).map { File(it, "cruise-wheel/$MAPPING_FILE") }.firstOrNull { it.canRead() }
                ?: return@withContext WheelMessage.NoFile
            val imported = Rule.listFromJson(f.readText())
            store.set(imported)
            WheelMessage.Imported(imported.size)
        }
    }

    // ---- Car report ------------------------------------------------------------------------------------------

    fun toggleListening() = if (probe.running.value) probe.stop() else probe.start()

    fun startStep(f: Feature) = viewModelScope.launch {
        if (currentStep.value != null) probe.endStep()
        currentStep.value = f
        probe.beginStep(f)
    }

    fun endStep() = viewModelScope.launch {
        currentStep.value = null
        probe.endStep()
    }

    fun saveReport() = viewModelScope.launch {
        message.value = withContext(Dispatchers.IO) { probe.saveReport()?.let { WheelMessage.ReportSaved(it.path) } ?: WheelMessage.ReportFailed }
    }

    private fun folder(): File? = (StorageRoots.removableRoots(context).map { File(it, "cruise-wheel") } + listOfNotNull(context.getExternalFilesDir(null)))
        .firstOrNull { d -> runCatching { d.mkdirs(); File(d, ".w").apply { writeText("ok") }.delete() }.getOrDefault(false) }

    private companion object {
        const val MAPPING_FILE = "mapping.json"
    }
}
