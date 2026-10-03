package com.ataraxia

import com.ataraxia.domain.model.*
import com.ataraxia.notifications.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class TimerNotificationTest {
    private val routine = WorkoutRoutine("r", "Piernas", WorkoutMode.TIMED,
        listOf(WorkoutExercise(Exercise("e", "Sentadilla", BodyArea.LEGS))), 60)

    @Test fun focusPauseResetAndCompletionRemoveCountdown() {
        val running = FocusTimer().start(1000)
        assertEquals(1501000L, running.notification()?.deadlineMillis)
        assertNull(running.pause(2000).notification())
        assertNull(running.reset().notification())
        assertNull(running.tick(1501000).notification())
        assertEquals("Tu tiempo de concentración terminó", running.notification()?.completion)
    }

    @Test fun breaksIdentifyTheirPhaseWithoutReportingFocusCompletion() {
        val shortBreak = FocusTimer().start(0).tick(1500000).start(1600000)
        assertEquals("Enfoque · Descanso corto", shortBreak.notification()?.title)
        assertEquals("Tu tiempo de descanso terminó", shortBreak.notification()?.completion)
        val longBreak = FocusTimer(completedSessions = 3).start(0).tick(1500000).start(1600000)
        assertEquals("Enfoque · Descanso largo", longBreak.notification()?.title)
    }

    @Test fun workoutPauseResumeUsesNewDeadlineAndSetsDoNotNotify() {
        val active = ActiveWorkout("a", routine, 1000)
        assertEquals("Ejercicio · Piernas", active.notification()?.title)
        assertEquals("Tu tiempo de ejercicio terminó", active.notification()?.completion)
        val paused = active.pause(31000)
        assertNull(paused.notification())
        assertEquals(130000L, paused.resume(100000).notification()?.deadlineMillis)
        assertNull(ActiveWorkout("b", routine.copy(mode = WorkoutMode.SETS), 0).notification())
    }

    @Test fun persistedFocusRecoversDeadlineAndCompletesOnlyOnce() {
        val timer = FocusTimer(completedSessions = 3).start(1000)
        val restored = Json.decodeFromString<FocusTimer>(Json.encodeToString(timer))
        assertEquals(timer, restored)
        val ended = restored.tick(1501000)
        assertEquals(FocusPhase.LONG_BREAK, ended.phase)
        assertEquals(4, ended.completedSessions)
        assertEquals(ended, ended.tick(9999999))
        val paused = restored.pause(31000)
        assertEquals(paused, Json.decodeFromString<FocusTimer>(Json.encodeToString(paused)))
    }
}
