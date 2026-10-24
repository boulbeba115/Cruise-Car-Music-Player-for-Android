package com.boulbeba.cruise.feature.carlab.diagnostics

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.feature.carlab.R
import com.boulbeba.cruise.feature.carlab.fieldtest.FieldTestScreen
import com.boulbeba.cruise.feature.carlab.steering.SteeringScreen
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

private enum class DiagnosticsTab { Steering, CarTest, Report, Keys }

@Composable
fun DiagnosticsScreen(
    modifier: Modifier = Modifier,
    /** The 1.1 steering-wheel mapping screen; Cruise Wheel has its own rules and leaves it out. */
    includeSteering: Boolean = true,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val keyEvents by viewModel.keyEvents.collectAsStateWithLifecycle()
    val export by viewModel.export.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (includeSteering) DiagnosticsTab.Steering else DiagnosticsTab.CarTest) }
    val context = LocalContext.current

    val uiInfo = rememberUiInfo()
    LaunchedEffect(uiInfo) { viewModel.refresh(uiInfo) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) viewModel.exportTo(uri, context.contentResolver)
    }

    val dimens = CruiseTheme.dimens
    Column(modifier.fillMaxSize().padding(horizontal = dimens.gutter)) {
        Text(
            text = stringResource(R.string.diagnostics_title),
            style = CruiseTheme.typography.title,
            color = CruiseTheme.colors.textPrimary,
            modifier = Modifier.padding(top = dimens.gutter, bottom = 12.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            if (includeSteering) Chip(CruiseIcons.Car, stringResource(R.string.settings_tab_steering), tab == DiagnosticsTab.Steering) { tab = DiagnosticsTab.Steering }
            Chip(CruiseIcons.Ok, stringResource(R.string.diagnostics_tab_car_test), tab == DiagnosticsTab.CarTest) { tab = DiagnosticsTab.CarTest }
            Chip(CruiseIcons.Chip, stringResource(R.string.diagnostics_tab_report), tab == DiagnosticsTab.Report) { tab = DiagnosticsTab.Report }
            Chip(CruiseIcons.Keyboard, stringResource(R.string.diagnostics_tab_keys), tab == DiagnosticsTab.Keys) { tab = DiagnosticsTab.Keys }
            Spacer(Modifier.width(12.dp))
            Chip(CruiseIcons.Save, stringResource(R.string.diagnostics_save), selected = false, enabled = report != null) {
                try {
                    saveLauncher.launch(REPORT_FILE_NAME)
                } catch (_: ActivityNotFoundException) {
                    viewModel.exportToAppFolder(context.getExternalFilesDir(null))
                }
            }
        }
        ExportBanner(export, onDismiss = viewModel::dismissExportState)
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "diagnosticsTab",
            modifier = Modifier.weight(1f).padding(top = 12.dp),
        ) { current ->
            when (current) {
                DiagnosticsTab.Steering -> SteeringScreen()
                DiagnosticsTab.CarTest -> FieldTestScreen(uiInfo, onOpenKeyTester = { tab = DiagnosticsTab.Keys })
                DiagnosticsTab.Report -> ReportList(report)
                DiagnosticsTab.Keys -> KeyTester(keyEvents, onClear = viewModel::clearKeyEvents)
            }
        }
    }
}

@Composable
private fun rememberUiInfo(): List<Pair<String, String>> {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val layout = CruiseTheme.layout
    return remember(configuration, density, layout) {
        listOf(
            "App window" to "${configuration.screenWidthDp} x ${configuration.screenHeightDp} dp",
            "App density" to "${density.density} (font scale ${density.fontScale})",
            "Layout class" to layout.name,
        )
    }
}

@Composable
private fun ExportBanner(state: ExportState, onDismiss: () -> Unit) {
    val (icon, text, color) = when (state) {
        ExportState.Idle -> return
        is ExportState.Saved -> Triple(CruiseIcons.Ok, stringResource(R.string.diagnostics_saved, state.location), CruiseTheme.colors.success)
        is ExportState.Failed -> Triple(CruiseIcons.Error, stringResource(R.string.diagnostics_save_failed, state.message), CruiseTheme.colors.error)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
            .background(CruiseTheme.colors.surfaceElevated)
            .clickable(onClick = onDismiss)
            .padding(16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = CruiseTheme.typography.body, color = CruiseTheme.colors.textPrimary)
    }
}

@Composable
private fun Chip(@DrawableRes icon: Int, label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = CruiseTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = CruiseTheme.dimens.minTouchTarget)
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.accent else colors.surfaceElevated)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        val tint = when {
            selected -> colors.onAccent
            enabled -> colors.textPrimary
            else -> colors.textSecondary
        }
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = CruiseTheme.typography.label, color = tint)
    }
}

@Composable
private fun ReportList(report: DeviceReport?) {
    if (report == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(report.sections, key = { it.title }) { section ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
                    .background(CruiseTheme.colors.surface)
                    .padding(16.dp),
            ) {
                Text(section.title, style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.accent)
                Spacer(Modifier.size(8.dp))
                section.entries.forEach { (key, value) ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        Text(
                            key,
                            style = CruiseTheme.typography.meta,
                            color = CruiseTheme.colors.textSecondary,
                            modifier = Modifier.weight(0.35f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            value,
                            style = CruiseTheme.typography.meta.copy(fontFamily = FontFamily.Monospace),
                            color = CruiseTheme.colors.textPrimary,
                            modifier = Modifier.weight(0.65f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyTester(events: List<KeyLogEntry>, onClear: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val probe = MediaKeyProbe(context.applicationContext)
        probe.start()
        onDispose { probe.stop() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.diagnostics_keys_hint),
                style = CruiseTheme.typography.body,
                color = CruiseTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Chip(CruiseIcons.Delete, stringResource(R.string.diagnostics_keys_clear), selected = false, onClick = onClear)
        }
        Spacer(Modifier.size(12.dp))
        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.diagnostics_keys_empty), style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textSecondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(events.asReversed(), key = { "${it.timeMillis}-${it.keyCode}-${it.action}-${it.source}" }) { event ->
                    Text(
                        event.describe(),
                        style = CruiseTheme.typography.meta.copy(fontFamily = FontFamily.Monospace),
                        color = if (event.action == "DOWN") CruiseTheme.colors.textPrimary else CruiseTheme.colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CruiseTheme.colors.surface)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
