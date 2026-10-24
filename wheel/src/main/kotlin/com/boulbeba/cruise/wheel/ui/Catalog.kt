package com.boulbeba.cruise.wheel.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.wheel.R
import com.boulbeba.cruise.wheel.engine.Action
import com.boulbeba.cruise.wheel.engine.Trigger

/** Names, icons and the unit's own behaviour for the wheel buttons (docs/unit-findings.md key table). */
object Buttons {
    private val names = mapOf(
        1 to R.string.key_1, 2 to R.string.key_2, 3 to R.string.key_3, 5 to R.string.key_5, 6 to R.string.key_6,
        16 to R.string.key_16, 18 to R.string.key_18, 19 to R.string.key_19, 20 to R.string.key_20, 21 to R.string.key_21,
        22 to R.string.key_22, 23 to R.string.key_23, 24 to R.string.key_24, 25 to R.string.key_25, 32 to R.string.key_32,
        41 to R.string.key_41, 66 to R.string.key_66, 90 to R.string.key_90,
    )

    @Composable
    fun name(code: Int): String = names[code]?.let { stringResource(it) } ?: stringResource(R.string.key_unknown, code)

    @DrawableRes
    fun icon(code: Int): Int = when (code) {
        1 -> R.drawable.ic_volume_up
        2 -> R.drawable.ic_volume_down
        3 -> R.drawable.ic_volume_off
        5, 19 -> CruiseIcons.Next
        6, 21 -> CruiseIcons.Previous
        16 -> R.drawable.ic_home
        18 -> CruiseIcons.Back
        20 -> CruiseIcons.Play
        22 -> R.drawable.ic_fast_forward
        23 -> R.drawable.ic_fast_rewind
        24 -> R.drawable.ic_call
        25 -> R.drawable.ic_call_end
        32 -> R.drawable.ic_navigation
        41 -> CruiseIcons.Library
        66 -> CruiseIcons.Equalizer
        90 -> CruiseIcons.Mic
        else -> R.drawable.ic_button
    }

    /** Completes "Normally this button …". */
    @StringRes
    fun does(code: Int): Int = when (code) {
        1, 2 -> R.string.does_volume
        3 -> R.string.does_mute
        5, 19 -> R.string.does_next
        6, 21 -> R.string.does_previous
        16 -> R.string.does_home
        18 -> R.string.does_back
        20 -> R.string.does_play
        22 -> R.string.does_ff
        23 -> R.string.does_rew
        24 -> R.string.does_phone
        25 -> R.string.does_hang_up
        32 -> R.string.does_navigation
        41 -> R.string.does_music
        66 -> R.string.does_mixer
        90 -> R.string.does_voice
        else -> R.string.does_unknown
    }
}

enum class Category(@StringRes val label: Int) {
    Music(R.string.cat_music), Volume(R.string.cat_volume), Calls(R.string.cat_calls),
    Apps(R.string.cat_apps), Screen(R.string.cat_screen), Other(R.string.cat_other),
}

/** How each action is shown in the picker. [needsHelper]: only the accessibility helper can do it. */
data class ActionInfo(val action: Action, val category: Category, @StringRes val label: Int, @DrawableRes val icon: Int, val needsHelper: Boolean = false)

object Actions {
    val all = listOf(
        ActionInfo(Action.PlayPause, Category.Music, R.string.action_PlayPause, CruiseIcons.Play),
        ActionInfo(Action.Next, Category.Music, R.string.action_Next, CruiseIcons.Next),
        ActionInfo(Action.Previous, Category.Music, R.string.action_Previous, CruiseIcons.Previous),
        ActionInfo(Action.FastForward, Category.Music, R.string.action_FastForward, R.drawable.ic_fast_forward),
        ActionInfo(Action.Rewind, Category.Music, R.string.action_Rewind, R.drawable.ic_fast_rewind),
        ActionInfo(Action.Stop, Category.Music, R.string.action_Stop, R.drawable.ic_stop),
        ActionInfo(Action.VolumeUp, Category.Volume, R.string.action_VolumeUp, R.drawable.ic_volume_up),
        ActionInfo(Action.VolumeDown, Category.Volume, R.string.action_VolumeDown, R.drawable.ic_volume_down),
        ActionInfo(Action.Mute, Category.Volume, R.string.action_Mute, R.drawable.ic_volume_off),
        ActionInfo(Action.AnswerCall, Category.Calls, R.string.action_AnswerCall, R.drawable.ic_call),
        ActionInfo(Action.RejectCall, Category.Calls, R.string.action_RejectCall, R.drawable.ic_call_end),
        ActionInfo(Action.OpenMusic, Category.Apps, R.string.action_OpenMusic, CruiseIcons.Library),
        ActionInfo(Action.OpenApp, Category.Apps, R.string.action_OpenApp, R.drawable.ic_apps),
        ActionInfo(Action.Home, Category.Screen, R.string.action_Home, R.drawable.ic_home),
        ActionInfo(Action.Back, Category.Screen, R.string.action_Back, CruiseIcons.Back, needsHelper = true),
        ActionInfo(Action.Recents, Category.Screen, R.string.action_Recents, R.drawable.ic_recents, needsHelper = true),
        ActionInfo(Action.Notifications, Category.Screen, R.string.action_Notifications, R.drawable.ic_notifications, needsHelper = true),
        ActionInfo(Action.VoiceAssistant, Category.Screen, R.string.action_VoiceAssistant, CruiseIcons.Mic),
        ActionInfo(Action.Nothing, Category.Other, R.string.action_Nothing, R.drawable.ic_block),
    )

    // Rules imported from an older mapping file may use actions the picker no longer offers.
    private val hidden = listOf(ActionInfo(Action.HangUp, Category.Calls, R.string.action_HangUp, R.drawable.ic_call_end))

    fun info(action: Action): ActionInfo = (all + hidden).first { it.action == action }
}

@StringRes
fun triggerLabel(t: Trigger): Int = when (t) {
    is Trigger.Tap -> R.string.trigger_tap
    is Trigger.Hold -> R.string.trigger_hold
    is Trigger.DoubleTap -> R.string.trigger_double
    is Trigger.Combo -> R.string.trigger_combo
}
