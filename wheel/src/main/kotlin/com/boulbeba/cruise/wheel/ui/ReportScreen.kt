package com.boulbeba.cruise.wheel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.wheel.R
import com.boulbeba.cruise.wheel.probe.Feature

/** The car integration probe (CHANGE_REQUESTS.md CR-10.3): listen, do each step in the car, save the report. */
@Composable
fun ReportScreen(vm: WheelViewModel, onBack: () -> Unit) {
    val running by vm.probe.running.collectAsStateWithLifecycle()
    val stats by vm.probe.stats.collectAsStateWithLifecycle()
    val results by vm.probe.results.collectAsStateWithLifecycle()
    val current by vm.currentStep.collectAsStateWithLifecycle()
    val colors = CruiseTheme.colors
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings_report_title), onBack = onBack) {
            CruisePill(stringResource(if (running) R.string.report_stop else R.string.report_start), onClick = vm::toggleListening, selected = !running, icon = CruiseIcons.Chip)
            CruisePill(stringResource(R.string.report_save), onClick = vm::saveReport, icon = CruiseIcons.Save, enabled = results.isNotEmpty() || running)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(stringResource(R.string.report_intro), style = CruiseTheme.typography.body, color = colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.report_stats, stats.mcuIds, stats.mcuMessages, stats.broadcasts), style = CruiseTheme.typography.meta, color = colors.textSecondary)
                MessageLine(vm)
            }
            items(Feature.entries, key = { it.name }) { f ->
                val result = results[f]
                val active = current == f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
                        .background(if (active) colors.accent.copy(alpha = 0.16f) else colors.surface).padding(horizontal = 20.dp, vertical = 14.dp),
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
                    if (active) CruisePill(stringResource(R.string.report_step_done), onClick = { vm.endStep() }, selected = true, icon = CruiseIcons.Ok)
                    else CruisePill(stringResource(R.string.report_step_start), onClick = { vm.startStep(f) }, icon = CruiseIcons.Play)
                }
            }
        }
    }
}
