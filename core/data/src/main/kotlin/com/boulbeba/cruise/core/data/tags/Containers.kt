package com.boulbeba.cruise.core.data.tags

import android.util.Base64

/** Vorbis comment block (FLAC, Ogg Vorbis, Opus): little-endian lengths, "KEY=value" UTF-8 entries. */
internal object VorbisComment {
    fun parse(d: ByteArray, start: Int, b: TagBuilder, wantPicture: Boolean): Picture? {
        var p = start
        if (p + 4 > d.size) return null
        val vendorLen = d.u32le(p).toInt()
        p += 4 + vendorLen
        if (p + 4 > d.size || vendorLen < 0) return null
        val count = d.u32le(p).toInt()
        p += 4
        var picture: Picture? = null
        repeat(count) {
            if (p + 4 > d.size) return picture
            val len = d.u32le(p).toInt()
            p += 4
            if (len < 0 || p + len > d.size) return picture
            val entry = d.text(p, p + len, Charsets.UTF_8)
            p += len
            val eq = entry.indexOf('=')
            if (eq > 0) {
                val key = entry.substring(0, eq)
                val value = entry.substring(eq + 1)
                if (key.equals("METADATA_BLOCK_PICTURE", ignoreCase = true)) {
                    b.hasPicture = true
                    if (wantPicture && picture == null) {
                        picture = runCatching { Base64.decode(value, Base64.DEFAULT) }.getOrNull()?.let { Flac.picture(it, 0) }
                    }
                } else {
                    b.put(key, value)
                }
            }
        }
        return picture
    }
}

/** Native FLAC: STREAMINFO, VORBIS_COMMENT and PICTURE metadata blocks. */
internal object Flac {
    fun read(r: BinaryReader, b: TagBuilder, wantPicture: Boolean): Picture? {
        var offset = Id3v2.size(r)
        r.position = offset
        if (r.ascii(4) != "fLaC") return null
        b.codec = "FLAC"
        b.lossless = true
        var picture: Picture? = null
        var last = false
        while (!last && r.position + 4 <= r.length) {
            val header = r.u8()
            last = (header and 0x80) != 0
            val type = header and 0x7F
            val len = (r.u8() shl 16) or (r.u8() shl 8) or r.u8()
            val blockStart = r.position
            when (type) {
                0 -> {
                    val s = r.bytes(len.coerceAtMost(34))
                    if (s.size >= 18) {
                        b.sampleRate = ((s[10].toInt() and 0xFF) shl 12) or ((s[11].toInt() and 0xFF) shl 4) or ((s[12].toInt() and 0xF0) shr 4)
                        b.channels = ((s[12].toInt() and 0x0E) shr 1) + 1
                        b.bitDepth = (((s[12].toInt() and 0x01) shl 4) or ((s[13].toInt() and 0xF0) shr 4)) + 1
                        val total = ((s[13].toLong() and 0x0F) shl 32) or s.u32be(14)
                        if (b.sampleRate > 0) b.durationMs = total * 1000 / b.sampleRate
                    }
                }
                4 -> VorbisComment.parse(r.bytes(len), 0, b, wantPicture = false)
                6 -> {
                    b.hasPicture = true
                    if (wantPicture) {
                        val pic = picture(r.bytes(len), 0)
                        if (pic != null && (picture == null || pic.type == 3)) picture = pic
                    }
                }
            }
            r.position = blockStart + len
        }
        offset = r.position
        if (b.durationMs > 0) b.bitrateKbps = ((r.length - offset) * 8 / b.durationMs).toInt()
        return picture
    }

    /** FLAC PICTURE block layout (also used, base64-encoded, by Ogg METADATA_BLOCK_PICTURE). */
    fun picture(d: ByteArray, at: Int): Picture? {
        var p = at
        if (p + 8 > d.size) return null
        val type = d.u32be(p).toInt(); p += 4
        val mimeLen = d.u32be(p).toInt(); p += 4
        if (mimeLen < 0 || p + mimeLen > d.size) return null
        val mime = d.latin1(p, p + mimeLen); p += mimeLen
        val descLen = d.u32be(p).toInt(); p += 4 + descLen + 16
        if (p + 4 > d.size) return null
        val dataLen = d.u32be(p).toInt(); p += 4
        if (dataLen <= 0 || p + dataLen > d.size) return null
        return Picture(type, mime, d.copyOfRange(p, p + dataLen))
    }
}

/** Ogg Vorbis and Opus: identification + comment headers from the first pages, duration from the last granule. */
internal object Ogg {
    fun read(r: BinaryReader, b: TagBuilder, wantPicture: Boolean): Picture? {
        r.position = 0
        val packets = firstPackets(r, 2, maxBytes = if (wantPicture) 16 * 1024 * 1024 else 1024 * 1024) ?: return null
        val id = packets.getOrNull(0) ?: return null
        var preSkip = 0
        var rate: Int
        var picture: Picture? = null
        when {
            id.size >= 16 && id.latin1(1, 7) == "vorbis" -> {
                b.codec = "Vorbis"
                b.channels = id[11].toInt() and 0xFF
                rate = id.u32le(12).toInt()
                b.sampleRate = rate
                packets.getOrNull(1)?.takeIf { it.size > 7 && it.latin1(1, 7) == "vorbis" }?.let {
                    picture = VorbisComment.parse(it, 7, b, wantPicture)
                }
            }
            id.size >= 19 && id.latin1(0, 8) == "OpusHead" -> {
                b.codec = "Opus"
                b.channels = id[9].toInt() and 0xFF
                preSkip = id.u16le(10)
                b.sampleRate = id.u32le(12).toInt().takeIf { it > 0 } ?: 48000
                rate = 48000
                packets.getOrNull(1)?.takeIf { it.size > 8 && it.latin1(0, 8) == "OpusTags" }?.let {
                    picture = VorbisComment.parse(it, 8, b, wantPicture)
                }
            }
            else -> return null
        }
        val granule = lastGranule(r)
        if (granule > 0 && rate > 0) {
            b.durationMs = (granule - preSkip).coerceAtLeast(0) * 1000 / rate
            b.bitrateKbps = (r.length * 8 / b.durationMs.coerceAtLeast(1)).toInt()
        }
        return picture
    }

    private fun firstPackets(r: BinaryReader, count: Int, maxBytes: Int): List<ByteArray>? {
        val packets = mutableListOf<ByteArray>()
        var current = java.io.ByteArrayOutputStream()
        var read = 0
        while (packets.size < count && r.position + 27 <= r.length && read < maxBytes) {
            if (r.ascii(4) != "OggS") return if (packets.isEmpty()) null else packets
            r.skip(22)
            val segments = r.u8()
            val lacing = r.bytes(segments)
            for (lace in lacing) {
                val len = lace.toInt() and 0xFF
                current.write(r.bytes(len))
                read += len
                if (len < 255) {
                    packets += current.toByteArray()
                    current = java.io.ByteArrayOutputStream()
                    if (packets.size >= count) return packets
                }
            }
        }
        return packets
    }

    private fun lastGranule(r: BinaryReader): Long {
        val tail = minOf(65_536L, r.length)
        r.position = r.length - tail
        val d = r.bytes(tail.toInt())
        var i = d.size - 27
        while (i >= 0) {
            if (d[i] == 'O'.code.toByte() && d[i + 1] == 'g'.code.toByte() && d[i + 2] == 'g'.code.toByte() && d[i + 3] == 'S'.code.toByte()) {
                val granule = d.u32le(i + 6) or (d.u32le(i + 10) shl 32)
                if (granule > 0) return granule
            }
            i--
        }
        return 0
    }
}

/** MP4 / M4A (AAC, ALAC): mvhd duration, stsd sample entry, ilst iTunes tags (incl. freeform ReplayGain). */
internal object Mp4 {
    private val containers = setOf("moov", "trak", "mdia", "minf", "stbl", "udta", "ilst")

    fun read(r: BinaryReader, b: TagBuilder, wantPicture: Boolean): Picture? {
        var picture: Picture? = null
        fun walk(start: Long, end: Long, depth: Int) {
            var p = start
            while (p + 8 <= end && depth < 10) {
                r.position = p
                var size = r.u32be()
                val type = r.ascii(4)
                var header = 8L
                if (size == 1L) { size = r.u64be(); header = 16 }
                if (size == 0L) size = end - p
                if (size < header || p + size > end) return
                val bodyStart = p + header
                val bodyEnd = p + size
                when {
                    type in containers -> walk(bodyStart, bodyEnd, depth + 1)
                    type == "meta" -> {
                        r.position = bodyStart
                        val versionFlags = r.u32be()
                        walk(if (versionFlags == 0L) bodyStart + 4 else bodyStart, bodyEnd, depth + 1)
                    }
                    type == "mvhd" -> {
                        r.position = bodyStart
                        val version = r.u8()
                        r.skip(3)
                        val (timescale, duration) = if (version == 1) {
                            r.skip(16); r.u32be() to r.u64be()
                        } else {
                            r.skip(8); r.u32be() to r.u32be()
                        }
                        if (timescale > 0) b.durationMs = duration * 1000 / timescale
                    }
                    type == "stsd" -> {
                        r.position = bodyStart + 8
                        val entrySize = r.u32be()
                        val format = r.ascii(4)
                        val entryStart = r.position
                        b.codec = when (format) { "alac" -> "ALAC"; "mp4a" -> "AAC"; "fLaC" -> "FLAC"; "Opus" -> "Opus"; else -> format.uppercase() }
                        b.lossless = format == "alac" || format == "fLaC"
                        r.position = entryStart + 16
                        b.channels = r.u16be()
                        b.bitDepth = if (b.lossless) r.u16be() else { r.skip(2); 0 }
                        r.position = entryStart + 24
                        b.sampleRate = (r.u32be() shr 16).toInt()
                        if (format == "alac" && entrySize >= 36 + 36) {
                            r.position = entryStart + 28 + 8 + 4 + 5
                            b.bitDepth = r.u8()
                            r.position = entryStart + 28 + 8 + 4 + 20
                            r.u32be().toInt().takeIf { it > 0 }?.let { b.sampleRate = it }
                        }
                    }
                    depth >= 1 && isIlstItem(type) -> item(r, type, bodyStart, bodyEnd, b, wantPicture)?.let { if (picture == null) picture = it }
                }
                p = bodyEnd
            }
        }
        walk(0, r.length, 0)
        if (b.durationMs > 0) b.bitrateKbps = (r.length * 8 / b.durationMs).toInt()
        return picture
    }

    private fun isIlstItem(type: String) = type[0] == '©' || type in setOf("aART", "trkn", "disk", "gnre", "cpil", "covr", "----", "tmpo")

    private fun item(r: BinaryReader, type: String, start: Long, end: Long, b: TagBuilder, wantPicture: Boolean): Picture? {
        var mean: String? = null
        var name: String? = null
        var p = start
        while (p + 8 <= end) {
            r.position = p
            val size = r.u32be()
            val child = r.ascii(4)
            if (size < 8 || p + size > end) return null
            val payloadLen = (size - 8).toInt()
            when (child) {
                "mean" -> { r.skip(4); mean = r.ascii(payloadLen - 4) }
                "name" -> { r.skip(4); name = String(r.bytes(payloadLen - 4), Charsets.UTF_8) }
                "data" -> {
                    val dataType = (r.u32be() and 0xFFFFFF).toInt()
                    r.skip(4)
                    val len = payloadLen - 8
                    if (type == "covr") {
                        b.hasPicture = true
                        return if (wantPicture && len > 0) Picture(3, if (dataType == 14) "image/png" else "image/jpeg", r.bytes(len)) else null
                    }
                    val data = r.bytes(len.coerceAtMost(1 shl 20))
                    when (type) {
                        "trkn", "disk" -> if (data.size >= 6) {
                            val n = data.u16be(2)
                            val t = data.u16be(4)
                            b.put(if (type == "trkn") "TRACKNUMBER" else "DISCNUMBER", if (t > 0) "$n/$t" else "$n")
                        }
                        "gnre" -> if (data.size >= 2) Id3Genres.name(data.u16be(0) - 1)?.let { b.put("GENRE", it) }
                        "cpil" -> if (data.isNotEmpty()) b.put("COMPILATION", (data[0].toInt() and 1).toString())
                        "----" -> if (name != null) b.put(name!!, String(data, Charsets.UTF_8))
                        else -> {
                            val text = String(data, Charsets.UTF_8)
                            when (type) {
                                "©nam" -> b.put("TITLE", text)
                                "©ART" -> b.put("ARTIST", text)
                                "©alb" -> b.put("ALBUM", text)
                                "aART" -> b.put("ALBUMARTIST", text)
                                "©gen" -> b.put("GENRE", text)
                                "©wrt" -> b.put("COMPOSER", text)
                                "©day" -> b.put("DATE", text)
                                "©lyr" -> b.put("LYRICS", text)
                            }
                        }
                    }
                }
            }
            p += size
        }
        if (mean != null && name == null) return null
        return null
    }
}

/** RIFF WAVE: fmt chunk, data length, LIST/INFO tags and an embedded "id3 " chunk. */
internal object Wav {
    fun read(r: BinaryReader, b: TagBuilder, wantPicture: Boolean): Picture? {
        r.position = 0
        if (r.ascii(4) != "RIFF") return null
        r.skip(4)
        if (r.ascii(4) != "WAVE") return null
        b.codec = "PCM"
        b.lossless = true
        var byteRate = 0L
        var dataSize = 0L
        var picture: Picture? = null
        while (r.position + 8 <= r.length) {
            val id = r.ascii(4)
            val size = r.u32le()
            val start = r.position
            when (id) {
                "fmt " -> {
                    val format = r.u16le()
                    b.channels = r.u16le()
                    b.sampleRate = r.u32le().toInt()
                    byteRate = r.u32le()
                    r.skip(2)
                    b.bitDepth = r.u16le()
                    if (format == 3) b.codec = "PCM float"
                }
                "data" -> dataSize = size
                "LIST" -> if (r.ascii(4) == "INFO") {
                    while (r.position + 8 <= start + size) {
                        val key = r.ascii(4)
                        val len = r.u32le().toInt()
                        val value = String(r.bytes(len), Charsets.UTF_8).trimEnd('\u0000')
                        when (key) {
                            "INAM" -> b.put("TITLE", value)
                            "IART" -> b.put("ARTIST", value)
                            "IPRD" -> b.put("ALBUM", value)
                            "IGNR" -> b.put("GENRE", value)
                            "ICRD" -> b.put("DATE", value)
                            "ITRK", "IPRT" -> b.put("TRACKNUMBER", value)
                        }
                        if (len % 2 == 1) r.skip(1)
                    }
                }
                "id3 ", "ID3 " -> picture = Id3v2.read(r, b, wantPicture, offset = start)
            }
            r.position = start + size + (size and 1)
        }
        if (byteRate > 0) {
            b.durationMs = dataSize * 1000 / byteRate
            b.bitrateKbps = (byteRate * 8 / 1000).toInt()
        }
        return picture
    }
}
