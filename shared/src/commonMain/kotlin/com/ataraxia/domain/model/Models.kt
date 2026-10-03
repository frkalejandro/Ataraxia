package com.ataraxia.domain.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// ─────────────────────────────────────────────
//  HÁBITOS
// ─────────────────────────────────────────────

enum class HabitFrequency { DAILY, WEEKLY, CUSTOM }
enum class HabitCategory  { GENERAL, MORNING, NIGHT }

data class Habit(
    val id: String,
    val title: String,
    val description: String = "",
    val icon: String = "star",
    val color: String = "#6C63FF",
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val targetDays: List<DayOfWeek> = DayOfWeek.entries,
    val targetCount: Int = 1,
    val isArchived: Boolean = false,
    val orderIndex: Int = 0,
    val category: HabitCategory = HabitCategory.GENERAL,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class HabitEntry(
    val id: String,
    val habitId: String,
    val date: LocalDate,
    val count: Int = 1,
    val note: String = "",
    val createdAt: Instant,
)

/** Habit enriched with today's completion state and current streak. */
data class HabitWithStatus(
    val habit: Habit,
    val todayEntry: HabitEntry?,
    val currentStreak: Int,
    val completionRate7d: Float,   // 0.0–1.0
)

// ─────────────────────────────────────────────
//  SUEÑO
// ─────────────────────────────────────────────

data class SleepGoal(
    val id: String,
    val bedtime: LocalTime,
    val wakeTime: LocalTime,
    val targetHours: Float = 8f,
    val activeDays: List<DayOfWeek> = DayOfWeek.entries,
    val isActive: Boolean = true,
    val updatedAt: Instant,
)

data class SleepEntry(
    val id: String,
    val date: LocalDate,              // date of wakeup
    val bedtime: Instant,
    val wakeTime: Instant,
    val durationMinutes: Int,
    val quality: Int,                 // 1–5
    val note: String = "",
    val createdAt: Instant,
) {
    val durationHours: Float get() = durationMinutes / 60f
}

data class SleepStats(
    val avgDurationMinutes: Float,
    val avgQuality: Float,
    val longestStreak: Int,           // consecutive days with entry
    val last7Days: List<SleepEntry>,
)

// ─────────────────────────────────────────────
//  AGENDA
// ─────────────────────────────────────────────

enum class TaskPriority { LOW, MEDIUM, HIGH, CRITICAL }
enum class EnergyLevel  { LOW, MEDIUM, HIGH }
enum class TaskStatus   { TODO, IN_PROGRESS, DONE, CANCELLED }

data class Project(
    val id: String,
    val name: String,
    val color: String = "#6C63FF",
    val icon: String = "folder",
    val isArchived: Boolean = false,
    val createdAt: Instant,
)

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val energyLevel: EnergyLevel = EnergyLevel.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val projectId: String? = null,
    val tags: List<String> = emptyList(),
    val orderIndex: Int = 0,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isCompleted: Boolean get() = status == TaskStatus.DONE
    val isOverdue: Boolean get() = dueDate != null && status == TaskStatus.TODO
        // caller should compare with today
}

// ─────────────────────────────────────────────
//  CALENDARIO
// ─────────────────────────────────────────────

enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String = "",
    val date: LocalDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val isAllDay: Boolean = false,
    val color: String = "#6C63FF",
    val recurrence: Recurrence = Recurrence.NONE,
    val recurrenceEnd: LocalDate? = null,
    val reminderMinutes: Int? = null,
    val linkedTaskId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

// ─────────────────────────────────────────────
//  ATARAXIA SCORE
// ─────────────────────────────────────────────

/**
 * Daily control score (0–100).
 * Weighted composite:
 *   habits  40 %
 *   sleep   35 %
 *   agenda  25 %
 */
data class AtaraxiaScore(
    val date: LocalDate,
    val total: Int,           // 0–100
    val habitsScore: Int,     // 0–100
    val sleepScore: Int,      // 0–100
    val agendaScore: Int,     // 0–100
    val breakdown: ScoreBreakdown,
)

data class ScoreBreakdown(
    val habitsCompleted: Int,
    val habitsTotal: Int,
    val sleepDurationOk: Boolean,
    val sleepQuality: Int,    // 1–5
    val tasksCompleted: Int,
    val tasksTotal: Int,
)

// ─────────────────────────────────────────────
//  DIARIO
// ─────────────────────────────────────────────

data class JournalEntry(
    val id: String,
    val content: String,
    val mood: String?,
    val tags: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

// ─────────────────────────────────────────────
//  ESTADO DIARIO
// ─────────────────────────────────────────────

enum class MorningErection { YES, NO, NOT_ANSWERED }

data class DailyState(
    val id: String,
    val date: LocalDate,
    val energy: Int,
    val mood: Int,
    val stress: Int,
    val trained: Boolean,
    val breakfast: String = "",
    val lunch: String = "",
    val tea: String = "",
    val dinner: String = "",
    val otherFood: String = "",
    val waterGlasses: Int = 0,
    val musclePain: Int,
    val fatigue: Int,
    val libido: Int? = null,
    val morningErection: MorningErection = MorningErection.NOT_ANSWERED,
    val deepStudyMinutes: Int = 0,
    val concentration: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val waterLiters: Float get() = waterGlasses * 0.25f
    val deepStudyHours: Float get() = deepStudyMinutes / 60f
}
