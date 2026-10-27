package com.nobodybuilds.listens.data.focus

import kotlinx.serialization.Serializable

/** What a focus session is made of. All lengths in minutes. */
@Serializable
data class FocusConfig(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    /** A long break replaces the short one after every Nth focus round. */
    val longBreakEvery: Int = 4,
    val rounds: Int = 4,
) {
    fun normalized(): FocusConfig = copy(
        focusMinutes = focusMinutes.coerceIn(MIN_MINUTES, MAX_FOCUS_MINUTES),
        shortBreakMinutes = shortBreakMinutes.coerceIn(MIN_MINUTES, MAX_BREAK_MINUTES),
        longBreakMinutes = longBreakMinutes.coerceIn(MIN_MINUTES, MAX_BREAK_MINUTES),
        longBreakEvery = longBreakEvery.coerceIn(2, MAX_ROUNDS),
        rounds = rounds.coerceIn(MIN_ROUNDS, MAX_ROUNDS),
    )

    fun minutesOf(phase: FocusPhase): Int = when (phase) {
        FocusPhase.Focus -> focusMinutes
        FocusPhase.ShortBreak -> shortBreakMinutes
        FocusPhase.LongBreak -> longBreakMinutes
        FocusPhase.Idle, FocusPhase.Done -> 0
    }

    fun durationMs(phase: FocusPhase): Long = minutesOf(phase) * MINUTE_MS

    /** The break that follows focus round [round] (1-based), or [FocusPhase.Done] after the last one. */
    fun phaseAfterFocus(round: Int): FocusPhase = when {
        round >= rounds -> FocusPhase.Done
        round % longBreakEvery == 0 -> FocusPhase.LongBreak
        else -> FocusPhase.ShortBreak
    }

    companion object {
        const val MINUTE_MS = 60_000L
        const val MIN_MINUTES = 1
        const val MAX_FOCUS_MINUTES = 180
        const val MAX_BREAK_MINUTES = 60
        const val MIN_ROUNDS = 1
        const val MAX_ROUNDS = 8
        val FOCUS_PRESETS = listOf(15, 25, 45, 60)
    }
}

@Serializable
enum class FocusPhase(val label: String) {
    Idle("Ready"),
    Focus("Focus"),
    ShortBreak("Short break"),
    LongBreak("Long break"),
    Done("Done");

    val isBreak: Boolean get() = this == ShortBreak || this == LongBreak
    val isActive: Boolean get() = this == Focus || isBreak
}

/**
 * A snapshot of the timer. Serializable so a running session survives process death: while
 * running, [phaseEndsAt] is a wall-clock time, so the remaining time can be recomputed later.
 */
@Serializable
data class FocusTimerState(
    val config: FocusConfig = FocusConfig(),
    val phase: FocusPhase = FocusPhase.Idle,
    /** 1-based focus round the session is on (a break belongs to the round before it). */
    val round: Int = 0,
    val completedRounds: Int = 0,
    val phaseDurationMs: Long = 0L,
    /** Wall-clock millis when the current phase ends; null while paused or not running. */
    val phaseEndsAt: Long? = null,
    /** Time left in the current phase while paused. */
    val pausedRemainingMs: Long = 0L,
) {
    val isRunning: Boolean get() = phase.isActive && phaseEndsAt != null
    val isPaused: Boolean get() = phase.isActive && phaseEndsAt == null

    fun remainingMs(now: Long): Long = when {
        !phase.isActive -> 0L
        phaseEndsAt != null -> (phaseEndsAt - now).coerceIn(0L, phaseDurationMs)
        else -> pausedRemainingMs
    }

    /** 0 at the start of the phase, 1 when it ends. */
    fun progress(now: Long): Float =
        if (phaseDurationMs <= 0L) 0f else (1f - remainingMs(now).toFloat() / phaseDurationMs).coerceIn(0f, 1f)
}

sealed interface FocusEvent {
    /** A new phase began at [at] (also emitted for the first focus round on start). */
    data class PhaseStarted(val phase: FocusPhase, val round: Int, val at: Long) : FocusEvent

    /** A focus round ran to its end. Skipped or stopped rounds don't count. */
    data class FocusCompleted(val round: Int, val minutes: Int, val at: Long) : FocusEvent

    /** Every round is done. */
    data class Finished(val completedRounds: Int, val at: Long) : FocusEvent
}

/**
 * The focus-session state machine: Focus → short/long break → Focus … → Done.
 *
 * Pure Kotlin, no Android: every call takes the current wall-clock time, so it is easy to test
 * and to catch up after the device slept or the process died ([advance] replays every phase end
 * that has passed, each next phase starting exactly when the previous one ended).
 */
class FocusTimer(initial: FocusTimerState = FocusTimerState()) {

    var state: FocusTimerState = initial
        private set

    fun restore(snapshot: FocusTimerState) {
        state = snapshot
    }

    /** Starts round 1 of a new session, replacing any current one. */
    fun start(config: FocusConfig, now: Long): List<FocusEvent> {
        val c = config.normalized()
        state = FocusTimerState(config = c, completedRounds = 0)
        return listOf(enter(FocusPhase.Focus, round = 1, startAt = now))
    }

    fun pause(now: Long): Boolean {
        val s = state
        if (!s.isRunning) return false
        state = s.copy(phaseEndsAt = null, pausedRemainingMs = s.remainingMs(now))
        return true
    }

    fun resume(now: Long): Boolean {
        val s = state
        if (!s.isPaused) return false
        state = s.copy(phaseEndsAt = now + s.pausedRemainingMs, pausedRemainingMs = 0L)
        return true
    }

    /** Ends the current phase now and moves on. A skipped focus round is not counted. */
    fun skip(now: Long): List<FocusEvent> {
        val s = state
        if (!s.phase.isActive) return emptyList()
        val wasPaused = s.isPaused
        val events = next(completed = false, at = now)
        // Skipping while paused keeps the session paused in the new phase.
        if (wasPaused && state.isRunning) pause(now)
        return events
    }

    /** Abandons the session. */
    fun stop() {
        state = state.copy(phase = FocusPhase.Idle, phaseEndsAt = null, pausedRemainingMs = 0L, phaseDurationMs = 0L)
    }

    /** Processes every phase end at or before [now]. Returns what happened, in order. */
    fun advance(now: Long): List<FocusEvent> {
        val events = mutableListOf<FocusEvent>()
        var guard = 0
        while (guard++ < 64) {
            val endsAt = state.phaseEndsAt ?: break
            if (!state.phase.isActive || endsAt > now) break
            events += next(completed = true, at = endsAt)
        }
        return events
    }

    /** Wall-clock time of the next phase change, or null when nothing is scheduled. */
    fun nextDeadline(): Long? = state.phaseEndsAt?.takeIf { state.phase.isActive }

    private fun next(completed: Boolean, at: Long): List<FocusEvent> {
        val s = state
        val events = mutableListOf<FocusEvent>()
        when (s.phase) {
            FocusPhase.Focus -> {
                if (completed) {
                    state = s.copy(completedRounds = s.completedRounds + 1)
                    events += FocusEvent.FocusCompleted(s.round, s.config.focusMinutes, at)
                }
                val after = s.config.phaseAfterFocus(s.round)
                if (after == FocusPhase.Done) {
                    state = state.copy(phase = FocusPhase.Done, phaseEndsAt = null, pausedRemainingMs = 0L, phaseDurationMs = 0L)
                    events += FocusEvent.PhaseStarted(FocusPhase.Done, s.round, at)
                    events += FocusEvent.Finished(state.completedRounds, at)
                } else {
                    events += enter(after, s.round, at)
                }
            }
            FocusPhase.ShortBreak, FocusPhase.LongBreak -> events += enter(FocusPhase.Focus, s.round + 1, at)
            FocusPhase.Idle, FocusPhase.Done -> Unit
        }
        return events
    }

    private fun enter(phase: FocusPhase, round: Int, startAt: Long): FocusEvent {
        val duration = state.config.durationMs(phase)
        state = state.copy(
            phase = phase,
            round = round,
            phaseDurationMs = duration,
            phaseEndsAt = startAt + duration,
            pausedRemainingMs = 0L,
        )
        return FocusEvent.PhaseStarted(phase, round, startAt)
    }
}
