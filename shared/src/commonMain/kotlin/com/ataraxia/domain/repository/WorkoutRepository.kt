package com.ataraxia.domain.repository

import com.ataraxia.domain.model.*
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun observeExercises(): Flow<List<Exercise>>
    fun observeRoutines(): Flow<List<WorkoutRoutine>>
    fun observeSessions(): Flow<List<WorkoutSession>>
    fun observeActive(): Flow<ActiveWorkout?>
    suspend fun saveExercise(exercise: Exercise)
    suspend fun deleteExercise(id: String)
    suspend fun saveRoutine(routine: WorkoutRoutine)
    suspend fun deleteRoutine(id: String)
    suspend fun saveActive(workout: ActiveWorkout)
    suspend fun discardActive()
    suspend fun completeActive(completedAt: Long, date: String)
}
