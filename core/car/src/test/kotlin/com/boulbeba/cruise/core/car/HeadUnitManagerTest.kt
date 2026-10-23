package com.boulbeba.cruise.core.car

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boulbeba.cruise.core.car.calls.CallControl
import com.boulbeba.cruise.core.car.calls.CallState
import com.boulbeba.cruise.core.car.steering.KeyAction
import com.boulbeba.cruise.core.car.steering.KeyMappingStore
import com.boulbeba.cruise.core.car.steering.MediaCommands
import com.boulbeba.cruise.core.car.steering.PressType
import com.boulbeba.cruise.core.car.steering.VendorScreenSuppressor
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

/** Steering-wheel routing on a TW unit (SPEC SW-2, SW-3), with the owner's defaults. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [27])
class HeadUnitManagerTest {

    private class FakeCalls : CallControl {
        override val state = MutableStateFlow(CallState.Idle)
        val log = mutableListOf<String>()
        override fun start() = Unit
        override fun answer() = log.add("answer")
        override fun reject() = log.add("reject")
        override fun hangUp() = log.add("hangUp")
    }

    private class FakeCommands : MediaCommands {
        val log = mutableListOf<String>()
        override fun playPause() { log += "playPause" }
        override fun next() { log += "next" }
        override fun previous() { log += "previous" }
        override fun nextFolder() { log += "nextFolder" }
        override fun previousFolder() { log += "previousFolder" }
        override fun toggleShuffle() { log += "shuffle" }
        override fun cycleRepeat() { log += "repeat" }
        override fun openApp() { log += "open" }
    }

    private lateinit var mapping: KeyMappingStore
    private val calls = FakeCalls()
    private val commands = FakeCommands()
    private lateinit var manager: HeadUnitManager

    @Before fun setUp() {
        mapping = KeyMappingStore(ApplicationProvider.getApplicationContext())
        manager = HeadUnitManager(mapping, calls).also { it.commands = commands }
    }

    private fun press(code: Int, type: PressType = PressType.Short) {
        manager.onKey(code, type)
        ShadowSystemClock.advanceBy(Duration.ofMillis(500))
    }

    @Test fun `phone button plays and pauses when no call, and the Bluetooth screen is marked for closing`() {
        press(KeyMappingStore.PHONE_KEY)
        assertEquals(listOf("playPause"), commands.log)
        assertTrue(VendorScreenSuppressor.consume("com.tw.bt"))
    }

    @Test fun `phone button answers a ringing call, long press rejects it`() {
        calls.state.value = CallState.Ringing
        press(KeyMappingStore.PHONE_KEY)
        press(KeyMappingStore.PHONE_KEY, PressType.Long)
        assertEquals(listOf("answer", "reject"), calls.log)
        assertTrue(commands.log.isEmpty())
    }

    @Test fun `phone and hang-up buttons end an active call`() {
        calls.state.value = CallState.Active
        press(KeyMappingStore.PHONE_KEY)
        press(KeyMappingStore.HANG_UP_KEY)
        assertEquals(listOf("hangUp", "hangUp"), calls.log)
    }

    @Test fun `smart call can be switched off`() {
        mapping.smartCallButton = false
        calls.state.value = CallState.Ringing
        press(KeyMappingStore.PHONE_KEY)
        assertTrue(calls.log.isEmpty())
        assertEquals(listOf("playPause"), commands.log)
    }

    @Test fun `sound-mixer button skips to the next folder, long press toggles shuffle`() {
        press(KeyMappingStore.MIXER_KEY)
        press(KeyMappingStore.MIXER_KEY, PressType.Long)
        assertEquals(listOf("nextFolder", "shuffle"), commands.log)
        assertTrue(VendorScreenSuppressor.consume("com.tw.eq"))
    }

    @Test fun `keys the unit already sends as media keys are not handled twice`() {
        listOf(5, 6, 19, 20, 21).forEach { press(it) }
        assertTrue(commands.log.isEmpty())
    }

    @Test fun `custom mapping is used and remembered`() {
        mapping.set(KeyMappingStore.MIXER_KEY, PressType.Short, KeyAction.Previous)
        press(KeyMappingStore.MIXER_KEY)
        assertEquals(listOf("previous"), commands.log)
        assertEquals(KeyAction.Previous, KeyMappingStore(ApplicationProvider.getApplicationContext()).actionFor(66, PressType.Short))
    }

    @Test fun `unit default leaves the button alone`() {
        mapping.set(KeyMappingStore.MIXER_KEY, PressType.Short, KeyAction.UnitDefault)
        press(KeyMappingStore.MIXER_KEY)
        assertTrue(commands.log.isEmpty())
    }

    @Test fun `a repeated event within the debounce window counts once`() {
        manager.onKey(KeyMappingStore.PHONE_KEY, PressType.Short)
        manager.onKey(KeyMappingStore.PHONE_KEY, PressType.Short)
        assertEquals(listOf("playPause"), commands.log)
    }

    @Test fun `every pressed button is remembered for the mapping screen`() {
        press(42)
        assertTrue(42 in mapping.knownCodes())
        assertEquals(42, manager.lastKeys.value.last().code)
    }
}
