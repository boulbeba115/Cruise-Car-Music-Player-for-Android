package com.boulbeba.cruise.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val ROW = """id, path, filePath, folder, title, artist, album, albumArtist, genre, year, trackNo, discNo,
    durationMs, sampleRate, bitDepth, codec, lossless, hasArt, albumKey, artistKey, cueStartMs, cueEndMs, dateAdded"""

private const val T_ROW = """t.id, t.path, t.filePath, t.folder, t.title, t.artist, t.album, t.albumArtist, t.genre, t.year,
    t.trackNo, t.discNo, t.durationMs, t.sampleRate, t.bitDepth, t.codec, t.lossless, t.hasArt, t.albumKey, t.artistKey,
    t.cueStartMs, t.cueEndMs, t.dateAdded"""

/** Visible = not hidden (whole-file of a CUE sheet) and on a mounted volume. */
private const val VISIBLE = "hidden = 0 AND volume IN (:volumes)"

@Dao
interface LibraryDao {

    // ---- Scanner -------------------------------------------------------------------------------------------

    @Query("SELECT path, size, modified FROM tracks WHERE volume = :volume")
    suspend fun stamps(volume: String): List<FileStamp>

    @Upsert
    suspend fun upsert(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE path IN (:paths)")
    suspend fun deletePaths(paths: List<String>)

    @Query("DELETE FROM tracks WHERE filePath = :filePath AND path != filePath")
    suspend fun deleteCueTracksOf(filePath: String)

    @Query("SELECT COUNT(*) FROM tracks WHERE hidden = 0")
    fun countAll(): Flow<Int>

    @Query("SELECT * FROM tracks WHERE path = :path LIMIT 1")
    suspend fun byPath(path: String): TrackEntity?

    @Query("SELECT DISTINCT albumKey FROM tracks WHERE hidden = 0")
    suspend fun allAlbumKeys(): List<String>

    @Query("SELECT $ROW FROM tracks WHERE albumKey = :albumKey AND hidden = 0 ORDER BY discNo, trackNo LIMIT 1")
    suspend fun firstTrackOfAlbum(albumKey: String): TrackRow?

    // ---- Browsing -----------------------------------------------------------------------------------------

    @Query(
        """SELECT albumKey, MAX(album) AS album, MAX(albumArtist) AS albumArtist, MAX(year) AS year, COUNT(*) AS trackCount,
        SUM(durationMs) AS durationMs, MIN(path) AS firstPath, MIN(folder) AS folder, MAX(hasArt) AS hasArt,
        MAX(dateAdded) AS added, MAX(lossless) AS lossless, MAX(sampleRate) AS maxRate, MAX(bitDepth) AS maxDepth
        FROM tracks WHERE $VISIBLE GROUP BY albumKey""",
    )
    fun albums(volumes: List<String>): Flow<List<AlbumRow>>

    @Query(
        """SELECT artistKey AS `key`, MAX(albumArtist) AS name, COUNT(DISTINCT albumKey) AS albumCount, COUNT(*) AS trackCount,
        MIN(albumKey) AS sampleAlbumKey, MIN(path) AS samplePath, MIN(folder) AS sampleFolder
        FROM tracks WHERE $VISIBLE GROUP BY artistKey""",
    )
    fun artists(volumes: List<String>): Flow<List<GroupRow>>

    @Query(
        """SELECT genre AS `key`, genre AS name, COUNT(DISTINCT albumKey) AS albumCount, COUNT(*) AS trackCount,
        MIN(albumKey) AS sampleAlbumKey, MIN(path) AS samplePath, MIN(folder) AS sampleFolder
        FROM tracks WHERE $VISIBLE AND genre != '' GROUP BY genre""",
    )
    fun genres(volumes: List<String>): Flow<List<GroupRow>>

    @Query(
        """SELECT CAST(year AS TEXT) AS `key`, CAST(year AS TEXT) AS name, COUNT(DISTINCT albumKey) AS albumCount,
        COUNT(*) AS trackCount, MIN(albumKey) AS sampleAlbumKey, MIN(path) AS samplePath, MIN(folder) AS sampleFolder
        FROM tracks WHERE $VISIBLE AND year > 0 GROUP BY year""",
    )
    fun years(volumes: List<String>): Flow<List<GroupRow>>

    @Query("SELECT $ROW FROM tracks WHERE $VISIBLE")
    fun tracks(volumes: List<String>): Flow<List<TrackRow>>

    @Query("SELECT $ROW FROM tracks WHERE $VISIBLE AND albumKey = :albumKey ORDER BY discNo, trackNo, path")
    fun albumTracks(albumKey: String, volumes: List<String>): Flow<List<TrackRow>>

    @Query("SELECT $ROW FROM tracks WHERE $VISIBLE AND artistKey = :artistKey ORDER BY year, albumKey, discNo, trackNo")
    fun artistTracks(artistKey: String, volumes: List<String>): Flow<List<TrackRow>>

    @Query("SELECT $ROW FROM tracks WHERE $VISIBLE AND genre = :genre ORDER BY albumKey, discNo, trackNo")
    fun genreTracks(genre: String, volumes: List<String>): Flow<List<TrackRow>>

    @Query("SELECT $ROW FROM tracks WHERE $VISIBLE AND year = :year ORDER BY albumKey, discNo, trackNo")
    fun yearTracks(year: Int, volumes: List<String>): Flow<List<TrackRow>>

    @Query("SELECT $ROW FROM tracks WHERE $VISIBLE ORDER BY dateAdded DESC LIMIT :limit")
    fun recentlyAdded(volumes: List<String>, limit: Int): Flow<List<TrackRow>>

    @Query(
        """SELECT $T_ROW FROM tracks t JOIN play_stats s ON s.path = t.path
        WHERE t.hidden = 0 AND t.volume IN (:volumes) AND s.playCount > 0 ORDER BY s.playCount DESC LIMIT :limit""",
    )
    fun mostPlayed(volumes: List<String>, limit: Int): Flow<List<TrackRow>>

    @Query(
        """SELECT $T_ROW FROM tracks t JOIN play_stats s ON s.path = t.path
        WHERE t.hidden = 0 AND t.volume IN (:volumes) AND s.lastPlayed > 0 ORDER BY s.lastPlayed DESC LIMIT :limit""",
    )
    fun recentlyPlayed(volumes: List<String>, limit: Int): Flow<List<TrackRow>>

    @Query(
        """SELECT $T_ROW FROM tracks t JOIN play_stats s ON s.path = t.path
        WHERE t.hidden = 0 AND t.volume IN (:volumes) AND s.favorite = 1 ORDER BY t.artistKey, t.albumKey, t.trackNo""",
    )
    fun favorites(volumes: List<String>): Flow<List<TrackRow>>

    @Query(
        """SELECT $T_ROW FROM tracks t JOIN tracks_fts f ON f.rowid = t.id
        WHERE tracks_fts MATCH :query AND t.hidden = 0 AND t.volume IN (:volumes) LIMIT :limit""",
    )
    suspend fun search(query: String, volumes: List<String>, limit: Int): List<TrackRow>

    /** Every folder that holds tracks, with what the folder browser shows for it (CHANGE_REQUESTS.md CR-04). */
    @Query(
        """SELECT folder, MIN(volume) AS volume, COUNT(*) AS trackCount, SUM(durationMs) AS durationMs,
        COUNT(DISTINCT albumKey) AS albumCount, MIN(albumKey) AS sampleAlbumKey, MIN(path) AS samplePath,
        MAX(album) AS album, MAX(albumArtist) AS albumArtist, MAX(year) AS year
        FROM tracks WHERE $VISIBLE GROUP BY folder""",
    )
    fun folders(volumes: List<String>): Flow<List<FolderRow>>

    @Query("SELECT $ROW FROM tracks WHERE $VISIBLE AND folder = :folder")
    fun folderTracks(folder: String, volumes: List<String>): Flow<List<TrackRow>>

    /** Tracks of [folder] and every folder below it ([prefix] = folder + "/"; no LIKE, paths may contain _ or %). */
    @Query("SELECT $ROW FROM tracks WHERE $VISIBLE AND (folder = :folder OR substr(folder, 1, length(:prefix)) = :prefix)")
    suspend fun folderTreeTracks(folder: String, prefix: String, volumes: List<String>): List<TrackRow>

    @Query("SELECT $ROW FROM tracks WHERE $VISIBLE")
    suspend fun allTracks(volumes: List<String>): List<TrackRow>

    @Query("SELECT $ROW FROM tracks WHERE $VISIBLE ORDER BY RANDOM() LIMIT 1")
    suspend fun randomTrack(volumes: List<String>): TrackRow?

    @Query("SELECT $ROW FROM tracks WHERE path IN (:paths)")
    suspend fun rowsByPath(paths: List<String>): List<TrackRow>

    @Query("SELECT rgTrackGain, rgTrackPeak, rgAlbumGain, rgAlbumPeak, albumKey FROM tracks WHERE path = :path LIMIT 1")
    suspend fun gain(path: String): GainRow?

    @Query("SELECT lyrics FROM tracks WHERE path = :path LIMIT 1")
    suspend fun lyrics(path: String): String?

    // ---- Stats ---------------------------------------------------------------------------------------------

    @Query("SELECT * FROM play_stats WHERE path = :path")
    suspend fun stat(path: String): PlayStatEntity?

    @Query("SELECT * FROM play_stats WHERE path = :path")
    fun statFlow(path: String): Flow<PlayStatEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putStat(stat: PlayStatEntity)

    @Query("SELECT * FROM play_stats")
    suspend fun allStats(): List<PlayStatEntity>

    // ---- Playlists ----------------------------------------------------------------------------------------

    @Query(
        """SELECT p.id, p.name, p.modified, p.sourceFile, COUNT(i.id) AS trackCount, MIN(i.path) AS firstPath
        FROM playlists p LEFT JOIN playlist_items i ON i.playlistId = p.id GROUP BY p.id ORDER BY p.name COLLATE NOCASE""",
    )
    fun playlists(): Flow<List<PlaylistRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun playlist(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE sourceFile = :file LIMIT 1")
    suspend fun playlistBySource(file: String): PlaylistEntity?

    @Insert
    suspend fun insertPlaylist(p: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, modified = :now WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistRow(id: Long)

    @Query("SELECT path FROM playlist_items WHERE playlistId = :id ORDER BY position")
    suspend fun playlistPaths(id: Long): List<String>

    @Query("SELECT path FROM playlist_items WHERE playlistId = :id ORDER BY position")
    fun playlistPathsFlow(id: Long): Flow<List<String>>

    @Query("DELETE FROM playlist_items WHERE playlistId = :id")
    suspend fun clearPlaylist(id: Long)

    @Insert
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Query("UPDATE playlists SET modified = :now WHERE id = :id")
    suspend fun touchPlaylist(id: Long, now: Long)

    @Transaction
    suspend fun replacePlaylistItems(id: Long, paths: List<String>, now: Long) {
        clearPlaylist(id)
        insertItems(paths.mapIndexed { i, p -> PlaylistItemEntity(playlistId = id, position = i, path = p) })
        touchPlaylist(id, now)
    }

    @Transaction
    suspend fun deletePlaylist(id: Long) {
        clearPlaylist(id)
        deletePlaylistRow(id)
    }
}

data class GainRow(val rgTrackGain: Float?, val rgTrackPeak: Float?, val rgAlbumGain: Float?, val rgAlbumPeak: Float?, val albumKey: String)

data class PlaylistRow(val id: Long, val name: String, val modified: Long, val sourceFile: String?, val trackCount: Int, val firstPath: String?)

data class FolderRow(
    val folder: String,
    val volume: String,
    val trackCount: Int,
    val durationMs: Long,
    val albumCount: Int,
    val sampleAlbumKey: String,
    val samplePath: String,
    val album: String,
    val albumArtist: String,
    val year: Int,
)
