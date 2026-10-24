package com.boulbeba.cruise.feature.carlab.steering

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.car.steering.KeyAction
import com.boulbeba.cruise.core.car.steering.PressType
import com.boulbeba.cruise.core.car.steering.VendorScreenSuppressor
import com.boulbeba.cruise.core.car.tw.TwDefaultApps
import com.boulbeba.cruise.core.car.tw.TwProtocol
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.feature.carlab.R

/** Steering-wheel buttons (SPEC SW-1…SW-4): see what each button sends and give it a job. */
@Composable
fun SteeringScreen(modifier: Modifier = Modifier, viewModel: SteeringViewModel = hiltViewModel()) {
    val keys by viewModel.headUnit.lastKeys.collectAsStateWithLifecycle()
    val source by viewModel.headUnit.source.collectAsStateWithLifecycle()
    val callState by viewModel.calls.state.collectAsStateWithLifecycle()
    val version by viewModel.mapping.version.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var helperEnabled by remember { mutableStateOf(VendorScreenSuppressor.isHelperEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { helperEnabled = VendorScreenSuppressor.isHelperEnabled(context) }
    var expanded by rememberSaveable { mutableIntStateOf(-1) }
    val codes = remember(version, keys) { viewModel.mapping.knownCodes().toList() }

    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Card {
                Text(stringResource(R.string.sw_status_title), style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.accent)
                Spacer(Modifier.height(8.dp))
                StatusLine(
                    R.string.sw_status_unit,
                    when {
                        !viewModel.headUnit.isTwUnit -> stringResource(R.string.sw_unit_generic)
                        viewModel.headUnit.isConnected -> stringResource(R.string.sw_unit_tw_connected, source?.toString() ?: "?")
                        else -> stringResource(R.string.sw_unit_tw_not_connected)
                    },
                )
                StatusLine(R.string.sw_status_call, callState.name)
                StatusLine(R.string.sw_status_helper, stringResource(if (helperEnabled) R.string.sw_on else R.string.sw_off))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sw_smart_call), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.mapping.smartCallButton.also { version }, onCheckedChange = viewModel::setSmartCall)
                }
                Text(stringResource(R.string.sw_smart_call_hint), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sw_own_phone), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.mapping.ownPhoneButton.also { version }, onCheckedChange = viewModel::setOwnPhoneButton)
                }
                Text(stringResource(R.string.sw_own_phone_hint), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary)
                if (viewModel.headUnit.isTwUnit) {
                    Spacer(Modifier.height(12.dp))
                    var musicApp by remember { mutableStateOf(TwDefaultApps.musicApp()) }
                    var musicResult by remember { mutableStateOf<Boolean?>(null) }
                    StatusLine(R.string.sw_status_music_key, musicApp ?: stringResource(R.string.sw_music_key_stock))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (musicApp != context.packageName) {
                            CruisePill(stringResource(R.string.sw_music_key_set), onClick = {
                                musicResult = TwDefaultApps.makeCruiseMusicApp(context)
                                musicApp = TwDefaultApps.musicApp()
                            }, icon = CruiseIcons.Library)
                        } else {
                            CruisePill(stringResource(R.string.sw_music_key_restore), onClick = {
                                TwDefaultApps.restore(context)
                                musicApp = TwDefaultApps.musicApp()
                            }, icon = CruiseIcons.Back)
                        }
                        TwDefaultApps.steeringLearningIntent(context)?.let { learn ->
                            CruisePill(stringResource(R.string.sw_learn_buttons), onClick = { runCatching { context.startActivity(learn) } }, icon = CruiseIcons.Car)
                        }
                    }
                    if (musicResult == false) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.sw_music_key_failed), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.warning)
                    }
                }
                if (!helperEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.sw_helper_hint), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    CruisePill(stringResource(R.string.sw_helper_enable), onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }, icon = CruiseIcons.Car)
                }
            }
        }
        item {
            Card {
                Text(stringResource(R.string.sw_press_title), style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.accent)
                Spacer(Modifier.height(6.dp))
                val last = keys.lastOrNull()
                Text(
                    if (last == null) stringResource(R.string.sw_press_waiting)
                    else stringResource(R.string.sw_press_last, keyName(last.code), last.code, pressLabel(last.press)),
                    style = CruiseTheme.typography.title,
                    color = CruiseTheme.colors.textPrimary,
                )
            }
        }
        items(codes, key = { it }) { code ->
            ButtonCard(
                code = code,
                shortAction = viewModel.mapping.actionFor(code, PressType.Short).also { version },
                longAction = viewModel.mapping.actionFor(code, PressType.Long).also { version },
                expanded = expanded == code,
                onToggle = { expanded = if (expanded == code) -1 else code },
                onSet = { press, action -> viewModel.set(code, press, action) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ButtonCard(
    code: Int,
    shortAction: KeyAction,
    longAction: KeyAction,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSet: (PressType, KeyAction) -> Unit,
) {
    val fixed = code in TwProtocol.standardMediaCodes
    Card(Modifier.clickable(enabled = !fixed, onClick = onToggle)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${keyName(code)}  ·  $code", style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textPrimary)
                Text(
                    if (fixed) stringResource(R.string.sw_fixed_media_key)
                    else stringResource(R.string.sw_actions_summary, actionLabel(shortAction), actionLabel(longAction)),
                    style = CruiseTheme.typography.meta,
                    color = CruiseTheme.colors.textSecondary,
                )
            }
        }
        AnimatedVisibility(expanded && !fixed) {
            Column {
                for (press in PressType.entries) {
                    Spacer(Modifier.height(12.dp))
                    Text(pressLabel(press), style = CruiseTheme.typography.label, color = CruiseTheme.colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val current = if (press == PressType.Short) shortAction else longAction
                        for (action in KeyAction.entries) {
                            CruisePill(actionLabel(action), onClick = { onSet(press, action) }, selected = action == current)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
            .background(CruiseTheme.colors.surface)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun StatusLine(@StringRes label: Int, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(stringResource(label), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textSecondary, modifier = Modifier.width(200.dp))
        Text(value, style = CruiseTheme.typography.body, color = CruiseTheme.colors.textPrimary)
    }
}

@Composable
private fun keyName(code: Int): String = TwProtocol.keyNames[code] ?: stringResource(R.string.sw_button_unknown)

@Composable
private fun pressLabel(press: PressType): String =
    stringResource(if (press == PressType.Short) R.string.sw_press_short else R.string.sw_press_long)

@Composable
private fun actionLabel(action: KeyAction): String = stringResource(
    when (action) {
        KeyAction.UnitDefault -> R.string.sw_action_unit
        KeyAction.PlayPause -> R.string.sw_action_play_pause
        KeyAction.Next -> R.string.sw_action_next
        KeyAction.Previous -> R.string.sw_action_previous
        KeyAction.NextFolder -> R.string.sw_action_next_folder
        KeyAction.PreviousFolder -> R.string.sw_action_previous_folder
        KeyAction.ToggleShuffle -> R.string.sw_action_shuffle
        KeyAction.CycleRepeat -> R.string.sw_action_repeat
        KeyAction.OpenCruise -> R.string.sw_action_open
        KeyAction.Nothing -> R.string.sw_action_nothing
    },
)
