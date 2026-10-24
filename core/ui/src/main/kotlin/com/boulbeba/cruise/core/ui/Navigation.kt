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

/** An audio file opened from another app, played once the player is connected. */
object PlayRequests {
    val path = MutableStateFlow<String?>(null)
}

/**
 * Storage access (CHANGE_REQUESTS.md CR-03): the app shell asks for it on launch and keeps [granted] current;
 * screens that need it call [request] to ask again.
 */
object StorageAccess {
    val granted = MutableStateFlow(true)
    /** Bumped to ask the shell to show the permission request (a counter, so asking twice still triggers). */
    val asks = MutableStateFlow(0)

    fun request() { asks.value++ }
}
