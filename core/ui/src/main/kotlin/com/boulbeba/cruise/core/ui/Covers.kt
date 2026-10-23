package com.boulbeba.cruise.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.boulbeba.cruise.core.data.artwork.ArtworkCache
import com.boulbeba.cruise.core.designsystem.theme.CruiseTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The app's artwork cache, provided by the activity. */
val LocalArtworkCache = staticCompositionLocalOf<ArtworkCache?> { null }

/**
 * Album / track cover thumbnail, loaded off the main thread from the disk cache (SPEC ART-3). Shows the generated
 * cover while loading and when there is no art, so lists never jump.
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
        else GeneratedCover(key, label)
    }
}

/** A distinct cover for music without art (SPEC ART-6): gradient seeded by the album, with its initial. */
@Composable
fun GeneratedCover(seed: String, label: String, modifier: Modifier = Modifier, fontSize: TextUnit = TextUnit.Unspecified) {
    val hue = Math.floorMod(seed.hashCode(), 360).toFloat()
    val skinDark = CruiseTheme.colors.isDark
    val a = Color.hsv(hue, 0.55f, if (skinDark) 0.55f else 0.75f)
    val b = Color.hsv((hue + 48f) % 360f, 0.65f, if (skinDark) 0.22f else 0.45f)
    Box(modifier.fillMaxSize().background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
        Text(
            label.trim().firstOrNull { it.isLetterOrDigit() }?.toString()?.uppercase(Locale.ROOT) ?: "♪",
            style = TextStyle(fontFamily = CruiseTheme.typography.display.fontFamily, fontSize = if (fontSize == TextUnit.Unspecified) 40.sp else fontSize),
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}
