package com.nobodybuilds.listens.data.focus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusTimerTest {

    private val min = 60_000L

    @Test
    fun `defaults are 25-5-15 with a long break every 4 rounds`() {
        val c = FocusConfig()
        assertEquals(25, c.focusMinutes)
        assertEquals(5, c.shortBreakMinutes)
        assertEquals(15, c.longBreakMinutes)
        assertEquals(4, c.longBreakEvery)
    }

    @Test
    fun `start enters focus round 1 with the right end time`() {
        val t = FocusTimer()
        val events = t.start(FocusConfig(), now = 1_000L)
        assertEquals(listOf(FocusEvent.PhaseStarted(FocusPhase.Focus, 1, 1_000L)), events)
        assertEquals(FocusPhase.Focus, t.state.phase)
        assertEquals(1, t.state.round)
        assertEquals(1_000L + 25 * min, t.state.phaseEndsAt)
        assertTrue(t.state.isRunning)
        assertEquals(25 * min, t.state.remainingMs(1_000L))
    }

    @Test
    fun `advance before the end does nothing`() {
        val t = FocusTimer()
        t.start(FocusConfig(), 0L)
        assertTrue(t.advance(25 * min - 1).isEmpty())
        assertEquals(FocusPhase.Focus, t.state.phase)
    }

    @Test
    fun `completing focus records the round and starts a short break`() {
        val t = FocusTimer()
        t.start(FocusConfig(), 0L)
        val events = t.advance(25 * min)
        assertEquals(
            listOf(
                FocusEvent.FocusCompleted(1, 25, 25 * min),
                FocusEvent.PhaseStarted(FocusPhase.ShortBreak, 1, 25 * min),
            ),
            events
        )
        assertEquals(1, t.state.completedRounds)
        assertEquals(30 * min, t.state.phaseEndsAt)
    }

    @Test
    fun `full session follows focus, short and long breaks and ends with done`() {
        val c = FocusConfig(focusMinutes = 10, shortBreakMinutes = 2, longBreakMinutes = 5, longBreakEvery = 2, rounds = 3)
        val t = FocusTimer()
        t.start(c, 0L)
        val phases = mutableListOf(t.state.phase)
        var now = 0L
        while (t.state.phase.isActive) {
            now = t.nextDeadline()!!
            t.advance(now).filterIsInstance<FocusEvent.PhaseStarted>().forEach { phases += it.phase }
        }
        assertEquals(
            listOf(
                FocusPhase.Focus, FocusPhase.ShortBreak,
                FocusPhase.Focus, FocusPhase.LongBreak,
                FocusPhase.Focus, FocusPhase.Done,
            ),
            phases
        )
        assertEquals(3, t.state.completedRounds)
        assertEquals(10 + 2 + 10 + 5 + 10, (now / min).toInt())
        assertNull(t.nextDeadline())
    }

    @Test
    fun `advance catches up across several phases at once`() {
        val c = FocusConfig(focusMinutes = 10, shortBreakMinutes = 2, rounds = 4)
        val t = FocusTimer()
        t.start(c, 0L)
        // 10 focus + 2 break + 10 focus + 2 break = 24; at 25 we're 1 min into round 3.
        val events = t.advance(25 * min)
        assertEquals(2, events.count { it is FocusEvent.FocusCompleted })
        assertEquals(FocusPhase.Focus, t.state.phase)
        assertEquals(3, t.state.round)
        assertEquals(9 * min, t.state.remainingMs(25 * min))
    }

    @Test
    fun `a single round ends without a break`() {
        val t = FocusTimer()
        t.start(FocusConfig(rounds = 1), 0L)
        val events = t.advance(25 * min)
        assertTrue(events.any { it is FocusEvent.Finished })
        assertEquals(FocusPhase.Done, t.state.phase)
        assertFalse(t.state.isRunning)
    }

    @Test
    fun `pause keeps the remaining time and resume continues from it`() {
        val t = FocusTimer()
        t.start(FocusConfig(), 0L)
        assertTrue(t.pause(5 * min))
        assertTrue(t.state.isPaused)
        assertNull(t.nextDeadline())
        // Time passing while paused changes nothing.
        assertTrue(t.advance(100 * min).isEmpty())
        assertEquals(20 * min, t.state.remainingMs(100 * min))
        assertTrue(t.resume(100 * min))
        assertEquals(120 * min, t.state.phaseEndsAt)
        assertTrue(t.state.isRunning)
    }

    @Test
    fun `pause and resume are no-ops in the wrong state`() {
        val t = FocusTimer()
        assertFalse(t.pause(0L))
        assertFalse(t.resume(0L))
        t.start(FocusConfig(), 0L)
        assertFalse(t.resume(1L))
    }

    @Test
    fun `skipping focus does not count the round`() {
        val t = FocusTimer()
        t.start(FocusConfig(), 0L)
        val events = t.skip(3 * min)
        assertTrue(events.none { it is FocusEvent.FocusCompleted })
        assertEquals(FocusPhase.ShortBreak, t.state.phase)
        assertEquals(0, t.state.completedRounds)
        assertEquals(8 * min, t.state.phaseEndsAt)
    }

    @Test
    fun `skipping a break starts the next focus round now`() {
        val t = FocusTimer()
        t.start(FocusConfig(), 0L)
        t.advance(25 * min)
        t.skip(26 * min)
        assertEquals(FocusPhase.Focus, t.state.phase)
        assertEquals(2, t.state.round)
        assertEquals(51 * min, t.state.phaseEndsAt)
    }

    @Test
    fun `skipping while paused stays paused in the next phase`() {
        val t = FocusTimer()
        t.start(FocusConfig(), 0L)
        t.pause(min)
        t.skip(2 * min)
        assertEquals(FocusPhase.ShortBreak, t.state.phase)
        assertTrue(t.state.isPaused)
        assertEquals(5 * min, t.state.remainingMs(10 * min))
    }

    @Test
    fun `stop returns to idle`() {
        val t = FocusTimer()
        t.start(FocusConfig(), 0L)
        t.stop()
        assertEquals(FocusPhase.Idle, t.state.phase)
        assertNull(t.nextDeadline())
        assertTrue(t.skip(1L).isEmpty())
    }

    @Test
    fun `restore continues a saved session`() {
        val t = FocusTimer()
        t.start(FocusConfig(), 0L)
        val saved = t.state
        val restored = FocusTimer().apply { restore(saved) }
        val events = restored.advance(25 * min)
        assertEquals(1, events.count { it is FocusEvent.FocusCompleted })
    }

    @Test
    fun `config is clamped`() {
        val c = FocusConfig(focusMinutes = 0, shortBreakMinutes = 999, rounds = 20, longBreakEvery = 1).normalized()
        assertEquals(1, c.focusMinutes)
        assertEquals(FocusConfig.MAX_BREAK_MINUTES, c.shortBreakMinutes)
        assertEquals(FocusConfig.MAX_ROUNDS, c.rounds)
        assertEquals(2, c.longBreakEvery)
    }

    @Test
    fun `progress goes from 0 to 1`() {
        val t = FocusTimer()
        t.start(FocusConfig(focusMinutes = 10), 0L)
        assertEquals(0f, t.state.progress(0L))
        assertEquals(0.5f, t.state.progress(5 * min))
        assertEquals(1f, t.state.progress(10 * min))
    }

    @Test
    fun `instrumental filter matches genres and titles`() {
        assertTrue(FocusMusicFilter.isInstrumentalLeaning("Clair de lune", "Classical"))
        assertTrue(FocusMusicFilter.isInstrumentalLeaning("Night drive", "Lo-Fi Hip Hop"))
        assertTrue(FocusMusicFilter.isInstrumentalLeaning("Song (Instrumental)", "Pop"))
        assertTrue(FocusMusicFilter.isInstrumentalLeaning("Theme", "Original Soundtrack"))
        assertFalse(FocusMusicFilter.isInstrumentalLeaning("Loud song", "Rock"))
        assertFalse(FocusMusicFilter.isInstrumentalLeaning("Untitled", null))
    }
}
