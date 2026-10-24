package com.boulbeba.cruise

import android.os.Environment
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulbeba.cruise.core.playback.PlayerConnection
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Library with music in it (CHANGE_REQUESTS.md CR-02, CR-04): a small music tree is written to the internal
 * storage next to folders without music, scanned by the real scanner, then browsed.
 *
 * Record: `gradlew :app:recordRoborazziDebug` → app/build/outputs/roborazzi/
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [27], qualifiers = "w1280dp-h660dp-land-mdpi")
class LibraryScreenshotTest {

    @get:Rule(order = 0) val storage = GrantStorageRule()

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        init { PlayerConnection.enabled = false }
    }

    @Before fun writeLibrary() {
        val root = Environment.getExternalStorageDirectory()
        listOf(
            "Music/Daft Punk/1997 - Homework/01 - Revolution 909.wav",
            "Music/Daft Punk/1997 - Homework/02 - Da Funk.wav",
            "Music/Daft Punk/2001 - Discovery/01 - One More Time.wav",
            "Music/Daft Punk/2001 - Discovery/02 - Aerodynamic.wav",
            "Music/Daft Punk/2001 - Discovery/03 - Digital Love.wav",
            "Music/Fairuz/Best Of/01 - Kifak Inta.wav",
            "Music/Fairuz/Best Of/02 - Li Beirut.wav",
            "Music/Singles/Nightcall.wav",
            "Music/Singles/Midnight City.wav",
        ).forEach { silentWav(File(root, it)) }
        File(root, "Download/manual.pdf").apply { parentFile!!.mkdirs(); writeText("not music") }
        File(root, "Documents/notes.txt").apply { parentFile!!.mkdirs(); writeText("not music") }
        compose.activity.carEvents.onStorageGranted()
    }

    /** 11 s of silence (the scanner ignores files under 10 s by default), 8 kHz mono 16-bit. */
    private fun silentWav(f: File) {
        f.parentFile!!.mkdirs()
        val rate = 8_000
        val bytes = rate * 2 * 11
        DataOutputStream(FileOutputStream(f)).use { o ->
            fun le32(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = o.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            o.writeBytes("RIFF"); le32(36 + bytes); o.writeBytes("WAVEfmt "); le32(16); le16(1); le16(1)
            le32(rate); le32(rate * 2); le16(2); le16(16); o.writeBytes("data"); le32(bytes)
            o.write(ByteArray(bytes))
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 20_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun clickText(text: String) {
        waitForText(text)
        compose.onAllNodesWithText(text, substring = true)[0].performClick()
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun folders_only_music_collapsed() {
        clickText("Library")
        // Internal storage › Music is skipped straight to its three music folders; Download and Documents never show.
        waitForText("Daft Punk")
        capture("30_library_folders_top")
        clickText("Daft Punk")
        waitForText("Discovery")
        capture("31_library_folders_artist")
    }

    @Test fun albums_tab() {
        clickText("Library")
        clickText("Albums")
        waitForText("Discovery")
        capture("32_library_albums")
    }

    @Test fun playlists_tab() {
        clickText("Library")
        clickText("Playlists")
        waitForText("Most played")
        capture("33_library_playlists")
    }

    /** A long folder: the fast scroller's handle dragged halfway shows the letter bubble (CR-14). */
    @Test fun fast_scroller_letter_bubble() {
        val root = Environment.getExternalStorageDirectory()
        val names = listOf(
            "Ahlam", "Amal", "Baladi", "Bahr", "Cafe", "Chams", "Dounia", "Dima", "Elissa", "Enta", "Fairuz", "Fares", "Ghali", "Habibi",
            "Hakim", "Hamaki", "Ilham", "Jamil", "Kalam", "Latifa", "Layali", "Mawal", "Nancy", "Nour", "Omri", "Qalbi", "Rahma",
            "Salam", "Samira", "Tarab", "Ward", "Yalla", "Zaman", "يا رايح", "يا مسافر", "حبيبي", "ليالي", "عمري", "قلبي", "سلام",
        )
        names.forEachIndexed { i, n -> silentWav(File(root, "Music/Big Mix/%02d - %s.wav".format(i + 1, n))) }
        compose.activity.carEvents.onStorageGranted()
        clickText("Library")
        clickText("Big Mix")
        waitForText("Ahlam")
        compose.onRoot().performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width - 18f, height * 0.35f))
            moveTo(androidx.compose.ui.geometry.Offset(width - 18f, height * 0.6f))
        }
        compose.waitForIdle()
        capture("35_library_fast_scroller")
        compose.onRoot().performTouchInput { up() }
    }

    @Test fun search_finds_folders_and_tracks() {
        clickText("Library")
        waitForText("Daft Punk")
        compose.onAllNodes(hasContentDescription("Search"))[0].performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("disc")
        waitForText("One More Time")
        capture("34_library_search")
    }
}
