package com.ataraxia

import com.ataraxia.notifications.*
import kotlinx.datetime.*
import kotlin.test.*

class ExerciseReminderTest {
    private val zone = TimeZone.of("America/Santiago")

    @Test fun disabledReminderDoesNotSchedule() {
        assertNull(nextExerciseReminder(ExerciseReminderConfig(), Instant.parse("2026-10-05T12:00:00Z"), zone))
    }

    @Test fun schedulesChosenTimeAndMovesToTomorrowOnceDelivered() {
        val config = ExerciseReminderConfig(true, LocalTime(18, 35))
        val before = LocalDateTime(2026, 10, 5, 18, 34).toInstant(zone)
        val delivery = LocalDateTime(2026, 10, 5, 18, 35).toInstant(zone)
        assertEquals(delivery, nextExerciseReminder(config, before, zone))
        assertEquals(LocalDateTime(2026, 10, 6, 18, 35).toInstant(zone), nextExerciseReminder(config, delivery, zone))
        assertEquals(LocalDateTime(2026, 10, 5, 19, 0).toInstant(zone),
            nextExerciseReminder(config.copy(time = LocalTime(19, 0)), delivery, zone))
        assertNull(nextExerciseReminder(config.copy(enabled = false), delivery, zone))
    }

    @Test fun supportsMidnightAndPreservesWallClockAcrossDst() {
        assertEquals(LocalDateTime(2027, 1, 1, 0, 0).toInstant(zone),
            nextExerciseReminder(ExerciseReminderConfig(true, LocalTime(0, 0)),
                LocalDateTime(2026, 12, 31, 23, 59).toInstant(zone), zone))
        val dstZone = TimeZone.of("America/New_York")
        assertEquals(LocalDateTime(2026, 3, 8, 18, 0).toInstant(dstZone),
            nextExerciseReminder(ExerciseReminderConfig(true, LocalTime(18, 0)),
                LocalDateTime(2026, 3, 7, 20, 0).toInstant(dstZone), dstZone))
    }
}
