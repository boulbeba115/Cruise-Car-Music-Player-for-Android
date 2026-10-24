package com.boulbeba.cruise.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.boulbeba.cruise.core.data.artwork.ArtworkCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The app's artwork cache, provided by the activity. */
val LocalArtworkCache = staticCompositionLocalOf<ArtworkCache?> { null }

/**
 * Album / track cover thumbnail, loaded off the main thread from the disk cache (SPEC ART-3). Shows the folder's
 * placeholder cover while loading and when there is no art (CHANGE_REQUESTS.md CR-08), so lists never jump.
 */
@Composable
fun CoverImage(
    key: String,
    trackPath: String,
    folder: String,
    label: String,
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    sizePx: Int = ArtworkCache.THUMB,
) {
    val cache = LocalArtworkCache.current
    var image by remember(key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key, sizePx) {
        if (cache != null) image = withContext(Dispatchers.IO) { cache.thumbnail(key, trackPath, folder, sizePx)?.asImageBitmap() }
    }
    Box(modifier.then(if (shape != null) Modifier.clip(shape) else Modifier)) {
        val img = image
        if (img != null) Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else PlaceholderCover(trackPath.ifBlank { key }, sizePx = sizePx)
    }
}
