package com.boulbeba.cruise.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.NavStyle

data class NavItem(val key: String, @DrawableRes val icon: Int, val label: String)

/**
 * Vertical rail: the four destinations as one centred group (CHANGE_REQUESTS.md CR-07.0), which fits a 400 dp-tall
 * screen as well as a 1200 dp one.
 */
@Composable
fun CruiseNavRail(
    items: List<NavItem>,
    selectedKey: String,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showLabels = CruiseTheme.layout.railShowsLabels
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(CruiseTheme.dimens.railWidth)
            .background(CruiseTheme.colors.surface.copy(alpha = 0.82f))
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        items.forEach { item ->
            NavEntry(
                item = item,
                selected = item.key == selectedKey,
                showLabel = showLabels,
                onClick = { onSelect(item) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
            )
        }
    }
}

/** Bottom bar for compact portrait windows (phones). Labels are dropped when items get narrow. */
@Composable
fun CruiseBottomBar(
    items: List<NavItem>,
    selectedKey: String,
    onSelect: (NavItem) -> Unit,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CruiseTheme.dimens.bottomBarHeight)
            .background(CruiseTheme.colors.surface.copy(alpha = 0.9f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            NavEntry(
                item = item,
                selected = item.key == selectedKey,
                showLabel = showLabels,
                onClick = { onSelect(item) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NavEntry(
    item: NavItem,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CruiseTheme.colors
    val skin = CruiseTheme.skin
    val filled = skin.nav == NavStyle.Pill || skin.nav == NavStyle.Block
    val indicator by animateColorAsState(if (selected && filled) colors.accent else Color.Transparent, label = "navIndicator")
    val tint by animateColorAsState(
        when {
            selected && filled -> colors.onAccent
            selected -> colors.accent
            else -> colors.textSecondary
        },
        label = "navTint",
    )
    val labelColor by animateColorAsState(if (selected) colors.textPrimary else colors.textSecondary, label = "navLabel")
    val pillWidth by animateDpAsState(if (selected) 64.dp else 48.dp, label = "navPill")
    val shape = if (skin.nav == NavStyle.Block) RoundedCornerShape(6.dp) else RoundedCornerShape(50)
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = if (skin.nav == NavStyle.Block) 56.dp else pillWidth, height = 40.dp)
                .drawBehind {
                    if (selected && skin.nav == NavStyle.Glow) {
                        drawCircle(Brush.radialGradient(listOf(colors.accent.copy(alpha = 0.45f), Color.Transparent)), radius = size.minDimension * 0.95f)
                    }
                }
                .clip(shape)
                .background(indicator),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(item.icon),
                contentDescription = if (showLabel) null else item.label,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (skin.uppercaseTitles) item.label.uppercase() else item.label,
                style = CruiseTheme.typography.meta,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (skin.nav == NavStyle.Underline) {
            val lineWidth by animateDpAsState(if (selected) 28.dp else 0.dp, label = "navLine")
            Spacer(Modifier.height(4.dp))
            Box(Modifier.size(width = lineWidth, height = 3.dp).clip(RoundedCornerShape(50)).background(colors.accent))
        }
    }
}
