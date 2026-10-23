package com.boulbeba.cruise.core.data.tags

import java.nio.charset.Charset

/** One embedded picture found in a tag. */
internal class Picture(val type: Int, val mime: String, val data: ByteArray)

/**
 * ID3v2.2 / 2.3 / 2.4 reader. Frames are read one by one; picture frames are skipped (only noted) unless
 * [wantPicture] is set, so scanning a library with large embedded covers stays fast.
 */
internal object Id3v2 {

    /** Returns the total tag size in bytes (header + body + footer), or 0 when there is no tag at [offset]. */
    fun size(r: BinaryReader, offset: Long = 0): Long {
        if (r.length < offset + 10) return 0
        r.position = offset
        val h = r.bytes(10)
        if (h[0] != 'I'.code.toByte() || h[1] != 'D'.code.toByte() || h[2] != '3'.code.toByte()) return 0
        val footer = if (h[3].toInt() == 4 && (h[5].toInt() and 0x10) != 0) 10 else 0
        return 10 + syncsafe(h, 6) + footer
    }

    fun read(r: BinaryReader, b: TagBuilder, wantPicture: Boolean = false, offset: Long = 0): Picture? {
        if (size(r, offset) == 0L) return null
        r.position = offset
        val h = r.bytes(10)
        val major = h[3].toInt()
        val flags = h[5].toInt()
        val tagSize = syncsafe(h, 6).toInt().coerceAtMost(BinaryReader.MAX_READ)
        if (major !in 2..4) return null
        var body = r.bytes(tagSize.coerceAtMost((r.length - r.position).toInt()))
        if (major < 4 && (flags and 0x80) != 0) body = unsynchronise(body)
        var pos = 0
        if ((flags and 0x40) != 0 && major >= 3) {
            pos = if (major == 4) syncsafe(body, 0).toInt() else (body.u32be(0) + 4).toInt()
        }
        var best: Picture? = null
        val idLen = if (major == 2) 3 else 4
        val headerLen = if (major == 2) 6 else 10
        while (pos + headerLen <= body.size) {
            if (body[pos].toInt() == 0) break
            val id = body.latin1(pos, pos + idLen)
            val size = when (major) {
                2 -> ((body[pos + 3].toInt() and 0xFF) shl 16) or ((body[pos + 4].toInt() and 0xFF) shl 8) or (body[pos + 5].toInt() and 0xFF)
                3 -> body.u32be(pos + 4).toInt()
                else -> syncsafe(body, pos + 4).toInt()
            }
            val frameFlags = if (major >= 3) body.u16be(pos + 8) else 0
            val start = pos + headerLen
            val end = start + size
            if (size <= 0 || end > body.size) break
            var data = body.copyOfRange(start, end)
            if (major == 4) {
                if ((frameFlags and 0x0002) != 0) data = unsynchronise(data)
                if ((frameFlags and 0x0001) != 0 && data.size >= 4) data = data.copyOfRange(4, data.size)
            }
            if (major == 3 && (frameFlags and 0x0040) != 0 && data.size >= 1) data = data.copyOfRange(1, data.size)
            val compressedOrEncrypted = (major == 3 && (frameFlags and 0x00C0) != 0) || (major == 4 && (frameFlags and 0x000C) != 0)
            if (!compressedOrEncrypted) frame(id, data, b)?.let { pic ->
                b.hasPicture = true
                if (wantPicture && (best == null || pic.type == 3 && best!!.type != 3)) best = pic
            }
            pos = end
        }
        return best
    }

    private fun frame(id: String, d: ByteArray, b: TagBuilder): Picture? {
        when (id) {
            "TIT2", "TT2" -> b.put("TITLE", text(d))
            "TPE1", "TP1" -> b.put("ARTIST", text(d))
            "TALB", "TAL" -> b.put("ALBUM", text(d))
            "TPE2", "TP2" -> b.put("ALBUMARTIST", text(d))
            "TCON", "TCO" -> b.put("GENRE", text(d))
            "TCOM", "TCM" -> b.put("COMPOSER", text(d))
            "TYER", "TYE", "TDRC", "TDRL" -> b.put("DATE", text(d))
            "TDOR", "TORY", "TOR" -> b.put("ORIGINALDATE", text(d))
            "TRCK", "TRK" -> b.put("TRACKNUMBER", text(d))
            "TPOS", "TPA" -> b.put("DISCNUMBER", text(d))
            "TCMP", "TCP" -> b.put("COMPILATION", text(d))
            "TXXX", "TXX" -> {
                val parts = splitTerminated(d)
                if (parts != null) b.put(parts.first, parts.second)
            }
            "USLT", "ULT" -> lyrics(d)?.let { b.put("LYRICS", it) }
            "APIC" -> return apic(d)
            "PIC" -> return pic(d)
        }
        return null
    }

    fun charset(enc: Int): Charset = when (enc) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    private fun terminatorLength(enc: Int) = if (enc == 1 || enc == 2) 2 else 1

    /** Text frame: encoding byte + text; v2.4 separates several values with NUL. */
    private fun text(d: ByteArray): String? {
        if (d.isEmpty()) return null
        val enc = d[0].toInt()
        val s = d.text(1, d.size, charset(enc))
        return s.split('\u0000').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("; ").ifEmpty { null }
    }

    /** Finds the end of a NUL-terminated string starting at [from] for the given encoding. */
    private fun terminator(d: ByteArray, from: Int, enc: Int): Int {
        val step = terminatorLength(enc)
        var i = from
        while (i + step <= d.size) {
            if (step == 1 && d[i].toInt() == 0) return i
            if (step == 2 && d[i].toInt() == 0 && d[i + 1].toInt() == 0) return i
            i += step
        }
        return d.size
    }

    private fun splitTerminated(d: ByteArray): Pair<String, String>? {
        if (d.isEmpty()) return null
        val enc = d[0].toInt()
        val end = terminator(d, 1, enc)
        val key = d.text(1, end, charset(enc))
        val value = d.text((end + terminatorLength(enc)).coerceAtMost(d.size), d.size, charset(enc)).trimEnd('\u0000')
        return key.trim() to value
    }

    private fun lyrics(d: ByteArray): String? {
        if (d.size < 4) return null
        val enc = d[0].toInt()
        val descEnd = terminator(d, 4, enc)
        return d.text((descEnd + terminatorLength(enc)).coerceAtMost(d.size), d.size, charset(enc)).trimEnd('\u0000').ifBlank { null }
    }

    private fun apic(d: ByteArray): Picture? {
        if (d.size < 4) return null
        val enc = d[0].toInt()
        val mimeEnd = d.indexOfFirstFrom(1) { it.toInt() == 0 }
        val mime = d.latin1(1, mimeEnd)
        val type = d.getOrNull(mimeEnd + 1)?.toInt() ?: 0
        val descEnd = terminator(d, mimeEnd + 2, enc)
        val dataStart = descEnd + terminatorLength(enc)
        if (dataStart >= d.size) return null
        return Picture(type, mime, d.copyOfRange(dataStart, d.size))
    }

    private fun pic(d: ByteArray): Picture? {
        if (d.size < 6) return null
        val enc = d[0].toInt()
        val format = d.latin1(1, 4)
        val type = d[4].toInt()
        val descEnd = terminator(d, 5, enc)
        val dataStart = descEnd + terminatorLength(enc)
        if (dataStart >= d.size) return null
        return Picture(type, if (format.equals("PNG", true)) "image/png" else "image/jpeg", d.copyOfRange(dataStart, d.size))
    }

    private fun syncsafe(b: ByteArray, at: Int): Long =
        ((b[at].toLong() and 0x7F) shl 21) or ((b[at + 1].toLong() and 0x7F) shl 14) or
            ((b[at + 2].toLong() and 0x7F) shl 7) or (b[at + 3].toLong() and 0x7F)

    private fun unsynchronise(d: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(d.size)
        var i = 0
        while (i < d.size) {
            out.write(d[i].toInt())
            if (d[i] == 0xFF.toByte() && i + 1 < d.size && d[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private inline fun ByteArray.indexOfFirstFrom(from: Int, predicate: (Byte) -> Boolean): Int {
        for (i in from until size) if (predicate(this[i])) return i
        return size
    }
}

/** ID3v1 (last 128 bytes): only used for fields the v2 tag did not provide. */
internal object Id3v1 {
    fun read(r: BinaryReader, b: TagBuilder): Boolean {
        if (r.length < 128) return false
        r.position = r.length - 128
        val t = r.bytes(128)
        if (t.latin1(0, 3) != "TAG") return false
        fun field(from: Int, len: Int) = t.latin1(from, from + len).trimEnd('\u0000', ' ')
        if (!b.has("TITLE")) b.put("TITLE", field(3, 30))
        if (!b.has("ARTIST")) b.put("ARTIST", field(33, 30))
        if (!b.has("ALBUM")) b.put("ALBUM", field(63, 30))
        if (!b.has("DATE")) b.put("DATE", field(93, 4))
        if (!b.has("TRACKNUMBER") && t[125].toInt() == 0 && t[126].toInt() != 0) b.put("TRACKNUMBER", (t[126].toInt() and 0xFF).toString())
        if (!b.has("GENRE")) Id3Genres.name(t[127].toInt() and 0xFF)?.let { b.put("GENRE", it) }
        return true
    }
}

/** MPEG audio stream info: sample rate, channels, bitrate and duration (Xing / Info / VBRI, else constant bitrate). */
internal object MpegAudio {
    private val bitratesV1L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val bitratesV1L2 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
    private val bitratesV1L1 = intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
    private val bitratesV2L1 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256)
    private val bitratesV2L23 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    private val sampleRates = arrayOf(intArrayOf(44100, 48000, 32000), intArrayOf(22050, 24000, 16000), intArrayOf(11025, 12000, 8000))

    fun read(r: BinaryReader, audioStart: Long, audioEnd: Long, b: TagBuilder) {
        r.position = audioStart
        val window = r.bytes(minOf(64 * 1024L, audioEnd - audioStart).toInt().coerceAtLeast(0))
        var i = 0
        while (i + 4 <= window.size) {
            if ((window[i].toInt() and 0xFF) == 0xFF && (window[i + 1].toInt() and 0xE0) == 0xE0) {
                val h = window.u32be(i)
                val versionBits = ((h shr 19) and 3).toInt()
                val layerBits = ((h shr 17) and 3).toInt()
                val bitrateIndex = ((h shr 12) and 0xF).toInt()
                val rateIndex = ((h shr 10) and 3).toInt()
                if (versionBits != 1 && layerBits != 0 && bitrateIndex in 1..14 && rateIndex != 3) {
                    val mpeg1 = versionBits == 3
                    val v = when (versionBits) { 3 -> 0; 2 -> 1; else -> 2 }
                    val layer = 4 - layerBits
                    val sampleRate = sampleRates[v][rateIndex]
                    val mono = ((h shr 6) and 3).toInt() == 3
                    val bitrate = when {
                        mpeg1 && layer == 3 -> bitratesV1L3[bitrateIndex]
                        mpeg1 && layer == 2 -> bitratesV1L2[bitrateIndex]
                        mpeg1 -> bitratesV1L1[bitrateIndex]
                        layer == 1 -> bitratesV2L1[bitrateIndex]
                        else -> bitratesV2L23[bitrateIndex]
                    }
                    val samplesPerFrame = when {
                        layer == 1 -> 384
                        layer == 3 && !mpeg1 -> 576
                        else -> 1152
                    }
                    b.sampleRate = sampleRate
                    b.channels = if (mono) 1 else 2
                    b.codec = if (layer == 3) "MP3" else "MP$layer"
                    val sideInfo = if (mpeg1) (if (mono) 17 else 32) else (if (mono) 9 else 17)
                    val xing = i + 4 + sideInfo
                    val frames: Long? = when {
                        xing + 12 <= window.size && window.latin1(xing, xing + 4).let { it == "Xing" || it == "Info" } -> {
                            val flags = window.u32be(xing + 4)
                            if ((flags and 1L) != 0L) window.u32be(xing + 8) else null
                        }
                        i + 36 + 18 <= window.size && window.latin1(i + 36, i + 40) == "VBRI" -> window.u32be(i + 36 + 14)
                        else -> null
                    }
                    val audioBytes = audioEnd - (audioStart + i)
                    if (frames != null && frames > 0) {
                        b.durationMs = frames * samplesPerFrame * 1000L / sampleRate
                        b.bitrateKbps = if (b.durationMs > 0) (audioBytes * 8 / b.durationMs).toInt() else bitrate
                    } else {
                        b.bitrateKbps = bitrate
                        b.durationMs = audioBytes * 8 / bitrate
                    }
                    return
                }
            }
            i++
        }
    }
}
