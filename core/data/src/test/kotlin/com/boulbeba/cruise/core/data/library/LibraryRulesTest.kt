package com.boulbeba.cruise.core.data.library

import com.boulbeba.cruise.core.data.db.TrackRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryRulesTest {
    private fun row(
        title: String, artist: String = "A", album: String = "X", year: Int = 0, track: Int = 0, disc: Int = 1,
        rate: Int = 44_100, bits: Int = 16, lossless: Boolean = true, path: String = "/m/$title.flac",
    ) = TrackRow(
        0, path, path, "/m", title, artist, album, artist, "", year, track, disc, 1000, rate, bits, "FLAC", lossless,
        false, album.lowercase(), artist.lowercase(), null, null, 0,
    )

    @Test fun same_album_title_by_different_artists_stays_apart() {
        assertNotEquals(
            LibraryScanner.albumKey("Artist One", "Greatest Hits", "/a", false),
            LibraryScanner.albumKey("Artist Two", "Greatest Hits", "/b", false),
        )
    }

    @Test fun album_artist_groups_across_folders_and_spacing() {
        assertEquals(
            LibraryScanner.albumKey("Daft Punk", "Discovery", "/usb/CD1", false),
            LibraryScanner.albumKey(" daft  punk", "discovery ", "/usb/CD2", false),
        )
    }

    @Test fun without_album_artist_the_folder_decides() {
        assertNotEquals(
            LibraryScanner.albumKey(null, "Untitled", "/usb/a", false),
            LibraryScanner.albumKey(null, "Untitled", "/usb/b", false),
        )
    }

    @Test fun articles_are_ignored_when_sorting_artists() {
        val sorted = LibraryRepository.sortTracks(listOf(row("1", "The Who"), row("2", "Abba"), row("3", "Les Négresses Vertes")), TrackSort.Artist)
        assertEquals(listOf("Abba", "Les Négresses Vertes", "The Who"), sorted.map { it.artist })
    }

    @Test fun album_sort_keeps_disc_then_track_order() {
        val sorted = LibraryRepository.sortTracks(listOf(row("c", track = 1, disc = 2), row("b", track = 2), row("a", track = 1)), TrackSort.Album)
        assertEquals(listOf("a", "b", "c"), sorted.map { it.title })
    }

    @Test fun title_sort_is_natural() {
        val sorted = LibraryRepository.sortTracks(listOf(row("Track 10"), row("Track 2"), row("track 1")), TrackSort.Title)
        assertEquals(listOf("track 1", "Track 2", "Track 10"), sorted.map { it.title })
    }

    @Test fun year_prefixed_folders_give_album_and_year() {
        assertEquals("The Dark Side of the Moon" to 1973, LibraryScanner.folderAlbum("1973 - The Dark Side of the Moon"))
        assertEquals("Discovery" to 2001, LibraryScanner.folderAlbum("(2001) Discovery"))
        assertEquals("Night Drive" to 2021, LibraryScanner.folderAlbum("2021 Night Drive"))
        assertEquals("Best Of" to null, LibraryScanner.folderAlbum("Best Of"))
        assertEquals("1 - Intro" to null, LibraryScanner.folderAlbum("1 - Intro"))
    }

    @Test fun artist_from_the_folder_above_the_album() {
        val vol = "/storage/usb"
        assertEquals("Pink Floyd", LibraryScanner.folderArtist(java.io.File("/storage/usb/Pink Floyd/1973 - DSOTM"), vol))
        assertEquals(null, LibraryScanner.folderArtist(java.io.File("/storage/usb/Music/Some Album"), vol))
        assertEquals(null, LibraryScanner.folderArtist(java.io.File("/storage/usb/Some Album"), vol))
        assertEquals(null, LibraryScanner.folderArtist(java.io.File("/storage/usb/Compilations/Été 2024"), vol))
        assertEquals(null, LibraryScanner.folderArtist(java.io.File("/storage/usb/Various Artists/Now 42"), vol))
    }

    @Test fun quality_classes() {
        assertEquals(Quality.HiRes, Quality.of(row("h", rate = 96_000, bits = 24)))
        assertEquals(Quality.HiRes, Quality.of(row("h2", rate = 44_100, bits = 24)))
        assertEquals(Quality.Lossless, Quality.of(row("l")))
        assertEquals(Quality.Lossy, Quality.of(row("m", lossless = false, bits = 0)))
    }
}
