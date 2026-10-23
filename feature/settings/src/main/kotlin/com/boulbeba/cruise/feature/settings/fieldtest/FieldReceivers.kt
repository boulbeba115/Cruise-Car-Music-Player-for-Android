package com.boulbeba.cruise.feature.settings.fieldtest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.boulbeba.cruise.feature.settings.diagnostics.KeyEventLog

/** Boot / wake-from-power-off: logs it and resumes the field-test logger if it was running. */
class BootLogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FieldLog.init(context)
        FieldLog.log("boot", intent.action?.substringAfterLast('.') ?: "?")
        FieldTestService.resumeIfActive(context)
    }
}

/**
 * Legacy media-button route: older head units send steering keys as MEDIA_BUTTON broadcasts to the
 * registered receiver of the last active session instead of to the session itself.
 */
class MediaButtonLogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FieldLog.init(context)
        @Suppress("DEPRECATION")
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
        if (event != null) KeyEventLog.record("media-button-broadcast", event)
        else FieldLog.log("key", "MEDIA_BUTTON broadcast without key event")
    }
}
