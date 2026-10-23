package com.boulbeba.cruise.feature.sound

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.audio.AudioTap
import com.boulbeba.cruise.core.audio.Focus
import com.boulbeba.cruise.core.audio.ReplayGainMode
import com.boulbeba.cruise.core.audio.SoundEngine
import com.boulbeba.cruise.core.audio.SoundPreset
import com.boulbeba.cruise.core.audio.SoundSettings
import com.boulbeba.cruise.core.audio.SoundSettingsStore
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.component.CruiseSwitch
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow

@HiltViewModel
class SoundViewModel @Inject constructor(val store: SoundSettingsStore) : ViewModel() {
    val settings = store.settings
    fun update(f: (SoundSettings) -> SoundSettings) = store.update { f(it).copy(preset = if (f(it).eq != it.eq || f(it).bassDb != it.bassDb) "custom" else f(it).preset) }
    fun preset(p: SoundPreset) = store.applyPreset(p)
}

/** The sound engine's controls (SPEC §4.3, DSP-3…16). */
@Composable
fun SoundScreen(modifier: Modifier = Modifier, viewModel: SoundViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val dimens = CruiseTheme.dimens
    LazyColumn(modifier.fillMaxSize().padding(horizontal = dimens.gutter), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = dimens.gutter)) {
                Column(Modifier.weight(1f)) {
                    Text(skinTitle(stringResource(R.string.snd_title)), style = CruiseTheme.typography.title, color = CruiseTheme.colors.textPrimary)
                    Text(
                        if (s.enabled) stringResource(R.string.snd_headroom, String.format(Locale.ROOT, "%.1f", SoundEngine.headroomDb())) else stringResource(R.string.snd_pure),
                        style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary,
                    )
                }
                CompareButton()
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.snd_effects), style = CruiseTheme.typography.label, color = CruiseTheme.colors.textPrimary)
                Spacer(Modifier.width(8.dp))
                CruiseSwitch(s.enabled) { on -> viewModel.update { it.copy(enabled = on) } }
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoundPreset.entries.forEach { p -> CruisePill(stringResource(p.label()), onClick = { viewModel.preset(p) }, selected = s.preset == p.id) }
            }
        }
        item { Card { Equalizer(s) { band, db -> viewModel.update { it.copy(eq = it.eq.toMutableList().also { l -> l[band] = db }) } } } }
        item {
            Card {
                LabeledSlider(R.string.snd_preamp, s.preampDb, -12f, 6f, "dB") { v -> viewModel.update { it.copy(preampDb = v) } }
                LabeledSlider(R.string.snd_bass, s.bassDb, 0f, 10f, "dB") { v -> viewModel.update { it.copy(bassDb = v) } }
                LabeledSlider(R.string.snd_width, s.width * 100, 0f, 180f, "%") { v -> viewModel.update { it.copy(width = v / 100f) } }
                LabeledSlider(R.string.snd_balance, s.balance * 100, -100f, 100f, "") { v -> viewModel.update { it.copy(balance = v / 100f) } }
                SwitchRow(R.string.snd_loudness, R.string.snd_loudness_hint, s.loudness) { on -> viewModel.update { it.copy(loudness = on) } }
                SwitchRow(R.string.snd_limiter, R.string.snd_limiter_hint, s.limiter) { on -> viewModel.update { it.copy(limiter = on) } }
            }
        }
        item {
            Card {
                Text(stringResource(R.string.snd_focus), style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textPrimary)
                Text(stringResource(R.string.snd_focus_hint), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                SeatPicker(s.focus) { f -> viewModel.update { it.copy(focus = f) } }
            }
        }
        item {
            Card {
                Text(stringResource(R.string.snd_rg), style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textPrimary)
                Text(stringResource(R.string.snd_rg_hint), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReplayGainMode.entries.forEach { m -> CruisePill(stringResource(m.label()), onClick = { viewModel.update { it.copy(replayGain = m) } }, selected = s.replayGain == m) }
                }
                LabeledSlider(R.string.snd_rg_preamp, s.rgPreampDb, -6f, 6f, "dB") { v -> viewModel.update { it.copy(rgPreampDb = v) } }
            }
        }
    }
}

/** Ten big vertical sliders over the live spectrum and the resulting response curve. Double-tap a band to reset it. */
@Composable
private fun Equalizer(s: SoundSettings, onBand: (Int, Float) -> Unit) {
    val colors = CruiseTheme.colors
    val quality = CruiseTheme.quality
    val bands = remember { FloatArray(32) }
    var tick by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(quality) {
        if (quality == VisualQuality.Saver) return@LaunchedEffect
        var frame = 0
        while (true) withFrameNanos {
            if (frame++ % 2 == 0) { AudioTap.spectrum(bands); tick = it.toFloat() }
        }
    }
    val params = remember(s) { SoundEngine.build(s, 44_100, 0f, 1f, SoundEngine.volume, false) }
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 24.dp)) {
            tick
            val w = size.width
            val h = size.height
            val slot = w / bands.size
            bands.forEachIndexed { i, v ->
                val bh = h * v * 0.9f
                drawRoundRect(colors.accent2.copy(alpha = 0.18f), Offset(i * slot + slot * 0.15f, h - bh), Size(slot * 0.7f, bh), CornerRadius(slot * 0.2f))
            }
            drawLine(colors.divider, Offset(0f, h / 2), Offset(w, h / 2), 2f)
            val path = Path()
            for (x in 0..120) {
                val f = 20.0 * (20_000.0 / 20.0).pow(x / 120.0)
                val db = if (s.enabled) params.responseDb(f) else 0.0
                val px = (log10(f / 20.0) / 3.0 * w).toFloat()
                val py = (h / 2 - db / 15.0 * (h / 2)).toFloat().coerceIn(0f, h)
                if (x == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            drawPath(path, Brush.horizontalGradient(listOf(colors.accent, colors.accent2)), style = Stroke(width = 5f))
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SoundSettings.EQ_BANDS.forEachIndexed { i, f ->
                VerticalBand(
                    label = if (f >= 1000) "${(f / 1000).toInt()}k" else f.toInt().toString(),
                    value = s.eq.getOrElse(i) { 0f },
                    enabled = s.enabled,
                    onValue = { onBand(i, it) },
                )
            }
        }
    }
}

@Composable
private fun VerticalBand(label: String, value: Float, enabled: Boolean, onValue: (Float) -> Unit) {
    val colors = CruiseTheme.colors
    val range = SoundSettings.EQ_RANGE_DB
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp).fillMaxSize()) {
        Text(String.format(Locale.ROOT, "%+.0f", value), style = CruiseTheme.typography.meta, color = if (value != 0f) colors.accent else colors.textSecondary)
        Canvas(
            Modifier
                .weight(1f)
                .width(48.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { change, _ ->
                        val v = (0.5f - change.position.y / size.height) * 2 * range
                        onValue((Math.round(v * 2) / 2f).coerceIn(-range, range))
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onDoubleTap = { onValue(0f) }, onTap = { pos ->
                        val v = (0.5f - pos.y / size.height) * 2 * range
                        onValue((Math.round(v * 2) / 2f).coerceIn(-range, range))
                    })
                },
        ) {
            val cx = size.width / 2
            drawRoundRect(colors.surfaceElevated, Offset(cx - 5f, 0f), Size(10f, size.height), CornerRadius(5f))
            val y = size.height * (0.5f - value / (2 * range))
            val top = minOf(y, size.height / 2)
            drawRoundRect(colors.accent.copy(alpha = if (enabled) 1f else 0.3f), Offset(cx - 5f, top), Size(10f, kotlin.math.abs(y - size.height / 2)), CornerRadius(5f))
            drawCircle(if (enabled) colors.accent else colors.textSecondary, radius = 18f, center = Offset(cx, y))
            drawCircle(colors.background, radius = 7f, center = Offset(cx, y))
        }
        Text(label, style = CruiseTheme.typography.meta, color = colors.textSecondary)
    }
}

/** Hold to hear the music without any processing, release to compare (SPEC DSP-16). */
@Composable
private fun CompareButton() {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(CruiseTheme.colors.surfaceElevated)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    SoundEngine.compareBypass = true
                    do { val event = awaitPointerEvent() } while (event.changes.any { it.pressed })
                    SoundEngine.compareBypass = false
                }
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) { Text(stringResource(R.string.snd_compare), style = CruiseTheme.typography.label, color = CruiseTheme.colors.textPrimary) }
}

/** Top-down car outline: tap the seat to focus the stereo image on it (left-hand drive). */
@Composable
private fun SeatPicker(focus: Focus, onFocus: (Focus) -> Unit) {
    val colors = CruiseTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(120.dp).height(150.dp).pointerInput(Unit) {
            detectTapGestures { p -> onFocus(if (p.y > size.height * 0.75f) Focus.Off else if (p.x < size.width / 2) Focus.Driver else Focus.Passenger) }
        }) {
            drawRoundRect(colors.surfaceElevated, Offset(size.width * 0.1f, 0f), Size(size.width * 0.8f, size.height), CornerRadius(40f))
            fun seat(x: Float, y: Float, on: Boolean) = drawRoundRect(if (on) colors.accent else colors.divider, Offset(x, y), Size(size.width * 0.28f, size.height * 0.24f), CornerRadius(12f))
            seat(size.width * 0.18f, size.height * 0.3f, focus == Focus.Driver)
            seat(size.width * 0.54f, size.height * 0.3f, focus == Focus.Passenger)
            drawRoundRect(if (focus == Focus.Off) colors.accent else colors.divider, Offset(size.width * 0.18f, size.height * 0.66f), Size(size.width * 0.64f, size.height * 0.22f), CornerRadius(12f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CruisePill(stringResource(R.string.snd_focus_driver), onClick = { onFocus(Focus.Driver) }, selected = focus == Focus.Driver)
            CruisePill(stringResource(R.string.snd_focus_passenger), onClick = { onFocus(Focus.Passenger) }, selected = focus == Focus.Passenger)
            CruisePill(stringResource(R.string.snd_focus_off), onClick = { onFocus(Focus.Off) }, selected = focus == Focus.Off)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).background(CruiseTheme.colors.surface.copy(alpha = 0.85f)).padding(16.dp)) { content() }
}

@Composable
private fun LabeledSlider(@StringRes label: Int, value: Float, min: Float, max: Float, unit: String, onChange: (Float) -> Unit) {
    val colors = CruiseTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = CruiseTheme.typography.body, color = colors.textPrimary, modifier = Modifier.width(190.dp))
        Slider(
            value = value.coerceIn(min, max), onValueChange = { onChange(Math.round(it * 2) / 2f) }, valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.surfaceElevated),
            modifier = Modifier.weight(1f),
        )
        Text(String.format(Locale.ROOT, "%+.1f %s", value, unit), style = CruiseTheme.typography.meta, color = colors.textSecondary, modifier = Modifier.width(84.dp).padding(start = 8.dp))
    }
}

@Composable
private fun SwitchRow(@StringRes label: Int, @StringRes hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textPrimary)
            Text(stringResource(hint), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary)
        }
        CruiseSwitch(checked, onChange)
    }
}

@StringRes private fun SoundPreset.label() = when (this) {
    SoundPreset.Flat -> R.string.preset_flat
    SoundPreset.Megane -> R.string.preset_megane
    SoundPreset.Highway -> R.string.preset_highway
    SoundPreset.Bass -> R.string.preset_bass
    SoundPreset.Vocal -> R.string.preset_vocal
    SoundPreset.Rock -> R.string.preset_rock
    SoundPreset.Pop -> R.string.preset_pop
    SoundPreset.Electronic -> R.string.preset_electronic
    SoundPreset.Jazz -> R.string.preset_jazz
    SoundPreset.Classical -> R.string.preset_classical
    SoundPreset.Night -> R.string.preset_night
}

@StringRes private fun ReplayGainMode.label() = when (this) {
    ReplayGainMode.Off -> R.string.rg_off
    ReplayGainMode.Track -> R.string.rg_track
    ReplayGainMode.Album -> R.string.rg_album
    ReplayGainMode.Auto -> R.string.rg_auto
}

