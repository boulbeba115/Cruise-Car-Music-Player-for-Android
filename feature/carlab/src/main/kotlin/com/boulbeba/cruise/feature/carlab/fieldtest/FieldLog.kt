package com.boulbeba.cruise.feature.carlab.fieldtest

import android.content.Context
import android.os.Process
import android.os.SystemClock
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Append-only event log for the car field test. It lives in a file so it survives sleep, process
 * death and reboots, and every line carries both clocks: `up` stops during deep sleep, `real` does
 * not, so the difference between two lines shows how long the unit really slept.
 */
object FieldLog {
    private const val MAX_IN_MEMORY = 400
    private const val MAX_FILE_BYTES = 4L * 1024 * 1024

    private var dir: File? = null
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    /** Folder holding the log and every step's result file; exported as a whole. */
    val folder: File? get() = dir
    val resultsFolder: File? get() = dir?.let { File(it, "results").apply { mkdirs() } }
    private val logFile: File? get() = dir?.let { File(it, "field-log.txt") }

    fun init(context: Context) {
        if (dir != null) return
        dir = File(context.filesDir, "fieldtest").apply { mkdirs() }
        log("process", "started pid=${Process.myPid()}")
        installCrashHandler()
    }

    @Synchronized
    fun log(tag: String, message: String) {
        val line = "%s up=%ds real=%ds [%s] %s".format(
            Locale.ROOT,
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date()),
            SystemClock.uptimeMillis() / 1000,
            SystemClock.elapsedRealtime() / 1000,
            tag,
            message,
        )
        logFile?.let { file ->
            runCatching {
                if (file.length() > MAX_FILE_BYTES) file.renameTo(File(file.parentFile, "field-log.old.txt"))
                file.appendText(line + "\n")
            }
        }
        _lines.update { (it + line).takeLast(MAX_IN_MEMORY) }
    }

    /** Writes one step's result where the exporter picks it up. */
    fun writeResult(name: String, text: String) {
        resultsFolder?.let { runCatching { File(it, "$name.txt").writeText(text) } }
        log("result", "$name saved (${text.length} chars)")
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
            log("CRASH", "thread=${thread.name}\n$trace")
            previous?.uncaughtException(thread, error)
        }
    }
}
