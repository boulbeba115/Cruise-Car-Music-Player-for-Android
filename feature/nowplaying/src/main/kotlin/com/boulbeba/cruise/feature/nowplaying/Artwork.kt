package com.boulbeba.cruise.feature.nowplaying

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.boulbeba.cruise.core.model.MediaIds
import com.boulbeba.cruise.core.ui.PlaceholderCovers
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The current cover and its colours. [placeholder]: no art of its own, the folder's placeholder cover is shown. */
@Immutable
data class DecodedArtwork(val image: ImageBitmap?, val accent: Color?, val deep: Color?, val placeholder: Boolean = false)

/**
 * Decodes embedded cover art at display size on a background thread (the owner's unit has 1 GB RAM: never decode
 * a 3000 px cover at full size) and extracts its colours for the background (SPEC VIS-1).
 */
@Composable
fun rememberArtwork(bytes: ByteArray?, key: String?, trackPath: String?, targetPx: Int): DecodedArtwork {
    var result by remember { mutableStateOf(DecodedArtwork(null, null, null)) }
    val context = LocalContext.current
    val skin = CruiseTheme.skin.id
    val folder = trackPath?.let { File(MediaIds.filePath(it)).parentFile }
    LaunchedEffect(key, trackPath, targetPx, skin) {
        result = withContext(Dispatchers.IO) {
            // Embedded art first, then the folder's image (SPEC ART-1, ART-2), then the skin's placeholder for this song (CR-08, CR-13).
            val data = bytes ?: folder?.let(::folderImage)?.let { runCatching { it.readBytes() }.getOrNull() }
            data?.let { decode(it, targetPx) }?.takeIf { it.image != null }
                ?: trackPath?.let { PlaceholderCovers.bitmap(context, skin, it, targetPx) }?.let { withColours(it, placeholder = true) }
                ?: DecodedArtwork(null, null, null)
        }
    }
    return result
}

private val folderImageNames = Regex("^(cover|folder|front|album|albumart.*)\\.(jpe?g|png|webp)$", RegexOption.IGNORE_CASE)

/** `cover.jpg`, `Folder.png`, `AlbumArtSmall.jpg`… in the track's folder, or the parent of a `CD1`/`Disc 2` folder. */
private fun folderImage(dir: File): File? {
    fun find(d: File) = d.listFiles { f -> f.isFile && folderImageNames.matches(f.name) }
        ?.sortedBy { if (it.name.startsWith("albumart", ignoreCase = true)) 1 else 0 }?.firstOrNull()
    return find(dir) ?: if (dir.name.matches(Regex("(?i)^(cd|disc|disk)\\s*\\d+$"))) dir.parentFile?.let(::find) else null
}

private fun decode(bytes: ByteArray, targetPx: Int): DecodedArtwork = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return DecodedArtwork(null, null, null)
    withColours(trimBorders(bitmap), placeholder = false)
}.getOrDefault(DecodedArtwork(null, null, null))

/**
 * Art taken from videos often carries flat bars (black, or a single colour) around the picture; covers that crop the art
 * to a shape — the Vinyl Lounge record, the Neon billboard — then show the bars as dark corners (owner, 2026-10-24).
 * Rows and columns at the edges that are one flat colour are cut off, at most a third of each side; a cover whose
 * design really has a flat border loses only that border.
 */
internal fun trimBorders(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    if (w < 16 || h < 16) return src
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    fun close(a: Int, b: Int) = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= 26 &&
        abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= 26 && abs((a and 0xFF) - (b and 0xFF)) <= 26
    // A line is border when nearly all of its samples match the colour of the corner it starts from.
    fun flat(ref: Int, at: (Int) -> Int, length: Int): Boolean {
        val samples = 48
        var misses = 0
        for (i in 0 until samples) if (!close(at(i * (length - 1) / (samples - 1)), ref)) misses++
        return misses <= 2
    }
    fun cut(limit: Int, isFlat: (Int) -> Boolean): Int {
        var k = 0
        while (k < limit && isFlat(k)) k++
        return k
    }
    val top = cut(h / 3) { y -> flat(px[y * w], { x -> px[y * w + x] }, w) }
    val bottom = cut(h / 3) { k -> val y = h - 1 - k; flat(px[y * w], { x -> px[y * w + x] }, w) }
    val left = cut(w / 3) { x -> flat(px[x], { y -> px[y * w + x] }, h) }
    val right = cut(w / 3) { k -> val x = w - 1 - k; flat(px[x], { y -> px[y * w + x] }, h) }
    // Ignore slivers (JPEG edges); keep the picture whole when nothing real was found.
    val t = if (top >= h / 50) top else 0
    val b = if (bottom >= h / 50) bottom else 0
    val l = if (left >= w / 50) left else 0
    val r = if (right >= w / 50) right else 0
    if (t + b + l + r == 0 || w - l - r < w / 3 || h - t - b < h / 3) return src
    return Bitmap.createBitmap(src, l, t, w - l - r, h - t - b)
}

private fun withColours(bitmap: Bitmap, placeholder: Boolean): DecodedArtwork {
    val palette = Palette.from(bitmap).maximumColorCount(12).generate()
    // Grey or black-and-white covers have no colour worth following: keep the skin's accent then.
    fun Palette.Swatch.colourful() = hsl[1] >= 0.25f && hsl[2] in 0.15f..0.85f
    val accent = listOfNotNull(palette.vibrantSwatch, palette.lightVibrantSwatch, palette.dominantSwatch)
        .firstOrNull { it.colourful() }?.rgb?.let(::Color)
    val deep = listOfNotNull(palette.darkMutedSwatch, palette.darkVibrantSwatch, palette.dominantSwatch)
        .firstOrNull { it.hsl[1] >= 0.15f }?.rgb?.let(::Color)
    return DecodedArtwork(bitmap.asImageBitmap(), accent, deep, placeholder)
}
