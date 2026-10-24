package com.boulbeba.cruise.wheel.service

import android.content.Context
import android.content.Intent

/**
 * The car-feature bus (CHANGE_REQUESTS.md CR-10.2): what Cruise Wheel learns from the car is re-sent as one documented
 * broadcast, so Cruise and future apps use car data without each talking to the vendor stack.
 *
 * Action [ACTION]; extras: [EXTRA_TYPE] (one of the TYPE_ constants), [EXTRA_VALUE] (text) and, for buttons,
 * [EXTRA_CODE] (the unit's key code). Receivers register for the action at runtime.
 *
 * | type | value |
 * |---|---|
 * | `key` | `short` / `long` — a wheel or panel button as the unit reported it |
 * | `trigger` | `tap`, `hold`, `double`, `combo:<code>` — what the rule engine recognised |
 * | `ignition` | `on` / `off` (unit broadcast, else screen on/off) |
 * | `lights` | `on` / `off` (headlights, from the unit's day/night signal) |
 * | `source` | the unit's current audio source number |
 * | `mcu` | `<what>:<arg1>:<arg2>` — any other MCU message the probe was told to forward |
 */
object CarBus {
    const val ACTION = "com.boulbeba.cruise.wheel.CAR_EVENT"
    const val EXTRA_TYPE = "type"
    const val EXTRA_VALUE = "value"
    const val EXTRA_CODE = "code"

    const val TYPE_KEY = "key"
    const val TYPE_TRIGGER = "trigger"
    const val TYPE_IGNITION = "ignition"
    const val TYPE_LIGHTS = "lights"
    const val TYPE_SOURCE = "source"
    const val TYPE_MCU = "mcu"

    fun send(context: Context, type: String, value: String, code: Int? = null) {
        val intent = Intent(ACTION).putExtra(EXTRA_TYPE, type).putExtra(EXTRA_VALUE, value)
        if (code != null) intent.putExtra(EXTRA_CODE, code)
        context.sendBroadcast(intent)
    }
}
