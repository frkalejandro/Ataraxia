package com.ataraxia

import com.ataraxia.domain.model.*
import com.ataraxia.notifications.agendaDigests
import kotlinx.datetime.*
import kotlin.test.*

class AgendaAndFocusTest {
    private val now = Instant.fromEpochMilliseconds(0)
    private fun event(title: String, day: String) = CalendarEvent(id = title, title = title,
        date = LocalDate.parse(day), createdAt = now, updatedAt = now)
    private fun task(title: String, status: TaskStatus, day: String? = "2026-12-28") = Task(
        id = title, title = title, status = status, dueDate = day?.let(LocalDate::parse), createdAt = now, updatedAt = now)

    @Test fun mondayIncludesBothWeeksAcrossYearBoundary() {
        val digests = agendaDigests(LocalDate.parse("2026-12-28"), listOf(
            event("Hoy", "2026-12-28"), event("Domingo", "2027-01-03"),
            event("Siguiente", "2027-01-04"), event("Último", "2027-01-10"),
            event("Fuera", "2027-01-11")), emptyList())
        assertEquals(3, digests.size)
        assertTrue("Hoy" in digests[0].message)
        assertFalse("Domingo" in digests[0].message)
        assertTrue("Domingo" in digests[1].message)
        assertFalse("Siguiente" in digests[1].message)
        assertTrue("Siguiente" in digests[2].message)
        assertTrue("Último" in digests[2].message)
        assertFalse("Fuera" in digests[2].message)
    }

    @Test fun dailyExcludesFinishedCancelledAndUndatedTasks() {
        val digest = agendaDigests(LocalDate.parse("2026-12-28"), emptyList(), listOf(
            task("Pendiente", TaskStatus.TODO), task("En progreso", TaskStatus.IN_PROGRESS),
            task("Hecha", TaskStatus.DONE), task("Cancelada", TaskStatus.CANCELLED),
            task("Sin fecha", TaskStatus.TODO, null))).first()
        assertTrue("Pendiente" in digest.message)
        assertTrue("En progreso" in digest.message)
        assertFalse("Hecha" in digest.message)
        assertFalse("Cancelada" in digest.message)
        assertFalse("Sin fecha" in digest.message)
    }

    @Test fun ordinaryDayOnlyHasDailyDigest() {
        val digests = agendaDigests(LocalDate.parse("2026-12-29"), emptyList(), emptyList())
        assertEquals(1, digests.size)
        assertTrue("No tienes" in digests.single().message)
    }

    @Test fun largeAgendaIsBounded() {
        val digest = agendaDigests(LocalDate.parse("2026-12-29"),
            (1..20).map { event("Evento $it", "2026-12-29") }, emptyList()).single()
        assertTrue("8 más" in digest.message)
        assertEquals(13, digest.message.lines().size)
    }

    @Test fun pauseAndResumePreserveRemainingTime() {
        val paused = FocusTimer().start(1000).pause(61000)
        assertEquals(24 * 60, paused.remainingSeconds)
        assertNull(paused.deadlineMillis)
        val resumed = paused.start(100000)
        assertEquals(1540000L, resumed.deadlineMillis)
        assertEquals(23 * 60, resumed.tick(160000).remainingSeconds)
    }

    @Test fun returningAfterDeadlineCompletesOnlyOneSession() {
        val finished = FocusTimer().start(0).tick(24 * 60 * 60 * 1000L)
        assertEquals(FocusPhase.SHORT_BREAK, finished.phase)
        assertEquals(1, finished.completedSessions)
        assertNull(finished.deadlineMillis)
        assertTrue(finished.awaitingNext)
        assertEquals(finished, finished.tick(Long.MAX_VALUE))
    }

    @Test fun fourthFocusSessionGetsLongBreak() {
        val finished = FocusTimer(completedSessions = 3).start(0).tick(25 * 60000L)
        assertEquals(FocusPhase.LONG_BREAK, finished.phase)
        assertEquals(15 * 60, finished.remainingSeconds)
        val rested = finished.start(2000000).tick(2900000)
        assertEquals(FocusPhase.FOCUS, rested.phase)
        assertEquals(4, rested.completedSessions)
        assertEquals(25 * 60, rested.remainingSeconds)
    }

    @Test fun resetDoesNotCountAsCompletedAndPresetUpdatesDuration() {
        val reset = FocusTimer().start(0).tick(60000).reset()
        assertEquals(0, reset.completedSessions)
        assertEquals(1500, reset.remainingSeconds)
        assertNull(reset.deadlineMillis)
        assertEquals(3000, reset.setMinutes(50).remainingSeconds)
        assertFailsWith<IllegalArgumentException> { reset.setMinutes(0) }
    }
}
