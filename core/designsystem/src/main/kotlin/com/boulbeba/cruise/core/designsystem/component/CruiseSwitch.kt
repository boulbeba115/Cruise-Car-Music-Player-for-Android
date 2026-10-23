package com.boulbeba.cruise.core.designsystem.component

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme

@Composable
fun CruiseSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = CruiseTheme.colors
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent, uncheckedTrackColor = colors.surfaceElevated),
    )
}
