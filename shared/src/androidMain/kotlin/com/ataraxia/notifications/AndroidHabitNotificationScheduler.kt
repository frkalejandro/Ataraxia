package com.ataraxia.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import java.util.Calendar

private const val HABIT_PREFS_NAME = "ataraxia_habit_reminders"
private const val HABIT_KEY_ENABLED = "enabled"

private const val HABIT_CHANNEL_ID = "daily_habit_reminders"
private const val HABIT_CHANNEL_NAME = "Recordatorios de hábitos"
private const val ACTION_HABIT_REMINDER =
    "com.ataraxia.notifications.DAILY_HABIT_REMINDER"
private const val EXTRA_REMINDER_ID = "habit_reminder_id"

private data class HabitReminder(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val title: String,
    val message: String,
)

private val DAILY_HABIT_REMINDERS = listOf(
    HabitReminder(
        id = 1,
        hour = 8,
        minute = 0,
        title = "Hábitos de la mañana",
        message = "Buenos días. Recuerda completar tus hábitos de la mañana.",
    ),
    HabitReminder(
        id = 2,
        hour = 14,
        minute = 0,
        title = "Hábitos durante el día",
        message = "Haz una pausa y revisa tus hábitos para el resto del día.",
    ),
    HabitReminder(
        id = 3,
        hour = 22,
        minute = 0,
        title = "Hábitos de la noche",
        message = "Antes de terminar el día, recuerda completar tus hábitos nocturnos.",
    ),
)

/**
 * Programa recordatorios diarios para las tres categorías de hábitos:
 * mañana a las 08:00, durante el día a las 14:00 y noche a las 22:00.
 */
class AndroidHabitNotificationScheduler(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val preferences =
        appContext.getSharedPreferences(HABIT_PREFS_NAME, Context.MODE_PRIVATE)

    fun scheduleDailyReminders() {
        preferences.edit()
            .putBoolean(HABIT_KEY_ENABLED, true)
            .apply()

        createHabitNotificationChannel(appContext)
        cancelPendingAlarms()

        DAILY_HABIT_REMINDERS.forEach { reminder ->
            scheduleSingleReminder(reminder.id)
        }
    }

    fun rescheduleSavedReminders() {
        if (!preferences.getBoolean(HABIT_KEY_ENABLED, false)) return
        scheduleDailyReminders()
    }

    fun cancelDailyReminders() {
        cancelPendingAlarms()
        preferences.edit().clear().apply()
    }

    /** Programa un recordatorio y lo deja preparado para su próxima ocurrencia. */
    internal fun scheduleSingleReminder(reminderId: Int) {
        val reminder = DAILY_HABIT_REMINDERS.firstOrNull { it.id == reminderId }
            ?: return

        val triggerAtMillis = nextHabitTriggerMillis(
            hour = reminder.hour,
            minute = reminder.minute,
        )
        val pendingIntent = habitReminderPendingIntent(reminder.id)

        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                // Sin el permiso especial, Android podría retrasarla algunos minutos.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun cancelPendingAlarms() {
        DAILY_HABIT_REMINDERS.forEach { reminder ->
            alarmManager.cancel(habitReminderPendingIntent(reminder.id))
        }
    }

    private fun habitReminderPendingIntent(reminderId: Int): PendingIntent {
        val intent = Intent(appContext, HabitReminderReceiver::class.java).apply {
            action = ACTION_HABIT_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

        return PendingIntent.getBroadcast(
            appContext,
            habitRequestCode(reminderId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
}

/** Muestra el recordatorio y vuelve a programar la misma hora para el día siguiente. */
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_HABIT_REMINDER) return

        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, -1)
        val reminder = DAILY_HABIT_REMINDERS.firstOrNull { it.id == reminderId }
            ?: return

        showHabitNotification(context, reminder)
        AndroidHabitNotificationScheduler(context).scheduleSingleReminder(reminder.id)
    }
}

/**
 * Recupera los avisos después de reiniciar, cambiar hora/zona, actualizar la app
 * o conceder el permiso de alarmas exactas.
 */
class HabitReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            -> AndroidHabitNotificationScheduler(context).rescheduleSavedReminders()
        }
    }
}

private fun nextHabitTriggerMillis(hour: Int, minute: Int): Long {
    val now = Calendar.getInstance()

    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)

        if (timeInMillis <= now.timeInMillis) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }.timeInMillis
}

private fun showHabitNotification(
    context: Context,
    reminder: HabitReminder,
) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    createHabitNotificationChannel(context)

    val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val launchIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    val contentIntent = launchIntent?.let {
        PendingIntent.getActivity(
            context,
            12_500 + reminder.id,
            it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    val notification = Notification.Builder(context, HABIT_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_popup_reminder)
        .setContentTitle(reminder.title)
        .setContentText(reminder.message)
        .setStyle(Notification.BigTextStyle().bigText(reminder.message))
        .setCategory(Notification.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setContentIntent(contentIntent)
        .build()

    manager.notify(habitNotificationId(reminder.id), notification)
}

private fun createHabitNotificationChannel(context: Context) {
    val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(
        HABIT_CHANNEL_ID,
        HABIT_CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Avisos diarios de hábitos a las 08:00, 14:00 y 22:00"
        enableVibration(true)
    }

    manager.createNotificationChannel(channel)
}

private fun habitRequestCode(reminderId: Int): Int = 11_000 + reminderId
private fun habitNotificationId(reminderId: Int): Int = 12_000 + reminderId
