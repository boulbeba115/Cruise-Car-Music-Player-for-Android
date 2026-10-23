package com.boulbeba.cruise.feature.voice

import android.content.Context
import com.boulbeba.cruise.core.car.CarLog
import com.boulbeba.cruise.core.model.StorageRoots
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/** Events while listening. */
sealed interface Hearing {
    data class Partial(val text: String) : Hearing
    data class Final(val alternatives: List<String>) : Hearing
    data object Silence : Hearing
    data class Failed(val error: Throwable) : Hearing
}

/** A voice pack found on the device: Vosk model folder + its language. */
data class VoicePack(val dir: File, val language: String)

/**
 * Offline speech recognition with Vosk (Kaldi) — the owner's unit has no Google speech services and no internet.
 *
 * Voice packs (Vosk "small" models, ~40 MB) are looked for in `Cruise/voice/` on every USB stick and installed once
 * into the app's storage, so the stick can be swapped afterwards. The model is loaded on first use and released after
 * a few idle minutes to give the 1 GB unit its memory back.
 */
@Singleton
class VoiceEngine @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private var model: Model? = null
    private var modelLanguage: String? = null
    private var releaseJob: Job? = null

    init {
        runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS) }
    }

    private val installed get() = File(context.filesDir, "voice")

    /** Language the user is most likely speaking: the app's language when a pack exists for it. */
    fun preferredLanguage(): String = if (Locale.getDefault().language == "fr") "fr" else "en"

    /** Installed packs plus packs waiting on a USB stick. */
    fun packs(): List<VoicePack> {
        val dirs = (installed.listFiles().orEmpty().toList() +
            StorageRoots.removableRoots(context).flatMap { File(it, "Cruise/voice").listFiles().orEmpty().toList() })
        return dirs.filter { isModel(it) }.map { VoicePack(it, languageOf(it.name)) }.distinctBy { it.dir.name }
    }

    private fun isModel(dir: File) = dir.isDirectory && (File(dir, "am/final.mdl").isFile || File(dir, "conf/model.conf").isFile)

    private fun languageOf(name: String) = when {
        Regex("(?i)-fr-|-fr$|french").containsMatchIn(name) -> "fr"
        Regex("(?i)-en-|-en$|english").containsMatchIn(name) -> "en"
        else -> name.substringAfter("vosk-model-small-").substringBefore('-').ifBlank { "en" }
    }

    fun hasPack(): Boolean = packs().isNotEmpty()

    /** Loads (installing from the stick first if needed) the pack for [language]; [onStage] reports progress text. */
    suspend fun prepare(language: String = preferredLanguage(), onStage: (Stage) -> Unit = {}): Boolean = lock.withLock {
        releaseJob?.cancel()
        val available = packs()
        val pack = available.firstOrNull { it.language == language } ?: available.firstOrNull() ?: return@withLock false
        if (model != null && modelLanguage == pack.language) return@withLock true
        withContext(Dispatchers.IO) {
            model?.close(); model = null
            val dir = if (pack.dir.parentFile == installed) pack.dir else {
                onStage(Stage.Installing)
                install(pack.dir)
            }
            onStage(Stage.Loading)
            val started = System.currentTimeMillis()
            model = runCatching { Model(dir.path) }.onFailure { CarLog.log("voice", "model failed: $it") }.getOrNull()
            modelLanguage = pack.language
            CarLog.log("voice", "model ${dir.name} loaded in ${System.currentTimeMillis() - started} ms")
            model != null
        }
    }

    enum class Stage { Installing, Loading }

    private fun install(source: File): File {
        val target = File(installed, source.name)
        val tmp = File(installed, source.name + ".tmp")
        tmp.deleteRecursively()
        source.copyRecursively(tmp, overwrite = true)
        target.deleteRecursively()
        tmp.renameTo(target)
        CarLog.log("voice", "installed ${source.name}")
        return target
    }

    /**
     * Listens until the driver stops talking (or [timeoutMs]); emits partial transcripts, then one [Hearing.Final]
     * with the recogniser's alternatives, best first.
     */
    fun listen(timeoutMs: Int = 8_000): Flow<Hearing> = callbackFlow {
        val m = model ?: run { trySend(Hearing.Failed(IllegalStateException("no model"))); close(); return@callbackFlow }
        val recognizer = Recognizer(m, SAMPLE_RATE).apply { setMaxAlternatives(3) }
        val service = runCatching { SpeechService(recognizer, SAMPLE_RATE) }.getOrElse {
            trySend(Hearing.Failed(it)); recognizer.close(); close(); return@callbackFlow
        }
        var finished = false
        fun finish(json: String?) {
            if (finished) return
            val alternatives = parseAlternatives(json)
            if (alternatives.isEmpty()) return
            finished = true
            trySend(Hearing.Final(alternatives))
            close()
        }
        service.startListening(object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                val text = hypothesis?.let { runCatching { JSONObject(it).optString("partial") }.getOrNull() }.orEmpty()
                if (text.isNotBlank()) trySend(Hearing.Partial(text))
            }
            override fun onResult(hypothesis: String?) = finish(hypothesis)
            override fun onFinalResult(hypothesis: String?) {
                finish(hypothesis)
                if (!finished) { finished = true; trySend(Hearing.Silence); close() }
            }
            override fun onError(exception: Exception) { trySend(Hearing.Failed(exception)); close() }
            override fun onTimeout() { if (!finished) { finished = true; trySend(Hearing.Silence); close() } }
        }, timeoutMs)
        awaitClose {
            runCatching { service.stop() }
            runCatching { service.shutdown() }
            runCatching { recognizer.close() }
            scheduleRelease()
        }
    }

    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            lock.withLock { model?.close(); model = null; modelLanguage = null }
            CarLog.log("voice", "model released")
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000f
        private const val IDLE_RELEASE_MS = 5 * 60_000L

        /** `{"text": …}` or `{"alternatives": [{"text": …}, …]}` → texts, best first, blanks dropped. */
        fun parseAlternatives(json: String?): List<String> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val o = JSONObject(json)
                val alts = o.optJSONArray("alternatives")
                if (alts != null) (0 until alts.length()).map { alts.getJSONObject(it).optString("text") }
                else listOf(o.optString("text"))
            }.getOrDefault(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        }
    }
}
