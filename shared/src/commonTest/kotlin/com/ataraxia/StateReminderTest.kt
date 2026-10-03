package com.ataraxia

import com.ataraxia.notifications.nextStateReminder
import kotlinx.datetime.*
import kotlin.test.*

class StateReminderTest {
    @Test fun schedulesAt21LocalAndAdvancesAfterDelivery() {
        val zone = TimeZone.of("America/Santiago")
        val before = LocalDateTime(2026, 10, 3, 20, 59).toInstant(zone)
        val tonight = LocalDateTime(2026, 10, 3, 21, 0).toInstant(zone)
        assertEquals(tonight, nextStateReminder(before, zone))
        assertEquals(LocalDateTime(2026, 10, 4, 21, 0).toInstant(zone), nextStateReminder(tonight, zone))
    }

    @Test fun followsLocalClockAcrossDaylightSavingAndYearBoundary() {
        val zone = TimeZone.of("America/New_York")
        val beforeDst = LocalDateTime(2026, 3, 7, 22, 0).toInstant(zone)
        assertEquals(LocalDateTime(2026, 3, 8, 21, 0).toInstant(zone), nextStateReminder(beforeDst, zone))
        val endOfYear = LocalDateTime(2026, 12, 31, 23, 0).toInstant(zone)
        assertEquals(LocalDateTime(2027, 1, 1, 21, 0).toInstant(zone), nextStateReminder(endOfYear, zone))
    }
}
