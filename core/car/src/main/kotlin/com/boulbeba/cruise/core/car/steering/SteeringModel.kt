package com.boulbeba.cruise.core.car.steering

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a steering-wheel button can do (SPEC SW-2). */
enum class KeyAction {
    UnitDefault, // leave the head unit's own behaviour alone
    PlayPause,
    Next,
    Previous,
    NextFolder,
    PreviousFolder,
    ToggleShuffle,
    CycleRepeat,
    OpenCruise,
    Nothing,
}

enum class PressType { Short, Long }

/** One raw key as the head unit sent it. */
data class RawKey(val code: Int, val press: PressType, val timeMillis: Long)

/** Implemented by the playback service; the car layer never depends on the player directly. */
interface MediaCommands {
    fun playPause()
    fun next()
    fun previous()
    fun nextFolder()
    fun previousFolder()
    fun toggleShuffle()
    fun cycleRepeat()
    fun openApp()
}

/**
 * Per-button actions, persisted. Defaults follow the owner's request (2026-10-23): the phone button (24) and
 * the sound-mixer button (66) get useful music functions instead of opening vendor screens.
 */
@Singleton
class KeyMappingStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("steering_keys", Context.MODE_PRIVATE)

    private val _version = MutableStateFlow(0)
    /** Bumps on every change so screens can recompose. */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Smart call handling on the phone button (answer / hang up while a call is ringing or active). */
    var smartCallButton: Boolean
        get() = prefs.getBoolean("smart_call", true)
        set(value) { prefs.edit().putBoolean("smart_call", value).apply(); _version.value++ }

    fun actionFor(code: Int, press: PressType): KeyAction {
        val stored = prefs.getString(key(code, press), null)
        return stored?.let { runCatching { KeyAction.valueOf(it) }.getOrNull() } ?: default(code, press)
    }

    fun set(code: Int, press: PressType, action: KeyAction) {
        prefs.edit().putString(key(code, press), action.name).apply()
        _version.value++
    }

    /** Every button the user has seen or configured, so the screen can list them. */
    fun knownCodes(): Set<Int> =
        (prefs.getStringSet("known", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() } + DEFAULTS.keys.map { it.first }).toSortedSet()

    fun remember(code: Int) {
        val known = prefs.getStringSet("known", emptySet()).orEmpty()
        if (code.toString() !in known) {
            prefs.edit().putStringSet("known", known + code.toString()).apply()
            _version.value++
        }
    }

    private fun key(code: Int, press: PressType) = "action_${code}_${press.name}"

    companion object {
        const val PHONE_KEY = 24
        const val HANG_UP_KEY = 25
        const val MIXER_KEY = 66

        private val DEFAULTS: Map<Pair<Int, PressType>, KeyAction> = mapOf(
            (PHONE_KEY to PressType.Short) to KeyAction.PlayPause,
            (PHONE_KEY to PressType.Long) to KeyAction.UnitDefault,
            (MIXER_KEY to PressType.Short) to KeyAction.NextFolder,
            (MIXER_KEY to PressType.Long) to KeyAction.ToggleShuffle,
        )

        fun default(code: Int, press: PressType): KeyAction = DEFAULTS[code to press] ?: KeyAction.UnitDefault
    }
}
