package com.boulbeba.cruise.feature.carlab.fieldtest

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.view.Choreographer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.feature.carlab.R
import kotlin.math.abs
import kotlin.math.sin

/** The one-visit car test (SPEC §10 Phase 0): everything needed from the unit, collected in one sitting. */
@Composable
fun FieldTestScreen(
    uiInfo: List<Pair<String, String>>,
    onOpenKeyTester: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FieldTestViewModel = hiltViewModel(),
) {
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val frameTestRunning by viewModel.frameTestRunning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionTick++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++ }
    val permissions = remember(permissionTick) { checkPermissions(context) }
    LaunchedEffect(permissions) { viewModel.permissionsChecked(permissions.summary, permissions.allGranted) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.ft_intro),
                    style = CruiseTheme.typography.body,
                    color = CruiseTheme.colors.textSecondary,
                )
            }
            items(FieldStep.entries, key = { it.name }) { step ->
                val state = steps[step] ?: StepState()
                StepCard(step, state, if (state.status == StepStatus.Running) progress else null) {
                    when (step) {
                        FieldStep.Permissions -> {
                            Action(R.string.ft_grant_storage) { requestStorage(context, permissionLauncher::launch) }
                            Action(R.string.ft_grant_location) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Action(R.string.ft_grant_notifications) { permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
                            }
                            Action(R.string.ft_battery) { requestBatteryExemption(context) }
                        }
                        FieldStep.Logger -> Action(if (viewModel.loggerActive) R.string.ft_logger_stop else R.string.ft_logger_start) {
                            viewModel.toggleLogger()
                        }
                        FieldStep.Report -> Action(R.string.ft_run) { viewModel.runReport(uiInfo) }
                        FieldStep.Sound -> {
                            Action(R.string.ft_tone_left) { ChannelTest.play(ChannelTest.Side.Left) }
                            Action(R.string.ft_tone_right) { ChannelTest.play(ChannelTest.Side.Right) }
                            Action(R.string.ft_tone_both) { ChannelTest.play(ChannelTest.Side.Both) }
                            Action(R.string.ft_answer_ok) { viewModel.recordSound("correct: left on left, right on right") }
                            Action(R.string.ft_answer_swapped) { viewModel.recordSound("SWAPPED: left tone came from the right") }
                            Action(R.string.ft_answer_one) { viewModel.recordSound("ONLY ONE SIDE played") }
                            Action(R.string.ft_answer_none) { viewModel.recordSound("NO SOUND") }
                        }
                        FieldStep.Performance -> Action(R.string.ft_run) { viewModel.startFrameTest() }
                        FieldStep.Keys -> {
                            Action(R.string.ft_open_key_tester, onClick = onOpenKeyTester)
                            Action(R.string.ft_done) { viewModel.keysDone() }
                        }
                        FieldStep.UsbScan -> Action(R.string.ft_run) { viewModel.runUsbScan() }
                        FieldStep.VendorFiles -> Action(R.string.ft_run) { viewModel.runVendorExport() }
                        FieldStep.PowerCycle -> Action(R.string.ft_done) { viewModel.powerCycleDone() }
                        FieldStep.Save -> Action(R.string.ft_save_all) { viewModel.saveAll() }
                    }
                }
            }
        }
        if (frameTestRunning) FrameTestScene(onFinished = viewModel::frameTestFinished)
    }
}

@StringRes private fun FieldStep.title(): Int = when (this) {
    FieldStep.Permissions -> R.string.ft_step_permissions
    FieldStep.Logger -> R.string.ft_step_logger
    FieldStep.Report -> R.string.ft_step_report
    FieldStep.Sound -> R.string.ft_step_sound
    FieldStep.Performance -> R.string.ft_step_performance
    FieldStep.Keys -> R.string.ft_step_keys
    FieldStep.UsbScan -> R.string.ft_step_usb
    FieldStep.VendorFiles -> R.string.ft_step_vendor
    FieldStep.PowerCycle -> R.string.ft_step_power
    FieldStep.Save -> R.string.ft_step_save
}

@StringRes private fun FieldStep.hint(): Int = when (this) {
    FieldStep.Permissions -> R.string.ft_hint_permissions
    FieldStep.Logger -> R.string.ft_hint_logger
    FieldStep.Report -> R.string.ft_hint_report
    FieldStep.Sound -> R.string.ft_hint_sound
    FieldStep.Performance -> R.string.ft_hint_performance
    FieldStep.Keys -> R.string.ft_hint_keys
    FieldStep.UsbScan -> R.string.ft_hint_usb
    FieldStep.VendorFiles -> R.string.ft_hint_vendor
    FieldStep.PowerCycle -> R.string.ft_hint_power
    FieldStep.Save -> R.string.ft_hint_save
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCard(step: FieldStep, state: StepState, progress: String?, actions: @Composable () -> Unit) {
    val colors = CruiseTheme.colors
    val statusColor = when (state.status) {
        StepStatus.Done -> colors.success
        StepStatus.Failed -> colors.error
        StepStatus.Running -> colors.accent
        StepStatus.Pending -> colors.textSecondary
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
            .background(colors.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(statusColor), contentAlignment = Alignment.Center) {
                if (state.status == StepStatus.Running) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = colors.onAccent, strokeWidth = 3.dp)
                } else {
                    Text(
                        if (state.status == StepStatus.Done) "✓" else (step.ordinal + 1).toString(),
                        style = CruiseTheme.typography.label,
                        color = colors.background,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(step.title()), style = CruiseTheme.typography.subtitle, color = colors.textPrimary)
        }
        Spacer(Modifier.size(8.dp))
        Text(stringResource(step.hint()), style = CruiseTheme.typography.body, color = colors.textSecondary)
        val detail = progress ?: state.summary
        if (detail.isNotBlank()) {
            Spacer(Modifier.size(8.dp))
            Text(detail, style = CruiseTheme.typography.meta.copy(fontFamily = FontFamily.Monospace), color = statusColor)
        }
        Spacer(Modifier.size(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            actions()
        }
    }
}

@Composable
private fun Action(@StringRes label: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = CruiseTheme.dimens.minTouchTarget)
            .clip(RoundedCornerShape(50))
            .background(CruiseTheme.colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(label), style = CruiseTheme.typography.label, color = CruiseTheme.colors.textPrimary, textAlign = TextAlign.Center)
    }
}

/** Animated scene shaped like the planned Now Playing visuals; frame intervals come from Choreographer. */
@Composable
private fun FrameTestScene(onFinished: (FrameStats) -> Unit) {
    var time by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val intervals = ArrayList<Double>()
        val start = System.nanoTime()
        var last = 0L
        val choreographer = Choreographer.getInstance()
        var stopped = false
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (stopped) return
                if (last != 0L) intervals += (frameTimeNanos - last) / 1e6
                last = frameTimeNanos
                time = (frameTimeNanos - start) / 1e9f
                if (System.nanoTime() - start > 6_000_000_000L) {
                    stopped = true
                    onFinished(FrameStats(intervals.drop(10)))
                } else {
                    choreographer.postFrameCallback(this)
                }
            }
        }
        choreographer.postFrameCallback(callback)
        onDispose { stopped = true }
    }
    val accent = CruiseTheme.colors.accent
    val background = CruiseTheme.colors.background
    Box(Modifier.fillMaxSize().background(background).clickable(enabled = false) {}) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width * (0.3f + 0.4f * sin(time * 0.7f)), size.height * 0.4f),
                    radius = size.minDimension,
                ),
            )
            val bars = 64
            val slot = size.width / bars
            for (i in 0 until bars) {
                val h = size.height * 0.5f * (0.15f + 0.85f * abs(sin(time * 3f + i * 0.35f) * sin(time * 1.3f + i * 0.11f)))
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(i * slot + slot * 0.2f, size.height * 0.85f - h),
                    size = Size(slot * 0.6f, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(slot * 0.3f),
                )
            }
        }
        Text(
            stringResource(R.string.ft_measuring),
            style = CruiseTheme.typography.title,
            color = CruiseTheme.colors.textPrimary,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
        )
    }
}

private data class PermissionStatus(val summary: String, val allGranted: Boolean)

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun checkPermissions(context: Context): PermissionStatus {
    val storage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Throws on systems without a primary external storage volume; treat that as "not granted".
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
    } else {
        granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val location = granted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    return PermissionStatus("storage=$storage location=$location battery-exempt=$battery", storage && location)
}

private fun requestStorage(context: Context, request: (Array<String>) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
    } else {
        request(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }
}

@SuppressLint("BatteryLife") // sideloaded car app: staying alive in the background is the point.
private fun requestBatteryExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        FieldLog.log("permissions", "battery optimisation settings not available on this ROM")
    }
}
