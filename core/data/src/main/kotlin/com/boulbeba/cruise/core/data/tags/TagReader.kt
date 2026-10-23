package com.boulbeba.cruise.core.data.tags

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads tags and stream info from audio files without native code (SPEC LIB-3). Pure Kotlin readers for MP3 (ID3v2/v1),
 * FLAC, Ogg Vorbis/Opus, MP4 (AAC/ALAC) and WAV; other formats get file-level info only. Never throws.
 */
object TagReader {

    fun read(file: File): AudioTags = runCatching { parse(file, wantPicture = false).first }
        .getOrElse { AudioTags(codec = file.extension.uppercase()) }

    /** The embedded cover (front cover preferred), or null. */
    fun readPicture(file: File): ByteArray? = runCatching { parse(file, wantPicture = true).second?.data }.getOrNull()

    private fun parse(file: File, wantPicture: Boolean): Pair<AudioTags, Picture?> {
        val b = TagBuilder()
        var picture: Picture? = null
        RandomAccessFile(file, "r").use { raf ->
            val r = BinaryReader(raf)
            when (file.extension.lowercase()) {
                "mp3", "mp2" -> {
                    val tagSize = Id3v2.size(r)
                    picture = Id3v2.read(r, b, wantPicture)
                    val hasV1 = Id3v1.read(r, b)
                    MpegAudio.read(r, tagSize, r.length - if (hasV1) 128 else 0, b)
                }
                "flac" -> picture = Flac.read(r, b, wantPicture)
                "ogg", "oga", "opus" -> picture = Ogg.read(r, b, wantPicture)
                "m4a", "m4b", "mp4", "alac", "3gp" -> picture = Mp4.read(r, b, wantPicture)
                "wav" -> picture = Wav.read(r, b, wantPicture)
                "aac" -> {
                    picture = Id3v2.read(r, b, wantPicture)
                    b.codec = "AAC"
                }
                else -> b.codec = file.extension.uppercase()
            }
        }
        if (b.codec.isEmpty()) b.codec = file.extension.uppercase()
        return b.build() to picture
    }
}
