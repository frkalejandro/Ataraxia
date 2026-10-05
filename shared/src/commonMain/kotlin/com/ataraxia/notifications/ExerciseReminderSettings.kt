package com.ataraxia.notifications

import kotlinx.datetime.*

data class ExerciseReminderConfig(
    val enabled: Boolean = false,
    val time: LocalTime = LocalTime(18, 0),
)

interface ExerciseReminderSettings {
    val supported: Boolean get() = false
    fun load(): ExerciseReminderConfig
    fun save(config: ExerciseReminderConfig)
}

class NoOpExerciseReminderSettings : ExerciseReminderSettings {
    override fun load() = ExerciseReminderConfig()
    override fun save(config: ExerciseReminderConfig) = Unit
}

fun nextExerciseReminder(config: ExerciseReminderConfig, now: Instant, zone: TimeZone): Instant? {
    if (!config.enabled) return null
    val today = now.toLocalDateTime(zone).date
    val candidate = LocalDateTime(today, config.time).toInstant(zone)
    return if (candidate > now) candidate
    else LocalDateTime(today.plus(1, DateTimeUnit.DAY), config.time).toInstant(zone)
}
