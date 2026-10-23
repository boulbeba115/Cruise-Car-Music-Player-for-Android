package com.boulbeba.cruise.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme

data class SheetAction(val label: String, @DrawableRes val icon: Int, val onClick: () -> Unit)

/**
 * Centered action panel (long-press menus): big rows, one tap each. A dialog rather than a bottom sheet so it stays
 * within reach on a wide landscape screen.
 */
@Composable
fun ActionSheet(title: String, subtitle: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
                    .background(CruiseTheme.colors.surfaceElevated)
                    .clickable(enabled = false) {}
                    .padding(20.dp),
            ) {
                Text(title, style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = CruiseTheme.typography.meta, color = CruiseTheme.colors.textSecondary, maxLines = 1)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(actions, key = { it.label }) { action ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onDismiss(); action.onClick() }
                                .padding(horizontal = 8.dp),
                        ) {
                            Icon(painterResource(action.icon), contentDescription = null, tint = CruiseTheme.colors.accent, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(action.label, style = CruiseTheme.typography.body, color = CruiseTheme.colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

/** Simple text-entry dialog (new playlist, rename). */
@Composable
fun TextEntryDialog(title: String, initial: String, confirm: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius)).background(CruiseTheme.colors.surfaceElevated).padding(20.dp),
        ) {
            Text(title, style = CruiseTheme.typography.subtitle, color = CruiseTheme.colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = CruiseTheme.typography.body.copy(color = CruiseTheme.colors.textPrimary),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                com.boulbeba.cruise.core.designsystem.component.CruisePill(confirm, onClick = { if (text.isNotBlank()) { onConfirm(text.trim()); onDismiss() } }, selected = true)
            }
        }
    }
}

