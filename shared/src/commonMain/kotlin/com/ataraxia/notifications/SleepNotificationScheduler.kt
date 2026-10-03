package com.ataraxia.notifications

import kotlinx.datetime.LocalTime

/**
 * Programa los avisos asociados a la hora recomendada para acostarse.
 *
 * En Android se crean alarmas para 60, 30 y 10 minutos antes.
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
