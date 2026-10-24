package com.boulbeba.cruise.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.boulbeba.cruise.R
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons

/** Top-level destinations of the navigation rail / bottom bar (SPEC §5.2, CHANGE_REQUESTS.md CR-02). */
enum class TopDestination(@DrawableRes val icon: Int, @StringRes val label: Int) {
    NowPlaying(CruiseIcons.NowPlaying, R.string.nav_now_playing),
    Library(CruiseIcons.Library, R.string.nav_library),
    Sound(CruiseIcons.Equalizer, R.string.nav_sound),
    Settings(CruiseIcons.Settings, R.string.nav_settings),
}
