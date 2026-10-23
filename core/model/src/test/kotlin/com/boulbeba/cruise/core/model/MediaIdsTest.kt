package com.boulbeba.cruise.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIdsTest {
    @Test fun plain_file() {
        val id = MediaIds.of("/storage/usb/Music/a #1.flac")
        assertEquals("/storage/usb/Music/a #1.flac", id)
        assertFalse(MediaIds.isCue(id))
        assertEquals(id, MediaIds.filePath(id))
        assertNull(MediaIds.clip(id))
    }

    @Test fun cue_track_round_trip() {
        val id = MediaIds.of("/usb/Live #2.flac#3", 192_493, 420_000)
        assertTrue(MediaIds.isCue(id))
        assertEquals("/usb/Live #2.flac#3", MediaIds.libraryPath(id))
        assertEquals("/usb/Live #2.flac", MediaIds.filePath(id))
        assertEquals(192_493L to 420_000L, MediaIds.clip(id))
    }

    @Test fun last_cue_track_has_open_end() {
        val id = MediaIds.of("/usb/Live.flac#9", 1_000, null)
        assertEquals(1_000L to null, MediaIds.clip(id))
    }
}
