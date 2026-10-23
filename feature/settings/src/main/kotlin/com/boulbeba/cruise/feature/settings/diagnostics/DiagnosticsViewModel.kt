package com.boulbeba.cruise.feature.settings.diagnostics

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val REPORT_FILE_NAME = "cruise-device-report.txt"

sealed interface ExportState {
    data object Idle : ExportState
    data class Saved(val location: String) : ExportState
    data class Failed(val message: String) : ExportState
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val collector: DeviceReportCollector,
) : ViewModel() {

    private val _report = MutableStateFlow<DeviceReport?>(null)
    val report: StateFlow<DeviceReport?> = _report.asStateFlow()

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()

    val keyEvents = KeyEventLog.events

    fun refresh(uiInfo: List<Pair<String, String>>) {
        viewModelScope.launch { _report.value = collector.collect(uiInfo) }
    }

    fun clearKeyEvents() = KeyEventLog.clear()

    fun exportTo(uri: Uri, resolver: ContentResolver) {
        val report = _report.value ?: return
        viewModelScope.launch {
            _export.value = runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
                        it.write(report.toText(KeyEventLog.events.value))
                    }
                }
                ExportState.Saved(uri.lastPathSegment ?: uri.toString())
            }.getOrElse { ExportState.Failed(it.message ?: it.toString()) }
        }
    }

    /**
     * For ROMs without the system file picker (common on budget head units): writes into the app's own
     * folder on shared storage, which any file manager can copy to the USB stick. No permission needed.
     */
    fun exportToAppFolder(folder: File?) {
        val report = _report.value ?: return
        viewModelScope.launch {
            _export.value = runCatching {
                val target = File(requireNotNull(folder) { "shared storage unavailable" }, REPORT_FILE_NAME)
                withContext(Dispatchers.IO) { target.writeText(report.toText(KeyEventLog.events.value)) }
                ExportState.Saved(target.absolutePath)
            }.getOrElse { ExportState.Failed(it.message ?: it.toString()) }
        }
    }

    fun dismissExportState() {
        _export.value = ExportState.Idle
    }
}
