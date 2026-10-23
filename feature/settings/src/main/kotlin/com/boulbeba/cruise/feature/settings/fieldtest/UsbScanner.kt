package com.boulbeba.cruise.feature.settings.fieldtest

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/**
 * Scans a music volume the way the real scanner will (SPEC §6.4) and records what the library looks like:
 * structure, formats, sizes, covers, sidecar files, read speed, and how well Android's own parsers read
 * the files. Call off the main thread.
 */
object UsbScanner {

    private val audioExt = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma", "ape", "wv", "dsf", "dff",
        "aif", "aiff", "aifc", "alac", "mka", "mpc", "tta", "ac3", "dts", "amr", "mp2", "m4b", "tak",
    )
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
    private val sidecarExt = setOf("cue", "m3u", "m3u8", "pls", "lrc", "nfo", "log", "txt")
    private val skipDirs = setOf("android", "lost.dir", "system volume information", "\$recycle.bin", ".trashes", "cruise-fieldtest")
    private val coverNames = Regex("^(cover|folder|front|album|albumart.*|thumb)$", RegexOption.IGNORE_CASE)

    fun scan(context: Context, root: File, onProgress: (String) -> Unit): String {
        val started = System.nanoTime()
        val audioFiles = ArrayList<File>()
        val extCount = HashMap<String, Int>()
        val extBytes = HashMap<String, Long>()
        var files = 0
        var dirs = 0
        var maxDepth = 0
        var audioDirs = 0
        var audioDirsWithCover = 0
        var nonAscii = 0
        var longestPath = 0
        val sampleDirs = ArrayList<String>()
        val rootDepth = root.path.count { it == '/' }

        val stack = ArrayDeque<File>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            dirs++
            val depth = dir.path.count { it == '/' } - rootDepth
            maxDepth = maxOf(maxDepth, depth)
            if (depth in 1..3 && sampleDirs.size < 80) sampleDirs += dir.path.removePrefix(root.path)
            var audioHere = 0
            var coverHere = false
            for (child in children) {
                if (child.isDirectory) {
                    if (child.name.lowercase() !in skipDirs) stack.add(child)
                    continue
                }
                files++
                val ext = child.extension.lowercase()
                extCount[ext] = (extCount[ext] ?: 0) + 1
                extBytes[ext] = (extBytes[ext] ?: 0L) + child.length()
                if (child.name.any { it.code > 127 }) nonAscii++
                longestPath = maxOf(longestPath, child.path.length)
                if (ext in audioExt) {
                    audioHere++
                    audioFiles += child
                } else if (ext in imageExt && coverNames.matches(child.nameWithoutExtension)) {
                    coverHere = true
                }
            }
            if (audioHere > 0) {
                audioDirs++
                if (coverHere) audioDirsWithCover++
            }
            if (dirs % 200 == 0) onProgress("$dirs folders, ${audioFiles.size} audio files…")
        }
        val walkSeconds = (System.nanoTime() - started) / 1e9

        val text = StringBuilder()
        text.appendLine("Root: ${root.path}")
        text.appendLine("Volume: total %.1f GB, free %.1f GB".format(root.totalSpace / 1e9, root.freeSpace / 1e9))
        text.appendLine("Walk: $dirs folders, $files files in %.1f s (%.0f files/s)".format(walkSeconds, files / walkSeconds.coerceAtLeast(0.001)))
        text.appendLine("Max depth: $maxDepth, longest path: $longestPath chars, non-ASCII names: $nonAscii")
        text.appendLine("Audio files: ${audioFiles.size} in $audioDirs folders (%.1f per folder), folders with a cover image: $audioDirsWithCover"
            .format(if (audioDirs == 0) 0.0 else audioFiles.size.toDouble() / audioDirs))
        text.appendLine()
        text.appendLine("By extension (count, size):")
        extCount.entries.sortedByDescending { it.value }.take(40).forEach { (ext, count) ->
            val tag = when (ext) { in audioExt -> "audio"; in imageExt -> "image"; in sidecarExt -> "sidecar"; else -> "" }
            text.appendLine("  %-6s %7d  %8.1f MB  %s".format(ext.ifEmpty { "(none)" }, count, (extBytes[ext] ?: 0L) / 1e6, tag))
        }

        onProgress("Measuring read speed…")
        audioFiles.maxByOrNull { it.length() }?.let { file ->
            val buffer = ByteArray(1 shl 20)
            val limit = 32L shl 20
            var read = 0L
            val t0 = System.nanoTime()
            runCatching {
                FileInputStream(file).use { input ->
                    while (read < limit) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        read += n
                    }
                }
            }
            val s = (System.nanoTime() - t0) / 1e9
            text.appendLine()
            text.appendLine("Sequential read: %.1f MB in %.2f s = %.1f MB/s (%s)".format(read / 1e6, s, read / 1e6 / s.coerceAtLeast(0.001), file.name))
        }

        onProgress("Reading sample files…")
        text.appendLine()
        text.appendLine("Sample files (Android's own parsers):")
        val step = (audioFiles.size / 30).coerceAtLeast(1)
        audioFiles.filterIndexed { i, _ -> i % step == 0 }.take(30).forEach { file ->
            text.appendLine("  ${file.path.removePrefix(root.path)}")
            text.appendLine("    ${probe(file)}")
        }

        text.appendLine()
        text.appendLine("MediaStore (Android's own index): ${mediaStoreCount(context, root)}")
        text.appendLine()
        text.appendLine("Folder sample (depth 1–3):")
        sampleDirs.sorted().forEach { text.appendLine("  $it") }
        return text.toString()
    }

    private fun probe(file: File): String {
        val t0 = System.nanoTime()
        val format = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.path)
                val f: MediaFormat? = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                if (f == null) "no audio track" else buildString {
                    append(f.getString(MediaFormat.KEY_MIME))
                    append(" ${f.intOrNull(MediaFormat.KEY_SAMPLE_RATE)} Hz ${f.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)} ch")
                    f.intOrNull("bits-per-sample")?.let { append(" ${it}-bit") }
                    f.intOrNull(MediaFormat.KEY_PCM_ENCODING)?.let { append(" pcm-enc=$it") }
                    f.intOrNull(MediaFormat.KEY_BIT_RATE)?.let { append(" ${it / 1000} kbps") }
                    if (f.containsKey(MediaFormat.KEY_DURATION)) append(" %.0f s".format(f.getLong(MediaFormat.KEY_DURATION) / 1e6))
                }
            } finally {
                extractor.release()
            }
        }.getOrElse { "extractor failed: ${it.javaClass.simpleName}" }
        val tags = runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(file.path)
                val keys = listOf(
                    "title" to MediaMetadataRetriever.METADATA_KEY_TITLE,
                    "artist" to MediaMetadataRetriever.METADATA_KEY_ARTIST,
                    "album" to MediaMetadataRetriever.METADATA_KEY_ALBUM,
                    "albumartist" to MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST,
                    "track" to MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER,
                    "year" to MediaMetadataRetriever.METADATA_KEY_YEAR,
                    "genre" to MediaMetadataRetriever.METADATA_KEY_GENRE,
                )
                val present = keys.filter { !r.extractMetadata(it.second).isNullOrBlank() }.joinToString(",") { it.first }
                val art = r.embeddedPicture?.size
                "tags[$present] art=${art?.let { "${it / 1024} KB" } ?: "none"}"
            } finally {
                r.release()
            }
        }.getOrElse { "retriever failed: ${it.javaClass.simpleName}" }
        return "$format | $tags | %.0f ms".format((System.nanoTime() - t0) / 1e6)
    }

    private fun MediaFormat.intOrNull(key: String): Int? = if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    @Suppress("DEPRECATION") // DATA is the only way to filter by path before API 29.
    private fun mediaStoreCount(context: Context, root: File): String = runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DATA),
            null, null, null,
        )?.use { cursor ->
            var total = 0
            var onRoot = 0
            val col = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                total++
                if (cursor.getString(col)?.startsWith(root.path) == true) onRoot++
            }
            "$total audio entries in total, $onRoot on this volume"
        } ?: "query returned nothing"
    }.getOrElse { "query failed: ${it.javaClass.simpleName}: ${it.message}" }
}
