package com.boulbeba.cruise.core.designsystem.component

import androidx.compose.runtime.Composable
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme

/** Titles in uppercase for skins that want it (Neon Drive, Amber Cockpit). */
@Composable
fun skinTitle(text: String): String = if (CruiseTheme.skin.uppercaseTitles) text.uppercase() else text
