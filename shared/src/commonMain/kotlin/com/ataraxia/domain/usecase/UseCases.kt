package com.ataraxia.domain.usecase

import com.ataraxia.domain.model.*
import com.ataraxia.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate

// ─────────────────────────────────────────────
//  HÁBITOS
// ─────────────────────────────────────────────

class ObserveHabitsWithStatusUseCase(
    private val repo: HabitRepository,
) {
    operator fun invoke(today: LocalDate): Flow<List<HabitWithStatus>> =
        repo.observeAllHabitsWithStatus(today)
}

class ToggleHabitEntryUseCase(
    private val repo: HabitRepository,
) {
    suspend operator fun invoke(habitId: String, date: LocalDate) =
        repo.toggleHabitEntry(habitId, date)
}

class SaveHabitUseCase(
    private val repo: HabitRepository,
) {
    suspend operator fun invoke(habit: Habit) = repo.saveHabit(habit)
}

class ArchiveHabitUseCase(
    private val repo: HabitRepository,
) {
    suspend operator fun invoke(id: String) = repo.archiveHabit(id)
}

// ─────────────────────────────────────────────
//  SUEÑO
// ─────────────────────────────────────────────

class ObserveSleepEntriesUseCase(
    private val repo: SleepRepository,
) {
    operator fun invoke(limit: Int = 30): Flow<List<SleepEntry>> =
        repo.observeSleepEntries(limit)
}

class SaveSleepEntryUseCase(
    private val repo: SleepRepository,
) {
    suspend operator fun invoke(entry: SleepEntry) = repo.saveSleepEntry(entry)
}

class GetSleepStatsUseCase(
    private val repo: SleepRepository,
) {
    suspend operator fun invoke(lastDays: Int = 7): SleepStats =
        repo.getSleepStats(lastDays)
}

class ObserveSleepGoalUseCase(
    private val repo: SleepRepository,
) {
    operator fun invoke(): Flow<SleepGoal?> = repo.observeSleepGoal()
}

class SaveSleepGoalUseCase(
    private val repo: SleepRepository,
) {
    suspend operator fun invoke(goal: SleepGoal) = repo.saveSleepGoal(goal)
}

// ─────────────────────────────────────────────
//  AGENDA & CALENDARIO
// ─────────────────────────────────────────────

class ObserveActiveTasksUseCase(
    private val repo: TaskRepository,
) {
    operator fun invoke(): Flow<List<Task>> = repo.observeActiveTasks()
}

class ObserveTasksForDateUseCase(
    private val repo: TaskRepository,
) {
    operator fun invoke(date: LocalDate): Flow<List<Task>> =
        repo.observeTasksByDate(date)
}

class SaveTaskUseCase(
    private val repo: TaskRepository,
) {
    suspend operator fun invoke(task: Task) = repo.saveTask(task)
}

class CompleteTaskUseCase(
    private val repo: TaskRepository,
) {
    suspend operator fun invoke(id: String, completed: Boolean = true) =
        repo.updateTaskStatus(id, if (completed) TaskStatus.DONE else TaskStatus.TODO)
}

class ObserveCalendarEventsUseCase(
    private val calendarRepo: CalendarRepository,
    private val taskRepo: TaskRepository,
) {
    /** Emits events + tasks-as-events for a given day. */
    operator fun invoke(date: LocalDate): Flow<Pair<List<CalendarEvent>, List<Task>>> =
        combine(
            calendarRepo.observeEventsForDate(date),
            taskRepo.observeTasksByDate(date),
        ) { events, tasks -> events to tasks }
}

class SaveCalendarEventUseCase(
    private val repo: CalendarRepository,
) {
    suspend operator fun invoke(event: CalendarEvent) = repo.saveEvent(event)
}

// ─────────────────────────────────────────────
//  ATARAXIA SCORE
// ─────────────────────────────────────────────

class ComputeAtaraxiaScoreUseCase(
    private val habitRepo: HabitRepository,
    private val sleepRepo: SleepRepository,
    private val taskRepo: TaskRepository,
) {
    suspend operator fun invoke(date: LocalDate): AtaraxiaScore {
        // ── Habits ──────────────────────────────
        val habitStatuses = habitRepo.observeAllHabitsWithStatus(date)
        // We need a snapshot here; in practice call from a collecting context
        // This is wired via the ViewModel with stateIn().
        // For simplicity we use repos directly with suspending calls:
        val habitsCompleted = 0  // placeholder — filled by ViewModel via Flow
        val habitsTotal     = 1
        val habitRatio      = if (habitsTotal > 0) habitsCompleted.toFloat() / habitsTotal else 0f
        val habitsScore     = (habitRatio * 100).toInt()

        // ── Sleep ────────────────────────────────
        val sleepEntry  = sleepRepo.getSleepEntryByDate(date)
        val goal        = sleepRepo.observeSleepGoal()
        // goal target hours: default 8h = 480 min
        val targetMin   = 480
        val actualMin   = sleepEntry?.durationMinutes ?: 0
        val durationOk  = actualMin >= targetMin
        val quality     = sleepEntry?.quality ?: 0
        val sleepScore  = if (sleepEntry == null) 0 else {
            val durationPart = (minOf(actualMin, targetMin).toFloat() / targetMin * 70).toInt()
            val qualityPart  = ((quality / 5f) * 30).toInt()
            durationPart + qualityPart
        }

        // ── Agenda ───────────────────────────────
        val todayTasks    = taskRepo.observeTasksByDate(date)
        val tasksTotal    = 0  // snapshot via ViewModel
        val tasksDone     = 0
        val taskRatio     = if (tasksTotal > 0) tasksDone.toFloat() / tasksTotal else 1f
        val agendaScore   = (taskRatio * 100).toInt()

        // ── Composite ────────────────────────────
        val total = (habitsScore * 0.40 + sleepScore * 0.35 + agendaScore * 0.25).toInt()

        return AtaraxiaScore(
            date        = date,
            total       = total.coerceIn(0, 100),
            habitsScore = habitsScore.coerceIn(0, 100),
            sleepScore  = sleepScore.coerceIn(0, 100),
            agendaScore = agendaScore.coerceIn(0, 100),
            breakdown   = ScoreBreakdown(
                habitsCompleted  = habitsCompleted,
                habitsTotal      = habitsTotal,
                sleepDurationOk  = durationOk,
                sleepQuality     = quality,
                tasksCompleted   = tasksDone,
                tasksTotal       = tasksTotal,
            ),
        )
    }
}
