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
    withColours(bitmap, placeholder = false)
}.getOrDefault(DecodedArtwork(null, null, null))

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
