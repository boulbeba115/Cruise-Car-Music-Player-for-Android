package com.boulbeba.cruise.core.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.boulbeba.cruise.core.data.db.LibraryDao
import com.boulbeba.cruise.core.data.tags.TagReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cover art (SPEC ART-1…3): embedded picture first, then the folder's image; decoded once at a small size and kept
 * as JPEG thumbnails on disk plus a bounded memory cache. A missing cover is remembered too, so it is not searched
 * for again on every scroll.
 */
@Singleton
class ArtworkCache @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: LibraryDao,
) {
    private val dir = File(context.cacheDir, "art").apply { mkdirs() }
    private val memory = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Thumbnail for an album (or any track), [size] px square at most. Blocking; call off the main thread. */
    fun thumbnail(key: String, trackPath: String, folder: String, size: Int = THUMB): Bitmap? {
        val id = "${hash(key)}_$size"
        memory.get(id)?.let { return it }
        val file = File(dir, "$id.jpg")
        val none = File(dir, "$id.none")
        if (none.exists()) return null
        val bitmap = if (file.exists()) {
            BitmapFactory.decodeFile(file.path)
        } else {
            val source = sourceBytes(trackPath, folder) ?: run {
                none.createNewFile()
                return null
            }
            decode(source, size)?.also { b ->
                runCatching { file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
            }
        }
        bitmap?.let { memory.put(id, it) }
        return bitmap
    }

    /** Full-resolution source bytes (embedded or folder image) for Now Playing. */
    fun sourceBytes(trackPath: String, folder: String): ByteArray? {
        val audio = File(trackPath.substringBefore('#'))
        return TagReader.readPicture(audio) ?: folderImage(File(folder))?.let { runCatching { it.readBytes() }.getOrNull() }
    }

    /** Pre-builds album thumbnails after a scan so the album grid never decodes while scrolling. */
    suspend fun prepareAlbumThumbnails() {
        for (key in dao.allAlbumKeys()) {
            val track = dao.firstTrackOfAlbum(key) ?: continue
            thumbnail(key, track.path, track.folder)
        }
    }

    fun clear() {
        memory.evictAll()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun decode(bytes: ByteArray, size: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = size.toFloat() / maxOf(decoded.width, decoded.height)
        if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
    }.getOrNull()

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    companion object {
        const val THUMB = 320
        const val LARGE = 640
        private const val MEMORY_BYTES = 12 * 1024 * 1024
        private val names = Regex("^(cover|folder|front|album|albumart.*)\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)

        /** cover.jpg, Folder.png, AlbumArtSmall.jpg… in the folder, or its parent for CD1/Disc 2 folders (SPEC ART-2). */
        fun folderImage(dir: File): File? {
            fun find(d: File) = d.listFiles { f -> f.isFile && names.matches(f.name) }
                ?.sortedBy { if (it.name.startsWith("albumart", ignoreCase = true)) 1 else 0 }?.firstOrNull()
            return find(dir) ?: if (dir.name.matches(Regex("(?i)^(cd|disc|disk)\\s*\\d+$"))) dir.parentFile?.let(::find) else null
        }
    }
}
