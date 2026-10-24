package com.boulbeba.cruise.feature.carlab.diagnostics

data class ReportSection(val title: String, val entries: List<Pair<String, String>>)

data class DeviceReport(val createdAtMillis: Long, val sections: List<ReportSection>) {

    fun toText(keyEvents: List<KeyLogEntry>): String = buildString {
        appendLine("Cruise device report")
        appendLine("Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).format(createdAtMillis)}")
        appendLine()
        for (section in sections) {
            appendLine("== ${section.title} ==")
            for ((key, value) in section.entries) appendLine("$key: $value")
            appendLine()
        }
        appendLine("== Key events (${keyEvents.size}) ==")
        for (event in keyEvents.asReversed()) appendLine(event.describe())
    }
}
