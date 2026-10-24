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

/** What a rule does (CHANGE_REQUESTS.md CR-10.1). */
enum class Action {
    PlayPause, Next, Previous, Stop,
    VolumeUp, VolumeDown, Mute,
    AnswerCall, RejectCall, HangUp,
    /** Opens [Rule.packageName]. */
    OpenApp,
    Home, Back, VoiceAssistant,
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

/** The buttons a TW unit reports (docs/unit-findings.md, key table of com.tw.service). */
object WheelKeys {
    const val PHONE = 24
    const val HANG_UP = 25
    const val MIXER = 66

    /** Keys the unit already turns into standard media keys for the playing app: rules on them come on top. */
    val mediaKeys = setOf(5, 6, 19, 20, 21, 22, 23)

    private val names = mapOf(
        1 to "Volume +", 2 to "Volume −", 3 to "Mute", 5 to "Next", 6 to "Previous", 16 to "Home", 18 to "Back",
        19 to "Next (2)", 20 to "Play / pause", 21 to "Previous (2)", 22 to "Fast forward", 23 to "Rewind",
        24 to "Phone", 25 to "Hang up", 32 to "Navigation", 41 to "Music", 66 to "Sound mixer", 90 to "Voice",
    )

    fun name(code: Int): String = names[code] ?: "Button $code"

    /** The vendor screen the unit opens for a button, closed again by the helper when a rule handles the button. */
    fun vendorScreen(code: Int): String? = when (code) {
        PHONE -> "com.tw.bt"
        MIXER -> "com.tw.eq"
        else -> null
    }
}

/** The rules, persisted; [rules] updates the service and the screens. */
@Singleton
class RuleStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("wheel_rules", Context.MODE_PRIVATE)
    private val _rules = MutableStateFlow(load())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    /** Phone answers calls when one is ringing and hangs up an active one, whatever its rules say. */
    var smartCalls: Boolean
        get() = prefs.getBoolean("smart_calls", true)
        set(value) { prefs.edit().putBoolean("smart_calls", value).apply() }

    private fun load(): List<Rule> = prefs.getString("rules", null)?.let(Rule::listFromJson) ?: DEFAULTS

    fun set(rules: List<Rule>) {
        _rules.value = rules
        prefs.edit().putString("rules", Rule.listToJson(rules)).apply()
    }

    /** Adds [rule], replacing any rule with the same trigger. */
    fun put(rule: Rule) = set(_rules.value.filter { it.trigger != rule.trigger } + rule)

    fun remove(rule: Rule) = set(_rules.value - rule)

    fun ruleFor(trigger: Trigger): Rule? = _rules.value.firstOrNull { it.trigger == trigger }

    companion object {
        /** The owner's 1.1 setup: the phone button plays/pauses, the mixer button skips. */
        val DEFAULTS = listOf(
            Rule(Trigger.Tap(WheelKeys.PHONE), Action.PlayPause),
            Rule(Trigger.Tap(WheelKeys.MIXER), Action.Next),
        )
    }
}
