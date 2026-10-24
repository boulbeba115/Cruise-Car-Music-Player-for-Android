package com.boulbeba.cruise.core.data.library

import com.boulbeba.cruise.core.data.db.FolderRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {

    private fun row(folder: String, tracks: Int = 10, albums: Int = 1, volume: String = USB) =
        FolderRow(folder, volume, tracks, tracks * 200_000L, albums, "a:$folder", "$folder/01.mp3", "Album", "Artist", 2000)

    @Test fun only_folders_with_music_and_their_parents_exist() {
        val tree = FolderTree(listOf(row("$USB/Music/Rock/Album A"), row("$USB/Music/Jazz/Album B")))
        assertEquals(listOf(USB), tree.volumes.map { it.path })
        assertNull(tree.node("$USB/DCIM"))
        assertEquals(20, tree.node(USB)!!.totalTracks)
        assertEquals(listOf("Jazz", "Rock"), tree.node("$USB/Music")!!.children.map { it.name })
    }

    @Test fun single_paths_collapse_to_the_first_real_choice() {
        val tree = FolderTree(listOf(row("$USB/Music/Rock/Album A"), row("$USB/Music/Rock/Album B")))
        // USB › Music › Rock has no music of its own and one way down until Rock, which holds two albums.
        assertEquals("$USB/Music/Rock", tree.start()!!.path)
        assertNull(tree.up(tree.start()!!))
        val album = tree.node("$USB/Music/Rock/Album A")!!
        assertEquals("$USB/Music/Rock", tree.up(album)!!.path)
        assertTrue(album.isAlbum)
    }

    @Test fun a_folder_with_its_own_tracks_is_never_skipped() {
        val tree = FolderTree(listOf(row("$USB/Music", tracks = 3), row("$USB/Music/Album A")))
        assertEquals("$USB/Music", tree.start()!!.path)
    }

    @Test fun several_volumes_start_on_the_volume_list() {
        val tree = FolderTree(listOf(row("$USB/A"), row("$SD/B", tracks = 50, volume = SD)))
        assertNull(tree.start())
        assertEquals(listOf(SD, USB), tree.volumes.map { it.path })
        val opened = tree.collapse(tree.volumes[1])
        assertEquals("$USB/A", opened.path)
        assertNull(tree.up(opened))
    }

    @Test fun search_ignores_case_and_accents() {
        val tree = FolderTree(listOf(row("$USB/Musique/Été 85"), row("$USB/Musique/Hiver")))
        assertEquals(listOf("Été 85"), tree.search("ete").map { it.name })
        assertTrue(tree.search("  ").isEmpty())
    }

    @Test fun paths_sort_segment_by_segment_in_natural_order() {
        val sorted = listOf("$USB/Rock 2", "$USB/Rock/x", "$USB/Rock 10", "$USB/Rock").sortedWith { a, b -> LibraryRepository.comparePaths(a, b) }
        assertEquals(listOf("$USB/Rock", "$USB/Rock/x", "$USB/Rock 2", "$USB/Rock 10"), sorted)
    }

    private companion object {
        const val USB = "/storage/usbdisk0"
        const val SD = "/storage/extsd"
    }
}
