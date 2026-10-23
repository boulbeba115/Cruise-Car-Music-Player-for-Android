package com.boulbeba.cruise.core.car

import android.util.Log

/**
 * Logging hook for head-unit events. The app routes it into the field-test log so every car visit records
 * what the unit sent; by default it only goes to logcat.
 */
object CarLog {
    @Volatile var sink: (tag: String, message: String) -> Unit = { tag, message -> Log.i("Cruise/$tag", message) }

    fun log(tag: String, message: String) = sink(tag, message)
}
