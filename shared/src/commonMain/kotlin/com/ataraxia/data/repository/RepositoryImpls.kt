package com.ataraxia.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.*
import com.ataraxia.domain.repository.*
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.datetime.*

// ─────────────────────────────────────────────
//  SLEEP
// ─────────────────────────────────────────────

class SleepRepositoryImpl(private val db: AtaraxiaDatabase) : SleepRepository {

    private val q get() = db.sleepQueries

    override fun observeSleepGoal(): Flow<SleepGoal?> =
        q.selectSleepGoal().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }

    override suspend fun saveSleepGoal(goal: SleepGoal) = withContext(Dispatchers.Default) {
        q.upsertSleepGoal(
            id            = goal.id,
            bedtimeHour   = goal.bedtime.hour.toLong(),
            bedtimeMinute = goal.bedtime.minute.toLong(),
            wakeHour      = goal.wakeTime.hour.toLong(),
            wakeMinute    = goal.wakeTime.minute.toLong(),
            targetHours   = goal.targetHours.toDouble(),
            activeDays    = goal.activeDays.map { it.isoDayNumber }.toString(),
            isActive      = if (goal.isActive) 1L else 0L,
            updatedAt     = goal.updatedAt.toEpochMilliseconds(),
        )
    }

    override fun observeSleepEntries(limit: Int): Flow<List<SleepEntry>> =
        q.selectSleepEntries(limit.toLong()).asFlow()
            .mapToList(Dispatchers.Default)
            .map { it.map { r -> r.toDomain() } }

    override fun observeSleepEntriesInRange(from: LocalDate, to: LocalDate): Flow<List<SleepEntry>> =
        q.selectSleepEntriesInRange(from.toString(), to.toString())
            .asFlow().mapToList(Dispatchers.Default)
            .map { it.map { r -> r.toDomain() } }

    override suspend fun getSleepEntryByDate(date: LocalDate): SleepEntry? =
        withContext(Dispatchers.Default) {
            q.selectSleepEntryByDate(date.toString()).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun saveSleepEntry(entry: SleepEntry) = withContext(Dispatchers.Default) {
        q.upsertSleepEntry(
            id              = entry.id,
            date            = entry.date.toString(),
            bedtimeEpoch    = entry.bedtime.toEpochMilliseconds(),
            wakeEpoch       = entry.wakeTime.toEpochMilliseconds(),
            durationMinutes = entry.durationMinutes.toLong(),
            quality         = entry.quality.toLong(),
            note            = entry.note,
            createdAt       = entry.createdAt.toEpochMilliseconds(),
        )
    }

    override suspend fun deleteSleepEntry(id: String) = withContext(Dispatchers.Default) {
        q.deleteSleepEntry(id)
    }

    override suspend fun getSleepStats(lastDays: Int): SleepStats {
        val to   = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val from = to.minus(lastDays - 1, DateTimeUnit.DAY)
        val entries = withContext(Dispatchers.Default) {
            q.selectSleepEntriesInRange(from.toString(), to.toString())
                .executeAsList().map { it.toDomain() }
        }
        val avgDuration = if (entries.isEmpty()) 0f
            else entries.sumOf { it.durationMinutes }.toFloat() / entries.size
        val avgQuality = if (entries.isEmpty()) 0f
            else entries.sumOf { it.quality }.toFloat() / entries.size

        var streak = 0; var maxStreak = 0; var prev: LocalDate? = null
        for (e in entries.sortedBy { it.date }) {
            if (prev == null || e.date == prev!!.plus(1, DateTimeUnit.DAY)) {
                streak++; maxStreak = maxOf(maxStreak, streak)
            } else { streak = 1 }
            prev = e.date
        }
        return SleepStats(avgDuration, avgQuality, maxStreak, entries.takeLast(7))
    }

    private fun com.ataraxia.db.SleepGoal.toDomain() = SleepGoal(
        id          = id,
        bedtime     = LocalTime(bedtimeHour.toInt(), bedtimeMinute.toInt()),
        wakeTime    = LocalTime(wakeHour.toInt(), wakeMinute.toInt()),
        targetHours = targetHours.toFloat(),
        activeDays  = parseDays(activeDays),
        isActive    = isActive != 0L,
        updatedAt   = Instant.fromEpochMilliseconds(updatedAt),
    )

    private fun com.ataraxia.db.SleepEntry.toDomain() = SleepEntry(
        id              = id,
        date            = LocalDate.parse(date),
        bedtime         = Instant.fromEpochMilliseconds(bedtimeEpoch),
        wakeTime        = Instant.fromEpochMilliseconds(wakeEpoch),
        durationMinutes = durationMinutes.toInt(),
        quality         = quality.toInt(),
        note            = note,
        createdAt       = Instant.fromEpochMilliseconds(createdAt),
    )

    private fun parseDays(json: String): List<DayOfWeek> =
        Regex("\\d").findAll(json).map { DayOfWeek(it.value.toInt()) }.toList()
            .ifEmpty { DayOfWeek.entries }
}

// ─────────────────────────────────────────────
//  TASKS
// ─────────────────────────────────────────────

class TaskRepositoryImpl(private val db: AtaraxiaDatabase) : TaskRepository {

    private val q get() = db.agendaQueries

    override fun observeActiveTasks(): Flow<List<Task>> =
        q.selectAllActiveTasks().asFlow().mapToList(Dispatchers.Default)
            .map { it.map { r -> r.toTask() } }

    override fun observeTasksByDate(date: LocalDate): Flow<List<Task>> =
        q.selectTasksByDate(date.toString()).asFlow().mapToList(Dispatchers.Default)
            .map { it.map { r -> r.toTask() } }
    override fun observeTasksByDateRange(
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Task>> =
        q.selectTasksByDateRange(
            from = from.toString(),
            to = to.toString(),
        )
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { it.toTask() }
            }

    override fun observeTasksByProject(projectId: String): Flow<List<Task>> =
        q.selectTasksByProject(projectId).asFlow().mapToList(Dispatchers.Default)
            .map { it.map { r -> r.toTask() } }

    override suspend fun getTaskById(id: String): Task? =
        withContext(Dispatchers.Default) {
            q.selectTaskById(id).executeAsOneOrNull()?.toTask()
        }

    override suspend fun saveTask(task: Task) = withContext(Dispatchers.Default) {
        q.insertTask(
            id          = task.id,
            title       = task.title,
            description = task.description,
            dueDate     = task.dueDate?.toString(),
            dueTime     = task.dueTime?.toString(),
            priority    = task.priority.name,
            energyLevel = task.energyLevel.name,
            status      = task.status.name,
            projectId   = task.projectId,
            tags        = task.tags.toString(),
            order_index = task.orderIndex.toLong(),
            completedAt = task.completedAt?.toEpochMilliseconds(),
            createdAt   = task.createdAt.toEpochMilliseconds(),
            updatedAt   = task.updatedAt.toEpochMilliseconds(),
        )
    }

    override suspend fun updateTaskStatus(id: String, status: TaskStatus) =
        withContext(Dispatchers.Default) {
            val now = Clock.System.now()
            q.updateTaskStatus(
                status      = status.name,
                completedAt = if (status == TaskStatus.DONE) now.toEpochMilliseconds() else null,
                updatedAt   = now.toEpochMilliseconds(),
                id          = id,
            )
        }

    override suspend fun deleteTask(id: String) = withContext(Dispatchers.Default) {
        q.deleteTask(id)
    }

    override fun observeProjects(): Flow<List<Project>> =
        q.selectAllProjects().asFlow().mapToList(Dispatchers.Default)
            .map {
                it.map { r ->
                    Project(
                        id         = r.id,
                        name       = r.name,
                        color      = r.color,
                        icon       = r.icon,
                        isArchived = r.isArchived != 0L,
                        createdAt  = Instant.fromEpochMilliseconds(r.createdAt),
                    )
                }
            }

    override suspend fun saveProject(project: Project) = withContext(Dispatchers.Default) {
        q.insertProject(
            id         = project.id,
            name       = project.name,
            color      = project.color,
            icon       = project.icon,
            isArchived = if (project.isArchived) 1L else 0L,
            createdAt  = project.createdAt.toEpochMilliseconds(),
        )
    }

    override suspend fun deleteProject(id: String) = withContext(Dispatchers.Default) {
        q.deleteProject(id)
    }

    private fun com.ataraxia.db.Task.toTask() = Task(
        id          = id,
        title       = title,
        description = description,
        dueDate     = dueDate?.let { LocalDate.parse(it) },
        dueTime     = dueTime?.let { LocalTime.parse(it) },
        priority    = TaskPriority.valueOf(priority),
        energyLevel = EnergyLevel.valueOf(energyLevel),
        status      = TaskStatus.valueOf(status),
        projectId   = projectId,
        tags        = Regex("\"([^\"]+)\"").findAll(tags).map { it.groupValues[1] }.toList(),
        orderIndex  = order_index.toInt(),
        completedAt = completedAt?.let { Instant.fromEpochMilliseconds(it) },
        createdAt   = Instant.fromEpochMilliseconds(createdAt),
        updatedAt   = Instant.fromEpochMilliseconds(updatedAt),
    )
}

// ─────────────────────────────────────────────
//  CALENDAR EVENTS
// ─────────────────────────────────────────────

class CalendarRepositoryImpl(private val db: AtaraxiaDatabase) : CalendarRepository {

    private val q get() = db.agendaQueries

    override fun observeEventsForDate(date: LocalDate): Flow<List<CalendarEvent>> =
        q.selectEventsForDate(date.toString()).asFlow().mapToList(Dispatchers.Default)
            .map { it.map { r -> r.toDomain() } }

    override fun observeEventsForRange(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> =
        q.selectEventsForDateRange(from.toString(), to.toString())
            .asFlow().mapToList(Dispatchers.Default)
            .map { it.map { r -> r.toDomain() } }

    override suspend fun getEventById(id: String): CalendarEvent? =
        withContext(Dispatchers.Default) {
            q.selectEventById(id).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun saveEvent(event: CalendarEvent) = withContext(Dispatchers.Default) {
        q.insertEvent(
            id              = event.id,
            title           = event.title,
            description     = event.description,
            date            = event.date.toString(),
            startTime       = event.startTime?.toString(),
            endTime         = event.endTime?.toString(),
            isAllDay        = if (event.isAllDay) 1L else 0L,
            color           = event.color,
            recurrence      = event.recurrence.name,
            recurrenceEnd   = event.recurrenceEnd?.toString(),
            reminderMinutes = event.reminderMinutes?.toLong(),
            linkedTaskId    = event.linkedTaskId,
            createdAt       = event.createdAt.toEpochMilliseconds(),
            updatedAt       = event.updatedAt.toEpochMilliseconds(),
        )
    }

    override suspend fun deleteEvent(id: String) = withContext(Dispatchers.Default) {
        q.deleteEvent(id)
    }

    private fun com.ataraxia.db.CalendarEvent.toDomain() = CalendarEvent(
        id              = id,
        title           = title,
        description     = description,
        date            = LocalDate.parse(date),
        startTime       = startTime?.let { LocalTime.parse(it) },
        endTime         = endTime?.let { LocalTime.parse(it) },
        isAllDay        = isAllDay != 0L,
        color           = color,
        recurrence      = Recurrence.valueOf(recurrence),
        recurrenceEnd   = recurrenceEnd?.let { LocalDate.parse(it) },
        reminderMinutes = reminderMinutes?.toInt(),
        linkedTaskId    = linkedTaskId,
        createdAt       = Instant.fromEpochMilliseconds(createdAt),
        updatedAt       = Instant.fromEpochMilliseconds(updatedAt),
    )
}
