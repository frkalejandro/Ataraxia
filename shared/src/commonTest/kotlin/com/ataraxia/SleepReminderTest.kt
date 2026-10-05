package com.ataraxia

import com.ataraxia.notifications.SleepReminder
import com.ataraxia.notifications.nextSleepReminder
import kotlinx.datetime.*
import kotlin.test.*

class SleepReminderTest {
    @Test fun calculatesMealAndWaterLimitsFromTheConfiguredBedtime() {
        val bedtime = LocalTime(23, 0)
        assertEquals(LocalTime(20, 30), SleepReminder.LAST_MEAL.timeBefore(bedtime))
        assertEquals(LocalTime(21, 0), SleepReminder.LAST_WATER.timeBefore(bedtime))
        val editedBedtime = LocalTime(22, 15)
        assertEquals(LocalTime(19, 45), SleepReminder.LAST_MEAL.timeBefore(editedBedtime))
        assertEquals(LocalTime(20, 15), SleepReminder.LAST_WATER.timeBefore(editedBedtime))
    }

    @Test fun wrapsToThePreviousDayAndHandlesMidnightExactly() {
        assertEquals(LocalTime(22, 45), SleepReminder.LAST_MEAL.timeBefore(LocalTime(1, 15)))
        assertEquals(LocalTime(23, 15), SleepReminder.LAST_WATER.timeBefore(LocalTime(1, 15)))
        assertEquals(LocalTime(0, 0), SleepReminder.LAST_MEAL.timeBefore(LocalTime(2, 30)))
        assertEquals(LocalTime(0, 0), SleepReminder.LAST_WATER.timeBefore(LocalTime(2, 0)))
    }

    @Test fun schedulesTonightForABedtimeAfterMidnightAndAdvancesAfterDelivery() {
        val zone = TimeZone.of("America/Santiago")
        val before = LocalDateTime(2026, 10, 5, 22, 0).toInstant(zone)
        val meal = LocalDateTime(2026, 10, 5, 22, 45).toInstant(zone)
        val water = LocalDateTime(2026, 10, 5, 23, 15).toInstant(zone)
        val bedtime = LocalTime(1, 15)
        assertEquals(meal, nextSleepReminder(SleepReminder.LAST_MEAL, bedtime, before, zone))
        assertEquals(water, nextSleepReminder(SleepReminder.LAST_WATER, bedtime, meal, zone))
        assertEquals(
            LocalDateTime(2026, 10, 6, 22, 45).toInstant(zone),
            nextSleepReminder(SleepReminder.LAST_MEAL, bedtime, meal, zone),
        )
    }

    @Test fun preservesLocalHourAcrossDaylightSavingAndYearBoundary() {
        val zone = TimeZone.of("America/New_York")
        val bedtime = LocalTime(23, 0)
        assertEquals(
            LocalDateTime(2026, 3, 8, 20, 30).toInstant(zone),
            nextSleepReminder(SleepReminder.LAST_MEAL, bedtime,
                LocalDateTime(2026, 3, 7, 22, 0).toInstant(zone), zone),
        )
        assertEquals(
            LocalDateTime(2027, 1, 1, 21, 0).toInstant(zone),
            nextSleepReminder(SleepReminder.LAST_WATER, bedtime,
                LocalDateTime(2026, 12, 31, 22, 0).toInstant(zone), zone),
        )
    }

    @Test fun retainsExistingBedtimeRemindersAndGivesEachAlarmADistinctOffset() {
        val bedtime = LocalTime(1, 0)
        assertEquals(LocalTime(0, 0), SleepReminder.ONE_HOUR.timeBefore(bedtime))
        assertEquals(LocalTime(0, 30), SleepReminder.THIRTY_MINUTES.timeBefore(bedtime))
        assertEquals(LocalTime(0, 50), SleepReminder.TEN_MINUTES.timeBefore(bedtime))
        assertEquals(5, SleepReminder.entries.map { it.offsetMinutes }.toSet().size)
        assertTrue(SleepReminder.LAST_MEAL.message(bedtime).contains("2 h 30 min"))
        assertTrue(SleepReminder.LAST_WATER.message(bedtime).contains("01:00"))
    }
}
