package com.boulbeba.cruise.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme

/** Rounded action button with optional icon; `selected` uses the accent (filters, tabs, primary actions). */
@Composable
fun CruisePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = CruiseTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.6f, Spring.StiffnessMedium), label = "pillScale")
    val background by animateColorAsState(if (selected) colors.accent else colors.surfaceElevated, label = "pillBg")
    val content = when {
        selected -> colors.onAccent
        enabled -> colors.textPrimary
        else -> colors.textSecondary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = CruiseTheme.dimens.minTouchTarget)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(if (CruiseTheme.skin.roundButtons) RoundedCornerShape(50) else RoundedCornerShape(6.dp))
            .background(background)
            .clickable(interaction, ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(if (CruiseTheme.skin.uppercaseTitles) label.uppercase() else label, style = CruiseTheme.typography.label, color = content)
    }
}
