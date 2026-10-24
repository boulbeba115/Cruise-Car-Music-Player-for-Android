package com.boulbeba.cruise.core.playback

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Car defaults only on a head unit: a phone keeps playing with the screen off (CHANGE_REQUESTS.md CR-21). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class PlaybackSettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun battery(present: Boolean) {
        context.sendStickyBroadcast(Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_PRESENT, present))
    }

    @Test fun `a phone never pauses at screen-off nor resumes at boot by default`() {
        battery(present = true)
        val s = PlaybackSettings(context)
        assertFalse(s.isHeadUnit)
        assertEquals(0L, s.sleepPauseDelayMs)
        assertFalse(s.resumeOnBoot)
    }

    @Test fun `a head unit without battery keeps the car defaults`() {
        battery(present = false)
        val s = PlaybackSettings(context)
        assertTrue(s.isHeadUnit)
        assertEquals(10_000L, s.sleepPauseDelayMs)
        assertTrue(s.resumeOnBoot)
    }

    @Test fun `a choice made in Settings wins`() {
        battery(present = true)
        val s = PlaybackSettings(context)
        s.sleepPauseDelayMs = 30_000
        assertEquals(30_000L, PlaybackSettings(context).sleepPauseDelayMs)
    }
}
