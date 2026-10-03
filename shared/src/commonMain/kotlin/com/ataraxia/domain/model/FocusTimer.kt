package com.ataraxia.domain.model

enum class FocusPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

data class FocusTimer(
    val focusMinutes: Int = 25,
    val phase: FocusPhase = FocusPhase.FOCUS,
    val remainingSeconds: Int = 25 * 60,
    val deadlineMillis: Long? = null,
    val completedSessions: Int = 0,
    val awaitingNext: Boolean = false,
) {
    val durationSeconds: Int get() = when (phase) {
        FocusPhase.FOCUS -> focusMinutes * 60
        FocusPhase.SHORT_BREAK -> 5 * 60
        FocusPhase.LONG_BREAK -> 15 * 60
    }

    fun start(now: Long): FocusTimer = if (deadlineMillis != null) this
        else copy(deadlineMillis = now + remainingSeconds * 1000L, awaitingNext = false)

    fun tick(now: Long): FocusTimer {
        val end = deadlineMillis ?: return this
        if (now < end) return copy(remainingSeconds = ((end - now + 999) / 1000).toInt())
        val completed = completedSessions + if (phase == FocusPhase.FOCUS) 1 else 0
        val next = if (phase != FocusPhase.FOCUS) FocusPhase.FOCUS
            else if (completed % 4 == 0) FocusPhase.LONG_BREAK else FocusPhase.SHORT_BREAK
        val timer = copy(phase = next, deadlineMillis = null, completedSessions = completed, awaitingNext = true)
        return timer.copy(remainingSeconds = timer.durationSeconds)
    }

    fun pause(now: Long): FocusTimer = tick(now).copy(deadlineMillis = null)
    fun reset(): FocusTimer = copy(remainingSeconds = durationSeconds, deadlineMillis = null, awaitingNext = false)
    fun setMinutes(minutes: Int): FocusTimer {
        require(minutes in 1..120)
        return copy(focusMinutes = minutes, phase = FocusPhase.FOCUS, remainingSeconds = minutes * 60,
            deadlineMillis = null, awaitingNext = false)
    }
}
