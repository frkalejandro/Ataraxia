package com.ataraxia

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ataraxia.data.repository.WorkoutRepositoryImpl
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.*
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class WorkoutRepositoryTest {
    private val exercise = Exercise("pushups", "Flexiones de brazo", BodyArea.CHEST)
    private val routine = WorkoutRoutine("routine", "Pecho", exercises = listOf(WorkoutExercise(exercise, 3, 10)))

    @Test fun savedRoutineAndActiveSessionSurviveDatabaseReopen() = runBlocking {
        val file = Files.createTempFile("atarax-workout-test", ".db").toFile()
        try {
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = AtaraxiaDatabase.Schema).use { driver ->
                val repo = WorkoutRepositoryImpl(AtaraxiaDatabase(driver))
                repo.saveExercise(exercise)
                repo.saveRoutine(routine)
                repo.saveActive(ActiveWorkout("session", routine, 1000))
                assertTrue(repo.observeSessions().first().isEmpty())
            }
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = AtaraxiaDatabase.Schema).use { driver ->
                val repo = WorkoutRepositoryImpl(AtaraxiaDatabase(driver))
                assertEquals(listOf(exercise), repo.observeExercises().first())
                assertEquals(listOf(routine), repo.observeRoutines().first())
                assertEquals("session", repo.observeActive().first()?.id)
                repo.completeActive(2000, "2026-09-30")
                repo.completeActive(3000, "2026-09-30")
                assertNull(repo.observeActive().first())
                assertEquals(1, repo.observeSessions().first().size)
                repo.saveExercise(exercise.copy(name = "Flexiones editadas"))
                repo.saveRoutine(routine.copy(mode = WorkoutMode.TIMED, durationSeconds = 300))
                repo.deleteExercise(exercise.id)
                repo.deleteRoutine(routine.id)
                assertEquals(routine, repo.observeSessions().first().single().routine)
                repo.saveActive(ActiveWorkout("discarded", routine, 4000))
                repo.discardActive()
                assertEquals(1, repo.observeSessions().first().size)
                assertNull(repo.observeActive().first())
            }
        } finally { file.delete() }
    }

    @Test fun migrationFromVersionThreePreservesExistingDataAndAddsWorkoutTables() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            AtaraxiaDatabase.Schema.create(driver)
            val db = AtaraxiaDatabase(driver)
            db.journalQueries.insertEntry("entry", "Conservar esta entrada", null, "", 1, 1)
            listOf("ExerciseDefinition", "SavedWorkout", "CompletedWorkout", "CurrentWorkout").forEach {
                driver.execute(null, "DROP TABLE $it", 0)
            }
            AtaraxiaDatabase.Schema.migrate(driver, 3, 4)
            assertEquals("Conservar esta entrada", db.journalQueries.selectEntryById("entry").executeAsOne().content)
            db.workoutQueries.saveExercise("exercise", "Sentadillas", "LEGS")
            assertEquals(1, db.workoutQueries.selectExercises().executeAsList().size)
            assertTrue(db.workoutQueries.selectRoutines().executeAsList().isEmpty())
            assertTrue(db.workoutQueries.selectSessions().executeAsList().isEmpty())
            assertNull(db.workoutQueries.selectActive().executeAsOneOrNull())
        }
    }
}
