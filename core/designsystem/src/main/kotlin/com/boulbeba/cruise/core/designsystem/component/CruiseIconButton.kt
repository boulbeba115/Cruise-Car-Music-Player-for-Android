package com.boulbeba.cruise.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme

/** Round icon button that shrinks slightly while pressed (SPEC §5.4: scale 0.92, spring). */
@Composable
fun CruiseIconButton(
    @DrawableRes icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = CruiseTheme.dimens.secondaryControl,
    filled: Boolean = false,
    tint: Color = if (filled) CruiseTheme.colors.onAccent else CruiseTheme.colors.textPrimary,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    val skin = CruiseTheme.skin
    val accent = CruiseTheme.colors.accent
    val shape = if (skin.roundButtons) CircleShape else RoundedCornerShape(size * 0.22f)
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                if (filled && skin.glow) {
                    drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), Color.Transparent)), radius = this.size.minDimension * 0.75f)
                }
            }
            .clip(shape)
            .then(if (filled) Modifier.background(CruiseTheme.colors.accent) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
