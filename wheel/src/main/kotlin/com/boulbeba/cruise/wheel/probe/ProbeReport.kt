package com.boulbeba.cruise.wheel.probe

import org.json.JSONArray
import org.json.JSONObject

data class McuSummary(val id: Int, val count: Int, val values: List<String>)

/**
 * The car integration report (CHANGE_REQUESTS.md CR-10.3), written as `report.json` (for tools and for analysis on
 * the PC) and `report.html` (to read on any screen).
 */
data class ProbeReport(
    val device: Map<String, String>,
    val linkOpen: Boolean,
    val idsWatched: Int,
    val results: List<StepResult>,
    val tested: Set<Feature>,
    val mcu: List<McuSummary>,
    val broadcasts: List<String>,
    val vendorSettings: Map<String, String>,
    val vendorProperties: Map<String, String>,
    val sensors: List<String>,
    val sysNodes: Map<String, String>,
) {
    /** Share of the car features the unit exposes, out of the ones tested (0–100). */
    val score: Int get() = if (tested.isEmpty()) 0 else results.count { it.feature in tested && it.detected } * 100 / tested.size

    /** What an app can do with a feature once it is detected. */
    private fun control(f: Feature): String = when (f) {
        Feature.WheelKeys -> "read; remap with Cruise Wheel rules"
        Feature.Volume -> "read; set through Android volume"
        else -> "read; control needs a write test"
    }

    fun toJson(): String = JSONObject().apply {
        put("device", JSONObject(device as Map<*, *>))
        put("mcuLinkOpen", linkOpen)
        put("mcuIdsWatched", idsWatched)
        put("score", score)
        put("features", JSONArray(results.map { r ->
            JSONObject()
                .put("id", r.feature.name).put("name", r.feature.title).put("tested", r.feature in tested).put("detected", r.detected)
                .put("control", if (r.detected) control(r.feature) else "")
                .put("mcu", JSONArray(r.mcu)).put("broadcasts", JSONArray(r.broadcasts))
                .put("settings", JSONArray(r.settings)).put("properties", JSONArray(r.properties))
        }))
        put("mcuMessages", JSONArray(mcu.map { m -> JSONObject().put("id", m.id).put("hex", "0x%04X".format(m.id)).put("count", m.count).put("values", JSONArray(m.values)) }))
        put("broadcasts", JSONArray(broadcasts))
        put("vendorSettings", JSONObject(vendorSettings as Map<*, *>))
        put("vendorProperties", JSONObject(vendorProperties as Map<*, *>))
        put("sensors", JSONArray(sensors))
        put("sysNodes", JSONObject(sysNodes as Map<*, *>))
    }.toString(2)

    fun toHtml(): String = buildString {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        append("""<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Car integration report</title><style>
body{font-family:system-ui,sans-serif;background:#0b0d10;color:#eef2f6;margin:0;padding:16px;max-width:1100px}
h1{margin:0 0 4px}h2{margin-top:32px;border-bottom:1px solid #2a3038;padding-bottom:6px}
.score{font-size:48px;font-weight:700;color:#5ed8ff}.muted{color:#9aa4b1}
table{border-collapse:collapse;width:100%;font-size:14px}td,th{border-bottom:1px solid #1e232a;padding:6px 8px;text-align:left;vertical-align:top}
.yes{color:#2de1c2;font-weight:700}.no{color:#ff6b8b}.skip{color:#9aa4b1}code{font-size:12px;color:#c8d2de;word-break:break-all}
</style></head><body>""")
        append("<h1>Car integration report</h1><p class=\"muted\">${esc(device["manufacturer"].orEmpty())} ${esc(device["model"].orEmpty())} · ${esc(device["android"].orEmpty())}</p>")
        append("<div class=\"score\">$score %</div><p class=\"muted\">of the ${tested.size} tested features show up on this unit. MCU link: ${if (linkOpen) "open" else "not available"}${if (linkOpen) " ($idsWatched message ids watched)" else ""}.</p>")
        append("<h2>Features</h2><table><tr><th>Feature</th><th>Result</th><th>Signals</th><th>What apps can do</th></tr>")
        for (r in results) {
            val status = when {
                r.feature !in tested -> "<span class=\"skip\">not tested</span>"
                r.detected -> "<span class=\"yes\">found</span>"
                else -> "<span class=\"no\">nothing seen</span>"
            }
            val signals = (r.mcu.map { "MCU $it" } + r.broadcasts.map { "broadcast $it" } + r.settings.map { "setting $it" } + r.properties.map { "property $it" })
                .joinToString("<br>") { "<code>${esc(it)}</code>" }
            append("<tr><td>${esc(r.feature.title)}</td><td>$status</td><td>$signals</td><td>${esc(if (r.detected) control(r.feature) else "")}</td></tr>")
        }
        append("</table>")
        append("<h2>MCU messages seen (${mcu.size} ids)</h2><table><tr><th>Id</th><th>Count</th><th>Values (first ones)</th></tr>")
        mcu.forEach { m -> append("<tr><td><code>0x%04X (%d)</code></td><td>${m.count}</td><td><code>${esc(m.values.take(8).joinToString(" | "))}</code></td></tr>".format(m.id, m.id)) }
        append("</table>")
        fun section(title: String, rows: Map<String, String>) {
            append("<h2>${esc(title)} (${rows.size})</h2><table>")
            rows.forEach { (k, v) -> append("<tr><td><code>${esc(k)}</code></td><td><code>${esc(v)}</code></td></tr>") }
            append("</table>")
        }
        section("Broadcasts", broadcasts.associateWith { "" })
        section("Vendor settings", vendorSettings)
        section("Vendor properties", vendorProperties)
        section("Sensors", sensors.associateWith { "" })
        section("Readable /sys nodes", sysNodes)
        section("Device", device)
        append("</body></html>")
    }
}
