package com.boulbeba.cruise.core.data.tags

import java.io.File
import java.nio.charset.Charset

/** One track of a CUE sheet over a single audio file (SPEC LIB-12). */
data class CueTrack(val number: Int, val title: String?, val performer: String?, val startMs: Long, val endMs: Long?)

data class CueSheet(val audioFile: File, val title: String?, val performer: String?, val genre: String?, val year: Int?, val tracks: List<CueTrack>)

object CueParser {

    /** Parses a .cue file; null if it does not describe exactly one existing audio file. */
    fun parse(cue: File): CueSheet? = runCatching {
        val lines = readText(cue).lines()
        var file: String? = null
        var title: String? = null
        var performer: String? = null
        var genre: String? = null
        var year: Int? = null
        data class Pending(val number: Int, var title: String? = null, var performer: String? = null, var start: Long = -1)
        val tracks = mutableListOf<Pending>()
        for (raw in lines) {
            val line = raw.trim()
            val upper = line.uppercase()
            when {
                upper.startsWith("FILE ") -> {
                    if (file != null) return null // multi-file sheets are already split: nothing to do
                    file = quoted(line.substring(5)) ?: line.substring(5).substringBeforeLast(' ').trim()
                }
                upper.startsWith("TRACK ") -> tracks += Pending(line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: (tracks.size + 1))
                upper.startsWith("TITLE ") -> quoted(line.substring(6)).let { if (tracks.isEmpty()) title = it else tracks.last().title = it }
                upper.startsWith("PERFORMER ") -> quoted(line.substring(10)).let { if (tracks.isEmpty()) performer = it else tracks.last().performer = it }
                upper.startsWith("REM GENRE ") -> genre = quoted(line.substring(10)) ?: line.substring(10).trim()
                upper.startsWith("REM DATE ") -> year = Regex("\\d{4}").find(line)?.value?.toInt()
                upper.startsWith("INDEX 01 ") && tracks.isNotEmpty() -> tracks.last().start = time(line.substring(9).trim())
            }
        }
        val audio = file?.let { File(cue.parentFile, it) }?.takeIf { it.isFile }
            ?: cue.parentFile?.listFiles()?.firstOrNull { it.nameWithoutExtension == cue.nameWithoutExtension && it.extension.lowercase() in setOf("flac", "wav", "ape", "wv", "m4a") }
            ?: return null
        val valid = tracks.filter { it.start >= 0 }
        if (valid.size < 2) return null
        CueSheet(
            audioFile = audio,
            title = title,
            performer = performer,
            genre = genre,
            year = year,
            tracks = valid.mapIndexed { i, t -> CueTrack(t.number, t.title, t.performer, t.start, valid.getOrNull(i + 1)?.start) },
        )
    }.getOrNull()

    private fun quoted(s: String): String? = Regex("\"([^\"]*)\"").find(s)?.groupValues?.get(1)

    /** mm:ss:ff, 75 frames per second. */
    private fun time(s: String): Long {
        val p = s.split(':').map { it.trim().toLong() }
        return if (p.size == 3) (p[0] * 60 + p[1]) * 1000 + p[2] * 1000 / 75 else -1
    }

    /** CUE files are often in a legacy code page; UTF-8 when it decodes cleanly, else Windows-1252. */
    internal fun readText(file: File): String {
        val bytes = file.readBytes()
        val utf8 = String(bytes, Charsets.UTF_8)
        return if ('\uFFFD' in utf8) String(bytes, Charset.forName("windows-1252")) else utf8.removePrefix("\uFEFF")
    }
}

/** A synced lyrics line. */
data class LyricLine(val timeMs: Long, val text: String)

/** LRC parser (SPEC ART-7): "[mm:ss.xx]text", several time tags per line, [offset:+/-ms]. */
object LrcParser {
    private val tag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")

    fun parse(text: String): List<LyricLine> {
        var offset = 0L
        val result = mutableListOf<LyricLine>()
        for (raw in text.lines()) {
            val line = raw.trim()
            Regex("^\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE).find(line)?.let { offset = it.groupValues[1].toLong() }
            val stamps = tag.findAll(line).toList()
            if (stamps.isEmpty()) continue
            val content = line.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val (min, sec, frac) = m.destructured
                val fracMs = when (frac.length) { 0 -> 0; 1 -> frac.toInt() * 100; 2 -> frac.toInt() * 10; else -> frac.take(3).toInt() }
                result += LyricLine(((min.toLong() * 60 + sec.toLong()) * 1000 + fracMs - offset).coerceAtLeast(0), content)
            }
        }
        return result.sortedBy { it.timeMs }
    }

    /** `Song.lrc` next to `Song.flac`. */
    fun sidecar(audio: File): File? = File(audio.parentFile, audio.nameWithoutExtension + ".lrc").takeIf { it.isFile }
}

/** M3U / M3U8 / PLS reading and M3U8 writing with paths relative to the playlist (SPEC Q-3, Q-4). */
object PlaylistFiles {
    fun read(file: File): List<String> {
        val text = CueParser.readText(file)
        val dir = file.parentFile
        val entries = if (file.extension.equals("pls", true)) {
            text.lines().mapNotNull { Regex("^File\\d+=(.*)$", RegexOption.IGNORE_CASE).find(it.trim())?.groupValues?.get(1) }
        } else {
            text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        }
        return entries.mapNotNull { entry ->
            val normalized = entry.removePrefix("file://").replace('\\', '/')
            val f = if (normalized.startsWith("/")) File(normalized) else File(dir, normalized)
            runCatching { f.canonicalFile }.getOrNull()?.takeIf { it.isFile }?.path ?: reanchor(normalized, dir)
        }
    }

    /**
     * Playlists made on a PC point at `E:/Music/Artist/song.mp3` or at another machine's folders. Look for the same
     * trailing path next to the playlist and in its parent folders, longest match first.
     */
    private fun reanchor(path: String, dir: File?): String? {
        val parts = path.substringAfter(':').split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        var base = dir
        repeat(4) {
            val b = base ?: return null
            for (k in parts.size downTo 1) {
                val candidate = File(b, parts.takeLast(k).joinToString("/"))
                if (candidate.isFile) return runCatching { candidate.canonicalPath }.getOrNull()
            }
            base = b.parentFile
        }
        return null
    }

    fun write(file: File, paths: List<String>, titles: (String) -> String?) {
        val dir = file.parentFile
        val text = buildString {
            appendLine("#EXTM3U")
            for (p in paths) {
                titles(p)?.let { appendLine("#EXTINF:-1,$it") }
                appendLine(dir?.let { File(p).relativeToOrNull(it)?.path } ?: p)
            }
        }
        file.writeText(text)
    }
}
