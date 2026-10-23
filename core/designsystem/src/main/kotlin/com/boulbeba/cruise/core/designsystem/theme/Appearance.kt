package com.boulbeba.cruise.core.designsystem.theme

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How much decorative animation the device can afford (SPEC VIS-10). */
enum class VisualQuality { Full, Balanced, Saver }

enum class PerformanceMode { Auto, Full, Balanced, Saver }

data class AppearanceState(
    val skinId: String = Skins.Midnight.id,
    /** Skin used while the headlights are on (SPEC SW-6); null = keep the same skin. */
    val nightSkinId: String? = null,
    val headlightsOn: Boolean = false,
    val uiScale: Float = 1f,
    val performance: PerformanceMode = PerformanceMode.Auto,
    val lowEndDevice: Boolean = false,
) {
    val activeSkin: Skin get() = Skins.byId(if (headlightsOn && nightSkinId != null) nightSkinId else skinId)

    val quality: VisualQuality
        get() = when (performance) {
            PerformanceMode.Full -> VisualQuality.Full
            PerformanceMode.Balanced -> VisualQuality.Balanced
            PerformanceMode.Saver -> VisualQuality.Saver
            PerformanceMode.Auto -> if (lowEndDevice) VisualQuality.Balanced else VisualQuality.Full
        }
}

/** Appearance preferences shared by every screen; initialised once by the Application. */
object Appearance {
    private var prefs: android.content.SharedPreferences? = null
    private val _state = MutableStateFlow(AppearanceState())
    val state: StateFlow<AppearanceState> = _state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
        prefs = p
        val am = context.getSystemService(ActivityManager::class.java)
        val ram = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }.totalMem
        val lowEnd = Runtime.getRuntime().availableProcessors() <= 4 && ram < 2_500_000_000L
        _state.value = AppearanceState(
            skinId = p.getString("skin", Skins.Midnight.id) ?: Skins.Midnight.id,
            nightSkinId = p.getString("night_skin", null),
            uiScale = p.getFloat("ui_scale", 1f),
            performance = runCatching { PerformanceMode.valueOf(p.getString("performance", "Auto")!!) }.getOrDefault(PerformanceMode.Auto),
            lowEndDevice = lowEnd,
        )
    }

    fun setSkin(id: String) = edit { copy(skinId = id) }.also { prefs?.edit()?.putString("skin", id)?.apply() }
    fun setNightSkin(id: String?) = edit { copy(nightSkinId = id) }.also { prefs?.edit()?.putString("night_skin", id)?.apply() }
    fun setUiScale(scale: Float) = edit { copy(uiScale = scale) }.also { prefs?.edit()?.putFloat("ui_scale", scale)?.apply() }
    fun setPerformance(mode: PerformanceMode) = edit { copy(performance = mode) }.also { prefs?.edit()?.putString("performance", mode.name)?.apply() }
    fun setHeadlights(on: Boolean) = edit { copy(headlightsOn = on) }

    private fun edit(f: AppearanceState.() -> AppearanceState) {
        _state.value = _state.value.f()
    }
}
