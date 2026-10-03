package com.ataraxia.notifications

import com.ataraxia.domain.model.CalendarEvent
import com.ataraxia.domain.model.Task
import com.ataraxia.domain.model.TaskStatus
import kotlinx.datetime.*

data class AgendaDigest(val id: Int, val title: String, val message: String)

/** Calendar weeks run Monday through Sunday, including month/year boundaries. */
fun agendaDigests(today: LocalDate, events: List<CalendarEvent>, tasks: List<Task>): List<AgendaDigest> {
    fun summary(id: Int, title: String, from: LocalDate, to: LocalDate): AgendaDigest {
        val entries = events.filter { it.date in from..to }
            .map { Triple(it.date, if (it.isAllDay) null else it.startTime,
                "Evento: ${it.title}" + if (it.isAllDay) " (todo el día)" else "") } +
            tasks.filter { it.dueDate != null && it.dueDate in from..to &&
                it.status != TaskStatus.DONE && it.status != TaskStatus.CANCELLED }
                .map { Triple(it.dueDate!!, it.dueTime, "Tarea: ${it.title}") }
        val sorted = entries.sortedWith(compareBy({ it.first }, { it.second ?: LocalTime(0, 0) }, { it.third }))
        val lines = sorted.take(12).map { (date, time, label) ->
            val day = if (from == to) "" else "${date.dayOfMonth}/${date.monthNumber} · "
            "$day${time?.let { "$it · " } ?: ""}${label.take(160)}"
        }
        val message = if (lines.isEmpty()) "No tienes eventos ni tareas pendientes para este período."
            else (lines + if (sorted.size > 12) listOf("Y ${sorted.size - 12} más. Revisa tu agenda.") else emptyList()).joinToString("\n")
        return AgendaDigest(id, title, message)
    }
    return buildList {
        add(summary(13001, "Tu agenda de hoy", today, today))
        if (today.dayOfWeek == DayOfWeek.MONDAY) {
            add(summary(13002, "Esta semana en Ataraxia", today, today.plus(6, DateTimeUnit.DAY)))
            add(summary(13003, "La próxima semana en Ataraxia", today.plus(7, DateTimeUnit.DAY), today.plus(13, DateTimeUnit.DAY)))
        }
    }
}
