package com.boulbeba.cruise.core.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Covers for songs without art (CHANGE_REQUESTS.md CR-08, CR-13): illustrations made with the owner's ComfyUI, one set
 * per skin, so a Neon City song gets a neon-city scene and a Vinyl Lounge song a cozy one. Files are named
 * `cover_<skin id>_<nn>` (drawable-nodpi, kept from resource shrinking by res/raw/keep_covers.xml) and found by name,
 * so adding covers needs no code change. The choice depends on the song's path: it looks random across the library,
 * but a song keeps its cover everywhere — lists, mini player, Now Playing. Prompts: docs/assets/placeholder-covers.md.
 */
object PlaceholderCovers {
    private const val SOURCE_PX = 512
    private const val MAX_PER_SKIN = 64

    private val sets = HashMap<String, IntArray>()

    // Decoded covers, by image and size (a few MB at most: lists use small thumbnails).
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** The covers of [skinId], falling back to every skin's covers for a skin without its own set. */
    @SuppressLint("DiscouragedApi") // the sets are open-ended on purpose; ids are looked up once per skin
    @Synchronized
    fun set(context: Context, skinId: String): IntArray = sets.getOrPut(skinId) {
        fun find(skin: String) = (1..MAX_PER_SKIN).map { n -> context.resources.getIdentifier("cover_${skin}_%02d".format(n), "drawable", context.packageName) }
            .filter { it != 0 }
        find(skinId).ifEmpty { ALL_SKINS.flatMap(::find) }.toIntArray()
    }

    /** The cover for the song at [key] (its path) in [skinId]'s set, decoded at about [sizePx]. Call off the main thread. */
    fun bitmap(context: Context, skinId: String, key: String, sizePx: Int): Bitmap? {
        val ids = set(context, skinId)
        if (ids.isEmpty()) return null
        val id = ids[Math.floorMod(key.hashCode() * 31 + 7, ids.size)]
        var sample = 1
        while (SOURCE_PX / (sample * 2) >= sizePx) sample *= 2
        val cacheKey = "$id/$sample"
        cache.get(cacheKey)?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }
        return BitmapFactory.decodeResource(context.resources, id, options)?.also { cache.put(cacheKey, it) }
    }

    private val ALL_SKINS = listOf("midnight", "neon", "amber", "vinyl", "tape", "aurora", "oled", "daylight")
}

/** The placeholder cover of the song at [key] in the active skin's set, filling its box. */
@Composable
fun PlaceholderCover(key: String, modifier: Modifier = Modifier, sizePx: Int = 256) {
    val context = LocalContext.current
    val skin = CruiseTheme.skin.id
    val image by produceState<ImageBitmap?>(null, key, skin, sizePx) {
        value = withContext(Dispatchers.IO) { PlaceholderCovers.bitmap(context, skin, key, sizePx)?.asImageBitmap() }
    }
    Box(modifier.fillMaxSize().background(CruiseTheme.colors.surfaceElevated)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}
