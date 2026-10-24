package com.boulbeba.cruise.core.data.library

import com.boulbeba.cruise.core.data.db.FolderRow
import com.boulbeba.cruise.core.model.NaturalOrder
import java.text.Normalizer
import java.util.Locale

/** One folder of the browser: it holds music itself ([row] != null) or somewhere below it. */
class FolderNode internal constructor(val path: String, val volume: String) {
    val name: String get() = path.substring(lastSeparator(path) + 1)

    /** The tracks directly in this folder, or null when it only holds subfolders. */
    var row: FolderRow? = null
        internal set
    var parent: FolderNode? = null
        internal set
    internal val childList = ArrayList<FolderNode>()
    val children: List<FolderNode> get() = childList

    /** Tracks here and in every subfolder. */
    var totalTracks: Int = 0
        internal set

    val directTracks: Int get() = row?.trackCount ?: 0
    val isVolume: Boolean get() = path == volume

    /** A folder that is one album: no subfolders, a single album inside. */
    val isAlbum: Boolean get() = childList.isEmpty() && (row?.albumCount ?: 0) == 1

    /** Nothing to show of its own: no tracks and a single way down. */
    internal val passThrough: Boolean get() = row == null && childList.size == 1
}

/**
 * The folder browser's tree, built only from folders that contain music (CHANGE_REQUESTS.md CR-04): system folders,
 * photos and downloads never appear. Each music folder brings its parents up to the volume root with it.
 */
class FolderTree(rows: List<FolderRow>) {
    private val nodes = HashMap<String, FolderNode>()

    /** Volume roots that hold music, largest first. */
    val volumes: List<FolderNode>

    init {
        for (row in rows) {
            val (node, _) = nodeFor(row.folder, row.volume)
            node.row = row
        }
        // Totals bottom-up: deeper paths first.
        nodes.values.sortedByDescending { it.path.length }.forEach { n ->
            n.totalTracks += n.directTracks
            n.parent?.let { it.totalTracks += n.totalTracks }
        }
        nodes.values.forEach { n -> n.childList.sortWith(compareBy(NaturalOrder) { it.name }) }
        volumes = nodes.values.filter { it.parent == null }.sortedByDescending { it.totalTracks }
    }

    private fun nodeFor(path: String, volume: String): Pair<FolderNode, Boolean> {
        nodes[path]?.let { return it to false }
        val node = FolderNode(path, volume)
        nodes[path] = node
        if (path != volume && path.length > volume.length + 1 && path.startsWith(volume) && isSeparator(path[volume.length])) {
            val (parent, _) = nodeFor(path.substring(0, lastSeparator(path)), volume)
            node.parent = parent
            parent.childList += node
        }
        return node to true
    }

    val isEmpty: Boolean get() = volumes.isEmpty()

    fun node(path: String): FolderNode? = nodes[path]

    /** Skips folders with no music of their own and a single subfolder: "USB › Music › Rock" opens on Rock. */
    fun collapse(node: FolderNode): FolderNode {
        var n = node
        while (n.passThrough) n = n.childList[0]
        return n
    }

    /** First level shown: the only volume with music, collapsed; null when several volumes need a choice. */
    fun start(): FolderNode? = volumes.singleOrNull()?.let(::collapse)

    /**
     * Where "up" goes from [node]: the nearest parent that is not skipped; null = already at the top (or, with several
     * volumes, back to the volume list). With one volume the start folder is never skipped, so up never goes above it.
     */
    fun up(node: FolderNode): FolderNode? {
        if (node == start()) return null
        var p = node.parent
        while (p != null && p.passThrough) p = p.parent
        return p
    }

    /** Folders whose name contains [query] (case and accents ignored), most tracks first. */
    fun search(query: String, limit: Int = 20): List<FolderNode> {
        val q = fold(query.trim())
        if (q.isEmpty()) return emptyList()
        return nodes.values.asSequence()
            .filter { !it.isVolume && fold(it.name).contains(q) }
            .sortedByDescending { it.totalTracks }
            .take(limit)
            .toList()
    }

    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(Locale.ROOT)
}

// Android paths use '/'; the JVM tests on Windows produce '\'. Accepting both keeps the tree right in both places.
private fun isSeparator(c: Char) = c == '/' || c == '\\'

internal fun lastSeparator(path: String): Int = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
