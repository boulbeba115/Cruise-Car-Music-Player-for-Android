package com.boulbeba.cruise.core.playback

import android.content.Context
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/** Where playback stood, saved every few seconds (SPEC PB-4, CAR-4). Small, so it is cheap to write often. */
data class PlaybackPosition(
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val wasPlaying: Boolean,
    /** Paused by Cruise because the unit went to sleep: resume when it wakes (SPEC EX-1). */
    val pausedForSleep: Boolean,
)

/**
 * Persists the queue (written only when it changes) and the position (written every few seconds) with
 * [AtomicFile], so a power cut leaves either the old or the new version on disk — never a torn file.
 */
@Singleton
class QueueStore @Inject constructor(@ApplicationContext context: Context) {

    private val dir = File(context.filesDir, "playback").apply { mkdirs() }
    private val queueFile = AtomicFile(File(dir, "queue.json"))
    private val positionFile = AtomicFile(File(dir, "position.json"))

    fun saveQueue(paths: List<String>) = write(queueFile, JSONArray(paths).toString())

    fun loadQueue(): List<String> = runCatching {
        val array = JSONArray(String(queueFile.readFully()))
        List(array.length()) { array.getString(it) }
    }.getOrDefault(emptyList())

    fun savePosition(p: PlaybackPosition) = write(
        positionFile,
        JSONObject()
            .put("index", p.index)
            .put("positionMs", p.positionMs)
            .put("shuffle", p.shuffle)
            .put("repeatMode", p.repeatMode)
            .put("wasPlaying", p.wasPlaying)
            .put("pausedForSleep", p.pausedForSleep)
            .toString(),
    )

    fun loadPosition(): PlaybackPosition? = runCatching {
        val o = JSONObject(String(positionFile.readFully()))
        PlaybackPosition(
            index = o.getInt("index"),
            positionMs = o.getLong("positionMs"),
            shuffle = o.optBoolean("shuffle"),
            repeatMode = o.optInt("repeatMode"),
            wasPlaying = o.optBoolean("wasPlaying"),
            pausedForSleep = o.optBoolean("pausedForSleep"),
        )
    }.getOrNull()

    private fun write(file: AtomicFile, text: String) {
        val out = file.startWrite()
        try {
            out.write(text.toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}
