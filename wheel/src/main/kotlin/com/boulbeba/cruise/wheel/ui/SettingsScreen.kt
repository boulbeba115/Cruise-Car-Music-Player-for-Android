package com.boulbeba.cruise.wheel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.component.CruiseSwitch
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.car.tw.TwProtocol
import com.boulbeba.cruise.wheel.R
import com.boulbeba.cruise.wheel.service.WheelState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: WheelViewModel, onBack: () -> Unit, onPickMusicApp: () -> Unit, onReport: () -> Unit, onKeyLog: () -> Unit, onTools: () -> Unit) {
    val setup by vm.setup.collectAsStateWithLifecycle()
    val smartCalls by vm.smartCalls.collectAsStateWithLifecycle()
    val target by vm.musicTarget.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = CruiseTheme.colors
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings_title), onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.weight(1f)) {
            item {
                Readable {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(stringResource(R.string.settings_always_on))
                        if (setup.keepAliveApplies) {
                            SettingRow(
                                R.drawable.ic_wheel, stringResource(R.string.settings_keep_title),
                                stringResource(if (setup.keepAliveRefused) R.string.setup_keep_refused else R.string.settings_keep_body),
                                onClick = { vm.setKeepAlive(!setup.keepAliveOn) },
                            ) { CruiseSwitch(setup.keepAliveOn) { vm.setKeepAlive(it) } }
                        }
                        SettingRow(
                            CruiseIcons.Unlock, stringResource(R.string.settings_helper_title),
                            stringResource(if (setup.helperOn) R.string.settings_helper_on else R.string.settings_helper_off),
                            iconTint = if (setup.helperOn) colors.success else colors.warning,
                            onClick = { openAccessibility(context) },
                        ) { CruisePill(stringResource(R.string.settings_helper_open), onClick = { openAccessibility(context) }) }
                        SettingRow(CruiseIcons.Refresh, stringResource(R.string.settings_autostart_title), stringResource(R.string.settings_autostart_body)) {
                            Icon(painterResource(CruiseIcons.Ok), contentDescription = null, tint = colors.success, modifier = Modifier.size(28.dp))
                        }

                        SectionHeader(stringResource(R.string.settings_music))
                        Panel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconBadge(CruiseIcons.Library, size = 44.dp)
                                    Spacer(Modifier.width(16.dp))
                                    Text(stringResource(R.string.settings_music_title), style = CruiseTheme.typography.body, color = colors.textPrimary)
                                }
                                Spacer(Modifier.height(14.dp))
                                // Without Cruise, "Cruise" falls back to whichever app plays.
                                val cruise = vm.cruiseInstalled
                                val other = target?.takeIf { it.isNotEmpty() }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CruisePill(
                                        stringResource(if (cruise) R.string.settings_music_cruise else R.string.settings_music_cruise_missing),
                                        onClick = { vm.setMusicTarget(null) }, selected = target == null && cruise, enabled = cruise, icon = CruiseIcons.Library,
                                    )
                                    CruisePill(
                                        stringResource(R.string.settings_music_any), onClick = { vm.setMusicTarget("") },
                                        selected = target == "" || (target == null && !cruise), icon = CruiseIcons.NowPlaying,
                                    )
                                    CruisePill(other?.let(vm::appLabel) ?: stringResource(R.string.settings_music_other), onClick = onPickMusicApp, selected = other != null, icon = R.drawable.ic_apps)
                                }
                            }
                        }

                        SectionHeader(stringResource(R.string.settings_calls))
                        SettingRow(R.drawable.ic_call, stringResource(R.string.status_smart_calls), stringResource(R.string.status_smart_calls_hint), onClick = { vm.setSmartCalls(!smartCalls) }) {
                            CruiseSwitch(smartCalls, vm::setSmartCalls)
                        }

                        SectionHeader(stringResource(R.string.settings_backup))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CruisePill(stringResource(R.string.rules_export), onClick = { vm.export() }, icon = CruiseIcons.Save)
                            CruisePill(stringResource(R.string.rules_import), onClick = { vm.import() }, icon = CruiseIcons.Usb)
                        }
                        MessageLine(vm)

                        SectionHeader(stringResource(R.string.settings_advanced))
                        SettingRow(CruiseIcons.Chip, stringResource(R.string.settings_report_title), stringResource(R.string.settings_report_body), onClick = onReport) { Chevron() }
                        SettingRow(CruiseIcons.Keyboard, stringResource(R.string.settings_log_title), stringResource(R.string.settings_log_body), onClick = onKeyLog) { Chevron() }
                        SettingRow(CruiseIcons.Tune, stringResource(R.string.settings_tools_title), stringResource(R.string.settings_tools_body), onClick = onTools) { Chevron() }

                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.settings_version, vm.version), style = CruiseTheme.typography.meta, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(painterResource(CruiseIcons.Chevron), contentDescription = null, tint = CruiseTheme.colors.textSecondary, modifier = Modifier.size(24.dp))
}

@Composable
fun MessageLine(vm: WheelViewModel) {
    val message by vm.message.collectAsStateWithLifecycle()
    val text = when (val m = message) {
        null -> return
        is WheelMessage.Exported -> stringResource(R.string.rules_exported, m.path)
        is WheelMessage.Imported -> stringResource(R.string.rules_imported, m.count)
        WheelMessage.NoFile -> stringResource(R.string.rules_no_file)
        is WheelMessage.ReportSaved -> stringResource(R.string.report_saved, m.path)
        WheelMessage.ReportFailed -> stringResource(R.string.report_save_failed)
    }
    Text(
        text, style = CruiseTheme.typography.body, color = CruiseTheme.colors.accent,
        modifier = Modifier.clickable { vm.message.value = null }.padding(vertical = 8.dp),
    )
}

/** Every MCU message as it arrives, newest first: what the unit really sends for each press and hold. */
@Composable
fun KeyLogScreen(onBack: () -> Unit) {
    val raw by WheelState.raw.collectAsStateWithLifecycle()
    val colors = CruiseTheme.colors
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings_log_title), onBack = onBack)
        if (raw.isEmpty()) {
            Text(stringResource(R.string.log_empty), style = CruiseTheme.typography.body, color = colors.textSecondary)
            return@Column
        }
        val newest = raw.last().timeMs
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(raw.asReversed(), key = { "${it.timeMs}-${it.what}-${it.arg1}-${it.arg2}" }) { m ->
                val isKey = m.what == TwProtocol.KEY || m.what == TwProtocol.KEY_LONG
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(
                        "%5d  arg1=%-3d arg2=%-4d".format(m.what, m.arg1, m.arg2),
                        style = CruiseTheme.typography.body.copy(fontFamily = FontFamily.Monospace), color = colors.textPrimary,
                    )
                    Spacer(Modifier.width(16.dp))
                    if (isKey) {
                        Text(
                            Buttons.name(m.arg2) + " · " + stringResource(if (m.what == TwProtocol.KEY_LONG || m.arg1 == 2) R.string.log_long else R.string.log_short),
                            style = CruiseTheme.typography.meta, color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    Text("−%.1f s".format((newest - m.timeMs) / 1000f), style = CruiseTheme.typography.meta, color = colors.textSecondary)
                }
            }
        }
    }
}
