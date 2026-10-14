package com.ataraxia

import com.ataraxia.domain.model.*
import kotlinx.datetime.*
import kotlin.test.*

class AgendaVisibilityTest {
    private val now = LocalDateTime(2026, 10, 13, 15, 30)
    private val event = CalendarEvent("event", "Leer", date = now.date, startTime = LocalTime(14, 0),
        endTime = LocalTime(15, 30), createdAt = Instant.fromEpochMilliseconds(0), updatedAt = Instant.fromEpochMilliseconds(0))

    @Test fun hidesEventsExactlyAtEndAndOnPastDays() {
        assertFalse(event.isUpcoming(now))
        assertTrue(event.copy(endTime = LocalTime(15, 31)).isUpcoming(now))
        assertFalse(event.copy(date = LocalDate(2026, 10, 12), isAllDay = true).isUpcoming(now))
        assertTrue(event.copy(date = LocalDate(2026, 10, 14)).isUpcoming(now))
    }

    @Test fun allDayAndUnknownEndRemainUntilDayEnds() {
        assertTrue(event.copy(isAllDay = true).isUpcoming(now))
        assertTrue(event.copy(endTime = null).isUpcoming(now))
        assertFalse(event.copy(endTime = null).isUpcoming(LocalDateTime(2026, 10, 14, 0, 0)))
    }

    @Test fun weeklyOccurrencesAreFilteredIndependently() {
        val series = event.copy(recurrence = Recurrence.WEEKLY)
        assertFalse(series.isUpcoming(now))
        assertTrue(series.copy(date = LocalDate(2026, 10, 20)).isUpcoming(now))
    }
}
