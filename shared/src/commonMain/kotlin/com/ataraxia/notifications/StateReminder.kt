package com.ataraxia.notifications

import kotlinx.datetime.*

fun nextStateReminder(now: Instant, zone: TimeZone): Instant {
    val today = now.toLocalDateTime(zone).date
    val tonight = today.atTime(21, 0).toInstant(zone)
    return if (tonight > now) tonight else today.plus(1, DateTimeUnit.DAY).atTime(21, 0).toInstant(zone)
}
