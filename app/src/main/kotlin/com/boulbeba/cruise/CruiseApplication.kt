package com.boulbeba.cruise

import android.app.Application
import com.boulbeba.cruise.core.designsystem.theme.Appearance
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CruiseApplication : Application() {
    @Inject lateinit var carEvents: CarEvents

    override fun onCreate() {
        super.onCreate()
        Appearance.init(this)
        carEvents.start()
    }
}
