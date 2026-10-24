package com.boulbeba.cruise.feature.settings.general

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.component.CruiseSwitch
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.feature.settings.R

/** Library, playback and backup settings (SPEC §5.2 Settings). */
@Composable
fun GeneralScreen(modifier: Modifier = Modifier, viewModel: GeneralViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val scan by viewModel.scan.collectAsStateWithLifecycle()
    val count by viewModel.trackCount.collectAsStateWithLifecycle()
    val backup by viewModel.backupState.collectAsStateWithLifecycle()
    val colors = CruiseTheme.colors
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SettingsCard(R.string.settings_library) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_library_count, count), style = CruiseTheme.typography.body, color = colors.textPrimary)
                        Text(
                            if (scan.running) stringResource(R.string.settings_scan_progress, scan.filesRead, scan.filesSeen) else scan.message,
                            style = CruiseTheme.typography.meta, color = colors.textSecondary,
                        )
                    }
                    CruisePill(stringResource(R.string.settings_rescan), onClick = viewModel::rescan, icon = CruiseIcons.Refresh, enabled = !scan.running)
                }
                if (scan.running) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.surfaceElevated)
                }
                SwitchRow(R.string.settings_auto_scan, R.string.settings_auto_scan_hint, s.autoScan, viewModel::setAutoScan)
                Choice(
                    R.string.settings_min_duration, R.string.settings_min_duration_hint,
                    listOf(0L to "0 s", 10_000L to "10 s", 30_000L to "30 s", 60_000L to "60 s"), s.minDurationMs, viewModel::setMinDuration,
                )
            }
        }
        item {
            SettingsCard(R.string.settings_playback) {
                Choice(
                    R.string.settings_sleep_pause, R.string.settings_sleep_pause_hint,
                    listOf(0L to stringResource(R.string.settings_never), 5_000L to "5 s", 10_000L to "10 s", 30_000L to "30 s", 60_000L to "1 min"),
                    s.sleepPauseDelayMs, viewModel::setSleepDelay,
                )
                SwitchRow(R.string.settings_resume_wake, R.string.settings_resume_wake_hint, s.resumeOnWake, viewModel::setResumeOnWake)
                SwitchRow(R.string.settings_resume_boot, R.string.settings_resume_boot_hint, s.resumeOnBoot, viewModel::setResumeOnBoot)
                SwitchRow(R.string.settings_gestures, R.string.settings_gestures_hint, s.screenGestures, viewModel::setScreenGestures)
            }
        }
        item {
            SettingsCard(R.string.settings_backup) {
                Text(stringResource(R.string.settings_backup_hint), style = CruiseTheme.typography.meta, color = colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CruisePill(stringResource(R.string.settings_backup_save), onClick = { viewModel.exportBackup() }, icon = CruiseIcons.Save, enabled = backup != BackupState.Working)
                    CruisePill(stringResource(R.string.settings_backup_restore), onClick = { viewModel.restoreBackup() }, icon = CruiseIcons.Usb, enabled = backup != BackupState.Working)
                }
                val message = when (val b = backup) {
                    BackupState.Idle, BackupState.Working -> null
                    is BackupState.Saved -> stringResource(R.string.settings_backup_saved, b.path)
                    is BackupState.Restored -> stringResource(R.string.settings_backup_restored, b.summary.stats, b.summary.playlists)
                    is BackupState.Failed -> stringResource(R.string.settings_backup_failed, b.message)
                }
                if (message != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(message, style = CruiseTheme.typography.body, color = if (backup is BackupState.Failed) colors.error else colors.success)
                }
                if (backup is BackupState.Restored) {
                    Spacer(Modifier.height(10.dp))
                    CruisePill(stringResource(R.string.settings_restart), onClick = viewModel::restart, icon = CruiseIcons.Refresh, selected = true)
                }
            }
        }
        item {
            SettingsCard(R.string.settings_about) {
                val context = LocalContext.current
                val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
                Text(stringResource(R.string.settings_version, version), style = CruiseTheme.typography.body, color = colors.textPrimary)
                Text(stringResource(R.string.settings_about_body), style = CruiseTheme.typography.meta, color = colors.textSecondary)
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
internal fun SettingsCard(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).background(CruiseTheme.colors.surface.copy(alpha = 0.92f)).padding(20.dp),
    ) {
        Text(skinTitle(stringResource(title)), style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
internal fun SwitchRow(@StringRes label: Int, @StringRes hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = CruiseTheme.dimens.minTouchTarget).padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textPrimary)
            Text(stringResource(hint), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary)
        }
        Spacer(Modifier.width(16.dp))
        CruiseSwitch(checked, onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> Choice(@StringRes label: Int, @StringRes hint: Int?, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(label), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textPrimary)
        if (hint != null) Text(stringResource(hint), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { (value, text) -> CruisePill(text, onClick = { onSelect(value) }, selected = value == selected) }
        }
    }
}
