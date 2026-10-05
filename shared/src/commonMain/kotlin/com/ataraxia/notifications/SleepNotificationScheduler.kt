package com.ataraxia.notifications

import kotlinx.datetime.LocalTime

/**
 * Programa los avisos asociados a la hora recomendada para acostarse.
 *
 * En Android se crean alarmas para la última comida (150 minutos antes),
 * el último buen vaso de agua (120) y la preparación para dormir (60, 30 y 10).
 * En escritorio e iOS esta implementación se deja como no-op por ahora.
 */
interface SleepNotificationScheduler {
    fun scheduleBedtimeReminders(
        bedtime: LocalTime,
        requestExactAlarmPermission: Boolean = false,
    )

    fun rescheduleSavedReminders()

    fun cancelBedtimeReminders()
}

class NoOpSleepNotificationScheduler : SleepNotificationScheduler {
    override fun scheduleBedtimeReminders(
        bedtime: LocalTime,
        requestExactAlarmPermission: Boolean,
    ) = Unit

    override fun rescheduleSavedReminders() = Unit

    override fun cancelBedtimeReminders() = Unit
}
