package com.boulbeba.cruise.wheel

import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulbeba.cruise.wheel.engine.KeyInput
import com.boulbeba.cruise.wheel.engine.Press
import com.boulbeba.cruise.wheel.service.WheelState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Cruise Wheel's screens on the owner's 1280×720 head unit and a small one, rendered under Robolectric
 * (CHANGE_REQUESTS.md CR-18). A crash fails the test; the images are reviewed by eye.
 *
 * Record: `gradlew :wheel:recordRoborazziDebug` → wheel/build/outputs/roborazzi/
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "w1280dp-h720dp-land-mdpi")
class WheelScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        // The service is not created under Robolectric: show it as running.
        init { WheelState.running.value = true }
    }

    @Before fun freshState() = WheelState.clearForTests()

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun home() = capture("wheel_01_home")

    @Test fun home_button_pressed() {
        WheelState.record(KeyInput(24, Press.Short, SystemClock.elapsedRealtime()))
        compose.mainClock.advanceTimeBy(300)
        capture("wheel_02_home_pressed")
    }

    @Test fun phone_button() {
        compose.onAllNodesWithText("Phone")[0].performClick()
        capture("wheel_03_button_phone")
    }

    @Test fun action_picker() {
        compose.onAllNodesWithText("Phone")[0].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hold").performClick()
        capture("wheel_04_pick_action")
    }

    @Test fun add_button() {
        compose.onNodeWithText("Add a button").performClick()
        capture("wheel_05_add_button")
    }

    @Test fun settings() {
        compose.onNodeWithContentDescription("Settings").performClick()
        capture("wheel_06_settings")
    }

    @Test @Config(qualifiers = "fr-w1280dp-h720dp-land-mdpi")
    fun home_french() = capture("wheel_07_home_fr")

    @Test @Config(qualifiers = "w683dp-h400dp-land-hdpi")
    fun small_unit_button() {
        compose.onAllNodesWithText("Sound mixer")[0].performClick()
        capture("wheel_08_small_button_mixer")
    }
}
