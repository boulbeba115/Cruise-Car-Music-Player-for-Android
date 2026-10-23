package com.boulbeba.cruise.core.data.tags

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads the FFmpeg-made fixtures (tools/make_tag_fixtures.py): every container's tags and stream properties. */
class TagReaderTest {

    private fun fixture(name: String) = File(requireNotNull(javaClass.getResource("/fixtures/$name")) { name }.toURI())

    private fun assertCommonTags(t: AudioTags) {
        assertEquals("Café del Mar", t.title)
        assertEquals("Artist A", t.artist)
        assertEquals("Album Ä", t.album)
        assertEquals("Various Artists", t.albumArtist)
        assertEquals(2019, t.year)
        assertEquals(3, t.trackNumber)
        assertEquals(12, t.trackTotal)
        assertEquals("Chillout", t.genre)
    }

    private fun assertDuration(expectedMs: Long, t: AudioTags, toleranceMs: Long = 120) =
        assertTrue("duration ${t.durationMs} ≠ $expectedMs", kotlin.math.abs(t.durationMs - expectedMs) <= toleranceMs)

    @Test fun id3v24_mp3() {
        val t = TagReader.read(fixture("id3v24.mp3"))
        assertCommonTags(t)
        assertEquals(1, t.discNumber)
        assertEquals(2, t.discTotal)
        assertEquals(44_100, t.sampleRate)
        assertFalse(t.lossless)
        assertDuration(1_000, t)
        assertEquals(-6.5f, t.rgTrackGain!!, 0.01f)
        assertEquals(0.987654f, t.rgTrackPeak!!, 0.0001f)
        assertEquals(-7.25f, t.rgAlbumGain!!, 0.01f)
        assertTrue(t.hasPicture)
        assertNotNull(TagReader.readPicture(fixture("id3v24.mp3")))
    }

    @Test fun id3v23_mp3_without_picture() {
        val t = TagReader.read(fixture("id3v23.mp3"))
        assertCommonTags(t)
        assertEquals(44_100, t.sampleRate)
        assertDuration(1_000, t)
        assertFalse(t.hasPicture)
        assertNull(TagReader.readPicture(fixture("id3v23.mp3")))
    }

    @Test fun flac_vorbis_comments_picture_lyrics() {
        val t = TagReader.read(fixture("vorbis.flac"))
        assertCommonTags(t)
        assertEquals(1, t.discNumber)
        assertEquals(44_100, t.sampleRate)
        assertEquals(16, t.bitDepth)
        assertEquals(2, t.channels)
        assertTrue(t.lossless)
        assertTrue(t.compilation)
        assertDuration(1_000, t, 5)
        assertEquals(-6.5f, t.rgTrackGain!!, 0.01f)
        assertEquals(0.999f, t.rgAlbumPeak!!, 0.0001f)
        assertTrue(t.hasPicture)
        assertNotNull(TagReader.readPicture(fixture("vorbis.flac")))
        val lines = LrcParser.parse(requireNotNull(t.lyrics))
        assertEquals(listOf(1_000L, 3_500L), lines.map { it.timeMs })
        assertEquals("Second line", lines[1].text)
    }

    @Test fun flac_hi_res() {
        val t = TagReader.read(fixture("hires.flac"))
        assertEquals(96_000, t.sampleRate)
        assertEquals(24, t.bitDepth)
        assertTrue(t.lossless)
        assertDuration(300, t, 5)
    }

    @Test fun mp4_aac() {
        val t = TagReader.read(fixture("aac.m4a"))
        assertCommonTags(t)
        assertEquals(1, t.discNumber)
        assertEquals(44_100, t.sampleRate)
        assertFalse(t.lossless)
        assertDuration(1_000, t)
        assertTrue(t.hasPicture)
        assertNotNull(TagReader.readPicture(fixture("aac.m4a")))
    }

    @Test fun mp4_alac_is_lossless() {
        val t = TagReader.read(fixture("alac.m4a"))
        assertCommonTags(t)
        assertTrue(t.lossless)
        assertEquals(16, t.bitDepth)
        assertDuration(500, t)
    }

    @Test fun ogg_vorbis() {
        val t = TagReader.read(fixture("vorbis.ogg"))
        assertCommonTags(t)
        assertEquals(44_100, t.sampleRate)
        assertDuration(1_000, t)
        assertEquals(-7.25f, t.rgAlbumGain!!, 0.01f)
    }

    @Test fun opus_reports_48k() {
        val t = TagReader.read(fixture("opus.opus"))
        assertCommonTags(t)
        assertEquals(48_000, t.sampleRate)
        assertDuration(1_000, t)
    }

    @Test fun wav_info_chunk() {
        val t = TagReader.read(fixture("info.wav"))
        assertEquals("Wave Title", t.title)
        assertEquals("Wave Artist", t.artist)
        assertEquals("Wave Album", t.album)
        assertEquals(16, t.bitDepth)
        assertTrue(t.lossless)
        assertDuration(200, t, 5)
    }

    @Test fun garbage_file_is_safe() {
        val f = File.createTempFile("broken", ".mp3").apply { writeBytes(ByteArray(4096) { (it * 31).toByte() }); deleteOnExit() }
        TagReader.read(f) // must not throw
        assertNull(TagReader.readPicture(f))
    }
}
