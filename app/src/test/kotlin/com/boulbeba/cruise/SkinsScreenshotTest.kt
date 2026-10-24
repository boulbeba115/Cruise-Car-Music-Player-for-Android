package com.boulbeba.cruise

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulbeba.cruise.core.designsystem.theme.Appearance
import com.boulbeba.cruise.core.designsystem.theme.Skins
import com.boulbeba.cruise.core.playback.PlayerConnection
import com.boulbeba.cruise.core.playback.PlayerUiState
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.ByteArrayOutputStream
import androidx.compose.ui.test.SemanticsNodeInteraction
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every skin on the owner's unit (1280×660 dp, Android 8.1) with a track playing, plus the screens that changed
 * with the full app: Appearance, Library & playback, Sound, Library, Search, blind mode, queue and track info.
 *
 * Record: `gradlew :app:recordRoborazziDebug` → app/build/outputs/roborazzi/
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [27], qualifiers = "w1280dp-h660dp-land-mdpi")
class SkinsScreenshotTest {

    @get:Rule(order = 0) val storage = GrantStorageRule()

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        init { PlayerConnection.enabled = false }
    }

    @After fun resetSkin() = Appearance.setSkin(Skins.Midnight.id)

    // Now Playing animates every frame (music bar, vinyl, backdrop): with the test clock advancing by itself
    // the UI never becomes idle. Freeze it and step time by hand.
    @Before fun freezeClock() {
        compose.mainClock.autoAdvance = false
        settle()
    }

    private fun settle(ms: Long = 1_200) = compose.mainClock.advanceTimeBy(ms)

    private fun click(node: SemanticsNodeInteraction) {
        node.performClick()
        settle()
    }

    /** Steps the frozen clock until [text] is on screen, then clicks it. */
    private fun clickText(text: String) {
        repeat(50) {
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return click(compose.onAllNodesWithText(text)[0])
            settle(200)
        }
        throw AssertionError("'$text' never appeared")
    }

    /** Like [clickText], for icon buttons found by their content description. */
    private fun clickDescription(label: String) {
        val matcher = androidx.compose.ui.test.hasContentDescription(label)
        repeat(50) {
            if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return click(compose.onAllNodes(matcher)[0])
            settle(200)
        }
        throw AssertionError("'$label' never appeared")
    }

    private fun waitForText(text: String) {
        repeat(50) {
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            settle(200)
        }
        throw AssertionError("'$text' never appeared")
    }

    private fun cover(): ByteArray {
        val bmp = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 400f, 400f, 0xFFFF2E88.toInt(), 0xFF1B1B6F.toInt(), Shader.TileMode.CLAMP) })
        c.drawCircle(260f, 150f, 120f, Paint().apply { shader = RadialGradient(260f, 150f, 120f, 0xFFFFD166.toInt(), 0x00FFD166, Shader.TileMode.CLAMP) })
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    private fun play(withArt: Boolean = true, title: String = "Nightcall", artist: String = "Kavinsky") {
        val path = "/storage/usbdisk0/Music/Kavinsky/OutRun/03 - Nightcall.flac"
        compose.activity.player.preview(
            PlayerUiState(
                connected = true, hasMedia = true, path = path, libraryPath = path,
                title = title, artist = artist, album = "OutRun", artwork = if (withArt) cover() else null, artworkKey = if (withArt) "nightcall" else null,
                isPlaying = true, positionMs = 95_000, positionSampledAt = SystemClock.elapsedRealtime(), durationMs = 258_000,
                queueIndex = 2, queueSize = 12,
            ),
            queue = listOf("/a/01 - Rampage.flac", "/a/02 - Blizzard.flac", path, "/a/04 - Testarossa Autodrive.flac"),
        )
        settle()
    }

    private fun capture(name: String) {
        settle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun skin(id: String) {
        Appearance.setSkin(id)
        play()
        settle(2_500) // let the record slide out, the sticker tilt, the tape start
        capture("20_skin_$id")
    }

    @Test fun skin_midnight() = skin("midnight")
    @Test fun skin_neon() = skin("neon")
    @Test fun skin_amber() = skin("amber")
    @Test fun skin_vinyl() = skin("vinyl")
    @Test fun skin_aurora() = skin("aurora")
    @Test fun skin_oled() = skin("oled")
    @Test fun skin_daylight() = skin("daylight")
    @Test fun skin_tape() = skin("tape")

    /** Songs without art get a cover from the active skin's own illustrated set (CR-13). */
    private fun noArt(id: String) {
        Appearance.setSkin(id)
        play(withArt = false)
        // The placeholder decodes on a background thread: step the frozen clock until it has had time to land.
        repeat(4) { settle(1_500); Thread.sleep(150) }
        capture("20_skin_${id}_no_art")
    }

    @Test fun skin_neon_no_art() = noArt("neon")
    @Test fun skin_midnight_no_art() = noArt("midnight")
    @Test fun skin_vinyl_no_art() = noArt("vinyl")
    @Test fun skin_tape_no_art() = noArt("tape")

    /** Neon City's towed banner with a name too long for it: the line scrolls, faded at the banner's ends (CR-15). */
    @Test fun skin_neon_long_title() {
        Appearance.setSkin("neon")
        play(title = "The Less I Know The Better (Extended Midnight Drive Remix)", artist = "فيروز وزياد الرحباني")
        settle(6_000)
        capture("20_skin_neon_long_title")
    }

    /** Switching between two skins that both have a texture must swap the texture (owner's bug 2026-10-24: Daylight kept Sport Cockpit's carbon). */
    @Test fun skin_switch_amber_to_daylight() {
        Appearance.setSkin("amber")
        play()
        settle(2_500)
        Appearance.setSkin("daylight")
        settle(2_500)
        capture("20_skin_switch_amber_to_daylight")
    }

    @Test fun settings_appearance() {
        clickText("Settings")
        waitForText("Vinyl Lounge")
        capture("21_settings_appearance")
    }

    @Test fun settings_library_playback() {
        clickText("Settings")
        clickText("Library & playback")
        capture("22_settings_library_playback")
    }

    @Test fun sound() {
        play()
        click(compose.onNodeWithText("Sound"))
        capture("23_sound")
    }

    @Test fun library_empty() {
        play()
        click(compose.onAllNodesWithText("Library")[0])
        capture("24_library_empty_with_miniplayer")
    }

    @Test fun search() {
        settle(2_000) // start-up: permission check, first scan, first frames
        clickText("Library")
        clickDescription("Search")
        capture("25_search")
    }

    @Test fun nowPlaying_queue() {
        Appearance.setSkin("neon")
        play()
        click(compose.onAllNodesWithContentDescriptionCompat("Queue"))
        capture("26_queue_neon")
    }

    @Test fun nowPlaying_info() {
        play()
        click(compose.onAllNodesWithContentDescriptionCompat("Track info"))
        capture("27_track_info")
    }

    @Test fun nowPlaying_blindMode() {
        Appearance.setSkin("amber")
        play()
        click(compose.onAllNodesWithContentDescriptionCompat("Blind mode"))
        capture("28_blind_mode_amber")
    }

    @Test @Config(sdk = [30], qualifiers = "w393dp-h873dp-port-xxhdpi")
    fun phonePortrait_aurora() {
        Appearance.setSkin("aurora")
        play()
        capture("29_phone_portrait_aurora")
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithContentDescriptionCompat(label: String) =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(label))[0]
}
