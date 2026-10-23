package com.boulbeba.cruise.feature.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.designsystem.component.CruisePill
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme

/** Full-screen voice panel, drawn over the whole app while a voice command is in progress. */
@Composable
fun VoiceOverlay(ui: VoiceUi, onCancel: () -> Unit, onRetry: () -> Unit) {
    if (ui == VoiceUi.Idle) return
    val colors = CruiseTheme.colors
    Box(
        Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.92f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.widthIn(max = 760.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (ui) {
                is VoiceUi.Preparing -> {
                    Mic(listening = false)
                    Spacer(Modifier.height(24.dp))
                    Title(stringResource(if (ui.installing) R.string.voice_installing else R.string.voice_loading))
                }
                is VoiceUi.Listening -> {
                    Mic(listening = true)
                    Spacer(Modifier.height(24.dp))
                    Title(ui.partial.ifBlank { stringResource(R.string.voice_listening) })
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.voice_examples), style = CruiseTheme.typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                }
                is VoiceUi.Thinking -> {
                    Mic(listening = false)
                    Spacer(Modifier.height(24.dp))
                    Title("“${ui.heard}”")
                }
                is VoiceUi.Done -> {
                    Badge(ui.icon, colors.accent)
                    Spacer(Modifier.height(24.dp))
                    Title(stringResource(ui.message, ui.arg))
                    Spacer(Modifier.height(8.dp))
                    Text("“${ui.heard}”", style = CruiseTheme.typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                }
                is VoiceUi.Failed -> {
                    Badge(CruiseIcons.Error, colors.warning)
                    Spacer(Modifier.height(24.dp))
                    Title(stringResource(ui.message, ui.arg))
                    if (ui.heard != null) {
                        Spacer(Modifier.height(8.dp))
                        Text("“${ui.heard}”", style = CruiseTheme.typography.body, color = colors.textSecondary)
                    }
                    Spacer(Modifier.height(20.dp))
                    Actions(onRetry, onCancel)
                }
                VoiceUi.NoPack -> {
                    Badge(CruiseIcons.Usb, colors.warning)
                    Spacer(Modifier.height(24.dp))
                    Title(stringResource(R.string.voice_no_pack_title))
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.voice_no_pack_body), style = CruiseTheme.typography.body, color = colors.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    Actions(onRetry, onCancel)
                }
                VoiceUi.Idle -> Unit
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(
    text, style = CruiseTheme.typography.display, color = CruiseTheme.colors.textPrimary,
    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
)

@Composable
private fun Actions(onRetry: () -> Unit, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        CruisePill(stringResource(R.string.voice_retry), onClick = onRetry, icon = CruiseIcons.Mic, selected = true)
        CruisePill(stringResource(R.string.voice_close), onClick = onCancel, icon = CruiseIcons.Back)
    }
}

@Composable
private fun Badge(icon: Int, color: Color) {
    Box(Modifier.size(150.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = CruiseTheme.colors.onAccent, modifier = Modifier.size(80.dp))
    }
}

/** Microphone with rings: rippling outwards while listening, a slow breath while preparing or thinking. */
@Composable
private fun Mic(listening: Boolean) {
    val accent = CruiseTheme.colors.accent
    val t by rememberInfiniteTransition(label = "mic").animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (listening) 1_400 else 2_400), RepeatMode.Restart), label = "ripple",
    )
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = size.minDimension * 0.30f
            for (k in 0 until 3) {
                val p = (t + k / 3f) % 1f
                drawCircle(accent.copy(alpha = (1f - p) * (if (listening) 0.45f else 0.2f)), base + p * size.minDimension * 0.2f, style = Stroke(5f))
            }
        }
        Box(Modifier.size(132.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
            Icon(painterResource(CruiseIcons.Mic), null, tint = CruiseTheme.colors.onAccent, modifier = Modifier.size(72.dp))
        }
    }
}
