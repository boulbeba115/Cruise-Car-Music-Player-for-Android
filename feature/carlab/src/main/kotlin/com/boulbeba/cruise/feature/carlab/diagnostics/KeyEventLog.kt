package com.boulbeba.cruise.feature.carlab.diagnostics

import android.view.KeyEvent
import com.boulbeba.cruise.feature.carlab.fieldtest.FieldLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class KeyLogEntry(
    val timeMillis: Long,
    val source: String,
    val action: String,
    val keyCode: Int,
    val keyName: String,
    val scanCode: Int,
    val repeatCount: Int,
    val isLongPress: Boolean,
    val deviceName: String?,
) {
    fun describe(): String =
        "${java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.ROOT).format(timeMillis)} " +
            "[$source] $action $keyName (keyCode=$keyCode, scanCode=$scanCode, repeat=$repeatCount" +
            (if (isLongPress) ", long-press" else "") + ") device=${deviceName ?: "?"}"
}

/**
 * Records every key event the app sees, from the activity and from the media session, so the
 * steering-wheel / CAN-bus keys of a head unit can be identified (SPEC CAR-2, Phase 0 report).
 */
object KeyEventLog {
    private const val MAX_ENTRIES = 300

    private val _events = MutableStateFlow<List<KeyLogEntry>>(emptyList())
    val events: StateFlow<List<KeyLogEntry>> = _events.asStateFlow()

    fun record(source: String, event: KeyEvent) {
        val entry = KeyLogEntry(
            timeMillis = System.currentTimeMillis(),
            source = source,
            action = when (event.action) {
                KeyEvent.ACTION_DOWN -> "DOWN"
                KeyEvent.ACTION_UP -> "UP"
                else -> "MULTIPLE"
            },
            keyCode = event.keyCode,
            keyName = KeyEvent.keyCodeToString(event.keyCode),
            scanCode = event.scanCode,
            repeatCount = event.repeatCount,
            isLongPress = event.isLongPress,
            deviceName = event.device?.name,
        )
        _events.update { (it + entry).takeLast(MAX_ENTRIES) }
        FieldLog.log("key", entry.describe())
    }

    fun clear() {
        _events.value = emptyList()
    }
}
