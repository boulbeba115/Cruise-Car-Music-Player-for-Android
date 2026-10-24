package com.boulbeba.cruise

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulbeba.cruise.core.playback.PlayerConnection
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Launches the real app on Android 11 (API 30) under Robolectric for each screen of the SPEC §5.1 matrix
 * and records a screenshot. A crash at start-up fails the test; the images are reviewed by eye.
 *
 * Record: `gradlew :app:recordRoborazziDebug` → app/build/outputs/roborazzi/
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30])
class ScreenMatrixScreenshotTest {

    @get:Rule(order = 0) val storage = GrantStorageRule()

    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        // Runs before the rule launches the activity.
        init { PlayerConnection.enabled = false }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun headUnit1280x720_default() = capture("01_headunit_1280x720_nowplaying")

    @Test @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun headUnit1280x720_settings() = settings("02_headunit_1280x720_settings")

    /** The owner's unit as measured: Android 8.1, 1280×660 dp app window, system font scale 1.15. */
    @Test @Config(sdk = [27], qualifiers = "w1280dp-h660dp-land-mdpi")
    fun ownerUnit_measured() {
        RuntimeEnvironment.setFontScale(1.15f)
        capture("00_owner_unit_android8.1_font115")
    }

    @Test @Config(qualifiers = "fr-w1280dp-h720dp-land-mdpi")
    fun headUnit1280x720_french() = capture("03_headunit_1280x720_french")

    @Test @Config(qualifiers = "w683dp-h400dp-land-hdpi")
    fun headUnit1024x600_hdpi() = capture("04_headunit_1024x600_compact")

    @Test @Config(qualifiers = "w1920dp-h720dp-land-mdpi")
    fun barScreen1920x720() = capture("05_bar_1920x720_ultrawide")

    @Test @Config(qualifiers = "w1333dp-h800dp-land-hdpi")
    fun tablet2000x1200() = capture("06_tablet_2000x1200")

    @Test @Config(qualifiers = "w393dp-h873dp-port-xxhdpi")
    fun phonePortrait() = capture("07_phone_portrait")

    @Test @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    fun tabletPortrait() = capture("08_tablet_portrait")

    // Older Android versions: budget head units (e.g. UNISOC SC7731E "TS7") run 8.1 Go or 10
    // while displaying a fake newer version (SPEC §2).
    @Test @Config(sdk = [23], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun android6_settings() = settings("09_android6_settings")

    @Test @Config(sdk = [27], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun android8_1_settings() = settings("10_android8.1_settings")

    @Test @Config(sdk = [29], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun android10_settings() = settings("11_android10_settings")

    @Test @Config(sdk = [27], qualifiers = "w1280dp-h660dp-land-mdpi")
    fun ownerUnit_library() {
        compose.onNodeWithText("Library").performClick()
        compose.waitForIdle()
        capture("15_owner_unit_library")
    }

    @Test @Config(sdk = [27], qualifiers = "fr-w1280dp-h660dp-land-mdpi")
    fun ownerUnit_library_french() {
        compose.onNodeWithText("Bibliothèque").performClick()
        compose.waitForIdle()
        capture("16_owner_unit_library_french")
    }

    /** Opens Settings → Library & playback. */
    private fun settings(name: String) {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Library & playback").performClick()
        capture(name)
    }
}
