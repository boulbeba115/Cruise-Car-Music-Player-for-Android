@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.boulbeba.cruise.core.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.boulbeba.cruise.core.model.MediaIds
import java.io.File

/** Media items are identified by [MediaIds], so they survive the trip through a MediaController and the saved queue. */
object MediaItems {
    fun fromPath(id: String): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.fromFile(File(MediaIds.filePath(id))))
            // No title/album here: Media3 lets item metadata override the file's tags. Untagged files fall back to
            // the file and folder names in PlayerConnection.
            .setMediaMetadata(MediaMetadata.Builder().setIsPlayable(true).setIsBrowsable(false).build())
        MediaIds.clip(id)?.let { (start, end) ->
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(start)
                    .apply { if (end != null) setEndPositionMs(end) }
                    .build(),
            )
        }
        return builder.build()
    }

    /** Controllers send items without a URI; rebuild them from the id. */
    fun resolve(item: MediaItem): MediaItem =
        if (item.localConfiguration != null) item else fromPath(item.mediaId)
}
