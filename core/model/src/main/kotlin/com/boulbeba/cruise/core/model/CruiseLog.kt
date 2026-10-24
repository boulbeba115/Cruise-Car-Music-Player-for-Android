package com.boulbeba.cruise.core.model

import android.util.Log

/** Player and library events, to logcat under `Cruise/<tag>`. */
object CruiseLog {
    fun log(tag: String, message: String) {
        Log.i("Cruise/$tag", message)
    }
}
