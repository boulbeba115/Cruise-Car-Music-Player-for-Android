package com.boulbeba.cruise.wheel.ui

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.boulbeba.cruise.core.designsystem.component.CruiseIconButton
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Screen header: back arrow (on sub-screens), title, and actions on the right. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    val dimens = CruiseTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(top = 8.dp, bottom = 8.dp),
    ) {
        if (onBack != null) {
            CruiseIconButton(CruiseIcons.Back, contentDescription = null, onClick = onBack, size = dimens.minTouchTarget)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            skinTitle(title), style = CruiseTheme.typography.title, color = CruiseTheme.colors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
    }
}

/** A coloured dot and a word: is the wheel being read right now. */
@Composable
fun StatusChip(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, style = CruiseTheme.typography.label, color = color, maxLines = 1)
    }
}

/** Icon in a soft round badge. */
@Composable
fun IconBadge(@DrawableRes icon: Int, tint: Color = CruiseTheme.colors.accent, size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

/** A rounded surface; [highlight] lights it up in the accent (a wheel press just reached it). */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CruiseTheme.colors
    val shape = RoundedCornerShape(CruiseTheme.dimens.cardRadius)
    val bg by animateColorAsState(if (highlight) colors.accent.copy(alpha = 0.18f) else colors.surface, label = "panelBg")
    val edge by animateColorAsState(if (highlight) colors.accent else Color.Transparent, label = "panelEdge")
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(2.dp, edge, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        skinTitle(text), style = CruiseTheme.typography.label, color = CruiseTheme.colors.accent,
        modifier = modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}

/** One setting: icon, title, explanation, and a control on the right. */
@Composable
fun SettingRow(
    @DrawableRes icon: Int,
    title: String,
    body: String?,
    onClick: (() -> Unit)? = null,
    iconTint: Color = CruiseTheme.colors.accent,
    trailing: @Composable () -> Unit = {},
) {
    val colors = CruiseTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
            .background(colors.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 80.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        IconBadge(icon, tint = iconTint, size = 44.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = CruiseTheme.typography.body, color = colors.textPrimary)
            if (body != null) Text(body, style = CruiseTheme.typography.meta, color = colors.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** Content column with a comfortable reading width on wide screens. */
@Composable
fun Readable(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 880.dp).fillMaxWidth()) { content() }
    }
}

/** An app's launcher icon, loaded off the main thread. */
@Composable
fun AppIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val image by produceState<ImageBitmap?>(null, packageName, px) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(px, px, Bitmap.Config.ARGB_8888).asImageBitmap() }.getOrNull()
        }
    }
    val img = image
    if (img != null) Image(img, contentDescription = null, modifier = Modifier.size(size))
    else Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(CruiseTheme.colors.surfaceElevated))
}
