package com.boulbeba.cruise.core.data.library

import android.os.Environment
import android.os.SystemClock
import com.boulbeba.cruise.core.model.CruiseLog
import com.boulbeba.cruise.core.data.artwork.ArtworkCache
import com.boulbeba.cruise.core.data.db.LibraryDao
import com.boulbeba.cruise.core.data.db.PlaylistEntity
import com.boulbeba.cruise.core.data.db.TrackEntity
import com.boulbeba.cruise.core.data.tags.AudioTags
import com.boulbeba.cruise.core.data.tags.CueParser
import com.boulbeba.cruise.core.data.tags.PlaylistFiles
import com.boulbeba.cruise.core.data.tags.TagReader
import com.boulbeba.cruise.core.model.AudioFiles
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScanState(
    val running: Boolean = false,
    val filesSeen: Int = 0,
    val filesRead: Int = 0,
    val tracks: Int = 0,
    val message: String = "",
    val lastScanMs: Long = 0,
)

/**
 * Incremental library scanner (SPEC §6.4): walks every mounted volume, re-reads only files whose size or date
 * changed, turns CUE sheets into virtual tracks, imports M3U playlists, removes tracks whose files are gone (only on
 * volumes that are mounted), then prepares album thumbnails.
 */
@Singleton
class LibraryScanner @Inject constructor(
    private val dao: LibraryDao,
    private val volumes: Volumes,
    private val artwork: ArtworkCache,
    private val settings: LibrarySettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    fun scan() {
        if (job?.isActive == true) return
        job = scope.launch { runCatching { scanNow() }.onFailure { CruiseLog.log("scan", "failed: $it") } }
    }

    suspend fun scanNow() {
        val started = SystemClock.elapsedRealtime()
        volumes.refresh()
        _state.value = ScanState(running = true, message = "Looking for music…")
        var seen = 0
        var read = 0
        val playlists = mutableListOf<File>()
        val minDuration = settings.minDurationMs
        for (root in volumes.roots()) {
            val volume = root.path
            val known = dao.stamps(volume).associateBy { it.path }
            val present = HashSet<String>()
            val batch = ArrayList<TrackEntity>()
            val internal = volume == Environment.getExternalStorageDirectory().path
            walk(root, internal) { dir, files ->
                val cues = files.filter { it.extension.equals("cue", true) }.mapNotNull(CueParser::parse)
                val cueAudio = cues.associateBy { it.audioFile.path }
                files.filter { it.extension.lowercase() in setOf("m3u", "m3u8", "pls") }.let(playlists::addAll)
                for (f in files) {
                    if (!AudioFiles.isAudio(f)) continue
                    seen++
                    val path = f.path
                    present += path
                    val stamp = known[path]
                    val cue = cueAudio[path]
                    cue?.tracks?.forEach { present += "$path#${it.number}" }
                    if (stamp != null && stamp.size == f.length() && stamp.modified == f.lastModified()) continue
                    val tags = TagReader.read(f)
                    read++
                    if (tags.durationMs in 1 until minDuration && cue == null) continue
                    val whole = entity(f, volume, tags, dir, hidden = cue != null)
                    batch += whole
                    if (cue != null) {
                        dao.deleteCueTracksOf(path)
                        batch += cue.tracks.map { t ->
                            whole.copy(
                                path = "$path#${t.number}",
                                title = t.title ?: "Track ${t.number}",
                                artist = t.performer ?: cue.performer ?: whole.artist,
                                album = cue.title ?: whole.album,
                                albumArtist = cue.performer ?: whole.albumArtist,
                                genre = cue.genre ?: whole.genre,
                                year = cue.year ?: whole.year,
                                trackNo = t.number,
                                durationMs = ((t.endMs ?: whole.durationMs) - t.startMs).coerceAtLeast(0),
                                cueStartMs = t.startMs,
                                cueEndMs = t.endMs,
                                hidden = false,
                                albumKey = albumKey(cue.performer ?: whole.albumArtist, cue.title ?: whole.album, dir.path, false),
                                artistKey = key(cue.performer ?: whole.artist),
                            )
                        }
                    }
                    if (batch.size >= BATCH) {
                        dao.upsert(batch.toList())
                        batch.clear()
                        _state.value = _state.value.copy(filesSeen = seen, filesRead = read, message = dir.name)
                    }
                }
                if (seen % 200 == 0) _state.value = _state.value.copy(filesSeen = seen, filesRead = read, message = dir.name)
            }
            if (batch.isNotEmpty()) dao.upsert(batch)
            val gone = known.keys - present
            if (gone.isNotEmpty() && root.canRead()) dao.deletePaths(gone.toList())
        }
        importPlaylists(playlists)
        val elapsed = SystemClock.elapsedRealtime() - started
        CruiseLog.log("scan", "done: $seen files, $read read, ${elapsed / 1000} s")
        _state.value = ScanState(running = false, filesSeen = seen, filesRead = read, message = "", lastScanMs = elapsed)
        artwork.prepareAlbumThumbnails()
    }

    private fun entity(f: File, volume: String, t: AudioTags, dir: File, hidden: Boolean): TrackEntity {
        val title = t.title ?: AudioFiles.displayName(f)
        // Untagged files: read "Artist/1973 - Album" folder layouts.
        val (folderAlbum, folderYear) = folderAlbum(dir.name)
        val artist = t.artist ?: if (t.album == null) folderArtist(dir, volume).orEmpty() else ""
        val album = t.album ?: folderAlbum
        val albumArtist = t.albumArtist ?: if (t.compilation) VARIOUS else artist
        return TrackEntity(
            path = f.path,
            filePath = f.path,
            folder = dir.path,
            volume = volume,
            fileName = f.name,
            size = f.length(),
            modified = f.lastModified(),
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = t.genre ?: "",
            composer = t.composer ?: "",
            year = t.year ?: folderYear ?: 0,
            trackNo = t.trackNumber ?: trackFromName(f),
            discNo = t.discNumber ?: 1,
            durationMs = t.durationMs,
            sampleRate = t.sampleRate,
            bitDepth = t.bitDepth,
            channels = t.channels,
            bitrate = t.bitrateKbps,
            codec = t.codec,
            lossless = t.lossless,
            rgTrackGain = t.rgTrackGain,
            rgTrackPeak = t.rgTrackPeak,
            rgAlbumGain = t.rgAlbumGain,
            rgAlbumPeak = t.rgAlbumPeak,
            hasArt = t.hasPicture,
            lyrics = t.lyrics,
            compilation = t.compilation,
            albumKey = albumKey(t.albumArtist ?: if (t.compilation) VARIOUS else null, album, dir.path, t.album == null),
            artistKey = key(albumArtist.ifBlank { artist }),
            cueStartMs = null,
            cueEndMs = null,
            hidden = hidden,
            dateAdded = System.currentTimeMillis(),
        )
    }

    private suspend fun importPlaylists(files: List<File>) {
        val now = System.currentTimeMillis()
        for (f in files) {
            val paths = runCatching { PlaylistFiles.read(f) }.getOrDefault(emptyList())
            if (paths.isEmpty()) continue
            val existing = dao.playlistBySource(f.path)
            val id = existing?.id ?: dao.insertPlaylist(PlaylistEntity(name = f.nameWithoutExtension, created = now, modified = now, sourceFile = f.path))
            dao.replacePlaylistItems(id, paths, now)
        }
    }

    /** Depth-first walk, folder by folder; skips system and hidden folders. */
    private suspend fun walk(root: File, internal: Boolean, onDir: suspend (File, List<File>) -> Unit) {
        val stack = ArrayDeque<File>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            val files = ArrayList<File>(children.size)
            for (c in children) {
                if (c.isDirectory) {
                    val name = c.name.lowercase()
                    if (!name.startsWith(".") && name !in SKIP && !(internal && name in SKIP_INTERNAL)) stack.add(c)
                } else {
                    files += c
                }
            }
            if (files.isNotEmpty()) onDir(dir, files)
        }
    }

    private fun trackFromName(f: File): Int = Regex("^(\\d{1,3})").find(f.nameWithoutExtension.trim())?.value?.toInt() ?: 0

    companion object {
        const val VARIOUS = "Various Artists"
        private const val BATCH = 100
        private val SKIP = setOf("android", "lost.dir", "system volume information", "\$recycle.bin", "cruise-fieldtest")
        private val SKIP_INTERNAL = setOf("notifications", "ringtones", "alarms", "dcim", "pictures", "movies")

        fun key(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")

        private val yearPrefix = Regex("""^[(\[]?((?:19|20)\d{2})[)\]]?\s*[-–.]?\s+(.+)$""")
        private val genericFolders = setOf(
            "music", "musique", "musik", "mp3", "flac", "audio", "albums", "media", "download", "downloads",
            "compilations", "compilation", "various", "various artists", "va", "singles", "misc", "divers", "soundtracks", "ost",
        )

        /** "1973 - The Dark Side of the Moon" / "(1973) Album" → album name and year; other names unchanged. */
        fun folderAlbum(name: String): Pair<String, Int?> =
            yearPrefix.find(name.trim())?.let { it.groupValues[2].trim() to it.groupValues[1].toInt() } ?: (name to null)

        /** The folder above the album folder names the artist, unless it is the stick's root or a generic "Music". */
        fun folderArtist(albumDir: File, volume: String): String? {
            val parent = albumDir.parentFile ?: return null
            if (parent.path == volume || parent.path.length <= volume.length) return null
            return parent.name.takeUnless { it.lowercase() in genericFolders }
        }

        /** Album identity: album artist + title; without an album artist the folder decides (SPEC LIB-9). */
        fun albumKey(albumArtist: String?, album: String, folder: String, albumFromFolder: Boolean): String = when {
            albumFromFolder -> "folder:$folder"
            !albumArtist.isNullOrBlank() -> "${key(albumArtist)}|${key(album)}"
            else -> "folder:$folder|${key(album)}"
        }
    }
}
