package com.ataraxia.notifications

import com.ataraxia.domain.model.*
import kotlinx.serialization.Serializable

enum class TimerKind { FOCUS, EXERCISE }

@Serializable
data class TimerNotice(
    val title: String,
    val completion: String,
    val deadlineMillis: Long,
)

fun FocusTimer.notification(): TimerNotice? = deadlineMillis?.let {
    TimerNotice(when (phase) {
        FocusPhase.FOCUS -> "Enfoque · Concentración"
        FocusPhase.SHORT_BREAK -> "Enfoque · Descanso corto"
        FocusPhase.LONG_BREAK -> "Enfoque · Descanso largo"
    }, if (phase == FocusPhase.FOCUS) "Tu tiempo de concentración terminó"
        else "Tu tiempo de descanso terminó", it)
}

fun ActiveWorkout.notification(): TimerNotice? =
    if (routine.mode == WorkoutMode.TIMED) deadlineMillis?.let {
        TimerNotice("Ejercicio · ${routine.name}", "Tu tiempo de ejercicio terminó", it)
    } else null

interface TimerNotificationScheduler {
    val supported: Boolean get() = false
    val exactAlarmsAllowed: Boolean get() = true
    fun requestExactAlarmPermission() = Unit
    fun update(kind: TimerKind, notice: TimerNotice?)
    fun checkDue()
    fun restoreFocus(): FocusTimer?
    fun saveFocus(timer: FocusTimer)
}

class NoOpTimerNotificationScheduler : TimerNotificationScheduler {
    override fun update(kind: TimerKind, notice: TimerNotice?) = Unit
    override fun checkDue() = Unit
    override fun restoreFocus(): FocusTimer? = null
    override fun saveFocus(timer: FocusTimer) = Unit
}
