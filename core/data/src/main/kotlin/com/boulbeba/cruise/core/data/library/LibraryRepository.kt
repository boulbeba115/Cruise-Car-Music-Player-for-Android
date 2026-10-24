package com.boulbeba.cruise.core.data.library

import com.boulbeba.cruise.core.data.db.AlbumRow
import com.boulbeba.cruise.core.data.db.FolderRow
import com.boulbeba.cruise.core.data.db.GroupRow
import com.boulbeba.cruise.core.data.db.LibraryDao
import com.boulbeba.cruise.core.data.db.PlayStatEntity
import com.boulbeba.cruise.core.data.db.PlaylistEntity
import com.boulbeba.cruise.core.data.db.PlaylistRow
import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.data.tags.PlaylistFiles
import com.boulbeba.cruise.core.model.NaturalOrder
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

enum class AlbumSort { Title, Artist, Year, Added }
enum class TrackSort { Title, Artist, Album, Year, Duration, Added, FileName }

/** Library queries for the screens (SPEC §4.5): every flow only shows tracks on mounted volumes. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LibraryRepository @Inject constructor(
    private val dao: LibraryDao,
    private val volumes: Volumes,
) {
    private fun <T> onVolumes(query: (List<String>) -> Flow<T>): Flow<T> = volumes.mounted.flatMapLatest(query)

    val trackCount: Flow<Int> = dao.countAll()

    fun albums(sort: AlbumSort): Flow<List<AlbumRow>> = onVolumes(dao::albums).map { list ->
        when (sort) {
            AlbumSort.Title -> list.sortedWith(compareBy(NaturalOrder) { it.album })
            AlbumSort.Artist -> list.sortedWith(compareBy<AlbumRow, String>(NaturalOrder) { it.albumArtist }.thenBy { it.year })
            AlbumSort.Year -> list.sortedWith(compareByDescending<AlbumRow> { it.year }.thenBy(NaturalOrder) { it.album })
            AlbumSort.Added -> list.sortedByDescending { it.added }
        }
    }

    fun artists(): Flow<List<GroupRow>> = onVolumes(dao::artists).map { l -> l.sortedWith(compareBy(NaturalOrder) { stripArticle(it.name) }) }

    fun genres(): Flow<List<GroupRow>> = onVolumes(dao::genres).map { l -> l.sortedWith(compareBy(NaturalOrder) { it.name }) }

    fun years(): Flow<List<GroupRow>> = onVolumes(dao::years).map { l -> l.sortedByDescending { it.name } }

    fun tracks(sort: TrackSort): Flow<List<TrackRow>> = onVolumes(dao::tracks).map { sortTracks(it, sort) }

    /** Folders with music, as a tree (CHANGE_REQUESTS.md CR-04). */
    fun folderTree(): Flow<FolderTree> = onVolumes(dao::folders).map(::FolderTree)

    fun folderTracks(folder: String): Flow<List<TrackRow>> = onVolumes { dao.folderTracks(folder, it) }.map { it.sortedWith(FOLDER_ORDER) }

    /** Everything in [folder] and below, folder by folder in reading order — "play folder and subfolders". */
    suspend fun folderTreeTracks(folder: String): List<TrackRow> =
        dao.folderTreeTracks(folder, folder + File.separator, volumes.mounted.value).sortedWith(FOLDER_ORDER)

    suspend fun randomTrack(): TrackRow? = dao.randomTrack(volumes.mounted.value)

    /** Every visible track, unsorted (start-up random queue). */
    suspend fun allTracks(): List<TrackRow> = dao.allTracks(volumes.mounted.value)

    fun albumTracks(key: String) = onVolumes { dao.albumTracks(key, it) }
    fun artistTracks(key: String) = onVolumes { dao.artistTracks(key, it) }
    fun genreTracks(genre: String) = onVolumes { dao.genreTracks(genre, it) }
    fun yearTracks(year: Int) = onVolumes { dao.yearTracks(year, it) }
    fun recentlyAdded(limit: Int = 300) = onVolumes { dao.recentlyAdded(it, limit) }
    fun recentlyPlayed(limit: Int = 200) = onVolumes { dao.recentlyPlayed(it, limit) }
    fun mostPlayed(limit: Int = 200) = onVolumes { dao.mostPlayed(it, limit) }
    fun favorites() = onVolumes { dao.favorites(it) }

    /** Hi-Res (> 48 kHz or > 16-bit lossless), Lossless, Lossy — SPEC §4.5 quality view. */
    fun quality(level: Quality): Flow<List<TrackRow>> = onVolumes(dao::tracks).map { all ->
        sortTracks(all.filter { Quality.of(it) == level }, TrackSort.Artist)
    }

    /** Accent- and case-insensitive prefix search across titles, artists, albums, genres, folders and file names. */
    suspend fun search(text: String, limit: Int = 300): List<TrackRow> {
        val terms = text.trim().split(Regex("\\s+")).map { it.replace(Regex("[\"'*():^-]"), "") }.filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        val query = terms.joinToString(" ") { "$it*" }
        return runCatching { dao.search(query, volumes.mounted.value, limit) }.getOrDefault(emptyList())
    }

    suspend fun rows(paths: List<String>): List<TrackRow> {
        val byPath = paths.chunked(500).flatMap { dao.rowsByPath(it) }.associateBy { it.path }
        return paths.mapNotNull { byPath[it] }
    }

    suspend fun lyrics(path: String): String? = dao.lyrics(path)

    // ---- Stats & favourites ------------------------------------------------------------------------------

    fun stat(path: String) = dao.statFlow(path)

    suspend fun toggleFavorite(path: String): Boolean {
        val s = dao.stat(path) ?: PlayStatEntity(path)
        dao.putStat(s.copy(favorite = !s.favorite))
        return !s.favorite
    }

    suspend fun recordPlay(path: String, completed: Boolean) {
        val s = dao.stat(path) ?: PlayStatEntity(path)
        dao.putStat(
            if (completed) s.copy(playCount = s.playCount + 1, lastPlayed = System.currentTimeMillis())
            else s.copy(skipCount = s.skipCount + 1),
        )
    }

    // ---- Playlists ---------------------------------------------------------------------------------------

    val playlists: Flow<List<PlaylistRow>> = dao.playlists()

    fun playlistTracks(id: Long): Flow<List<String>> = dao.playlistPathsFlow(id)

    suspend fun createPlaylist(name: String, paths: List<String> = emptyList()): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertPlaylist(PlaylistEntity(name = name, created = now, modified = now))
        if (paths.isNotEmpty()) dao.replacePlaylistItems(id, paths, now)
        return id
    }

    suspend fun addToPlaylist(id: Long, paths: List<String>) =
        dao.replacePlaylistItems(id, dao.playlistPaths(id) + paths, System.currentTimeMillis())

    suspend fun removeFromPlaylist(id: Long, index: Int) {
        val items = dao.playlistPaths(id).toMutableList()
        if (index in items.indices) items.removeAt(index)
        dao.replacePlaylistItems(id, items, System.currentTimeMillis())
    }

    suspend fun movePlaylistItem(id: Long, from: Int, to: Int) {
        val items = dao.playlistPaths(id).toMutableList()
        if (from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        dao.replacePlaylistItems(id, items, System.currentTimeMillis())
    }

    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name, System.currentTimeMillis())

    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)

    suspend fun playlistPaths(id: Long) = dao.playlistPaths(id)

    /** Writes the playlist as M3U8 with relative paths into "Playlists" on the volume of its first track (SPEC Q-3). */
    suspend fun exportPlaylist(id: Long): File? {
        val p: PlaylistEntity = dao.playlist(id) ?: return null
        val paths = dao.playlistPaths(id).map { it.substringBefore('#') }.distinct()
        if (paths.isEmpty()) return null
        val dir = File(volumes.volumeOf(paths.first()), "Playlists").apply { mkdirs() }
        val file = File(dir, p.name.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".m3u8")
        val titles = rows(paths).associate { it.path to "${it.artist} - ${it.title}" }
        PlaylistFiles.write(file, paths) { titles[it] }
        return file
    }

    companion object {
        fun sortTracks(list: List<TrackRow>, sort: TrackSort): List<TrackRow> = when (sort) {
            TrackSort.Title -> list.sortedWith(compareBy(NaturalOrder) { it.title })
            TrackSort.Artist -> list.sortedWith(compareBy<TrackRow, String>(NaturalOrder) { stripArticle(it.artist) }.thenBy { it.albumKey }.thenBy { it.discNo }.thenBy { it.trackNo })
            TrackSort.Album -> list.sortedWith(compareBy<TrackRow, String>(NaturalOrder) { it.album }.thenBy { it.discNo }.thenBy { it.trackNo })
            TrackSort.Year -> list.sortedWith(compareByDescending<TrackRow> { it.year }.thenBy { it.albumKey }.thenBy { it.trackNo })
            TrackSort.Duration -> list.sortedByDescending { it.durationMs }
            TrackSort.Added -> list.sortedByDescending { it.dateAdded }
            TrackSort.FileName -> list.sortedWith(compareBy(NaturalOrder) { File(it.path).name })
        }

        /** Folder by folder (each path segment in natural order), then file name, then CUE position. */
        val FOLDER_ORDER: Comparator<TrackRow> = Comparator<TrackRow> { a, b -> comparePaths(a.folder, b.folder) }
            .thenBy(NaturalOrder) { File(it.filePath).name }
            .thenBy { it.cueStartMs ?: 0L }

        fun comparePaths(a: String, b: String): Int {
            if (a == b) return 0
            val x = a.split('/', '\\')
            val y = b.split('/', '\\')
            for (i in 0 until minOf(x.size, y.size)) {
                val c = NaturalOrder.compare(x[i], y[i])
                if (c != 0) return c
            }
            return x.size - y.size
        }

        /** "The Beatles" sorts under B (SPEC §4.5 ignore articles). */
        fun stripArticle(s: String): String = s.replace(Regex("^(the|le|la|les|l')\\s*", RegexOption.IGNORE_CASE), "")
    }
}

enum class Quality {
    HiRes, Lossless, Lossy;

    companion object {
        fun of(t: TrackRow): Quality = when {
            t.lossless && (t.sampleRate > 48_000 || t.bitDepth > 16) -> HiRes
            t.lossless -> Lossless
            else -> Lossy
        }
    }
}
