package com.boulbeba.cruise.core.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The queue and position must survive restarts and never come back half-written (SPEC PB-4, CAR-4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class QueueStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `queue round-trips, including accents and spaces`() {
        val paths = listOf("/storage/usbdisk0/Music/Été 2024 (Mix)/01 - Café.mp3", "/storage/usbdisk0/a b/c.flac")
        QueueStore(context).saveQueue(paths)
        assertEquals(paths, QueueStore(context).loadQueue())
    }

    @Test fun `position round-trips`() {
        val p = PlaybackPosition(index = 7, positionMs = 123_456, shuffle = true, repeatMode = 2, wasPlaying = true, pausedForSleep = true)
        QueueStore(context).savePosition(p)
        assertEquals(p, QueueStore(context).loadPosition())
    }

    @Test fun `nothing saved yet means no position and an empty queue`() {
        File(context.filesDir, "playback").deleteRecursively()
        val store = QueueStore(context)
        assertNull(store.loadPosition())
        assertEquals(emptyList<String>(), store.loadQueue())
    }

    @Test fun `a damaged file is ignored instead of crashing`() {
        val store = QueueStore(context)
        File(context.filesDir, "playback/position.json").writeText("{\"index\": 3, \"positi")
        assertNull(store.loadPosition())
    }
}
