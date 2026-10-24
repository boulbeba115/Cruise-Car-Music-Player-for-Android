package com.boulbeba.cruise.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
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
