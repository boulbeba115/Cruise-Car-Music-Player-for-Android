package com.boulbeba.cruise.wheel

import android.Manifest
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import com.boulbeba.cruise.core.designsystem.theme.Skins
import com.boulbeba.cruise.core.designsystem.theme.VisualQuality
import com.boulbeba.cruise.wheel.service.WheelService
import com.boulbeba.cruise.wheel.ui.WheelApp
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

/**
 * Whatever starts the process — the launcher, a start broadcast, the system binding the accessibility helper at boot —
 * also starts the wheel service, so the buttons work without opening the app (CHANGE_REQUESTS.md CR-19).
 */
@HiltAndroidApp
class WheelApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WheelService.start(this)
    }
}

/** Cruise Wheel's screens; opening it also (re)starts the wheel service. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WheelService.start(this)
        // Storage for the report and the mapping file on the USB stick; notifications for the service (Android 13+).
        val wanted = buildList {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { add(Manifest.permission.READ_EXTERNAL_STORAGE); add(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (savedInstanceState == null && wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
        setContent {
            CruiseTheme(skin = Skins.Midnight, quality = VisualQuality.Balanced) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) { WheelApp() }
            }
        }
    }
}
