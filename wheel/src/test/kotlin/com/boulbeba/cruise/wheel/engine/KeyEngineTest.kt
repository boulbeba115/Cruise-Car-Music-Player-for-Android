package com.boulbeba.cruise.wheel.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyEngineTest {

    private fun engine(vararg used: Trigger) = KeyEngine(350, 700) { used.toSet() }

    @Test fun a_button_without_second_press_rules_fires_at_once() {
        val e = engine(Trigger.DoubleTap(66))
        assertEquals(listOf(Trigger.Tap(24)), e.onInput(KeyInput(24, Press.Short, 0)))
        assertNull(e.nextDeadline())
    }

    @Test fun hold_fires_at_once() {
        val e = engine(Trigger.DoubleTap(24))
        assertEquals(listOf(Trigger.Hold(24)), e.onInput(KeyInput(24, Press.Long, 0)))
    }

    @Test fun double_tap_within_the_window() {
        val e = engine(Trigger.DoubleTap(24))
        assertTrue(e.onInput(KeyInput(24, Press.Short, 0)).isEmpty())
        assertEquals(350L, e.nextDeadline())
        assertEquals(listOf(Trigger.DoubleTap(24)), e.onInput(KeyInput(24, Press.Short, 200)))
        assertNull(e.nextDeadline())
    }

    @Test fun a_waiting_tap_is_reported_when_its_window_passes() {
        val e = engine(Trigger.DoubleTap(24))
        e.onInput(KeyInput(24, Press.Short, 0))
        assertTrue(e.tick(300).isEmpty())
        assertEquals(listOf(Trigger.Tap(24)), e.tick(351))
        assertTrue(e.tick(400).isEmpty())
    }

    @Test fun two_buttons_together_make_a_combination_in_either_order() {
        val e = engine(Trigger.Combo.of(1, 2))
        e.onInput(KeyInput(2, Press.Short, 0))
        assertEquals(listOf(Trigger.Combo(1, 2)), e.onInput(KeyInput(1, Press.Short, 120)))
        e.onInput(KeyInput(1, Press.Short, 1_000))
        assertEquals(listOf(Trigger.Combo(1, 2)), e.onInput(KeyInput(2, Press.Long, 1_100)))
    }

    @Test fun unrelated_buttons_pressed_quickly_stay_two_taps() {
        val e = engine(Trigger.DoubleTap(24))
        e.onInput(KeyInput(24, Press.Short, 0))
        assertEquals(listOf(Trigger.Tap(24), Trigger.Tap(66)), e.onInput(KeyInput(66, Press.Short, 100)))
    }

    @Test fun a_late_second_press_is_two_separate_taps() {
        val e = engine(Trigger.DoubleTap(24))
        e.onInput(KeyInput(24, Press.Short, 0))
        assertTrue(e.onInput(KeyInput(24, Press.Short, 900)).let { it == listOf(Trigger.Tap(24)) })
        assertEquals(1_250L, e.nextDeadline())
    }

    @Test fun a_hold_after_a_waiting_tap_of_the_same_button_keeps_both() {
        val e = engine(Trigger.DoubleTap(24))
        e.onInput(KeyInput(24, Press.Short, 0))
        assertEquals(listOf(Trigger.Tap(24), Trigger.Hold(24)), e.onInput(KeyInput(24, Press.Long, 100)))
    }

    @Test fun a_button_waiting_only_for_a_combination_double_taps_as_two_taps() {
        val e = engine(Trigger.Combo.of(1, 2))
        e.onInput(KeyInput(1, Press.Short, 0))
        assertEquals(listOf(Trigger.Tap(1)), e.onInput(KeyInput(1, Press.Short, 100)))
        assertEquals(listOf(Trigger.Tap(1)), e.tick(500))
    }

    @Test fun repeated_hold_messages_are_one_hold() {
        val e = engine()
        assertEquals(listOf(Trigger.Hold(24)), e.onInput(KeyInput(24, Press.Long, 0)))
        assertTrue(e.onInput(KeyInput(24, Press.Long, 300)).isEmpty())
        assertTrue(e.onInput(KeyInput(24, Press.Long, 900)).isEmpty())
        assertEquals(listOf(Trigger.Hold(24)), e.onInput(KeyInput(24, Press.Long, 2_000)))
    }

    @Test fun a_short_report_right_after_a_hold_is_its_release() {
        val e = engine()
        assertEquals(listOf(Trigger.Hold(24)), e.onInput(KeyInput(24, Press.Long, 0)))
        assertTrue(e.onInput(KeyInput(24, Press.Short, 200)).isEmpty())
        assertEquals(listOf(Trigger.Tap(24)), e.onInput(KeyInput(24, Press.Short, 1_500)))
    }

    @Test fun another_button_during_a_hold_is_not_swallowed() {
        val e = engine()
        assertEquals(listOf(Trigger.Hold(24)), e.onInput(KeyInput(24, Press.Long, 0)))
        assertEquals(listOf(Trigger.Tap(66)), e.onInput(KeyInput(66, Press.Short, 100)))
    }
}
