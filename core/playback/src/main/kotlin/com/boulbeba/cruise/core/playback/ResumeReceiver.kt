@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.boulbeba.cruise.core.playback

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.media3.session.MediaButtonReceiver
import com.boulbeba.cruise.core.model.CruiseLog
import java.io.File

/**
 * Cold-boot resume (SPEC CAR-3): after the unit's long-sleep shutdown, play again once the USB stick is mounted.
 * It sends a PLAY key to Media3's own media-button receiver, which starts the service in the foreground and
 * restores the queue through `onPlaybackResumption` — no hand-made foreground-service timing.
 */
class ResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PlaybackSettings(context)
        if (!prefs.resumeOnBoot) return
        val store = QueueStore(context)
        val position = store.loadPosition() ?: return
        if (!position.wasPlaying && !position.pausedForSleep) return
        val first = store.loadQueue().getOrNull(position.index) ?: return
        if (!File(first).canRead()) {
            CruiseLog.log("player", "boot resume waits for ${File(first).parent} (${intent.action})")
            return
        }
        // Once per boot: wall-clock time of the boot, rounded so small clock adjustments do not count.
        val bootId = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000
        if (prefs.lastBootResumed == bootId) return
        prefs.lastBootResumed = bootId
        CruiseLog.log("player", "boot resume (${intent.action})")
        context.sendBroadcast(
            Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)),
        )
    }
}
