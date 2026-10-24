package com.boulbeba.cruise.wheel.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** What a rule does (CHANGE_REQUESTS.md CR-10.1, CR-18). Names are stored in the mapping file: never rename one. */
enum class Action {
    PlayPause, Next, Previous, FastForward, Rewind, Stop,
    VolumeUp, VolumeDown, Mute,
    /** Answers a ringing call, ends an active one. */
    AnswerCall,
    RejectCall, HangUp,
    /** Opens the music app the media actions go to (Cruise when installed). */
    OpenMusic,
    /** Opens [Rule.packageName]. */
    OpenApp,
    Home, Back, Recents, Notifications, VoiceAssistant,
    /** Swallows the button: it does nothing (the unit's own action is still closed by the helper). */
    Nothing,
}

data class Rule(val trigger: Trigger, val action: Action, val packageName: String? = null) {

    fun toJson(): JSONObject = JSONObject().apply {
        when (trigger) {
            is Trigger.Tap -> put("on", "tap").put("key", trigger.code)
            is Trigger.Hold -> put("on", "hold").put("key", trigger.code)
            is Trigger.DoubleTap -> put("on", "double").put("key", trigger.code)
            is Trigger.Combo -> put("on", "combo").put("key", trigger.first).put("key2", trigger.second)
        }
        put("action", action.name)
        packageName?.let { put("package", it) }
    }

    companion object {
        fun fromJson(o: JSONObject): Rule? = runCatching {
            val key = o.getInt("key")
            val trigger = when (o.getString("on")) {
                "tap" -> Trigger.Tap(key)
                "hold" -> Trigger.Hold(key)
                "double" -> Trigger.DoubleTap(key)
                "combo" -> Trigger.Combo.of(key, o.getInt("key2"))
                else -> return null
            }
            Rule(trigger, Action.valueOf(o.getString("action")), o.optString("package").ifBlank { null })
        }.getOrNull()

        fun listToJson(rules: List<Rule>): String = JSONArray(rules.map { it.toJson() }).toString(2)

        fun listFromJson(s: String): List<Rule> = runCatching {
            val a = JSONArray(s)
            List(a.length()) { a.getJSONObject(it) }.mapNotNull(::fromJson)
        }.getOrDefault(emptyList())
    }
}

/** The buttons a rule uses. */
val Trigger.codes: List<Int>
    get() = when (this) {
        is Trigger.Tap -> listOf(code)
        is Trigger.Hold -> listOf(code)
        is Trigger.DoubleTap -> listOf(code)
        is Trigger.Combo -> listOf(first, second)
    }

/** How far Cruise Wheel can take a button away from the unit (docs/unit-findings.md, com.tw.service key table). */
enum class Takeover {
    /** The unit's own action is switched off while a rule uses the button. */
    Full,

    /** The unit opens one of its screens; the accessibility helper closes it again right away. */
    Helper,

    /** The unit keeps doing its own thing on a press; rules come on top (hold and double press are free). */
    Shared,
}

/** The buttons a TW unit reports (docs/unit-findings.md, key table of com.tw.service). */
object WheelKeys {
    const val PHONE = 24
    const val HANG_UP = 25
    const val MIXER = 66

    /** Shown before the driver has pressed anything: the two buttons the unit wastes on screens. */
    val defaults = listOf(PHONE, MIXER)

    /** Every code the unit is known to send, in the order the "add a button" list shows them. */
    val known = listOf(24, 25, 66, 20, 19, 5, 21, 6, 22, 23, 1, 2, 3, 41, 90, 32, 16, 18)

    fun takeover(code: Int): Takeover = when (code) {
        PHONE, HANG_UP -> Takeover.Full
        MIXER -> Takeover.Helper
        else -> Takeover.Shared
    }

    /** The vendor screen the unit opens for a button, closed again by the helper when a rule handles the button. */
    fun vendorScreen(code: Int): String? = when (code) {
        PHONE -> "com.tw.bt"
        MIXER -> "com.tw.eq"
        else -> null
    }
}

/** The rules and the wheel settings, persisted; the flows update the service and the screens. */
@Singleton
class RuleStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("wheel_rules", Context.MODE_PRIVATE)
    private val _rules = MutableStateFlow(load())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    private val _buttons = MutableStateFlow(loadButtons())
    /** The buttons on the main screen, in the order they were added. */
    val buttons: StateFlow<List<Int>> = _buttons.asStateFlow()

    /** Phone answers calls when one is ringing and hangs up an active one, whatever its rules say. */
    var smartCalls: Boolean
        get() = prefs.getBoolean("smart_calls", true)
        set(value) { prefs.edit().putBoolean("smart_calls", value).apply() }

    /** The driver agreed to keep apps running at ignition-off (KillGuard); re-applied whenever the service starts. */
    var keepAlive: Boolean
        get() = prefs.getBoolean("keep_alive", false)
        set(value) { prefs.edit().putBoolean("keep_alive", value).apply() }

    /** Package the media actions go to; "" sends them to whichever app is playing; null means Cruise when installed. */
    var musicTarget: String?
        get() = prefs.getString("music_target", null)
        set(value) { prefs.edit().putString("music_target", value).apply() }

    private fun load(): List<Rule> = prefs.getString("rules", null)?.let(Rule::listFromJson) ?: DEFAULTS

    private fun loadButtons(): List<Int> {
        val saved = prefs.getString("buttons", null)?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: WheelKeys.defaults
        return (saved + _rules.value.flatMap { it.trigger.codes }).distinct()
    }

    fun set(rules: List<Rule>) {
        _rules.value = rules
        prefs.edit().putString("rules", Rule.listToJson(rules)).apply()
        val missing = rules.flatMap { it.trigger.codes }.filter { it !in _buttons.value }
        if (missing.isNotEmpty()) setButtons(_buttons.value + missing.distinct())
    }

    /** Adds [rule], replacing any rule with the same trigger. */
    fun put(rule: Rule) = set(_rules.value.filter { it.trigger != rule.trigger } + rule)

    fun remove(rule: Rule) = set(_rules.value - rule)

    /** Removes the rule on [trigger]: the button does what the unit does by default. */
    fun clear(trigger: Trigger) = set(_rules.value.filter { it.trigger != trigger })

    fun ruleFor(trigger: Trigger): Rule? = _rules.value.firstOrNull { it.trigger == trigger }

    fun addButton(code: Int) {
        if (code !in _buttons.value) setButtons(_buttons.value + code)
    }

    /** Removes the button from the main screen together with its rules. */
    fun forgetButton(code: Int) {
        set(_rules.value.filter { code !in it.trigger.codes })
        setButtons(_buttons.value - code)
    }

    private fun setButtons(codes: List<Int>) {
        _buttons.value = codes
        prefs.edit().putString("buttons", codes.joinToString(",")).apply()
    }

    companion object {
        /** The owner's 1.1 setup: the phone button plays/pauses, the mixer button skips. */
        val DEFAULTS = listOf(
            Rule(Trigger.Tap(WheelKeys.PHONE), Action.PlayPause),
            Rule(Trigger.Tap(WheelKeys.MIXER), Action.Next),
        )
    }
}
