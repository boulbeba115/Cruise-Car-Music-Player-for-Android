package com.boulbeba.cruise.feature.settings.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.skin.SkinBackground
import com.boulbeba.cruise.core.designsystem.theme.Appearance
import com.boulbeba.cruise.core.designsystem.theme.BarStyle
import com.boulbeba.cruise.core.designsystem.theme.CoverStyle
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.PerformanceMode
import com.boulbeba.cruise.core.designsystem.theme.Skin
import com.boulbeba.cruise.core.designsystem.theme.Skins
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import com.boulbeba.cruise.feature.settings.R
import com.boulbeba.cruise.feature.settings.general.Choice
import com.boulbeba.cruise.feature.settings.general.SettingsCard
import kotlin.math.abs
import kotlin.math.sin

/** Skins, night skin with the headlights, UI size and animation level (SPEC §5.1, UI-6, VIS-10). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(modifier: Modifier = Modifier) {
    val state by Appearance.state.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SettingsCard(R.string.appearance_skin) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Skins.all.forEach { skin -> SkinCard(skin, selected = skin.id == state.skinId, onClick = { Appearance.setSkin(skin.id) }) }
                }
            }
        }
        item {
            SettingsCard(R.string.appearance_night) {
                Choice(
                    R.string.appearance_night_skin, R.string.appearance_night_hint,
                    listOf<Pair<String?, String>>(null to stringResource(R.string.appearance_night_same)) + Skins.all.map { it.id to stringResource(it.name) },
                    state.nightSkinId, Appearance::setNightSkin,
                )
                if (state.headlightsOn) Text(stringResource(R.string.appearance_headlights_on), style = CruiseTheme.typography.meta, color = CruiseTheme.colors.accent)
            }
        }
        item {
            SettingsCard(R.string.appearance_display) {
                Choice(
                    R.string.appearance_scale, R.string.appearance_scale_hint,
                    listOf(0.9f, 1f, 1.1f, 1.2f, 1.3f).map { it to "${(it * 100).toInt()} %" },
                    listOf(0.9f, 1f, 1.1f, 1.2f, 1.3f).minBy { abs(it - state.uiScale) }, Appearance::setUiScale,
                )
                Choice(
                    R.string.appearance_performance, R.string.appearance_performance_hint,
                    listOf(
                        PerformanceMode.Auto to stringResource(R.string.appearance_perf_auto),
                        PerformanceMode.Full to stringResource(R.string.appearance_perf_full),
                        PerformanceMode.Balanced to stringResource(R.string.appearance_perf_balanced),
                        PerformanceMode.Saver to stringResource(R.string.appearance_perf_saver),
                    ),
                    state.performance, Appearance::setPerformance,
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/** A miniature of the skin's Now Playing, drawn with the skin itself: backdrop, typeface, cover style, bar. */
@Composable
private fun SkinCard(skin: Skin, selected: Boolean, onClick: () -> Unit) {
    val outer = CruiseTheme.colors
    Column(
        Modifier
            .width(260.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(if (selected) 3.dp else 1.dp, if (selected) outer.accent else outer.divider, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
    ) {
        CruiseTheme(skin = skin, quality = VisualQuality.Saver) {
            SkinCardContent(skin, selected)
        }
    }
}

@Composable
private fun SkinCardContent(skin: Skin, selected: Boolean) {
    Column {
            val c = CruiseTheme.colors
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                SkinBackground(Modifier.fillMaxSize())
                Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    MiniCover(skin)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(skinTitle("Nightcall"), style = CruiseTheme.typography.subtitle, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Kavinsky", style = CruiseTheme.typography.meta, color = c.textSecondary, maxLines = 1)
                        Spacer(Modifier.height(10.dp))
                        MiniBar(skin.bar)
                    }
                }
                if (selected) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                        Icon(painterResource(CruiseIcons.Ok), null, tint = c.onAccent, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(skinTitle(stringResource(skin.name)), style = CruiseTheme.typography.label, color = c.textPrimary)
                Text(stringResource(skin.tagline), style = CruiseTheme.typography.meta, color = c.textSecondary, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            }
    }
}

@Composable
private fun MiniCover(skin: Skin) {
    val c = CruiseTheme.colors
    val radius = CruiseTheme.dimens.coverRadius * 0.4f
    Canvas(Modifier.size(84.dp)) {
        val art = Brush.linearGradient(listOf(c.accent, c.accent2))
        when (skin.cover) {
            CoverStyle.Vinyl -> {
                // Picture disc: the art covers the record, grooves on top, a small label.
                drawCircle(art, size.minDimension * 0.46f)
                for (k in 0 until 8) drawCircle(Color.Black.copy(alpha = 0.18f), size.minDimension / 2 * (0.42f + k * 0.065f), style = Stroke(1f))
                drawCircle(c.accent, size.minDimension * 0.08f)
                drawLine(Color(0xFFD9DCE3), Offset(size.width * 0.95f, size.height * 0.05f), Offset(size.width * 0.62f, size.height * 0.30f), 4f)
            }
            CoverStyle.Gauge -> {
                drawArc(c.surfaceElevated, 135f, 270f, false, style = Stroke(8f))
                drawArc(Brush.sweepGradient(listOf(c.accent2, c.accent)), 135f, 170f, false, style = Stroke(8f))
                drawCircle(art, size.minDimension * 0.30f)
                val a = Math.toRadians(135.0 + 200.0)
                drawLine(c.accent, center, Offset(center.x + kotlin.math.cos(a).toFloat() * size.minDimension * 0.44f, center.y + sin(a).toFloat() * size.minDimension * 0.44f), 4f)
                drawCircle(Color(0xFF15181D), size.minDimension * 0.07f)
            }
            CoverStyle.Neon -> {
                drawRoundRect(Color(0xFF0C1020), cornerRadius = CornerRadius(radius.toPx()))
                for ((w, a) in listOf(10f to 0.2f, 4f to 1f)) drawRoundRect(c.accent2.copy(alpha = a), Offset(6f, 6f), Size(size.width - 12f, size.height - 12f), CornerRadius(radius.toPx()), style = Stroke(w))
                drawRoundRect(art, Offset(16f, 16f), Size(size.width - 32f, size.height - 32f), CornerRadius(radius.toPx() * 0.6f), alpha = 0.9f)
            }
            CoverStyle.Minimal -> drawRoundRect(art, Offset(12f, 12f), Size(size.width - 24f, size.height - 24f), CornerRadius(radius.toPx() * 0.6f))
            CoverStyle.Sleeve -> {
                val w = size.width
                drawCircle(Color(0xFF0E0E12), w * 0.34f, Offset(w * 0.62f, center.y))
                drawCircle(art, w * 0.12f, Offset(w * 0.62f, center.y))
                drawRoundRect(art, Offset(w * 0.04f, w * 0.17f), Size(w * 0.66f, w * 0.66f), CornerRadius(w * 0.04f))
                drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(w * 0.04f, w * 0.17f), Size(w * 0.66f, w * 0.66f), CornerRadius(w * 0.04f), style = Stroke(2f))
            }
            CoverStyle.Liquid -> {
                val p = Path()
                for (i in 0..40) {
                    val a = i / 40f * 6.283f
                    val r = size.minDimension * (0.40f + 0.05f * sin(a * 3f) + 0.03f * kotlin.math.cos(a * 5f))
                    val pt = Offset(center.x + kotlin.math.cos(a) * r, center.y + sin(a) * r)
                    if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
                }
                p.close()
                drawPath(p, art)
                drawPath(p, Brush.sweepGradient(listOf(c.accent, c.accent2, c.accent)), style = Stroke(3f))
            }
            CoverStyle.Sticker -> rotate(-6f) {
                drawRoundRect(Color.Black.copy(alpha = 0.12f), Offset(10f, 14f), Size(size.width - 16f, size.height - 16f), CornerRadius(radius.toPx()))
                drawRoundRect(Color.White, Offset(6f, 6f), Size(size.width - 16f, size.height - 16f), CornerRadius(radius.toPx()))
                drawRoundRect(art, Offset(12f, 12f), Size(size.width - 28f, size.height - 28f), CornerRadius(radius.toPx() * 0.7f))
            }
            CoverStyle.Cassette -> {
                val w = size.width
                val h = w * 0.64f
                val top = (size.height - h) / 2
                drawRoundRect(Color(0xFF2E3946), Offset(0f, top), Size(w, h), CornerRadius(w * 0.05f))
                drawRoundRect(Color(0xFFF1F4F6), Offset(w * 0.07f, top + h * 0.09f), Size(w * 0.86f, h * 0.56f), CornerRadius(w * 0.03f))
                drawRoundRect(art, Offset(w * 0.09f, top + h * 0.12f), Size(h * 0.48f, h * 0.48f), CornerRadius(w * 0.02f))
                drawRoundRect(Color(0xFF0D0B09), Offset(w * 0.30f, top + h * 0.36f), Size(w * 0.50f, h * 0.22f), CornerRadius(h * 0.08f))
                drawCircle(Color(0xFFEDE6DA), w * 0.045f, Offset(w * 0.42f, top + h * 0.47f))
                drawCircle(Color(0xFFEDE6DA), w * 0.045f, Offset(w * 0.68f, top + h * 0.47f))
            }
        }
    }
}

@Composable
private fun MiniBar(style: BarStyle) {
    val c = CruiseTheme.colors
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val mid = size.height / 2
        when (style) {
            BarStyle.Waveform -> {
                val n = 24
                val slot = size.width / n
                for (i in 0 until n) {
                    val h = size.height * (0.3f + 0.7f * abs(sin(i * 0.7f)))
                    drawRoundRect(if (i < n * 0.4f) c.accent else c.textSecondary.copy(alpha = 0.3f), Offset(i * slot + slot * 0.2f, mid - h / 2), Size(slot * 0.6f, h), CornerRadius(slot * 0.3f))
                }
            }
            BarStyle.Wavy -> {
                var prev = Offset(0f, mid)
                for (s in 1..40) {
                    val x = size.width * 0.45f * s / 40
                    val p = Offset(x, mid + sin(s * 0.9f) * size.height * 0.3f)
                    drawLine(c.accent, prev, p, 4f)
                    prev = p
                }
                drawLine(c.textSecondary.copy(alpha = 0.35f), Offset(size.width * 0.45f, mid), Offset(size.width, mid), 3f)
                drawCircle(c.textPrimary, 7f, prev)
            }
            BarStyle.NeonTube -> {
                drawRoundRect(c.textSecondary.copy(alpha = 0.3f), Offset(0f, mid - 5f), Size(size.width, 10f), CornerRadius(5f), style = Stroke(1.5f))
                for ((w, a) in listOf(12f to 0.18f, 6f to 0.5f, 2.5f to 1f)) drawLine(c.accent.copy(alpha = a), Offset(4f, mid), Offset(size.width * 0.45f, mid), w)
            }
            BarStyle.Tape -> {
                val r = size.height * 0.45f
                drawCircle(c.textPrimary.copy(alpha = 0.8f), r, Offset(r, mid), style = Stroke(3f))
                drawCircle(c.textPrimary.copy(alpha = 0.8f), r, Offset(size.width - r, mid), style = Stroke(3f))
                drawLine(c.accent, Offset(r, mid + r), Offset(size.width * 0.45f, mid + r), 4f)
                drawLine(c.textSecondary.copy(alpha = 0.5f), Offset(size.width * 0.45f, mid + r), Offset(size.width - r, mid + r), 4f)
                drawRect(c.accent2, Offset(size.width * 0.45f - 4f, mid + r - 8f), Size(8f, 10f))
            }
            BarStyle.LedSegments -> {
                val n = 18
                val w = size.width / n - 3f
                for (i in 0 until n) drawRoundRect(if (i < 8) (if (i > 15) c.accent2 else c.accent) else c.surfaceElevated, Offset(i * (w + 3f), 3f), Size(w, size.height - 6f), CornerRadius(2f))
            }
            BarStyle.Groove -> {
                drawLine(c.textSecondary.copy(alpha = 0.25f), Offset(0f, mid), Offset(size.width, mid), 1f)
                drawLine(c.accent, Offset(0f, mid), Offset(size.width * 0.4f, mid), 5f)
                drawCircle(c.accent, 7f, Offset(size.width * 0.4f, mid))
            }
            BarStyle.Hairline -> {
                drawLine(c.textSecondary.copy(alpha = 0.35f), Offset(0f, mid), Offset(size.width, mid), 2f)
                drawLine(c.textPrimary, Offset(0f, mid), Offset(size.width * 0.4f, mid), 3f)
                drawCircle(c.textPrimary, 6f, Offset(size.width * 0.4f, mid))
            }
        }
    }
}
