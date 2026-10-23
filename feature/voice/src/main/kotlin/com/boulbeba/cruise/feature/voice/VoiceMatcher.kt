package com.boulbeba.cruise.feature.voice

import kotlin.math.max
import kotlin.math.min

/** One searchable thing in the library. */
data class Candidate(val kind: VoiceCommand.Kind, val text: String, val key: String, val artist: String = "")

data class Match(val candidate: Candidate, val score: Float)

/**
 * Finds what the driver meant. Offline recognisers mishear names — especially names that are not English or French
 * words ("Nawal Al Zoughbi" may come out as "now well all zoo be") — so the transcript is compared by sound, not by
 * spelling: both sides are folded to a rough phonetic key and compared with an edit distance, alongside plain word
 * overlap.
 */
object VoiceMatcher {

    const val THRESHOLD = 0.62f

    /** Rough, language-neutral sound key: similar-sounding spellings fold together; vowels count less than consonants. */
    fun phonetic(text: String): String {
        var s = VoiceCommands.normalize(text).replace(" ", "")
        listOf(
            "eau" to "o", "sch" to "sh", "igh" to "i", "ph" to "f", "gh" to "g", "kh" to "k", "ch" to "sh", "ck" to "k", "qu" to "k", "q" to "k",
            "x" to "ks", "z" to "s", "w" to "v", "y" to "i", "th" to "t", "dh" to "d", "ou" to "u", "oo" to "u",
            "ee" to "i", "ea" to "i", "ie" to "i", "ei" to "e", "ai" to "e", "ay" to "e", "au" to "o",
        ).forEach { (from, to) -> s = s.replace(from, to) }
        // o/u are interchangeable in transliterated names (Zoghbi / Zoughbi).
        s = s.replace(Regex("c(?=[eiy])"), "s").replace("c", "k").replace("h", "").replace('o', 'u')
        val sb = StringBuilder()
        for ((i, ch) in s.withIndex()) {
            if (i > 0 && ch == s[i - 1]) continue
            sb.append(ch)
        }
        return sb.toString()
    }

    private fun consonants(p: String) = if (p.isEmpty()) p else p[0] + p.substring(1).replace(Regex("[aeiou]"), "")

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** Best [ratio] of [short] against any window of [long] with the same length. */
    private fun partialRatio(short: String, long: String): Float {
        var best = 0f
        for (start in 0..long.length - short.length) best = max(best, ratio(short, long.substring(start, start + short.length)))
        return best
    }

    private fun ratio(a: String, b: String): Float {
        val m = max(a.length, b.length)
        return if (m == 0) 0f else 1f - levenshtein(a, b).toFloat() / m
    }

    /** 0…1: how well [query] names [field]. */
    fun score(query: String, field: String): Float {
        val qn = VoiceCommands.normalize(query)
        val fn = VoiceCommands.normalize(field.replace(Regex("[(\\[].*?[)\\]]"), " "))
        if (qn.isEmpty() || fn.isEmpty()) return 0f
        if (qn == fn) return 1f
        val qp = phonetic(qn)
        val fp = phonetic(fn)
        val whole = max(ratio(qp, fp), ratio(consonants(qp), consonants(fp)) * 0.95f)
        // The query may name only part of a long title ("another love" for "Another Love (Zwette Edit)"), or the
        // recogniser may have lost a piece of the name ("i drive" for "Night Drive"): compare against every stretch
        // of the field with the query's length.
        val contained = if (fp.length > qp.length && qp.length >= 5) 0.9f * partialRatio(qp, fp) * (0.75f + 0.25f * qp.length / fp.length) else 0f
        val qWords = qn.split(' ').filter { it.length > 1 }
        val fWords = fn.split(' ').filter { it.length > 1 }
        val words = if (qWords.isEmpty()) 0f else qWords.count { q -> fWords.any { f -> f == q || ratio(phonetic(q), phonetic(f)) >= 0.8f } }.toFloat() / max(qWords.size, fWords.size)
        return maxOf(whole, contained, words * 0.95f)
    }

    /**
     * The best match for [command], or null when nothing is close enough. A kind named by the driver ("the album …")
     * restricts the search; otherwise titles, artists and albums compete and a near tie goes to the title.
     */
    fun best(command: VoiceCommand.Play, candidates: List<Candidate>): Match? {
        val pool = if (command.kind == VoiceCommand.Kind.Any) candidates else candidates.filter { it.kind == command.kind }
        val bias = mapOf(VoiceCommand.Kind.Track to 0.02f, VoiceCommand.Kind.Artist to 0.01f, VoiceCommand.Kind.Album to 0f)
        return pool.asSequence()
            .map { c ->
                var s = score(command.query, c.text)
                if (command.byArtist != null && c.kind == VoiceCommand.Kind.Track) s = 0.6f * s + 0.4f * score(command.byArtist, c.artist)
                Match(c, s + (bias[c.kind] ?: 0f))
            }
            .maxByOrNull { it.score }
            ?.takeIf { it.score >= THRESHOLD }
    }
}
