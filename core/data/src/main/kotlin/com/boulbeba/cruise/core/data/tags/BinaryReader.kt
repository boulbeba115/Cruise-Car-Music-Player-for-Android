package com.boulbeba.cruise.core.data.tags

import java.io.RandomAccessFile
import java.nio.charset.Charset

/** Small helpers over [RandomAccessFile] for tag parsing: big/little-endian ints and text decoding. */
internal class BinaryReader(val file: RandomAccessFile) {
    val length: Long get() = file.length()
    var position: Long
        get() = file.filePointer
        set(value) = file.seek(value)

    fun bytes(count: Int): ByteArray {
        require(count in 0..MAX_READ) { "read of $count bytes refused" }
        val b = ByteArray(count)
        file.readFully(b)
        return b
    }

    fun u8(): Int = file.readUnsignedByte()
    fun u16be(): Int = file.readUnsignedShort()
    fun u32be(): Long = file.readInt().toLong() and 0xFFFFFFFFL
    fun u64be(): Long = file.readLong()
    fun u16le(): Int = u8() or (u8() shl 8)
    fun u32le(): Long = (u8().toLong()) or (u8().toLong() shl 8) or (u8().toLong() shl 16) or (u8().toLong() shl 24)
    fun skip(count: Long) { position += count }

    fun ascii(count: Int): String = String(bytes(count), Charsets.ISO_8859_1)

    companion object {
        const val MAX_READ = 32 * 1024 * 1024
    }
}

internal fun ByteArray.u32be(at: Int): Long =
    ((this[at].toLong() and 0xFF) shl 24) or ((this[at + 1].toLong() and 0xFF) shl 16) or
        ((this[at + 2].toLong() and 0xFF) shl 8) or (this[at + 3].toLong() and 0xFF)

internal fun ByteArray.u32le(at: Int): Long =
    (this[at].toLong() and 0xFF) or ((this[at + 1].toLong() and 0xFF) shl 8) or
        ((this[at + 2].toLong() and 0xFF) shl 16) or ((this[at + 3].toLong() and 0xFF) shl 24)

internal fun ByteArray.u16be(at: Int): Int = ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

internal fun ByteArray.u16le(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.latin1(from: Int, to: Int): String = String(this, from, (to - from).coerceAtLeast(0), Charsets.ISO_8859_1)

internal fun ByteArray.text(from: Int, to: Int, charset: Charset): String =
    if (to <= from) "" else String(this, from, to - from, charset)
