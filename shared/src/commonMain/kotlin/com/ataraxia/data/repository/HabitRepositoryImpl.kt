package com.ataraxia.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.*
import com.ataraxia.domain.repository.HabitRepository
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.datetime.*

class HabitRepositoryImpl(
    private val db: AtaraxiaDatabase,
) : HabitRepository {

    private val queries get() = db.habitQueries

    override fun observeHabits(): Flow<List<Habit>> =
        queries.selectAllHabits()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeAllHabitsWithStatus(today: LocalDate): Flow<List<HabitWithStatus>> =
        observeHabits().flatMapLatest { habits ->
            if (habits.isEmpty()) return@flatMapLatest flowOf(emptyList())
            combine(habits.map { habit ->
                observeHabitWithStatus(habit.id, today)
            }) { it.toList() }
        }

    override fun observeHabitWithStatus(habitId: String, today: LocalDate): Flow<HabitWithStatus> =
        observeHabits()
            .map { habits -> habits.first { it.id == habitId } }
            .combine(
                queries.selectEntryForHabitAndDate(habitId, today.toString())
                    .asFlow()
                    .mapToOneOrNull(Dispatchers.Default)
            ) { habit, entry ->
                val streak = getStreak(habit.id, today)
                val rate   = getCompletionRate(habit.id, today.minus(6, DateTimeUnit.DAY), today)
                HabitWithStatus(
                    habit            = habit,
                    todayEntry       = entry?.toDomain(),
                    currentStreak    = streak,
                    completionRate7d = rate,
                )
            }

    override suspend fun getHabitById(id: String): Habit? =
        withContext(Dispatchers.Default) {
            queries.selectHabitById(id).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun saveHabit(habit: Habit) = withContext(Dispatchers.Default) {
        queries.insertHabit(
            id          = habit.id,
            title       = habit.title,
            description = habit.description,
            icon        = habit.icon,
            color       = habit.color,
            frequency   = habit.frequency.name,
            targetDays  = habit.targetDays.map { it.isoDayNumber }.toString(),
            targetCount = habit.targetCount.toLong(),
            isArchived  = if (habit.isArchived) 1L else 0L,
            order_index = habit.orderIndex.toLong(),
            category    = habit.category.name,
            createdAt   = habit.createdAt.toEpochMilliseconds(),
            updatedAt   = habit.updatedAt.toEpochMilliseconds(),
        )
    }

    override suspend fun archiveHabit(id: String) = withContext(Dispatchers.Default) {
        queries.archiveHabit(updatedAt = Clock.System.now().toEpochMilliseconds(), id = id)
    }

    override suspend fun deleteHabit(id: String) = withContext(Dispatchers.Default) {
        queries.deleteHabit(id)
    }

    override fun observeEntriesForDate(date: LocalDate): Flow<List<HabitEntry>> =
        queries.selectEntriesForDateRange(date.toString(), date.toString())
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map {
                    HabitEntry(
                        id        = it.id,
                        habitId   = it.habitId,
                        date      = LocalDate.parse(it.date),
                        count     = it.count.toInt(),
                        note      = it.note,
                        createdAt = Instant.fromEpochMilliseconds(it.createdAt),
                    )
                }
            }

    override suspend fun toggleHabitEntry(habitId: String, date: LocalDate) =
        withContext(Dispatchers.Default) {
            val existing = queries.selectEntryForHabitAndDate(habitId, date.toString())
                .executeAsOneOrNull()
            if (existing != null) {
                queries.deleteHabitEntry(habitId, date.toString())
            } else {
                queries.upsertHabitEntry(
                    id        = uuid4().toString(),
                    habitId   = habitId,
                    date      = date.toString(),
                    count     = 1L,
                    note      = "",
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                )
            }
        }

    override suspend fun setHabitEntryCount(
        habitId: String, date: LocalDate, count: Int, note: String
    ) = withContext(Dispatchers.Default) {
        queries.upsertHabitEntry(
            id        = uuid4().toString(),
            habitId   = habitId,
            date      = date.toString(),
            count     = count.toLong(),
            note      = note,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
    }

    override suspend fun getStreak(habitId: String, today: LocalDate): Int =
        withContext(Dispatchers.Default) {
            val dates = queries.selectDatesWithEntry(habitId).executeAsList()
                .map { LocalDate.parse(it) }
                .sortedDescending()
            var streak  = 0
            var current = today
            for (d in dates) {
                if (d == current) {
                    streak++
                    current = current.minus(1, DateTimeUnit.DAY)
                } else if (d < current) break
            }
            streak
        }

    override suspend fun getCompletionRate(
        habitId: String, from: LocalDate, to: LocalDate
    ): Float = withContext(Dispatchers.Default) {
        val total = from.daysUntil(to) + 1
        val done  = queries.countCompletedDaysInRange(habitId, from.toString(), to.toString())
            .executeAsOne()
        if (total == 0) 0f else done.toFloat() / total
    }

    private fun com.ataraxia.db.Habit.toDomain() = Habit(
        id          = id,
        title       = title,
        description = description,
        icon        = icon,
        color       = color,
        frequency   = HabitFrequency.valueOf(frequency),
        targetDays  = parseTargetDays(targetDays),
        targetCount = targetCount.toInt(),
        isArchived  = isArchived != 0L,
        orderIndex  = order_index.toInt(),
        category    = HabitCategory.valueOf(category),
        createdAt   = Instant.fromEpochMilliseconds(createdAt),
        updatedAt   = Instant.fromEpochMilliseconds(updatedAt),
    )
    private fun com.ataraxia.db.HabitEntry.toDomain() = HabitEntry(
        id        = id,
        habitId   = habitId,
        date      = LocalDate.parse(date),
        count     = count.toInt(),
        note      = note,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
    )

    private fun parseTargetDays(json: String): List<DayOfWeek> =
        Regex("\\d").findAll(json).map { DayOfWeek(it.value.toInt()) }.toList()
            .ifEmpty { DayOfWeek.entries }
}
