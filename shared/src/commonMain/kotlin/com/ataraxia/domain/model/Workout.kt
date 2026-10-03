package com.ataraxia.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BodyArea(val label: String) {
    NECK("Cuello"), SHOULDERS("Hombros"), CHEST("Pecho"), TRICEPS("Tríceps"),
    BICEPS("Bíceps"), FOREARMS("Antebrazos"), BACK("Espalda"), ABS("Abdominales"), LEGS("Piernas")
}

@Serializable
data class Exercise(val id: String, val name: String, val area: BodyArea) {
    init { require(id.isNotBlank() && name.isNotBlank()) }
}

@Serializable
enum class WorkoutMode { SETS, TIMED }

@Serializable
data class WorkoutExercise(val exercise: Exercise, val sets: Int = 3, val reps: Int = 10) {
    init { require(sets in 1..999 && reps in 1..9999) }
}

/** Exercises are snapshots: later library/routine edits never rewrite a completed workout. */
@Serializable
data class WorkoutRoutine(
    val id: String,
    val name: String,
    val mode: WorkoutMode = WorkoutMode.SETS,
    val exercises: List<WorkoutExercise>,
    val durationSeconds: Int = 300,
) {
    init {
        require(id.isNotBlank() && name.isNotBlank() && exercises.isNotEmpty())
        require(exercises.map { it.exercise.id }.distinct().size == exercises.size)
        require(durationSeconds in 1..86400)
    }
}

@Serializable
data class ActiveWorkout(
    val id: String,
    val routine: WorkoutRoutine,
    val startedAt: Long,
    val remainingMillis: Long = routine.durationSeconds * 1000L,
    val deadlineMillis: Long? = if (routine.mode == WorkoutMode.TIMED) startedAt + remainingMillis else null,
) {
    fun remainingAt(now: Long): Long =
        (deadlineMillis?.let { it - now } ?: remainingMillis).coerceIn(0, routine.durationSeconds * 1000L)

    fun pause(now: Long) = copy(remainingMillis = remainingAt(now), deadlineMillis = null)

    fun resume(now: Long): ActiveWorkout =
        if (routine.mode == WorkoutMode.TIMED && deadlineMillis == null && remainingMillis > 0)
            copy(deadlineMillis = now + remainingMillis) else this
}

@Serializable
data class WorkoutSession(
    val id: String,
    val routine: WorkoutRoutine,
    val date: String,
    val completedAt: Long,
)

fun lastWorkoutDay(sessions: List<WorkoutSession>): List<WorkoutSession> {
    val date = sessions.maxByOrNull { it.completedAt }?.date ?: return emptyList()
    return sessions.filter { it.date == date }.sortedByDescending { it.completedAt }
}
