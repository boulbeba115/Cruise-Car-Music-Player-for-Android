package com.boulbeba.cruise.core.model

/**
 * Media ids used by the player and the saved queue. A normal track is its file path; a CUE virtual track is
 * "file#N|startMs|endMs" so its clip survives restarts. [libraryPath] gives the id used by the library and stats.
 */
object MediaIds {
    fun of(path: String, cueStartMs: Long? = null, cueEndMs: Long? = null): String =
        if (cueStartMs == null) path else "$path|$cueStartMs|${cueEndMs ?: -1}"

    /** "file#N" or the file path — the library's track key. */
    fun libraryPath(id: String): String = id.substringBefore('|')

    /** The audio file on disk. */
    fun filePath(id: String): String = libraryPath(id).let { p -> if (isCue(id)) p.substringBeforeLast('#') else p }

    fun isCue(id: String): Boolean = id.contains('|')

    /** Clip (start, end or null) of a CUE track id. */
    fun clip(id: String): Pair<Long, Long?>? {
        if (!isCue(id)) return null
        val parts = id.split('|')
        val start = parts.getOrNull(1)?.toLongOrNull() ?: return null
        val end = parts.getOrNull(2)?.toLongOrNull()?.takeIf { it >= 0 }
        return start to end
    }
}
