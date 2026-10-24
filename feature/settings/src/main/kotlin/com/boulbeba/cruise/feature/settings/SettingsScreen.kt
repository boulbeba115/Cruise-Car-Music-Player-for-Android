package com.boulbeba.cruise.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.boulbeba.cruise.core.designsystem.component.skinTitle
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.feature.settings.appearance.AppearanceScreen
import com.boulbeba.cruise.feature.settings.general.GeneralScreen

private enum class SettingsTab { Appearance, General }

/** Settings (SPEC §5.2, CHANGE_REQUESTS.md CR-05): appearance, then library, playback, backup and about. */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.Appearance) }
    val dimens = CruiseTheme.dimens
    Column(modifier.fillMaxSize().padding(horizontal = dimens.gutter)) {
        Text(
            skinTitle(stringResource(R.string.settings_title)),
            style = CruiseTheme.typography.title,
            color = CruiseTheme.colors.textPrimary,
            modifier = Modifier.padding(top = dimens.gutter, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip(CruiseIcons.Palette, stringResource(R.string.settings_tab_appearance), tab == SettingsTab.Appearance) { tab = SettingsTab.Appearance }
            Chip(CruiseIcons.Library, stringResource(R.string.settings_tab_general), tab == SettingsTab.General) { tab = SettingsTab.General }
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "settingsTab",
            modifier = Modifier.weight(1f).padding(top = 12.dp),
        ) { current ->
            when (current) {
                SettingsTab.Appearance -> AppearanceScreen()
                SettingsTab.General -> GeneralScreen()
            }
        }
    }
}

@Composable
private fun Chip(@DrawableRes icon: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = CruiseTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = CruiseTheme.dimens.minTouchTarget)
            .clip(RoundedCornerShape(50))
            .background(if (selected) colors.accent else colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        val tint = if (selected) colors.onAccent else colors.textPrimary
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = CruiseTheme.typography.label, color = tint)
    }
}
