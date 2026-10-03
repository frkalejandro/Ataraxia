package com.ataraxia.ui.exercise

import com.ataraxia.domain.model.*
import kotlin.test.*

class RoutineDraftTest {
    private val target = ExerciseTarget(Exercise("pushups", "Flexiones", BodyArea.CHEST), "4", "12")
    private val draft = RoutineDraft(name = "Rutina", targets = listOf(target))

    @Test fun switchingModesIgnoresHiddenInvalidFields() {
        assertNotNull(draft.copy(minutes = "", seconds = "").build())
        assertNotNull(draft.copy(mode = WorkoutMode.TIMED, targets = listOf(target.copy(sets = "", reps = ""))).build())
        assertNull(draft.copy(targets = listOf(target.copy(sets = "0"))).build())
        assertNull(draft.copy(targets = listOf(target.copy(reps = "999999999999999"))).build())
    }

    @Test fun switchingToTimePreservesPreviouslyConfiguredSeries() {
        val timed = assertNotNull(draft.copy(mode = WorkoutMode.TIMED).build())
        val sets = assertNotNull(timed.toDraft().copy(mode = WorkoutMode.SETS).build())
        assertEquals(4, sets.exercises.single().sets)
        assertEquals(12, sets.exercises.single().reps)
    }

    @Test fun validatesNameSelectionAndDurationBoundaries() {
        assertNull(draft.copy(name = " ").build())
        assertNull(draft.copy(targets = emptyList()).build())
        val timed = draft.copy(mode = WorkoutMode.TIMED)
        assertNull(timed.copy(minutes = "0", seconds = "0").build())
        assertNull(timed.copy(minutes = "-1").build())
        assertNull(timed.copy(seconds = "60").build())
        assertNull(timed.copy(minutes = "999999999999").build())
        assertNull(timed.copy(minutes = "1440", seconds = "1").build())
        assertEquals(1, timed.copy(minutes = "0", seconds = "1").build()?.durationSeconds)
    }

    @Test fun buildsOneTimedCircuitAcrossBodyAreas() {
        val circuit = assertNotNull(draft.copy(mode = WorkoutMode.TIMED, targets = listOf(target,
            ExerciseTarget(Exercise("squats", "Sentadillas", BodyArea.LEGS)),
            ExerciseTarget(Exercise("abs", "Abdominales", BodyArea.ABS)),
        )).build())
        assertEquals(300, circuit.durationSeconds)
        assertEquals(3, circuit.exercises.size)
        assertEquals(circuit, circuit.toDraft().build())
    }
}
