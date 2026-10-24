package com.boulbeba.cruise.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

/**
 * A neon sign's switch-on when [key] changes (a new track): two short dips, then steady. Two flashes once per track
 * stays inside the photosensitivity limit (no more than 3 flashes in any second, SPEC §5.4).
 */
@Composable
internal fun rememberNeonFlicker(key: Any?): State<Float> {
    val lit = remember { Animatable(1f) }
    LaunchedEffect(key) {
        lit.snapTo(0.15f)
        delay(90)
        lit.snapTo(1f)
        delay(140)
        lit.snapTo(0.4f)
        delay(70)
        lit.animateTo(1f, tween(220))
    }
    return lit.asState()
}

/**
 * Text as a lit neon sign (CR-07.4): a wide soft glow, then the tube itself in a whiter tint of [color] with a tight
 * glow. Two layers of Text with shadows only — no blur effects — so it costs little on the unit.
 */
@Composable
internal fun NeonSignText(text: String, color: Color, style: TextStyle, lit: Float, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Box(modifier.alpha(0.35f + 0.65f * lit)) {
        Text(
            text, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            style = style.copy(color = color.copy(alpha = 0.85f), shadow = Shadow(color, blurRadius = 34f)),
        )
        Text(
            text, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            style = style.copy(color = lerp(color, Color.White, 0.72f), shadow = Shadow(color, blurRadius = 12f)),
        )
    }
}
