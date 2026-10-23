package com.boulbeba.cruise.feature.voice

import java.text.Normalizer

/** What the driver asked for. */
sealed interface VoiceCommand {
    enum class Kind { Any, Track, Artist, Album }

    data class Play(val query: String, val kind: Kind = Kind.Any, val shuffle: Boolean = false, val byArtist: String? = null) : VoiceCommand
    data object Resume : VoiceCommand
    data object Pause : VoiceCommand
    data object Next : VoiceCommand
    data object Previous : VoiceCommand
    data object VolumeUp : VoiceCommand
    data object VolumeDown : VoiceCommand
    data object Favorites : VoiceCommand
    data object ShuffleAll : VoiceCommand
    data object Unknown : VoiceCommand
}

/**
 * Turns a transcript (English or French) into a [VoiceCommand]. Anything that is not a known control word is treated
 * as "play <it>", so the driver can simply say a song or an artist.
 */
object VoiceCommands {

    fun normalize(text: String): String = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val next = setOf("next", "next song", "next track", "skip", "skip this", "skip it", "suivant", "suivante", "chanson suivante", "morceau suivant", "passe")
    private val previous = setOf("previous", "previous song", "previous track", "go back", "back", "last song", "precedent", "precedente", "chanson precedente", "morceau precedent", "retour")
    private val pause = setOf("pause", "stop", "stop music", "stop the music", "be quiet", "silence", "arrete", "arrete la musique", "mets en pause", "pause la musique")
    private val resume = setOf("play", "resume", "continue", "play music", "play the music", "start", "reprends", "reprendre", "continue la musique", "lecture", "joue", "joue la musique", "remets la musique")
    private val louder = setOf("louder", "volume up", "turn it up", "turn up", "plus fort", "monte le son", "augmente le volume", "volume plus")
    private val quieter = setOf("quieter", "volume down", "turn it down", "turn down", "softer", "moins fort", "baisse le son", "baisse le volume", "volume moins")
    private val favorites = setOf("favorites", "favourites", "my favorites", "my favourites", "play my favorites", "play my favourites", "play favorites", "mes favoris", "joue mes favoris", "mets mes favoris", "favoris")
    private val shuffleAll = setOf("shuffle", "shuffle all", "shuffle everything", "shuffle my music", "play something", "play anything", "surprise me", "aleatoire", "mode aleatoire", "joue n importe quoi", "mets de la musique", "joue quelque chose")

    private val playVerbs = listOf(
        "can you play", "could you play", "please play", "i want to listen to", "i want to hear", "let s hear", "put on", "play me", "listen to", "play",
        "est ce que tu peux jouer", "je veux ecouter", "fais moi ecouter", "mets moi", "joue moi", "ecoute", "lance", "joue", "mets",
    )
    private val shuffleVerbs = listOf("shuffle", "mix", "random", "aleatoire", "melange", "en aleatoire")
    private val albumWords = listOf("the album", "album", "l album")
    private val artistWords = listOf("the artist", "artist", "songs by", "music by", "something by", "l artiste", "artiste", "les chansons de", "la musique de", "des chansons de")
    private val trackWords = listOf("the song", "song", "the track", "track", "la chanson", "chanson", "le morceau", "morceau", "le titre", "titre")
    private val politeTail = listOf("please", "for me", "s il te plait", "s il vous plait", "now", "maintenant")

    /** First words the small models often hear instead of the verb (recorded with the English model, 2026-10-23). */
    private val misheardVerbs = mapOf(
        "plane" to "play", "plain" to "play", "played" to "play", "plays" to "play", "place" to "play", "plate" to "play",
        "pray" to "play", "blame" to "play", "playing" to "play", "shovel" to "shuffle", "shuffled" to "shuffle",
    )

    /** Same for French, where the verb often merges with what follows ("joue" → "j'vous", "mets" → "mais"). */
    private val misheardPrefixes = listOf(
        "j vous" to "joue", "je vous" to "joue", "je loue" to "joue", "jour" to "joue", "joe" to "joue", "jou" to "joue",
        "je veux" to "joue", "mais" to "mets", "met" to "mets", "mai" to "mets",
    )

    fun parse(transcript: String): VoiceCommand {
        var t = normalize(transcript).removePrefix("cruise ").trim()
        if (t.isEmpty()) return VoiceCommand.Unknown
        misheardPrefixes.firstOrNull { (from, _) -> t.startsWith("$from ") }?.let { (from, to) -> t = to + t.removePrefix(from) }
        val firstWord = t.substringBefore(' ')
        misheardVerbs[firstWord]?.let { t = it + t.removePrefix(firstWord) }
        // "plain a …": the article the recogniser glued to the verb.
        if (t.startsWith("play a ")) t = "play " + t.removePrefix("play a ")
        politeTail.forEach { tail -> if (t.endsWith(" $tail")) t = t.removeSuffix(" $tail").trim() }
        when (t) {
            in next -> return VoiceCommand.Next
            in previous -> return VoiceCommand.Previous
            in pause -> return VoiceCommand.Pause
            in resume -> return VoiceCommand.Resume
            in louder -> return VoiceCommand.VolumeUp
            in quieter -> return VoiceCommand.VolumeDown
            in favorites -> return VoiceCommand.Favorites
            in shuffleAll -> return VoiceCommand.ShuffleAll
        }
        var shuffle = false
        var rest = t
        shuffleVerbs.firstOrNull { rest.startsWith("$it ") }?.let { rest = rest.removePrefix("$it ").trim(); shuffle = true }
        playVerbs.firstOrNull { rest.startsWith("$it ") }?.let { rest = rest.removePrefix("$it ").trim() }
        if (rest.endsWith(" on shuffle") || rest.endsWith(" shuffled") || rest.endsWith(" en aleatoire")) {
            shuffle = true
            rest = rest.removeSuffix(" on shuffle").removeSuffix(" shuffled").removeSuffix(" en aleatoire").trim()
        }
        if (rest in setOf("my favorites", "my favourites", "favorites", "favourites", "mes favoris", "favoris", "my favorite songs", "my favourite songs")) {
            return VoiceCommand.Favorites
        }
        var kind = VoiceCommand.Kind.Any
        fun strip(words: List<String>, k: VoiceCommand.Kind) {
            words.firstOrNull { rest.startsWith("$it ") }?.let { rest = rest.removePrefix("$it ").trim(); kind = k }
        }
        strip(albumWords, VoiceCommand.Kind.Album)
        if (kind == VoiceCommand.Kind.Any) strip(artistWords, VoiceCommand.Kind.Artist)
        if (kind == VoiceCommand.Kind.Any) strip(trackWords, VoiceCommand.Kind.Track)
        rest = rest.removePrefix("some ").removePrefix("du ").removePrefix("de la ").removePrefix("des ").trim()
        if (rest.isEmpty()) return if (shuffle) VoiceCommand.ShuffleAll else VoiceCommand.Resume
        // "<title> by <artist>" / "<titre> par <artiste>".
        val by = Regex("^(.+?) (?:by|par) (.+)$").find(rest)
        if (by != null && kind != VoiceCommand.Kind.Artist && kind != VoiceCommand.Kind.Album) {
            return VoiceCommand.Play(by.groupValues[1], VoiceCommand.Kind.Track, shuffle, byArtist = by.groupValues[2])
        }
        return VoiceCommand.Play(rest, kind, shuffle)
    }
}
