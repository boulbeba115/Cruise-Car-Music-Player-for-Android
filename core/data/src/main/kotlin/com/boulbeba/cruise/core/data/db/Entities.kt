package com.boulbeba.cruise.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One playable track (SPEC §7). CUE sheets create virtual tracks: [path] is then "file#N" and [cueStartMs]/[cueEndMs]
 * clip the audio file at [filePath]; the whole file itself is stored with [hidden] = true.
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["path"], unique = true), Index("folder"), Index("albumKey"), Index("artistKey"),
        Index("genre"), Index("year"), Index("dateAdded"), Index("volume"),
    ],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val filePath: String,
    val folder: String,
    val volume: String,
    val fileName: String,
    val size: Long,
    val modified: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val composer: String,
    val year: Int,
    val trackNo: Int,
    val discNo: Int,
    val durationMs: Long,
    val sampleRate: Int,
    val bitDepth: Int,
    val channels: Int,
    val bitrate: Int,
    val codec: String,
    val lossless: Boolean,
    val rgTrackGain: Float?,
    val rgTrackPeak: Float?,
    val rgAlbumGain: Float?,
    val rgAlbumPeak: Float?,
    val hasArt: Boolean,
    val lyrics: String?,
    val compilation: Boolean,
    /** Groups tracks into albums without merging two artists' "Greatest Hits" (SPEC LIB-9). */
    val albumKey: String,
    val artistKey: String,
    val cueStartMs: Long?,
    val cueEndMs: Long?,
    val hidden: Boolean,
    val dateAdded: Long,
)

/** Search index: unicode61 folds case and accents for every script, so "cafe" finds "Café", "ete" finds "Été". */
@Fts4(contentEntity = TrackEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61, tokenizerArgs = ["remove_diacritics=1"])
@Entity(tableName = "tracks_fts")
data class TrackFts(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val composer: String,
    val fileName: String,
    val folder: String,
)

@Entity(tableName = "play_stats")
data class PlayStatEntity(
    @PrimaryKey val path: String,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayed: Long = 0,
    val favorite: Boolean = false,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val created: Long,
    val modified: Long,
    /** M3U file this playlist was imported from (re-imported when it changes). */
    val sourceFile: String? = null,
)

@Entity(
    tableName = "playlist_items",
    indices = [Index("playlistId")],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val position: Int,
    val path: String,
)

/** Light projection used by every list (no lyrics column). */
data class TrackRow(
    val id: Long,
    val path: String,
    val filePath: String,
    val folder: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: Int,
    val trackNo: Int,
    val discNo: Int,
    val durationMs: Long,
    val sampleRate: Int,
    val bitDepth: Int,
    val codec: String,
    val lossless: Boolean,
    val hasArt: Boolean,
    val albumKey: String,
    val artistKey: String,
    val cueStartMs: Long?,
    val cueEndMs: Long?,
    val dateAdded: Long,
)

data class AlbumRow(
    val albumKey: String,
    val album: String,
    val albumArtist: String,
    val year: Int,
    val trackCount: Int,
    val durationMs: Long,
    val firstPath: String,
    val folder: String,
    val hasArt: Boolean,
    val added: Long,
    val lossless: Boolean,
    @ColumnInfo(name = "maxRate") val maxSampleRate: Int,
    @ColumnInfo(name = "maxDepth") val maxBitDepth: Int,
)

data class GroupRow(
    val key: String,
    val name: String,
    val albumCount: Int,
    val trackCount: Int,
    /** An album of the group, for its cover. */
    val sampleAlbumKey: String,
    val samplePath: String,
    val sampleFolder: String,
)

data class FileStamp(val path: String, val size: Long, val modified: Long)
