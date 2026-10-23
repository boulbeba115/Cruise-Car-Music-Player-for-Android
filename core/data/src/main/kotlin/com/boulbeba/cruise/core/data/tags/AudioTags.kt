package com.boulbeba.cruise.core.data.tags

/** Everything the library needs from one audio file (SPEC LIB-3). Missing values are null / 0. */
data class AudioTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val composer: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val discTotal: Int? = null,
    val compilation: Boolean = false,
    val durationMs: Long = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 0,
    val bitrateKbps: Int = 0,
    val codec: String = "",
    val lossless: Boolean = false,
    val rgTrackGain: Float? = null,
    val rgTrackPeak: Float? = null,
    val rgAlbumGain: Float? = null,
    val rgAlbumPeak: Float? = null,
    val hasPicture: Boolean = false,
    val lyrics: String? = null,
)

/**
 * Collects tag fields under Vorbis-comment style names (TITLE, ARTIST, REPLAYGAIN_TRACK_GAIN …) from any format,
 * then normalises them. Repeated fields (several ARTIST comments) are joined with "; " (SPEC LIB-8).
 */
internal class TagBuilder {
    private val fields = LinkedHashMap<String, MutableList<String>>()
    var durationMs = 0L
    var sampleRate = 0
    var bitDepth = 0
    var channels = 0
    var bitrateKbps = 0
    var codec = ""
    var lossless = false
    var hasPicture = false

    fun put(key: String, value: String?) {
        val v = value?.trim()?.trimEnd('\u0000')?.trim() ?: return
        if (v.isEmpty()) return
        val k = when (val upper = key.trim().uppercase()) {
            "ALBUM ARTIST", "ALBUM_ARTIST", "BAND" -> "ALBUMARTIST"
            "YEAR", "ORIGINALDATE", "ORIGINALYEAR" -> if (fields.containsKey("DATE")) "ORIGINALDATE" else "DATE"
            "TOTALTRACKS" -> "TRACKTOTAL"
            "TOTALDISCS" -> "DISCTOTAL"
            "UNSYNCEDLYRICS", "UNSYNCED LYRICS" -> "LYRICS"
            else -> upper
        }
        val list = fields.getOrPut(k) { mutableListOf() }
        if (v !in list) list += v
    }

    fun has(key: String) = fields.containsKey(key)

    private fun text(key: String): String? = fields[key]?.joinToString("; ")
    private fun first(key: String): String? = fields[key]?.firstOrNull()

    fun build(): AudioTags {
        val (track, trackTotal) = numberPair(first("TRACKNUMBER"))
        val (disc, discTotal) = numberPair(first("DISCNUMBER"))
        return AudioTags(
            title = text("TITLE"),
            artist = text("ARTIST"),
            album = first("ALBUM"),
            albumArtist = text("ALBUMARTIST"),
            genre = text("GENRE")?.let(Id3Genres::resolve),
            composer = text("COMPOSER"),
            year = (first("DATE") ?: first("ORIGINALDATE"))?.let { Regex("\\d{4}").find(it)?.value?.toInt() },
            trackNumber = track,
            trackTotal = trackTotal ?: first("TRACKTOTAL")?.toIntOrNull(),
            discNumber = disc,
            discTotal = discTotal ?: first("DISCTOTAL")?.toIntOrNull(),
            compilation = first("COMPILATION")?.let { it == "1" || it.equals("true", true) } ?: false,
            durationMs = durationMs,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            bitrateKbps = bitrateKbps,
            codec = codec,
            lossless = lossless,
            rgTrackGain = decibels(first("REPLAYGAIN_TRACK_GAIN")),
            rgTrackPeak = first("REPLAYGAIN_TRACK_PEAK")?.trim()?.toFloatOrNull(),
            rgAlbumGain = decibels(first("REPLAYGAIN_ALBUM_GAIN")),
            rgAlbumPeak = first("REPLAYGAIN_ALBUM_PEAK")?.trim()?.toFloatOrNull(),
            hasPicture = hasPicture,
            lyrics = text("LYRICS"),
        )
    }

    private fun numberPair(value: String?): Pair<Int?, Int?> {
        if (value == null) return null to null
        val parts = value.split('/')
        return parts[0].trim().toIntOrNull() to parts.getOrNull(1)?.trim()?.toIntOrNull()
    }

    private fun decibels(value: String?): Float? =
        value?.replace("dB", "", ignoreCase = true)?.trim()?.replace(',', '.')?.toFloatOrNull()
}

/** ID3v1 genre numbers, still found as "(17)" or "17" in many files. */
internal object Id3Genres {
    private val names = listOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal", "New Age",
        "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
        "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk", "Fusion",
        "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise", "Alternative Rock",
        "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
        "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult",
        "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychedelic",
        "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical",
        "Rock & Roll", "Hard Rock",
    )

    fun name(index: Int): String? = names.getOrNull(index)

    fun resolve(value: String): String =
        value.split("; ").joinToString("; ") { part ->
            val m = Regex("^\\((\\d+)\\)(.*)$").find(part.trim())
            when {
                m != null -> m.groupValues[2].trim().ifEmpty { name(m.groupValues[1].toInt()) ?: part }
                part.trim().toIntOrNull() != null -> name(part.trim().toInt()) ?: part
                else -> part
            }
        }
}
