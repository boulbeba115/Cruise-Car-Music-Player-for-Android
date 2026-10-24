package com.boulbeba.cruise.wheel.engine

/**
 * How the head unit reported a press. TW units classify presses in the MCU and only send the result — a short press
 * or a long one — never separate "down" and "up" events (docs/unit-findings.md). The engine is built on that.
 */
enum class Press { Short, Long }

/** One button event from the head unit. */
data class KeyInput(val code: Int, val press: Press, val timeMs: Long)

/** What a rule reacts to (CHANGE_REQUESTS.md CR-10.1). */
sealed interface Trigger {
    /** A short press. */
    data class Tap(val code: Int) : Trigger

    /** A hold, as the head unit reports it (its own long-press threshold). */
    data class Hold(val code: Int) : Trigger

    /** Two short presses of the same button in quick succession. */
    data class DoubleTap(val code: Int) : Trigger

    /**
     * Two different buttons pressed together. Without press/release events "together" means both reported within
     * the engine's window; the pair is stored in ascending order so it does not matter which one came first.
     */
    data class Combo(val first: Int, val second: Int) : Trigger {
        init { require(first < second) { "use Combo.of" } }

        companion object {
            fun of(a: Int, b: Int) = Combo(minOf(a, b), maxOf(a, b))
        }
    }
}

/**
 * Turns button events into [Trigger]s. Pure logic, no Android: the service feeds it events and calls [tick] at
 * [nextDeadline].
 *
 * A short press is reported at once unless the same button also starts a double tap or a combination that a rule
 * uses ([inUse]); then it waits up to [windowMs] for the second press, and is reported as a plain tap when none comes.
 * Buttons without such rules keep no delay at all, and two unrelated buttons pressed quickly stay two taps.
 */
class KeyEngine(private val windowMs: Long = DEFAULT_WINDOW_MS, private val inUse: () -> Set<Trigger>) {

    private var pending: KeyInput? = null

    private fun waitsFor(code: Int, used: Set<Trigger>) = used.any {
        (it is Trigger.DoubleTap && it.code == code) || (it is Trigger.Combo && (it.first == code || it.second == code))
    }

    fun onInput(input: KeyInput): List<Trigger> {
        val used = inUse()
        val out = ArrayList<Trigger>(2)
        val waiting = pending
        if (waiting != null) {
            pending = null
            val inTime = input.timeMs - waiting.timeMs <= windowMs
            val double = Trigger.DoubleTap(input.code)
            val combo = if (input.code != waiting.code) Trigger.Combo.of(waiting.code, input.code) else null
            when {
                inTime && input.press == Press.Short && input.code == waiting.code && double in used -> return listOf(double)
                inTime && combo != null && combo in used -> return listOf(combo)
                else -> out += Trigger.Tap(waiting.code)
            }
        }
        when (input.press) {
            Press.Long -> out += Trigger.Hold(input.code)
            Press.Short -> if (waitsFor(input.code, used)) pending = input else out += Trigger.Tap(input.code)
        }
        return out
    }

    /** The tap that waited for a second press, once its window has passed. */
    fun tick(nowMs: Long): List<Trigger> {
        val waiting = pending ?: return emptyList()
        if (nowMs - waiting.timeMs < windowMs) return emptyList()
        pending = null
        return listOf(Trigger.Tap(waiting.code))
    }

    /** When [tick] has something to report, or null. */
    fun nextDeadline(): Long? = pending?.let { it.timeMs + windowMs }

    companion object {
        const val DEFAULT_WINDOW_MS = 350L
    }
}
