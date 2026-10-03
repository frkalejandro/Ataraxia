package com.ataraxia.notifications

enum class HabitReminderTime(val id: Int, val label: String) {
    MORNING(1, "Mañana · 08:00"), DAY(2, "Durante el día · 14:00"), NIGHT(3, "Noche · 22:00")
}

interface HabitNotificationSettings {
    val supported: Boolean get() = false
    fun isEnabled(time: HabitReminderTime): Boolean
    fun setEnabled(time: HabitReminderTime, enabled: Boolean)
}

class NoOpHabitNotificationSettings : HabitNotificationSettings {
    override fun isEnabled(time: HabitReminderTime) = false
    override fun setEnabled(time: HabitReminderTime, enabled: Boolean) = Unit
}
