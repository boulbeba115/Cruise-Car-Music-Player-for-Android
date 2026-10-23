package com.boulbeba.cruise.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.boulbeba.cruise.R
import com.boulbeba.cruise.core.designsystem.icon.CruiseIcons

/** Top-level destinations of the navigation rail / bottom bar (SPEC §5.2). */
enum class TopDestination(@DrawableRes val icon: Int, @StringRes val label: Int) {
    NowPlaying(CruiseIcons.NowPlaying, R.string.nav_now_playing),
    Library(CruiseIcons.Library, R.string.nav_library),
    Folders(CruiseIcons.Folder, R.string.nav_folders),
    Playlists(CruiseIcons.Playlist, R.string.nav_playlists),
    Search(CruiseIcons.Search, R.string.nav_search),
    Sound(CruiseIcons.Equalizer, R.string.nav_sound),
    Settings(CruiseIcons.Settings, R.string.nav_settings),
}
