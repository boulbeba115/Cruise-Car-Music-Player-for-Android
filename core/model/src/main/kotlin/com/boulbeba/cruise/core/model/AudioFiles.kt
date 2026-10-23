package com.boulbeba.cruise.core.model

import java.io.File

/**
 * File extensions Cruise plays: those Media3 can demux. WavPack, DSD (dsf/dff), WMA and APE need the FFmpeg
 * demuxer of Phase 6 (SPEC §4.2 Tier 3) and stay hidden until then so they never break a queue.
 */
object AudioFiles {
    val extensions: Set<String> = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "mka", "webm", "amr", "mp2", "m4b", "3gp",
    )

    fun isAudio(file: File): Boolean = file.isFile && file.extension.lowercase() in extensions

    /** Title shown for a file without tags: drops the extension and a leading "01 - " / "01. " track number. */
    fun displayName(file: File): String =
        file.nameWithoutExtension.replace(Regex("^\\d{1,3}\\s*[-._)]\\s*"), "").ifBlank { file.nameWithoutExtension }
}

/**
 * Orders names the way people read them: "Track 2" before "Track 10", case- and accent-insensitive
 * (SPEC §4.5). Digit runs compare by value; everything else by a collation-free lowercase comparison.
 */
object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
            } else {
                val cmp = fold(ca).compareTo(fold(cb))
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    private fun fold(c: Char): Char = when (c.lowercaseChar()) {
        'à', 'á', 'â', 'ä', 'ã', 'å' -> 'a'
        'ç' -> 'c'
        'è', 'é', 'ê', 'ë' -> 'e'
        'ì', 'í', 'î', 'ï' -> 'i'
        'ñ' -> 'n'
        'ò', 'ó', 'ô', 'ö', 'õ' -> 'o'
        'ù', 'ú', 'û', 'ü' -> 'u'
        else -> c.lowercaseChar()
    }

    val files: Comparator<File> = Comparator { x, y -> compare(x.name, y.name) }
}

/** One directory level, as the folder browser shows it. */
data class FolderListing(val dir: File, val folders: List<File>, val tracks: List<File>)

object FolderScanner {

    private val hidden = setOf("android", "lost.dir", "system volume information", "\$recycle.bin", "cruise-fieldtest")

    fun list(dir: File): FolderListing {
        val children = dir.listFiles().orEmpty()
        val folders = children.filter { it.isDirectory && !it.name.startsWith(".") && it.name.lowercase() !in hidden }
            .sortedWith(NaturalOrder.files)
        val tracks = children.filter(AudioFiles::isAudio).sortedWith(NaturalOrder.files)
        return FolderListing(dir, folders, tracks)
    }

    /** Every track below [dir], folder by folder in natural order — for "play folder and subfolders". */
    fun tracksRecursive(dir: File, limit: Int = 20_000): List<File> {
        val result = ArrayList<File>()
        fun walk(d: File) {
            if (result.size >= limit) return
            val listing = list(d)
            result += listing.tracks
            listing.folders.forEach(::walk)
        }
        walk(dir)
        return result
    }
}
