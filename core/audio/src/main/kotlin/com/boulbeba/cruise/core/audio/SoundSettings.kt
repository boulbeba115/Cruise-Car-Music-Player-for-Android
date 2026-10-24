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

/**
 * All sound-engine settings (SPEC §4.3). The Sound screen shows profiles, Bass, Treble and Focus only
 * (CHANGE_REQUESTS.md CR-06); width, balance, loudness, limiter and ReplayGain keep their defaults.
 */
data class SoundSettings(
    val enabled: Boolean = true,
    val preset: String = SoundPreset.Flat.id,
    val eq: List<Float> = List(EQ_BANDS.size) { 0f },
    /** The user's own curve, kept while another profile is tried, restored by the Custom profile. */
    val customEq: List<Float> = List(EQ_BANDS.size) { 0f },
    val preampDb: Float = 0f,
    /** Added on top of the profile (−6…+6 dB). */
    val bassDb: Float = 0f,
    val trebleDb: Float = 0f,
    val loudness: Boolean = true,
    val width: Float = 1f,
    val focus: Focus = Focus.Off,
    val balance: Float = 0f,
    val limiter: Boolean = true,
    val replayGain: ReplayGainMode = ReplayGainMode.Auto,
    val rgPreampDb: Float = 0f,
) {
    fun toJson(): String = JSONObject()
        .put("v", VERSION)
        .put("enabled", enabled).put("preset", preset).put("eq", JSONArray(eq.map { it.toDouble() }))
        .put("customEq", JSONArray(customEq.map { it.toDouble() }))
        .put("preamp", preampDb.toDouble()).put("bass", bassDb.toDouble()).put("treble", trebleDb.toDouble()).put("loudness", loudness)
        .put("width", width.toDouble()).put("focus", focus.name).put("balance", balance.toDouble())
        .put("limiter", limiter).put("rg", replayGain.name).put("rgPreamp", rgPreampDb.toDouble())
        .toString()

    companion object {
        /** Graphic EQ centres (SPEC DSP-3). */
        val EQ_BANDS = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0)
        const val EQ_RANGE_DB = 12f
        const val TONE_RANGE_DB = 6f

        /** 2 = profiles (1.2.0). Older settings are carried over without the controls the screen no longer shows. */
        private const val VERSION = 2

        private fun JSONObject.bands(name: String): List<Float>? =
            optJSONArray(name)?.takeIf { it.length() == EQ_BANDS.size }?.let { a -> List(EQ_BANDS.size) { a.getDouble(it).toFloat() } }

        /** A fresh install starts on the profile tuned for the owner's car. */
        val FRESH get() = SoundSettings(preset = SoundPreset.Megane.id, eq = SoundPreset.Megane.eq)

        fun fromJson(s: String): SoundSettings = runCatching {
            val o = JSONObject(s)
            if (!o.has("enabled")) return@runCatching FRESH
            val saved = SoundSettings(
                enabled = o.optBoolean("enabled", true),
                preset = o.optString("preset", SoundPreset.Flat.id),
                eq = o.bands("eq") ?: List(EQ_BANDS.size) { 0f },
                customEq = o.bands("customEq") ?: List(EQ_BANDS.size) { 0f },
                preampDb = o.optDouble("preamp", 0.0).toFloat(),
                bassDb = o.optDouble("bass", 0.0).toFloat(),
                trebleDb = o.optDouble("treble", 0.0).toFloat(),
                loudness = o.optBoolean("loudness", true),
                width = o.optDouble("width", 1.0).toFloat(),
                focus = runCatching { Focus.valueOf(o.optString("focus")) }.getOrDefault(Focus.Off),
                balance = o.optDouble("balance", 0.0).toFloat(),
                limiter = o.optBoolean("limiter", true),
                replayGain = runCatching { ReplayGainMode.valueOf(o.optString("rg")) }.getOrDefault(ReplayGainMode.Auto),
                rgPreampDb = o.optDouble("rgPreamp", 0.0).toFloat(),
            )
            if (o.optInt("v", 1) >= VERSION) saved else migrate(saved)
        }.getOrDefault(SoundSettings())

        /**
         * 1.1 → 1.2: keep the curve and the focus; reset what has no control any more, so nothing invisible stays
         * active. The old "flat" with the engine on becomes Natural (engine off); a hand-made curve becomes Custom.
         */
        private fun migrate(old: SoundSettings): SoundSettings {
            val custom = old.preset == "custom"
            return SoundSettings(
                enabled = old.enabled && old.preset != SoundPreset.Flat.id,
                preset = old.preset,
                eq = old.eq,
                customEq = if (custom) old.eq else List(EQ_BANDS.size) { 0f },
                focus = old.focus,
            )
        }
    }
}

/** Built-in sound presets (SPEC DSP-14). Gains per EQ band 31 Hz … 16 kHz. */
enum class SoundPreset(val id: String, val eq: List<Float>) {
    Flat("flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    Megane("megane", listOf(2f, 3f, 2f, 0f, -2f, -1f, 0f, 1.5f, 2f, 2.5f)),
    Highway("highway", listOf(3f, 4f, 3f, 1f, 0f, 0f, 1f, 2.5f, 3f, 2f)),
    // The former extra 2 dB bass shelf is folded into the curve, so the Bass control stays the user's own.
    Bass("bass", listOf(7f, 8f, 5.5f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
    Vocal("vocal", listOf(-2f, -2f, -1f, 0f, 2f, 3.5f, 3.5f, 2f, 0f, -1f)),
    Rock("rock", listOf(4f, 3f, 1.5f, 0f, -1.5f, -1f, 1f, 2.5f, 3.5f, 3.5f)),
    Pop("pop", listOf(-1f, 1f, 2.5f, 3f, 2f, 0f, -1f, -1f, 1f, 1.5f)),
    Electronic("electronic", listOf(5f, 4.5f, 1.5f, 0f, -2f, 1f, 0.5f, 1f, 4f, 4.5f)),
    Jazz("jazz", listOf(3f, 2f, 1f, 2f, -1.5f, -1.5f, 0f, 1.5f, 2.5f, 3f)),
    Classical("classical", listOf(3.5f, 3f, 2f, 1.5f, -1f, -1f, 0f, 2f, 2.5f, 3f)),
    Night("night", listOf(2f, 2f, 1f, 0f, 0f, 0f, 0f, -1f, -2f, -3f)),
    ;

    companion object {
        fun byId(id: String): SoundPreset? = entries.firstOrNull { it.id == id }
    }
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

    fun applyPreset(preset: SoundPreset) = update { it.copy(preset = preset.id, eq = preset.eq, enabled = true) }

    init {
        SoundEngine.settings = _settings.value
    }
}
