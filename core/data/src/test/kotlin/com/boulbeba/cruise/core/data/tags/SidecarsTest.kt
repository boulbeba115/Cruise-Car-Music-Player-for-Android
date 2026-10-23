package com.boulbeba.cruise.core.data.tags

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SidecarsTest {
    private val dir: File = Files.createTempDirectory("sidecars").toFile().apply { deleteOnExit() }

    @Test fun cue_sheet_tracks_and_times() {
        File(dir, "Live.flac").writeBytes(ByteArray(16))
        val cue = File(dir, "Live.cue").apply {
            writeText(
                """
                REM GENRE Rock
                REM DATE 1994
                PERFORMER "The Band"
                TITLE "Live at Home"
                FILE "Live.flac" WAVE
                  TRACK 01 AUDIO
                    TITLE "Intro"
                    INDEX 01 00:00:00
                  TRACK 02 AUDIO
                    TITLE "Song Two"
                    PERFORMER "Guest"
                    INDEX 00 03:10:00
                    INDEX 01 03:12:37
                  TRACK 03 AUDIO
                    TITLE "Finale"
                    INDEX 01 07:00:00
                """.trimIndent(),
            )
        }
        val sheet = assertNotNull(CueParser.parse(cue)).let { CueParser.parse(cue)!! }
        assertEquals("Live at Home", sheet.title)
        assertEquals("The Band", sheet.performer)
        assertEquals(1994, sheet.year)
        assertEquals("Rock", sheet.genre)
        assertEquals("Live.flac", sheet.audioFile.name)
        assertEquals(3, sheet.tracks.size)
        val two = sheet.tracks[1]
        assertEquals("Song Two", two.title)
        assertEquals("Guest", two.performer)
        // 03:12:37 → 3 min 12 s + 37 frames of 1/75 s.
        assertEquals(192_000L + 37 * 1000L / 75, two.startMs)
        assertEquals(sheet.tracks[2].startMs, two.endMs)
        assertEquals(two.startMs, sheet.tracks[0].endMs)
        assertNull(sheet.tracks[2].endMs)
    }

    @Test fun cue_in_windows_1252_is_readable() {
        File(dir, "Album.flac").writeBytes(ByteArray(16))
        val cue = File(dir, "Album.cue")
        val text = "FILE \"Album.flac\" WAVE\n  TRACK 01 AUDIO\n    TITLE \"Été\"\n    INDEX 01 00:00:00\n" +
            "  TRACK 02 AUDIO\n    TITLE \"Deux\"\n    INDEX 01 02:00:00\n"
        cue.writeBytes(text.toByteArray(charset("windows-1252")))
        assertEquals("Été", CueParser.parse(cue)!!.tracks[0].title)
    }

    @Test fun cue_with_a_single_track_is_ignored() {
        File(dir, "One.flac").writeBytes(ByteArray(16))
        val cue = File(dir, "One.cue").apply { writeText("FILE \"One.flac\" WAVE\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n") }
        assertNull(CueParser.parse(cue))
    }

    @Test fun lrc_with_multiple_stamps_and_offset() {
        val lines = LrcParser.parse(
            """
            [ti:Song]
            [offset:+500]
            [00:12.00][00:42.50]Chorus
            [00:05.3]Verse
            not a lyric line
            """.trimIndent(),
        )
        assertEquals(listOf("Verse", "Chorus", "Chorus"), lines.map { it.text })
        assertTrue(lines.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs })
    }

    @Test fun lrc_sidecar_found_next_to_audio() {
        val audio = File(dir, "Track.mp3").apply { writeBytes(ByteArray(4)) }
        assertNull(LrcParser.sidecar(audio))
        File(dir, "Track.lrc").writeText("[00:01.00]Hi")
        assertEquals("Track.lrc", LrcParser.sidecar(audio)?.name)
    }

    @Test fun m3u_relative_windows_and_pc_paths() {
        val sub = File(dir, "Music/Artist").apply { mkdirs() }
        listOf("a.mp3", "b.flac", "c.ogg").forEach { File(sub, it).writeBytes(ByteArray(4)) }
        val lists = File(dir, "Playlists").apply { mkdirs() }
        val m3u = File(lists, "Mix.m3u8").apply {
            writeText(
                "#EXTM3U\n#EXTINF:123,Artist - Title\n../Music/Artist/a.mp3\n\n" +
                    "E:\\Music\\Artist\\b.flac\n..\\Music\\Artist\\c.ogg\nmissing.mp3\n",
            )
        }
        val paths = PlaylistFiles.read(m3u).map { File(it).canonicalFile }
        assertEquals(listOf("a.mp3", "b.flac", "c.ogg").map { File(sub, it).canonicalFile }, paths)
    }

    @Test fun m3u_write_then_read_round_trip() {
        val sub = File(dir, "RT").apply { mkdirs() }
        val songs = listOf("x.mp3", "y é.flac").map { File(sub, it).apply { writeBytes(ByteArray(4)) }.canonicalPath }
        val out = File(dir, "rt.m3u8")
        PlaylistFiles.write(out, songs) { "T" }
        assertEquals(songs, PlaylistFiles.read(out))
    }
}
