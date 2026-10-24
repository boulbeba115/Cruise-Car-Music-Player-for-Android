package com.boulbeba.cruise.feature.sound

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.audio.AudioTap
import com.boulbeba.cruise.core.audio.Focus
import com.boulbeba.cruise.core.audio.SoundEngine
import com.boulbeba.cruise.core.audio.SoundPreset
import com.boulbeba.cruise.core.audio.SoundSettings
import com.boulbeba.cruise.core.audio.SoundSettingsStore
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow

/** What the Sound screen offers (CHANGE_REQUESTS.md CR-06): one card per profile. */
enum class Profile(@StringRes val title: Int, @StringRes val description: Int, @DrawableRes val icon: Int, val preset: SoundPreset?) {
    Natural(R.string.profile_natural, R.string.profile_natural_desc, CruiseIcons.Note, null),
    Megane(R.string.profile_megane, R.string.profile_megane_desc, CruiseIcons.Car, SoundPreset.Megane),
    Bass(R.string.profile_bass, R.string.profile_bass_desc, CruiseIcons.Speaker, SoundPreset.Bass),
    Vocal(R.string.profile_vocal, R.string.profile_vocal_desc, CruiseIcons.Mic, SoundPreset.Vocal),
    Highway(R.string.profile_highway, R.string.profile_highway_desc, CruiseIcons.Road, SoundPreset.Highway),
    Night(R.string.profile_night, R.string.profile_night_desc, CruiseIcons.Night, SoundPreset.Night),
    Rock(R.string.profile_rock, R.string.profile_rock_desc, CruiseIcons.Album, SoundPreset.Rock),
    Pop(R.string.profile_pop, R.string.profile_pop_desc, CruiseIcons.Playlist, SoundPreset.Pop),
    Electronic(R.string.profile_electronic, R.string.profile_electronic_desc, CruiseIcons.NowPlaying, SoundPreset.Electronic),
    Jazz(R.string.profile_jazz, R.string.profile_jazz_desc, CruiseIcons.Headphones, SoundPreset.Jazz),
    Classical(R.string.profile_classical, R.string.profile_classical_desc, CruiseIcons.Library, SoundPreset.Classical),
    Custom(R.string.profile_custom, R.string.profile_custom_desc, CruiseIcons.Tune, null),
}

/** The profile the saved settings correspond to. */
fun SoundSettings.profile(): Profile = when {
    !enabled -> Profile.Natural
    preset == "custom" -> Profile.Custom
    else -> Profile.entries.firstOrNull { it.preset?.id == preset } ?: Profile.Custom
}

@HiltViewModel
class SoundViewModel @Inject constructor(private val store: SoundSettingsStore) : ViewModel() {
    val settings = store.settings

    fun select(p: Profile) = when (p) {
        Profile.Natural -> store.update { it.copy(enabled = false) }
        Profile.Custom -> store.update { it.copy(enabled = true, preset = "custom", eq = it.customEq) }
        else -> store.applyPreset(p.preset!!)
    }

    fun setBand(band: Int, db: Float) = store.update {
        val eq = it.eq.toMutableList().also { l -> l[band] = db }
        it.copy(enabled = true, preset = "custom", eq = eq, customEq = eq)
    }

    fun setBass(db: Float) = store.update { it.copy(bassDb = db) }
    fun setTreble(db: Float) = store.update { it.copy(trebleDb = db) }
    fun setFocus(f: Focus) = store.update { it.copy(focus = f) }
}

/** The Sound screen (SPEC §4.3, CHANGE_REQUESTS.md CR-06): profile cards, then Bass, Treble and Focus. */
@Composable
fun SoundScreen(modifier: Modifier = Modifier, viewModel: SoundViewModel = hiltViewModel()) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val active = s.profile()
    val dimens = CruiseTheme.dimens
    LazyVerticalGrid(
        GridCells.Adaptive(216.dp),
        modifier = modifier.fillMaxSize().padding(horizontal = dimens.gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = dimens.gutter)) {
                Column(Modifier.weight(1f)) {
                    Text(skinTitle(stringResource(R.string.snd_title)), style = CruiseTheme.typography.title, color = CruiseTheme.colors.textPrimary)
                    Text(stringResource(R.string.snd_active, stringResource(active.title)), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary)
                }
                CompareButton()
            }
        }
        items(Profile.entries, key = { it.name }) { p ->
            val curve = remember(p, s.customEq) { responseCurve(if (p == Profile.Custom) s.customEq else p.preset?.eq) }
            ProfileCard(p, selected = p == active, curve = curve, onClick = { viewModel.select(p) })
        }
        if (active == Profile.Custom) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "custom") {
                Card {
                    Text(stringResource(R.string.snd_custom_hint), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary)
                    Equalizer(s, onBand = viewModel::setBand)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "tune") {
            Card {
                Text(skinTitle(stringResource(R.string.snd_tune)), style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textPrimary)
                Spacer(Modifier.height(8.dp))
                ToneSlider(R.string.snd_bass, s.bassDb, enabled = s.enabled, onChange = viewModel::setBass)
                ToneSlider(R.string.snd_treble, s.trebleDb, enabled = s.enabled, onChange = viewModel::setTreble)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.snd_focus), style = CruiseTheme.typography.body, color = CruiseTheme.colors.textPrimary, modifier = Modifier.width(160.dp))
                    CruisePill(stringResource(R.string.snd_focus_off), onClick = { viewModel.setFocus(Focus.Off) }, selected = s.focus == Focus.Off, enabled = s.enabled)
                    Spacer(Modifier.width(10.dp))
                    CruisePill(stringResource(R.string.snd_focus_driver), onClick = { viewModel.setFocus(Focus.Driver) }, selected = s.focus == Focus.Driver, icon = CruiseIcons.Car, enabled = s.enabled)
                }
            }
        }
    }
}

/** dB at 41 log-spaced points from 20 Hz to 20 kHz, for a card's small curve; null = flat. */
private fun responseCurve(eq: List<Float>?): FloatArray {
    if (eq == null) return FloatArray(CURVE_POINTS)
    val params = SoundEngine.build(SoundSettings(eq = eq, loudness = false, limiter = false), 44_100, 0f, 1f, 1f, false)
    return FloatArray(CURVE_POINTS) { i -> params.responseDb(20.0 * 1000.0.pow(i / (CURVE_POINTS - 1.0))).toFloat() }
}

private const val CURVE_POINTS = 41

@Composable
private fun ProfileCard(p: Profile, selected: Boolean, curve: FloatArray, onClick: () -> Unit) {
    val colors = CruiseTheme.colors
    val shape = RoundedCornerShape(CruiseTheme.dimens.cardRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .height(152.dp)
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.20f) else colors.surface.copy(alpha = 0.85f))
            .then(if (selected) Modifier.border(2.dp, colors.accent, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(p.icon), contentDescription = null, tint = if (selected) colors.accent else colors.textSecondary, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            val line = if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.7f)
            Canvas(Modifier.weight(1f).height(36.dp)) {
                val mid = size.height / 2
                drawLine(colors.divider, Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
                val path = Path()
                curve.forEachIndexed { i, db ->
                    val x = size.width * i / (curve.size - 1)
                    val y = (mid - db / 8f * mid).coerceIn(0f, size.height)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(skinTitle(stringResource(p.title)), style = CruiseTheme.typography.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(p.description), style = CruiseTheme.typography.meta, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    Box(Modifier.fillMaxWidth().height(280.dp)) {
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
                val db = params.responseDb(f)
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
                    onValue = { onBand(i, it) },
                )
            }
        }
    }
}

@Composable
private fun VerticalBand(label: String, value: Float, onValue: (Float) -> Unit) {
    val colors = CruiseTheme.colors
    val range = SoundSettings.EQ_RANGE_DB
    fun valueAt(y: Float, height: Int) = (Math.round((0.5f - y / height) * 2 * range * 2) / 2f).coerceIn(-range, range)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(56.dp).fillMaxSize()) {
        Text(String.format(Locale.ROOT, "%+.0f", value), style = CruiseTheme.typography.meta, color = if (value != 0f) colors.accent else colors.textSecondary)
        Canvas(
            Modifier
                .weight(1f)
                .width(48.dp)
                .pointerInput(Unit) { detectVerticalDragGestures { change, _ -> onValue(valueAt(change.position.y, size.height)) } }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onValue(0f) }, onTap = { pos -> onValue(valueAt(pos.y, size.height)) }) },
        ) {
            val cx = size.width / 2
            drawRoundRect(colors.surfaceElevated, Offset(cx - 5f, 0f), Size(10f, size.height), CornerRadius(5f))
            val y = size.height * (0.5f - value / (2 * range))
            val top = minOf(y, size.height / 2)
            drawRoundRect(colors.accent, Offset(cx - 5f, top), Size(10f, kotlin.math.abs(y - size.height / 2)), CornerRadius(5f))
            drawCircle(colors.accent, radius = 18f, center = Offset(cx, y))
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

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).background(CruiseTheme.colors.surface.copy(alpha = 0.85f)).padding(20.dp)) { content() }
}

/** Bass or treble on top of the profile, −6…+6 dB in half-dB steps. */
@Composable
private fun ToneSlider(@StringRes label: Int, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val colors = CruiseTheme.colors
    val range = SoundSettings.TONE_RANGE_DB
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = CruiseTheme.typography.body, color = colors.textPrimary, modifier = Modifier.width(160.dp))
        Slider(
            value = value.coerceIn(-range, range), onValueChange = { onChange(Math.round(it * 2) / 2f) }, valueRange = -range..range, enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.surfaceElevated,
                disabledThumbColor = colors.textSecondary, disabledActiveTrackColor = colors.divider, disabledInactiveTrackColor = colors.surfaceElevated,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            if (value == 0f) "0 dB" else String.format(Locale.ROOT, "%+.1f dB", value),
            style = CruiseTheme.typography.meta, color = if (enabled) colors.textSecondary else colors.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.width(84.dp).padding(start = 12.dp),
        )
    }
}
