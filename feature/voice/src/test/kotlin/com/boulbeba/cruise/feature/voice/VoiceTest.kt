package com.boulbeba.cruise.feature.voice

import com.boulbeba.cruise.feature.voice.VoiceCommand.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTest {

    // ---- Parsing ----------------------------------------------------------------------------------------------

    @Test fun control_words_in_english_and_french() {
        assertEquals(VoiceCommand.Next, VoiceCommands.parse("Next song"))
        assertEquals(VoiceCommand.Next, VoiceCommands.parse("suivant"))
        assertEquals(VoiceCommand.Previous, VoiceCommands.parse("précédent"))
        assertEquals(VoiceCommand.Pause, VoiceCommands.parse("stop the music please"))
        assertEquals(VoiceCommand.Resume, VoiceCommands.parse("reprends"))
        assertEquals(VoiceCommand.VolumeUp, VoiceCommands.parse("plus fort"))
        assertEquals(VoiceCommand.VolumeDown, VoiceCommands.parse("turn it down"))
        assertEquals(VoiceCommand.Favorites, VoiceCommands.parse("joue mes favoris"))
        assertEquals(VoiceCommand.ShuffleAll, VoiceCommands.parse("surprise me"))
        assertEquals(VoiceCommand.Unknown, VoiceCommands.parse("  "))
    }

    @Test fun play_requests() {
        assertEquals(VoiceCommand.Play("hotel california"), VoiceCommands.parse("Play Hotel California"))
        assertEquals(VoiceCommand.Play("stromae"), VoiceCommands.parse("joue Stromae"))
        assertEquals(VoiceCommand.Play("the dark side of the moon", Kind.Album), VoiceCommands.parse("play the album The Dark Side of the Moon"))
        assertEquals(VoiceCommand.Play("fairuz", Kind.Artist, shuffle = true), VoiceCommands.parse("shuffle songs by Fairuz"))
        assertEquals(VoiceCommand.Play("angele", Kind.Artist), VoiceCommands.parse("mets l'artiste Angèle"))
        assertEquals(VoiceCommand.Play("another love", Kind.Track, byArtist = "tom odell"), VoiceCommands.parse("play Another Love by Tom Odell"))
        assertEquals(VoiceCommand.Play("dal oona"), VoiceCommands.parse("Dal'oona"))
    }

    // ---- Matching ---------------------------------------------------------------------------------------------

    private val library = listOf(
        Candidate(Kind.Track, "Dal'oona", "t1", "Nawal Al Zoughbi"),
        Candidate(Kind.Track, "Another Love (Zwette Edit)", "t2", "Tom Odell"),
        Candidate(Kind.Track, "Hotel California", "t3", "Eagles"),
        Candidate(Kind.Track, "Alors on danse", "t4", "Stromae"),
        Candidate(Kind.Artist, "Nawal Al Zoughbi", "a1"),
        Candidate(Kind.Artist, "Fairuz", "a2"),
        Candidate(Kind.Artist, "Stromae", "a3"),
        Candidate(Kind.Artist, "Tom Odell", "a4"),
        Candidate(Kind.Album, "The Dark Side of the Moon", "b1", "Pink Floyd"),
        Candidate(Kind.Album, "Maloom", "b2", "Nawal Al Zoughbi"),
    )

    private fun match(said: String) = VoiceMatcher.best(VoiceCommands.parse(said) as VoiceCommand.Play, library)?.candidate?.key

    @Test fun exact_and_partial_names() {
        assertEquals("t3", match("play hotel california"))
        assertEquals("t2", match("play another love"))
        assertEquals("b1", match("play the album dark side of the moon"))
        assertEquals("a3", match("joue stromae"))
    }

    @Test fun misheard_names_still_match_by_sound() {
        assertEquals("a1", match("play nawal al zoghbi"))
        assertEquals("a1", match("play now well al zoubi"))
        assertEquals("a2", match("play fayrouz"))
        assertEquals("t1", match("play daloona"))
        assertEquals("t4", match("joue alor on danse"))
    }

    @Test fun title_by_artist() {
        assertEquals("t2", match("play another love by tom odell"))
    }

    @Test fun nothing_close_returns_null() {
        assertNull(VoiceMatcher.best(VoiceCommand.Play("bohemian rhapsody"), library))
        assertNotNull(VoiceMatcher.best(VoiceCommand.Play("maloom", Kind.Album), library))
    }

    @Test fun phonetic_folds_spelling_variants() {
        assertEquals(VoiceMatcher.phonetic("Zoughbi"), VoiceMatcher.phonetic("Zoghbi"))
        assertEquals(VoiceMatcher.phonetic("Fairouz"), VoiceMatcher.phonetic("Fayrouz"))
        assertEquals(VoiceMatcher.phonetic("Photo"), VoiceMatcher.phonetic("Foto"))
    }

    /**
     * Real transcripts: Windows text-to-speech saying the command, recognised by vosk-model-small-en-us-0.15 with up to
     * three alternatives. The first alternative that makes sense must lead to the right thing.
     */
    @Test fun real_recogniser_transcripts() {
        val lib = library + listOf(
            Candidate(Kind.Album, "Night Drive", "b3", "Aurora Lane"),
            Candidate(Kind.Album, "Hi-Res Sessions", "b4", "The Meridians"),
        )
        fun resolve(vararg alternatives: String): Any? {
            for (a in alternatives) {
                when (val c = VoiceCommands.parse(a)) {
                    is VoiceCommand.Play -> VoiceMatcher.best(c, lib)?.let { return it.candidate.key }
                    VoiceCommand.Unknown -> Unit
                    else -> return c
                }
            }
            return null
        }
        assertEquals("b3", resolve("plane i drive", "plain i drive"))
        assertEquals("b4", resolve("play the album hi residential sessions", "play the album high residential sessions"))
        assertEquals(VoiceCommand.Next, resolve("next song"))
        assertEquals("t2", resolve("play another love by tom odell", "play another loved by tom odell"))
        assertEquals(VoiceCommand.Favorites, resolve("shuffle my favorites", "shovel my favorites"))
        assertEquals(VoiceCommand.VolumeUp, resolve("turn it up"))
        assertEquals("a2", resolve("play pharaohs", "play pharos", "play farrow's"))
        // "play Nawal Al Zoughbi", badly misheard: the sound key still finds her.
        assertEquals("a1", resolve("plain a while else off by", "plane a while else off by"))
        // Something that is not in the library at all.
        assertNull(resolve("play bohemian rhapsody by queen"))
    }

    /** Real transcripts from vosk-model-small-fr-0.22 (Windows "Hortense" voice). */
    @Test fun real_french_transcripts() {
        fun resolve(vararg alternatives: String): Any? {
            for (a in alternatives) {
                when (val c = VoiceCommands.parse(a)) {
                    is VoiceCommand.Play -> VoiceMatcher.best(c, library)?.let { return it.candidate.key }
                    VoiceCommand.Unknown -> Unit
                    else -> return c
                }
            }
            return null
        }
        assertEquals("a3", resolve("j'vous stromaé", "je loue stromaé", "je veux stromaé"))
        assertEquals("b2", resolve("mais l'album mal hum", "mais l'album malou hum", "mais l'album mâle hum"))
        assertEquals(VoiceCommand.Next, resolve("suivant", "suivants"))
        assertEquals(VoiceCommand.Favorites, resolve("joue mes favoris", "jour mes favoris"))
        assertEquals(VoiceCommand.VolumeUp, resolve("plus fort", "plus forts"))
        assertEquals("a2", resolve("je vous fais russe", "je vous fais ruse", "je vous fais russes"))
        assertEquals("t4", resolve("mais la chanson alors on danse"))
        assertEquals(VoiceCommand.Pause, resolve("pause", "pose"))
    }
}
