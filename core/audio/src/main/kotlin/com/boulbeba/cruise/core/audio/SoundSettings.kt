package com.boulbeba.cruise.core.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Where the listener sits (SPEC DSP-11). The Mégane is left-hand drive: the driver is on the left. */
enum class Focus { Off, Driver, Passenger }

enum class ReplayGainMode { Off, Track, Album, Auto }

/** All sound-engine settings (SPEC §4.3). */
data class SoundSettings(
    val enabled: Boolean = true,
    val preset: String = "flat",
    val eq: List<Float> = List(EQ_BANDS.size) { 0f },
    val preampDb: Float = 0f,
    val bassDb: Float = 0f,
    val loudness: Boolean = true,
    val width: Float = 1f,
    val focus: Focus = Focus.Off,
    val balance: Float = 0f,
    val limiter: Boolean = true,
    val replayGain: ReplayGainMode = ReplayGainMode.Auto,
    val rgPreampDb: Float = 0f,
) {
    fun toJson(): String = JSONObject()
        .put("enabled", enabled).put("preset", preset).put("eq", JSONArray(eq.map { it.toDouble() }))
        .put("preamp", preampDb.toDouble()).put("bass", bassDb.toDouble()).put("loudness", loudness)
        .put("width", width.toDouble()).put("focus", focus.name).put("balance", balance.toDouble())
        .put("limiter", limiter).put("rg", replayGain.name).put("rgPreamp", rgPreampDb.toDouble())
        .toString()

    companion object {
        /** Graphic EQ centres (SPEC DSP-3). */
        val EQ_BANDS = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        const val EQ_RANGE_DB = 12f

        fun fromJson(s: String): SoundSettings = runCatching {
            val o = JSONObject(s)
            val eqArray = o.optJSONArray("eq")
            SoundSettings(
                enabled = o.optBoolean("enabled", true),
                preset = o.optString("preset", "flat"),
                eq = if (eqArray != null && eqArray.length() == EQ_BANDS.size) List(EQ_BANDS.size) { eqArray.getDouble(it).toFloat() } else List(EQ_BANDS.size) { 0f },
                preampDb = o.optDouble("preamp", 0.0).toFloat(),
                bassDb = o.optDouble("bass", 0.0).toFloat(),
                loudness = o.optBoolean("loudness", true),
                width = o.optDouble("width", 1.0).toFloat(),
                focus = runCatching { Focus.valueOf(o.optString("focus")) }.getOrDefault(Focus.Off),
                balance = o.optDouble("balance", 0.0).toFloat(),
                limiter = o.optBoolean("limiter", true),
                replayGain = runCatching { ReplayGainMode.valueOf(o.optString("rg")) }.getOrDefault(ReplayGainMode.Auto),
                rgPreampDb = o.optDouble("rgPreamp", 0.0).toFloat(),
            )
        }.getOrDefault(SoundSettings())
    }
}

/** Built-in sound presets (SPEC DSP-14). Gains per EQ band 31 Hz … 16 kHz. */
enum class SoundPreset(val id: String, val eq: List<Float>, val bassDb: Float = 0f) {
    Flat("flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    Megane("megane", listOf(2f, 3f, 2f, 0f, -2f, -1f, 0f, 1.5f, 2f, 2.5f)),
    Highway("highway", listOf(3f, 4f, 3f, 1f, 0f, 0f, 1f, 2.5f, 3f, 2f)),
    Bass("bass", listOf(5f, 6f, 4f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f), bassDb = 2f),
    Vocal("vocal", listOf(-2f, -2f, -1f, 0f, 2f, 3.5f, 3.5f, 2f, 0f, -1f)),
    Rock("rock", listOf(4f, 3f, 1.5f, 0f, -1.5f, -1f, 1f, 2.5f, 3.5f, 3.5f)),
    Pop("pop", listOf(-1f, 1f, 2.5f, 3f, 2f, 0f, -1f, -1f, 1f, 1.5f)),
    Electronic("electronic", listOf(5f, 4.5f, 1.5f, 0f, -2f, 1f, 0.5f, 1f, 4f, 4.5f)),
    Jazz("jazz", listOf(3f, 2f, 1f, 2f, -1.5f, -1.5f, 0f, 1.5f, 2.5f, 3f)),
    Classical("classical", listOf(3.5f, 3f, 2f, 1.5f, -1f, -1f, 0f, 2f, 2.5f, 3f)),
    Night("night", listOf(2f, 2f, 1f, 0f, 0f, 0f, 0f, -1f, -2f, -3f)),
}

/** Persists [SoundSettings]; changes flow straight into the [SoundEngine]. */
@Singleton
class SoundSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("sound_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(SoundSettings.fromJson(prefs.getString("settings", null) ?: "{}"))
    val settings: StateFlow<SoundSettings> = _settings.asStateFlow()

    fun update(transform: (SoundSettings) -> SoundSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit().putString("settings", next.toJson()).apply()
        SoundEngine.settings = next
    }

    fun applyPreset(preset: SoundPreset) = update { it.copy(preset = preset.id, eq = preset.eq, bassDb = preset.bassDb, enabled = true) }

    init {
        SoundEngine.settings = _settings.value
    }
}
