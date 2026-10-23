package com.boulbeba.cruise

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boulbeba.cruise.core.data.artwork.ArtworkCache
import com.boulbeba.cruise.core.designsystem.theme.Appearance
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.ui.LocalArtworkCache
import com.boulbeba.cruise.core.playback.PlayerConnection
import com.boulbeba.cruise.feature.settings.diagnostics.KeyEventLog
import com.boulbeba.cruise.ui.CruiseApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var player: PlayerConnection
    @Inject lateinit var artwork: ArtworkCache

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by Appearance.state.collectAsStateWithLifecycle()
            val skin = appearance.activeSkin
            // System-bar icons follow the skin: light icons on dark skins, dark on Daylight.
            val dark = skin.colors.isDark
            androidx.compose.runtime.LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CruiseTheme(skin = skin, uiScale = appearance.uiScale, quality = appearance.quality) {
                CompositionLocalProvider(LocalArtworkCache provides artwork) {
                    CruiseApp(player)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player.connect()
    }

    override fun onStop() {
        player.disconnect()
        super.onStop()
    }

    // Overrides the framework Activity method; lint flags it only because ComponentActivity marks its copy restricted.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        KeyEventLog.record("activity", event)
        return super.dispatchKeyEvent(event)
    }
}
