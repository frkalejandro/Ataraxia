package com.ataraxia

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import com.ataraxia.data.repository.*
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import java.nio.file.Files
import kotlin.test.*

class CrudRepositoryTest {
    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val now = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
    private fun database() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        it.execute(null, "PRAGMA foreign_keys = ON", 0)
        AtaraxiaDatabase.Schema.create(it)
    }
    private fun sleep(id: String, date: LocalDate = today) = SleepEntry(id, date,
        date.minus(1, DateTimeUnit.DAY).atTime(23, 0).toInstant(TimeZone.currentSystemDefault()),
        date.atTime(7, 0).toInstant(TimeZone.currentSystemDefault()), 480, 3, "Original", now)

    @Test fun financeEditDeleteAndOpeningBalanceRemainCorrectAfterReopen() = runBlocking {
        val file = Files.createTempFile("atarax-crud", ".db").toFile()
        try {
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = AtaraxiaDatabase.Schema).use { driver ->
                val repo = FinanceRepositoryImpl(AtaraxiaDatabase(driver))
                repo.add(FinanceEntry("opening", "Saldo", FinanceCategory.INITIAL, 1000, 1))
                repo.add(FinanceEntry("income", "Regalo", FinanceCategory.GIFT, 500, 2))
                val expense = FinanceEntry("expense", "Comida", FinanceCategory.FOOD, 400, 3)
                repo.add(expense)
                repo.update(expense.copy(name = "Herramientas", category = FinanceCategory.TOOLS, amount = 600))
                assertEquals(900L, FinanceSummary(repo.observeEntries().first()).available)
                assertEquals(FinanceCategory.TOOLS, FinanceSummary(repo.observeEntries().first()).shares.single().category)
                val initial = repo.observeEntries().first().first { it.kind == FinanceKind.INITIAL }
                repo.update(initial.copy(amount = 2000))
                assertFailsWith<IllegalArgumentException> { repo.update(expense.copy(category = FinanceCategory.SALARY)) }
                repo.delete(expense.id)
                repo.delete("income")
                assertEquals(2000L, FinanceSummary(repo.observeEntries().first()).available)
                repo.delete(initial.id)
                repo.add(FinanceEntry("new-opening", "Nuevo saldo", FinanceCategory.INITIAL, 2500, 4))
            }
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = AtaraxiaDatabase.Schema).use { driver ->
                val summary = FinanceSummary(FinanceRepositoryImpl(AtaraxiaDatabase(driver)).observeEntries().first())
                assertEquals(1, summary.entries.size)
                assertEquals(2500L, summary.available)
                assertTrue(summary.shares.isEmpty())
            }
        } finally { file.delete() }
    }

    @Test fun habitEditingPreservesTrackingAndDeletingDoesNotBreakObservers() = runBlocking {
        database().use { driver ->
            val db = AtaraxiaDatabase(driver)
            val repo = HabitRepositoryImpl(db)
            val habit = Habit("habit", "Caminar", category = HabitCategory.MORNING, createdAt = now, updatedAt = now)
            repo.saveHabit(habit)
            repo.toggleHabitEntry(habit.id, today)
            repo.saveHabit(habit.copy(title = "Caminar 30 minutos", category = HabitCategory.NIGHT))
            assertEquals(1, repo.observeEntriesForDate(today).first().size)
            assertEquals(1, repo.getStreak(habit.id, today))
            repo.archiveHabit(habit.id)
            val archived = repo.observeArchivedHabits().first().single()
            repo.saveHabit(archived.copy(isArchived = false))
            assertTrue(repo.observeArchivedHabits().first().isEmpty())
            assertEquals(1, repo.observeEntriesForDate(today).first().size)
            repo.observeAllHabitsWithStatus(today).test {
                assertEquals("Caminar 30 minutos", awaitItem().single().habit.title)
                repo.deleteHabit(habit.id)
                while (awaitItem().isNotEmpty()) { /* discard intermediate query invalidations */ }
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(repo.getHabitById(habit.id))
            assertTrue(db.habitQueries.selectEntriesForHabit(habit.id).executeAsList().isEmpty())
        }
    }

    @Test fun sleepCanMoveDatesWithoutReplacingOtherNightsAndStatsRefresh() = runBlocking {
        database().use { driver ->
            val repo = SleepRepositoryImpl(AtaraxiaDatabase(driver))
            val first = sleep("night")
            val yesterday = today.minus(1, DateTimeUnit.DAY)
            val other = sleep("other", yesterday)
            repo.saveSleepEntry(first)
            repo.saveSleepEntry(other)
            assertFailsWith<IllegalArgumentException> { repo.saveSleepEntry(first.copy(date = yesterday)) }
            assertEquals(other, repo.getSleepEntryByDate(yesterday))
            repo.saveSleepEntry(first.copy(durationMinutes = 420, quality = 5, note = "Corregido"))
            assertEquals(450f, repo.getSleepStats(7).avgDurationMinutes)
            assertEquals(2, repo.observeSleepEntries().first().size)
            repo.deleteSleepEntry(other.id)
            assertEquals(420f, repo.getSleepStats(7).avgDurationMinutes)
            repo.saveSleepEntry(first.copy(date = yesterday, quality = 5))
            assertNull(repo.getSleepEntryByDate(today))
            assertEquals(first.createdAt, repo.getSleepEntryByDate(yesterday)?.createdAt)
            repo.deleteSleepEntry(first.id)
            assertTrue(repo.observeSleepEntries().first().isEmpty())
            assertEquals(0f, repo.getSleepStats(7).avgDurationMinutes)
            repo.saveSleepGoal(SleepGoal("goal", LocalTime(23, 0), LocalTime(7, 0), updatedAt = now))
            repo.deleteSleepGoal()
            assertNull(repo.observeSleepGoal().first())
        }
    }

    @Test fun agendaEditsPreserveCompletionAndDeletesRemoveCalendarEntries() = runBlocking {
        database().use { driver ->
            val db = AtaraxiaDatabase(driver)
            val tasks = TaskRepositoryImpl(db)
            val events = CalendarRepositoryImpl(db)
            val task = Task("task", "Leer", dueDate = today, createdAt = now, updatedAt = now)
            tasks.saveTask(task)
            tasks.updateTaskStatus(task.id, TaskStatus.DONE)
            val completed = tasks.getTaskById(task.id)!!
            tasks.saveTask(completed.copy(title = "Leer un capítulo", priority = TaskPriority.HIGH))
            assertTrue(tasks.getTaskById(task.id)!!.isCompleted)
            assertEquals(completed.completedAt, tasks.getTaskById(task.id)!!.completedAt)
            val event = CalendarEvent("event", "Visita", date = today, createdAt = now, updatedAt = now)
            events.saveEvent(event)
            events.saveEvent(event.copy(title = "Visita nueva", date = today.plus(1, DateTimeUnit.DAY)))
            assertTrue(events.observeEventsForDate(today).first().isEmpty())
            tasks.deleteTask(task.id)
            events.deleteEvent(event.id)
            assertNull(tasks.getTaskById(task.id))
            assertNull(events.getEventById(event.id))
        }
    }

    @Test fun exerciseSessionChangesDoNotRewriteTheSavedRoutine() = runBlocking {
        database().use { driver ->
            val repo = WorkoutRepositoryImpl(AtaraxiaDatabase(driver))
            val routine = WorkoutRoutine("routine", "Piernas", exercises = listOf(WorkoutExercise(Exercise("ex", "Sentadilla", BodyArea.LEGS))))
            repo.saveRoutine(routine)
            repo.saveActive(ActiveWorkout("session", routine, 0))
            repo.completeActive(now.toEpochMilliseconds(), today.toString())
            val session = repo.observeSessions().first().single()
            repo.updateSession(session.copy(routine = routine.copy(name = "Piernas corregido",
                exercises = listOf(routine.exercises.single().copy(reps = 15)))))
            assertEquals(15, repo.observeSessions().first().single().routine.exercises.single().reps)
            assertEquals(routine, repo.observeRoutines().first().single())
            repo.deleteSession(session.id)
            assertTrue(repo.observeSessions().first().isEmpty())
            assertEquals(1, repo.observeRoutines().first().size)
        }
    }

    @Test fun deletedFocusBlocksAreNotRecreatedByDelayedTimerRecovery() = runBlocking {
        database().use { driver ->
            val repo = FocusRepositoryImpl(AtaraxiaDatabase(driver))
            val start = today.atTime(10, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
            val timer = FocusTimer().start(start)
            val end = start + 25 * 60 * 1000
            repo.recordElapsedBlock(timer, end)
            val record = repo.observeBlocks().first().single()
            repo.updateBlock(record.copy(durationSeconds = 600))
            repo.recordElapsedBlock(timer, end + 1)
            assertEquals(600L, repo.observeTotalSeconds(today).first())
            repo.deleteBlock(record.id)
            repo.recordElapsedBlock(timer, end + 2)
            assertTrue(repo.observeBlocks().first().isEmpty())
            assertEquals(0L, repo.observeTotalSeconds(today).first())
            val manual = FocusRecord("manual", today, 300, end)
            repo.addBlock(manual)
            repo.updateBlock(manual.copy(date = today.minus(1, DateTimeUnit.DAY), durationSeconds = 450))
            assertEquals(0L, repo.observeTotalSeconds(today).first())
            assertEquals(450L, repo.observeTotalSeconds(today.minus(1, DateTimeUnit.DAY)).first())
        }
    }

    @Test fun stateAndJournalAreIndependentOfSleepWhenDeleted() = runBlocking {
        database().use { driver ->
            val db = AtaraxiaDatabase(driver)
            val states = DailyStateRepositoryImpl(db)
            val sleeps = SleepRepositoryImpl(db)
            sleeps.saveSleepEntry(sleep("night"))
            val state = DailyState("state", today, 3, 3, 3, false, musclePain = 1, fatigue = 2,
                concentration = 3, createdAt = now, updatedAt = now)
            states.saveState(state)
            states.saveState(state.copy(mood = 5))
            assertEquals(5, states.getStateForDate(today)?.mood)
            states.deleteState(state.id)
            assertNull(states.getStateForDate(today))
            assertNotNull(sleeps.getSleepEntryByDate(today))
            val journals = JournalRepositoryImpl(db)
            val entry = JournalEntry("journal", "Original", mood = null, createdAt = now, updatedAt = now)
            journals.saveEntry(entry)
            journals.saveEntry(entry.copy(content = "Corregido"))
            assertEquals(1, journals.observeAllEntries().first().size)
            journals.deleteEntry(entry.id)
            assertTrue(journals.observeAllEntries().first().isEmpty())
        }
    }
}
