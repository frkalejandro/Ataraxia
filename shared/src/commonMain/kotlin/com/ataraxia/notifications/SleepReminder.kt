package com.ataraxia.notifications

import kotlinx.datetime.*

/** Los horarios de la tarjeta y de las alarmas comparten la misma meta de acostarse. */
enum class SleepReminder(val offsetMinutes: Int, val title: String) {
    LAST_MEAL(150, "Hora de última comida"),
    LAST_WATER(120, "Último buen vaso de agua"),
    ONE_HOUR(60, "Prepárate para dormir"),
    THIRTY_MINUTES(30, "Prepárate para dormir"),
    TEN_MINUTES(10, "Prepárate para dormir");

    fun timeBefore(bedtime: LocalTime): LocalTime {
        val minutes = (bedtime.hour * 60 + bedtime.minute - offsetMinutes + 24 * 60) % (24 * 60)
        return LocalTime(minutes / 60, minutes % 60)
    }

    fun message(bedtime: LocalTime): String {
        val time = bedtime.hour.toString().padStart(2, '0') + ":" +
            bedtime.minute.toString().padStart(2, '0')
        return when (this) {
            LAST_MEAL -> "Es la hora límite de tu última comida: faltan 2 h 30 min para acostarte a las $time, según tu meta de sueño."
            LAST_WATER -> "Es la hora límite de tu último buen vaso de agua: faltan 2 h para acostarte a las $time, según tu meta de sueño."
            else -> "Faltan $offsetMinutes minutos para tu hora recomendada de acostarte: $time."
        }
    }
}

/** Próximo aviso diario en hora local, también cuando corresponde al día anterior a la meta. */
fun nextSleepReminder(
    reminder: SleepReminder,
    bedtime: LocalTime,
    now: Instant,
    zone: TimeZone,
): Instant {
    val today = now.toLocalDateTime(zone).date
    val time = reminder.timeBefore(bedtime)
    val candidate = LocalDateTime(today, time).toInstant(zone)
    return if (candidate > now) candidate
    else LocalDateTime(today.plus(1, DateTimeUnit.DAY), time).toInstant(zone)
}
