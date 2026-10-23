package com.boulbeba.cruise.core.ui

import com.boulbeba.cruise.core.data.db.TrackRow
import com.boulbeba.cruise.core.model.MediaIds
import kotlinx.coroutines.flow.MutableStateFlow

/** Player id of a library track (keeps the clip of CUE tracks). */
fun TrackRow.mediaId(): String = MediaIds.of(path, cueStartMs, cueEndMs)

/** Cross-screen requests: "go to this folder" / "go to this album" from a long-press menu. */
object NavRequests {
    val folder = MutableStateFlow<String?>(null)
    val album = MutableStateFlow<String?>(null)
}
