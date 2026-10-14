package com.ataraxia.domain.model

import kotlinx.datetime.*

/** Expand only the requested window; a null end date has no time limit. */
fun CalendarEvent.occurrences(from: LocalDate, to: LocalDate): List<CalendarEvent> {
    if (from > to) return emptyList()
    if (recurrence != Recurrence.WEEKLY) return if (date in from..to) listOf(this) else emptyList()
    val end = recurrenceEnd?.let { minOf(it, to) } ?: to
    val lower = maxOf(date, from)
    val elapsed = date.daysUntil(lower)
    var occurrence = date.plus(((elapsed + 6) / 7) * 7, DateTimeUnit.DAY)
    return buildList {
        while (occurrence <= end) {
            add(copy(date = occurrence))
            if (occurrence.daysUntil(end) < 7) break
            occurrence = occurrence.plus(7, DateTimeUnit.DAY)
        }
    }
}
