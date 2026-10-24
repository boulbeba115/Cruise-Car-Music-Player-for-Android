package com.boulbeba.cruise.wheel.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.component.CruiseSwitch
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.feature.carlab.diagnostics.DiagnosticsScreen
import com.boulbeba.cruise.wheel.R
import com.boulbeba.cruise.wheel.engine.Action
import com.boulbeba.cruise.wheel.engine.Press
import com.boulbeba.cruise.wheel.engine.Rule
import com.boulbeba.cruise.wheel.engine.Trigger
import com.boulbeba.cruise.wheel.engine.WheelKeys
import com.boulbeba.cruise.wheel.probe.Feature
import com.boulbeba.cruise.wheel.service.WheelState

private enum class WheelTab { Buttons, Report, Tools }

/** Cruise Wheel (CHANGE_REQUESTS.md CR-10): the wheel's rules, the car integration report, the car-test tools. */
@Composable
fun WheelApp(viewModel: WheelViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableStateOf(WheelTab.Buttons) }
    val dimens = CruiseTheme.dimens
    Column(Modifier.fillMaxSize().background(CruiseTheme.colors.background).padding(horizontal = dimens.gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = dimens.gutter, bottom = 12.dp)) {
            Text(skinTitle(stringResource(R.string.wheel_app_name)), style = CruiseTheme.typography.title, color = CruiseTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CruisePill(stringResource(R.string.tab_buttons), onClick = { tab = WheelTab.Buttons }, selected = tab == WheelTab.Buttons, icon = CruiseIcons.Car)
                CruisePill(stringResource(R.string.tab_report), onClick = { tab = WheelTab.Report }, selected = tab == WheelTab.Report, icon = CruiseIcons.Chip)
                CruisePill(stringResource(R.string.tab_tools), onClick = { tab = WheelTab.Tools }, selected = tab == WheelTab.Tools, icon = CruiseIcons.Tune)
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                WheelTab.Buttons -> ButtonsScreen(viewModel)
                WheelTab.Report -> ReportScreen(viewModel)
                WheelTab.Tools -> DiagnosticsScreen(includeSteering = false)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).background(CruiseTheme.colors.surface).padding(20.dp)) { content() }
}

@Composable
private fun CardTitle(text: String) {
    Text(skinTitle(text), style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textPrimary)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MessageLine(viewModel: WheelViewModel) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val text = when (val m = message) {
        null -> return
        is WheelMessage.Exported -> stringResource(R.string.rules_exported, m.path)
        is WheelMessage.Imported -> stringResource(R.string.rules_imported, m.count)
        WheelMessage.NoFile -> stringResource(R.string.rules_no_file)
        is WheelMessage.ReportSaved -> stringResource(R.string.report_saved, m.path)
        WheelMessage.ReportFailed -> stringResource(R.string.report_save_failed)
    }
    Text(text, style = CruiseTheme.typography.body, color = CruiseTheme.colors.accent, modifier = Modifier.clickable { viewModel.message.value = null }.padding(vertical = 8.dp))
}

// ---- Buttons --------------------------------------------------------------------------------------------------

@Composable
private fun ButtonsScreen(vm: WheelViewModel) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val keys by WheelState.keys.collectAsStateWithLifecycle()
    val connected by WheelState.connected.collectAsStateWithLifecycle()
    val smartCalls by vm.smartCalls.collectAsStateWithLifecycle()
    var helper by remember { mutableStateOf(vm.helperEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { helper = vm.helperEnabled() }
    var adding by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = CruiseTheme.colors
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Card {
                CardTitle(stringResource(R.string.status_title))
                Text(
                    stringResource(if (!vm.isTwUnit) R.string.status_tw_no else if (connected) R.string.status_tw_yes else R.string.status_tw_link_off),
                    style = CruiseTheme.typography.body, color = if (connected) colors.success else colors.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(if (helper) R.string.status_helper_on else R.string.status_helper_off), style = CruiseTheme.typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    if (!helper) CruisePill(stringResource(R.string.status_helper_open), onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }, icon = CruiseIcons.Unlock)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = CruiseTheme.dimens.minTouchTarget)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.status_smart_calls), style = CruiseTheme.typography.body, color = colors.textPrimary)
                        Text(stringResource(R.string.status_smart_calls_hint), style = CruiseTheme.typography.meta, color = colors.textSecondary)
                    }
                    CruiseSwitch(smartCalls, vm::setSmartCalls)
                }
            }
        }
        item {
            Card {
                CardTitle(stringResource(R.string.live_title))
                if (keys.isEmpty()) Text(stringResource(R.string.live_empty), style = CruiseTheme.typography.body, color = colors.textSecondary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.asReversed().forEach { k ->
                        CruisePill("${WheelKeys.name(k.code)} · ${k.code} · ${stringResource(if (k.press == Press.Long) R.string.live_long else R.string.live_short)}", onClick = {})
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(skinTitle(stringResource(R.string.rules_title)), style = CruiseTheme.typography.subtitle, color = colors.textPrimary, modifier = Modifier.weight(1f))
                CruisePill(stringResource(R.string.rules_add), onClick = { adding = true }, icon = CruiseIcons.PlayAll, selected = true)
                Spacer(Modifier.width(10.dp))
                CruisePill(stringResource(R.string.rules_export), onClick = { vm.export() }, icon = CruiseIcons.Save)
                Spacer(Modifier.width(10.dp))
                CruisePill(stringResource(R.string.rules_import), onClick = { vm.import() }, icon = CruiseIcons.Usb)
            }
            MessageLine(vm)
        }
        if (rules.isEmpty()) item { Text(stringResource(R.string.rules_empty), style = CruiseTheme.typography.body, color = colors.textSecondary) }
        items(rules, key = { it.trigger.toString() }) { rule -> RuleRow(rule, vm) }
    }
    if (adding) AddRuleDialog(vm, onDone = { adding = false })
}

@Composable
private fun triggerText(t: Trigger): String = when (t) {
    is Trigger.Tap -> "${WheelKeys.name(t.code)} · ${stringResource(R.string.trigger_tap)}"
    is Trigger.Hold -> "${WheelKeys.name(t.code)} · ${stringResource(R.string.trigger_hold)}"
    is Trigger.DoubleTap -> "${WheelKeys.name(t.code)} · ${stringResource(R.string.trigger_double)}"
    is Trigger.Combo -> "${WheelKeys.name(t.first)} + ${WheelKeys.name(t.second)}"
}

@Composable
private fun actionText(rule: Rule, apps: List<LaunchableApp>): String {
    val base = stringResource(actionLabel(rule.action))
    return if (rule.action == Action.OpenApp) "$base: ${apps.firstOrNull { it.packageName == rule.packageName }?.label ?: rule.packageName.orEmpty()}" else base
}

private fun actionLabel(a: Action): Int = when (a) {
    Action.PlayPause -> R.string.action_PlayPause
    Action.Next -> R.string.action_Next
    Action.Previous -> R.string.action_Previous
    Action.Stop -> R.string.action_Stop
    Action.VolumeUp -> R.string.action_VolumeUp
    Action.VolumeDown -> R.string.action_VolumeDown
    Action.Mute -> R.string.action_Mute
    Action.AnswerCall -> R.string.action_AnswerCall
    Action.RejectCall -> R.string.action_RejectCall
    Action.HangUp -> R.string.action_HangUp
    Action.OpenApp -> R.string.action_OpenApp
    Action.Home -> R.string.action_Home
    Action.Back -> R.string.action_Back
    Action.VoiceAssistant -> R.string.action_VoiceAssistant
    Action.Nothing -> R.string.action_Nothing
}

@Composable
private fun RuleRow(rule: Rule, vm: WheelViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val colors = CruiseTheme.colors
    val codes = com.boulbeba.cruise.wheel.service.WheelService.codes(rule.trigger)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius * 0.7f)).background(colors.surface.copy(alpha = 0.8f)).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(triggerText(rule.trigger), style = CruiseTheme.typography.body, color = colors.textPrimary)
            Text("→ " + actionText(rule, apps), style = CruiseTheme.typography.body, color = colors.accent)
            if (codes.any { it in WheelKeys.mediaKeys }) Text(stringResource(R.string.rules_media_note), style = CruiseTheme.typography.meta, color = colors.textSecondary)
        }
        CruisePill("", onClick = { vm.remove(rule) }, icon = CruiseIcons.Delete)
    }
}

private enum class Kind { Tap, Hold, Double, Combo }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRuleDialog(vm: WheelViewModel, onDone: () -> Unit) {
    var kind by remember { mutableStateOf(Kind.Tap) }
    var first by remember { mutableStateOf<Int?>(null) }
    var second by remember { mutableStateOf<Int?>(null) }
    var action by remember { mutableStateOf<Action?>(null) }
    var app by remember { mutableStateOf<String?>(null) }
    val apps by vm.apps.collectAsStateWithLifecycle()
    val keys by WheelState.keys.collectAsStateWithLifecycle()
    // Whatever the driver presses on the wheel while the dialog is open picks the button(s).
    val seen = remember { keys.size }
    LaunchedEffect(keys) {
        val fresh = keys.drop(seen.coerceAtMost(keys.size)).lastOrNull() ?: return@LaunchedEffect
        if (kind == Kind.Combo && first != null && fresh.code != first) second = fresh.code else first = fresh.code
    }
    val colors = CruiseTheme.colors
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(32.dp).fillMaxWidth().clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).background(colors.surfaceElevated).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(skinTitle(stringResource(R.string.add_title)), style = CruiseTheme.typography.title, color = colors.textPrimary)
            LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(stringResource(R.string.add_step_trigger), style = CruiseTheme.typography.label, color = colors.accent)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Kind.Tap to R.string.trigger_tap, Kind.Hold to R.string.trigger_hold, Kind.Double to R.string.trigger_double, Kind.Combo to R.string.trigger_combo).forEach { (k, label) ->
                            CruisePill(stringResource(label), onClick = { kind = k; second = null }, selected = kind == k)
                        }
                    }
                }
                item {
                    Text(stringResource(if (kind == Kind.Combo) R.string.add_step_button_two else R.string.add_step_button), style = CruiseTheme.typography.label, color = colors.accent)
                    Text(stringResource(R.string.add_press_now), style = CruiseTheme.typography.meta, color = colors.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf(24, 66, 25, 20, 5, 6, 1, 2, 3, 41, 90, 32, 16, 18) + keys.map { it.code }).distinct().forEach { code ->
                            val picked = code == first || code == second
                            CruisePill("${WheelKeys.name(code)} · $code", onClick = {
                                if (kind == Kind.Combo && first != null && first != code && second == null) second = code else { first = code; second = null }
                            }, selected = picked)
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.add_step_action), style = CruiseTheme.typography.label, color = colors.accent)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action.entries.forEach { a -> CruisePill(stringResource(actionLabel(a)), onClick = { action = a }, selected = action == a) }
                    }
                }
                if (action == Action.OpenApp) {
                    item { Text(stringResource(R.string.add_pick_app), style = CruiseTheme.typography.label, color = colors.accent) }
                    items(apps, key = { it.packageName }) { a ->
                        Text(
                            a.label, style = CruiseTheme.typography.body, color = if (app == a.packageName) colors.accent else colors.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().clickable { app = a.packageName }.padding(vertical = 10.dp),
                        )
                    }
                }
            }
            val trigger = first?.let { f ->
                when (kind) {
                    Kind.Tap -> Trigger.Tap(f)
                    Kind.Hold -> Trigger.Hold(f)
                    Kind.Double -> Trigger.DoubleTap(f)
                    Kind.Combo -> second?.takeIf { it != f }?.let { Trigger.Combo.of(f, it) }
                }
            }
            val ready = trigger != null && action != null && (action != Action.OpenApp || app != null)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CruisePill(stringResource(R.string.add_save), onClick = {
                    if (trigger != null && action != null) vm.save(Rule(trigger, action!!, app))
                    onDone()
                }, selected = true, enabled = ready, icon = CruiseIcons.Ok)
                CruisePill(stringResource(R.string.add_cancel), onClick = onDone)
            }
        }
    }
}

// ---- Car report -----------------------------------------------------------------------------------------------

@Composable
private fun ReportScreen(vm: WheelViewModel) {
    val running by vm.probe.running.collectAsStateWithLifecycle()
    val stats by vm.probe.stats.collectAsStateWithLifecycle()
    val results by vm.probe.results.collectAsStateWithLifecycle()
    var current by remember { mutableStateOf(vm.probe.stepRunning) }
    val colors = CruiseTheme.colors
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Card {
                Text(stringResource(R.string.report_intro), style = CruiseTheme.typography.body, color = colors.textPrimary)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CruisePill(stringResource(if (running) R.string.report_stop else R.string.report_start), onClick = vm::toggleListening, selected = !running, icon = CruiseIcons.Chip)
                    CruisePill(stringResource(R.string.report_save), onClick = vm::saveReport, icon = CruiseIcons.Save, enabled = results.isNotEmpty() || running)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.report_stats, stats.mcuIds, stats.mcuMessages, stats.broadcasts), style = CruiseTheme.typography.meta, color = colors.textSecondary)
                MessageLine(vm)
            }
        }
        items(Feature.entries, key = { it.name }) { f ->
            val result = results[f]
            val active = current == f
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius * 0.7f))
                    .background(if (active) colors.accent.copy(alpha = 0.16f) else colors.surface.copy(alpha = 0.8f)).padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.title, style = CruiseTheme.typography.body, color = colors.textPrimary)
                    Text(f.instruction, style = CruiseTheme.typography.meta, color = colors.textSecondary)
                    if (result != null) {
                        Text(
                            if (result.detected) stringResource(R.string.report_found) + ": " + (result.mcu + result.broadcasts + result.settings + result.properties).take(3).joinToString("  ·  ")
                            else stringResource(R.string.report_nothing),
                            style = CruiseTheme.typography.meta, color = if (result.detected) colors.success else colors.error, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (active) CruisePill(stringResource(R.string.report_step_done), onClick = { vm.endStep(); current = null }, selected = true, icon = CruiseIcons.Ok)
                else CruisePill(stringResource(R.string.report_step_start), onClick = { if (current != null) vm.endStep(); vm.beginStep(f); current = f }, icon = CruiseIcons.Play)
            }
        }
    }
}
