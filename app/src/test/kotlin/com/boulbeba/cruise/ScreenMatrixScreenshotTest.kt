package com.boulbeba.cruise

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

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
    fun headUnit1280x720_diagnostics() = diagnostics("02_headunit_1280x720_diagnostics")

    @Test @Config(qualifiers = "w1280dp-h720dp-land-mdpi")
    fun headUnit1280x720_carTest() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Car test").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Allow access").fetchSemanticsNodes().isNotEmpty()
        }
        capture("12_headunit_1280x720_car_test")
    }

    @Test @Config(sdk = [27], qualifiers = "fr-w1280dp-h720dp-land-mdpi")
    fun android8_1_carTest_french() {
        compose.onNodeWithText("Réglages").performClick()
        compose.onNodeWithText("Test voiture").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Autoriser l'accès").fetchSemanticsNodes().isNotEmpty()
        }
        capture("13_android8.1_car_test_french")
    }

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
    fun android6_diagnostics() = diagnostics("09_android6_diagnostics")

    @Test @Config(sdk = [27], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun android8_1_diagnostics() = diagnostics("10_android8.1_diagnostics")

    @Test @Config(sdk = [29], qualifiers = "w1280dp-h720dp-land-mdpi")
    fun android10_diagnostics() = diagnostics("11_android10_diagnostics")

    @Test @Config(sdk = [27], qualifiers = "w1280dp-h660dp-land-mdpi")
    fun ownerUnit_steeringWheel() {
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Steering wheel").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Press a button on the steering wheel").fetchSemanticsNodes().isNotEmpty()
        }
        capture("14_owner_unit_steering_wheel")
    }

    @Test @Config(sdk = [27], qualifiers = "w1280dp-h660dp-land-mdpi")
    fun ownerUnit_folders() {
        compose.onNodeWithText("Folders").performClick()
        compose.waitForIdle()
        capture("15_owner_unit_folders")
    }

    /** Opens Settings → Device report and waits for the report, which is collected on a background thread. */
    private fun diagnostics(name: String) {
        compose.onNodeWithText("Settings").performClick()
        // The tab chip comes first; the Car test also has a step with the same title.
        compose.onAllNodesWithText("Device report")[0].performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Display").fetchSemanticsNodes().isNotEmpty()
        }
        capture(name)
    }
}
