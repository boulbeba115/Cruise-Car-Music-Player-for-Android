package com.boulbeba.cruise.core.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderTest {

    @Test fun `numbers compare by value`() =
        assertEquals(listOf("Track 1", "Track 2", "Track 10"), listOf("Track 10", "Track 2", "Track 1").sortedWith(NaturalOrder))

    @Test fun `leading zeros do not matter`() =
        assertEquals(listOf("2 - b", "07 - a", "10 - c"), listOf("10 - c", "07 - a", "2 - b").sortedWith(NaturalOrder))

    @Test fun `case and accents are folded`() =
        assertEquals(listOf("café", "Cafe2", "Été", "zebra"), listOf("zebra", "Été", "Cafe2", "café").sortedWith(NaturalOrder))

    @Test fun `display name drops track numbers and extension`() {
        assertEquals("Breathe", AudioFiles.displayName(File("02 - Breathe.flac")))
        assertEquals("Money", AudioFiles.displayName(File("06. Money.mp3")))
        assertEquals("1999", AudioFiles.displayName(File("1999.mp3")))
    }
}
