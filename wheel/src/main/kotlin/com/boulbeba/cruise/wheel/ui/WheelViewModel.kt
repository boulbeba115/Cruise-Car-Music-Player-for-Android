package com.boulbeba.cruise.wheel.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boulbeba.cruise.core.car.steering.VendorScreenSuppressor
import com.boulbeba.cruise.core.car.tw.TwUtilBridge
import com.boulbeba.cruise.core.model.StorageRoots
import com.boulbeba.cruise.wheel.engine.Rule
import com.boulbeba.cruise.wheel.engine.RuleStore
import com.boulbeba.cruise.wheel.probe.CarProbe
import com.boulbeba.cruise.wheel.probe.Feature
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

@HiltViewModel
class WheelViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: RuleStore,
    val probe: CarProbe,
) : ViewModel() {
    val rules = store.rules
    val smartCalls = MutableStateFlow(store.smartCalls)
    val apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val message = MutableStateFlow<WheelMessage?>(null)
    val isTwUnit = TwUtilBridge.isAvailable

    init {
        viewModelScope.launch {
            apps.value = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { LaunchableApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }
        }
    }

    fun helperEnabled() = VendorScreenSuppressor.isHelperEnabled(context)

    fun setSmartCalls(on: Boolean) {
        store.smartCalls = on
        smartCalls.value = on
    }

    fun save(rule: Rule) = store.put(rule)
    fun remove(rule: Rule) = store.remove(rule)

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

    fun toggleListening() = if (probe.running.value) probe.stop() else probe.start()
    fun beginStep(f: Feature) = probe.beginStep(f)
    fun endStep() { probe.endStep() }

    fun saveReport() = viewModelScope.launch {
        message.value = withContext(Dispatchers.IO) { probe.saveReport()?.let { WheelMessage.ReportSaved(it.path) } ?: WheelMessage.ReportFailed }
    }

    private fun folder(): File? = (StorageRoots.removableRoots(context).map { File(it, "cruise-wheel") } + listOfNotNull(context.getExternalFilesDir(null)))
        .firstOrNull { d -> runCatching { d.mkdirs(); File(d, ".w").apply { writeText("ok") }.delete() }.getOrDefault(false) }

    private companion object {
        const val MAPPING_FILE = "mapping.json"
    }
}
