package com.boulbeba.cruise

import android.app.Application
import android.util.Log
import com.boulbeba.cruise.core.car.CarLog
import com.boulbeba.cruise.core.designsystem.theme.Appearance
import com.boulbeba.cruise.feature.settings.fieldtest.FieldLog
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CruiseApplication : Application() {
    @Inject lateinit var carEvents: CarEvents

    override fun onCreate() {
        super.onCreate()
        FieldLog.init(this)
        // Every head-unit and player event lands in the field log, so each car visit records what the unit did.
        CarLog.sink = { tag, message ->
            Log.i("Cruise/$tag", message)
            FieldLog.log(tag, message)
        }
        Appearance.init(this)
        carEvents.start()
    }
}
