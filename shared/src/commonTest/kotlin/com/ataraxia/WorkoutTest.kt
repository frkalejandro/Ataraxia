package com.ataraxia

import com.ataraxia.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class WorkoutTest {
    private val pushups = Exercise("pushups", "Flexiones de brazo", BodyArea.CHEST)
    private fun circuit() = WorkoutRoutine("circuit", "Circuito de 5 minutos", WorkoutMode.TIMED, listOf(
        WorkoutExercise(pushups),
        WorkoutExercise(Exercise("squats", "Sentadillas", BodyArea.LEGS)),
        WorkoutExercise(Exercise("abs", "Abdominales", BodyArea.ABS)),
    ), 300)

    @Test fun circuitUsesOneSharedDeadlineAndSurvivesSerialization() {
        val active = ActiveWorkout("session", circuit(), 1000)
        assertEquals(301000L, active.deadlineMillis)
        val restored = Json.decodeFromString<ActiveWorkout>(Json.encodeToString(active))
        assertEquals(3, restored.routine.exercises.size)
        assertEquals(240000L, restored.remainingAt(61000))
        assertEquals(0L, restored.remainingAt(400000))
    }

    @Test fun pauseResumeDoesNotCountPausedTimeOrRestartFinishedTimer() {
        val paused = ActiveWorkout("session", circuit(), 1000).pause(61000)
        assertNull(paused.deadlineMillis)
        assertEquals(240000L, paused.remainingAt(900000))
        val resumed = paused.resume(900000)
        assertEquals(1140000L, resumed.deadlineMillis)
        assertEquals(resumed, resumed.resume(1000000))
        val finished = resumed.pause(1200000)
        assertEquals(finished, finished.resume(1300000))
    }

    @Test fun setsSessionHasNoTimer() {
        val routine = circuit().copy(mode = WorkoutMode.SETS, exercises = listOf(WorkoutExercise(pushups, 3, 10)))
        val active = ActiveWorkout("session", routine, 0)
        assertNull(active.deadlineMillis)
        assertEquals(active, active.resume(10))
    }

    @Test fun lastDayContainsEverySessionOnThatDayAcrossMonthBoundary() {
        val older = WorkoutSession("a", circuit(), "2026-09-30", 1)
        val first = WorkoutSession("b", circuit(), "2026-10-01", 2)
        val second = WorkoutSession("c", circuit(), "2026-10-01", 3)
        assertEquals(listOf(second, first), lastWorkoutDay(listOf(second, older, first)))
        assertTrue(lastWorkoutDay(emptyList()).isEmpty())
    }

    @Test fun invalidWorkoutsAreRejected() {
        assertFailsWith<IllegalArgumentException> { pushups.copy(name = " ") }
        assertFailsWith<IllegalArgumentException> { circuit().copy(exercises = emptyList()) }
        assertFailsWith<IllegalArgumentException> { circuit().copy(exercises = listOf(WorkoutExercise(pushups), WorkoutExercise(pushups))) }
        assertFailsWith<IllegalArgumentException> { circuit().copy(durationSeconds = 0) }
        assertFailsWith<IllegalArgumentException> { WorkoutExercise(pushups, sets = -1) }
        assertFailsWith<IllegalArgumentException> { WorkoutExercise(pushups, reps = 0) }
    }
}
