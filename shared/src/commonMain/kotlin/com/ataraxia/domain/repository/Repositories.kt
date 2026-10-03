package com.ataraxia.domain.repository

import com.ataraxia.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

// ─────────────────────────────────────────────
//  Hábitos
// ─────────────────────────────────────────────

interface HabitRepository {
    fun observeHabits(): Flow<List<Habit>>
    fun observeHabitWithStatus(habitId: String, today: LocalDate): Flow<HabitWithStatus>
    fun observeAllHabitsWithStatus(today: LocalDate): Flow<List<HabitWithStatus>>
    suspend fun getHabitById(id: String): Habit?
    suspend fun saveHabit(habit: Habit)
    suspend fun archiveHabit(id: String)
    suspend fun deleteHabit(id: String)

    // Entries
    fun observeEntriesForDate(date: LocalDate): Flow<List<HabitEntry>>
    suspend fun toggleHabitEntry(habitId: String, date: LocalDate)
    suspend fun setHabitEntryCount(habitId: String, date: LocalDate, count: Int, note: String = "")
    suspend fun getStreak(habitId: String, today: LocalDate): Int
    suspend fun getCompletionRate(habitId: String, from: LocalDate, to: LocalDate): Float
}

// ─────────────────────────────────────────────
//  Sueño
// ─────────────────────────────────────────────

interface SleepRepository {
    fun observeSleepGoal(): Flow<SleepGoal?>
    suspend fun saveSleepGoal(goal: SleepGoal)

    fun observeSleepEntries(limit: Int = 30): Flow<List<SleepEntry>>
    fun observeSleepEntriesInRange(from: LocalDate, to: LocalDate): Flow<List<SleepEntry>>
    suspend fun getSleepEntryByDate(date: LocalDate): SleepEntry?
    suspend fun saveSleepEntry(entry: SleepEntry)
    suspend fun deleteSleepEntry(id: String)
    suspend fun getSleepStats(lastDays: Int): SleepStats
}

// ─────────────────────────────────────────────
//  Agenda
// ─────────────────────────────────────────────

interface TaskRepository {
    fun observeActiveTasks(): Flow<List<Task>>
    fun observeTasksByDate(date: LocalDate): Flow<List<Task>>
    fun observeTasksByDateRange(from: LocalDate, to: LocalDate): Flow<List<Task>>
    fun observeTasksByProject(projectId: String): Flow<List<Task>>
    suspend fun getTaskById(id: String): Task?
    suspend fun saveTask(task: Task)
    suspend fun updateTaskStatus(id: String, status: TaskStatus)
    suspend fun deleteTask(id: String)

    fun observeProjects(): Flow<List<Project>>
    suspend fun saveProject(project: Project)
    suspend fun deleteProject(id: String)
}

interface CalendarRepository {
    fun observeEventsForDate(date: LocalDate): Flow<List<CalendarEvent>>
    fun observeEventsForRange(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>>
    suspend fun getEventById(id: String): CalendarEvent?
    suspend fun saveEvent(event: CalendarEvent)
    suspend fun deleteEvent(id: String)
}

// ─────────────────────────────────────────────
//  Diario
// ─────────────────────────────────────────────

interface JournalRepository {
    fun observeAllEntries(): Flow<List<JournalEntry>>
    suspend fun getEntryById(id: String): JournalEntry?
    suspend fun saveEntry(entry: JournalEntry)
    suspend fun deleteEntry(id: String)
}

// ─────────────────────────────────────────────
//  Estado diario
// ─────────────────────────────────────────────

interface DailyStateRepository {
    fun observeStateForDate(date: LocalDate): Flow<DailyState?>
    fun observeAllStates(): Flow<List<DailyState>>
    fun observeStatesInRange(from: LocalDate, to: LocalDate): Flow<List<DailyState>>
    suspend fun getStateForDate(date: LocalDate): DailyState?
    suspend fun saveState(state: DailyState)
    suspend fun deleteState(id: String)
}
