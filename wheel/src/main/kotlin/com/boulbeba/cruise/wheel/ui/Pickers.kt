package com.boulbeba.cruise.wheel.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.wheel.R
import com.boulbeba.cruise.wheel.engine.Action
import com.boulbeba.cruise.wheel.engine.Trigger
import com.boulbeba.cruise.wheel.engine.codes

/** Every action a button can get, by category, as large tiles; the current one is filled. */
@Composable
fun ActionPicker(trigger: Trigger, vm: WheelViewModel, onBack: () -> Unit, onPickApp: () -> Unit) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val current = rules.firstOrNull { it.trigger == trigger }
    val code = trigger.codes.first()
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.pick_title, Buttons.name(code), stringResource(triggerLabel(trigger))), onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 172.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.weight(1f),
        ) {
            for (category in Category.entries) {
                val shown = Actions.all.filter { it.category == category && (it.action != Action.VoiceAssistant || vm.voiceAvailable) }
                item(span = { GridItemSpan(maxLineSpan) }, key = "h-$category") {
                    SectionHeader(stringResource(category.label), Modifier.padding(top = 4.dp))
                }
                items(shown, key = { it.action.name }) { info ->
                    val selected = current?.action == info.action
                    val sub = when {
                        info.action == Action.OpenApp && selected && current?.packageName != null -> vm.appLabel(current.packageName)
                        info.needsHelper && !setup.helperOn -> stringResource(R.string.needs_helper)
                        else -> null
                    }
                    Tile(info.icon, stringResource(info.label), sub, selected, subIsWarning = info.needsHelper && !setup.helperOn) {
                        if (info.action == Action.OpenApp) onPickApp() else { vm.setAction(trigger, info.action); onBack() }
                    }
                }
                if (category == Category.Other) item(key = "default") {
                    Tile(CruiseIcons.Refresh, stringResource(R.string.unit_default), null, selected = current == null) {
                        vm.clear(trigger)
                        onBack()
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(@DrawableRes icon: Int, label: String, sub: String?, selected: Boolean, subIsWarning: Boolean = false, onClick: () -> Unit) {
    val colors = CruiseTheme.colors
    val content = if (selected) colors.onAccent else colors.textPrimary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
            .background(if (selected) colors.accent else colors.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = if (selected) colors.onAccent else colors.accent, modifier = Modifier.size(32.dp))
        Spacer(Modifier.weight(1f))
        Text(label, style = CruiseTheme.typography.label, color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub != null) {
            Text(
                sub, style = CruiseTheme.typography.meta, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = when { selected -> colors.onAccent; subIsWarning -> colors.warning; else -> colors.textSecondary },
            )
        }
    }
}

/** The launchable apps with their icons; [selected] is filled. */
@Composable
fun AppPicker(vm: WheelViewModel, selected: String?, onBack: () -> Unit, onPicked: (String) -> Unit) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val colors = CruiseTheme.colors
    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.pick_app_title), onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(apps, key = { it.packageName }) { app ->
                val isSelected = app.packageName == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .clip(RoundedCornerShape(CruiseTheme.dimens.cardRadius))
                        .background(if (isSelected) colors.accent else colors.surface)
                        .clickable { onPicked(app.packageName) }
                        .padding(12.dp),
                ) {
                    AppIcon(app.packageName, 56.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        app.label, style = CruiseTheme.typography.meta, color = if (isSelected) colors.onAccent else colors.textPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
