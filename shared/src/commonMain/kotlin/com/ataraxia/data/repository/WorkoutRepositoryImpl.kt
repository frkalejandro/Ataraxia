package com.ataraxia.data.repository

import app.cash.sqldelight.coroutines.*
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.*
import com.ataraxia.domain.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutRepositoryImpl(private val db: AtaraxiaDatabase) : WorkoutRepository {
    private val q get() = db.workoutQueries
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun observeExercises() = q.selectExercises().asFlow().mapToList(Dispatchers.Default).map { rows ->
        rows.map { Exercise(it.id, it.name, BodyArea.valueOf(it.area)) }
    }
    override fun observeRoutines() = q.selectRoutines().asFlow().mapToList(Dispatchers.Default).map { rows ->
        rows.map { json.decodeFromString<WorkoutRoutine>(it.payload) }.sortedBy { it.name.lowercase() }
    }
    override fun observeSessions() = q.selectSessions().asFlow().mapToList(Dispatchers.Default).map { rows ->
        rows.map { json.decodeFromString<WorkoutSession>(it.payload) }
    }
    override fun observeActive() = q.selectActive().asFlow().mapToOneOrNull(Dispatchers.Default).map { row ->
        row?.let { json.decodeFromString<ActiveWorkout>(it.payload) }
    }
    override suspend fun saveExercise(exercise: Exercise) = withContext(Dispatchers.Default) {
        q.saveExercise(exercise.id, exercise.name.trim(), exercise.area.name)
    }
    override suspend fun deleteExercise(id: String) = withContext(Dispatchers.Default) { q.deleteExercise(id) }
    override suspend fun saveRoutine(routine: WorkoutRoutine) = withContext(Dispatchers.Default) {
        q.saveRoutine(routine.id, json.encodeToString(routine))
    }
    override suspend fun deleteRoutine(id: String) = withContext(Dispatchers.Default) { q.deleteRoutine(id) }
    override suspend fun saveActive(workout: ActiveWorkout) = withContext(Dispatchers.Default) {
        q.saveActive(json.encodeToString(workout))
    }
    override suspend fun discardActive() = withContext(Dispatchers.Default) { q.clearActive() }
    override suspend fun completeActive(completedAt: Long, date: String) = withContext(Dispatchers.Default) {
        db.transaction {
            val active = q.selectActive().executeAsOneOrNull()?.let {
                json.decodeFromString<ActiveWorkout>(it.payload)
            } ?: return@transaction
            val session = WorkoutSession(active.id, active.routine, date, completedAt)
            q.saveSession(session.id, completedAt, json.encodeToString(session))
            q.clearActive()
        }
    }
}
