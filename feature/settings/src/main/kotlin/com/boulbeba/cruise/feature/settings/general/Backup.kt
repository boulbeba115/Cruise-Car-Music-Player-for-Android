package com.boulbeba.cruise.feature.settings.general

import android.content.Context
import android.os.Environment
import com.boulbeba.cruise.core.data.db.LibraryDao
import com.boulbeba.cruise.core.data.db.PlayStatEntity
import com.boulbeba.cruise.core.data.db.PlaylistEntity
import com.boulbeba.cruise.core.model.StorageRoots
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup and restore (SPEC SET-4): every preference (skin, sound, steering keys, library, playback), playlists,
 * favourites and play counts in one JSON file on the USB stick — survives a unit reset or a move to another unit.
 */
@Singleton
class Backup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LibraryDao,
) {
    /** Where backups go: `Cruise/cruise-backup.json` on the first USB stick, else on internal storage. */
    fun file(): File {
        val root = StorageRoots.removableRoots(context).firstOrNull { it.canWrite() } ?: Environment.getExternalStorageDirectory()
        return File(root, "Cruise/$FILE_NAME")
    }

    fun find(): File? = (StorageRoots.removableRoots(context) + Environment.getExternalStorageDirectory())
        .map { File(it, "Cruise/$FILE_NAME") }.firstOrNull { it.isFile }

    suspend fun export(): File = withContext(Dispatchers.IO) {
        val json = JSONObject()
        json.put("version", 1)
        json.put("created", System.currentTimeMillis())
        val prefs = JSONObject()
        for (name in PREFS) {
            val values = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                values.put(k, JSONObject().put("type", typeOf(v)).put("value", if (v is Set<*>) JSONArray(v.toList()) else v))
            }
            prefs.put(name, values)
        }
        json.put("prefs", prefs)
        json.put("stats", JSONArray().apply {
            dao.allStats().forEach { s ->
                put(JSONObject().put("path", s.path).put("plays", s.playCount).put("skips", s.skipCount).put("last", s.lastPlayed).put("fav", s.favorite))
            }
        })
        json.put("playlists", JSONArray().apply {
            dao.playlists().first().filter { it.sourceFile == null }.forEach { p ->
                put(JSONObject().put("name", p.name).put("paths", JSONArray(dao.playlistPaths(p.id))))
            }
        })
        val out = file()
        out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, "$FILE_NAME.tmp")
        tmp.writeText(json.toString(2))
        if (!tmp.renameTo(out)) { out.delete(); tmp.renameTo(out) }
        out
    }

    /** Restores [source]; preferences take effect after the app restarts. Returns what was restored. */
    suspend fun restore(source: File): Summary = withContext(Dispatchers.IO) {
        val json = JSONObject(source.readText())
        val prefs = json.optJSONObject("prefs") ?: JSONObject()
        for (name in prefs.keys()) {
            if (name !in PREFS) continue
            val values = prefs.getJSONObject(name)
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            for (k in values.keys()) {
                val o = values.getJSONObject(k)
                when (o.getString("type")) {
                    "b" -> editor.putBoolean(k, o.getBoolean("value"))
                    "i" -> editor.putInt(k, o.getInt("value"))
                    "l" -> editor.putLong(k, o.getLong("value"))
                    "f" -> editor.putFloat(k, o.getDouble("value").toFloat())
                    "set" -> o.getJSONArray("value").let { a -> editor.putStringSet(k, (0 until a.length()).map(a::getString).toSet()) }
                    else -> editor.putString(k, o.getString("value"))
                }
            }
            editor.commit()
        }
        val stats = json.optJSONArray("stats") ?: JSONArray()
        for (i in 0 until stats.length()) {
            val s = stats.getJSONObject(i)
            dao.putStat(PlayStatEntity(s.getString("path"), s.optInt("plays"), s.optInt("skips"), s.optLong("last"), s.optBoolean("fav")))
        }
        val lists = json.optJSONArray("playlists") ?: JSONArray()
        val existing = dao.playlists().first().map { it.name }.toSet()
        var added = 0
        for (i in 0 until lists.length()) {
            val p = lists.getJSONObject(i)
            val name = p.getString("name")
            if (name in existing) continue
            val now = System.currentTimeMillis()
            val id = dao.insertPlaylist(PlaylistEntity(name = name, created = now, modified = now))
            val a = p.getJSONArray("paths")
            dao.replacePlaylistItems(id, (0 until a.length()).map(a::getString), now)
            added++
        }
        Summary(prefs.length(), stats.length(), added)
    }

    data class Summary(val settings: Int, val stats: Int, val playlists: Int)

    private fun typeOf(v: Any?) = when (v) {
        is Boolean -> "b"
        is Int -> "i"
        is Long -> "l"
        is Float -> "f"
        is Set<*> -> "set"
        else -> "s"
    }

    companion object {
        const val FILE_NAME = "cruise-backup.json"
        val PREFS = listOf("appearance", "sound_settings", "steering_keys", "library_settings", "playback_settings", "library_ui")
    }
}
