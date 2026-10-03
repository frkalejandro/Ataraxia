package com.ataraxia.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.ataraxia.domain.repository.CalendarRepository
import com.ataraxia.domain.repository.TaskRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.Calendar

private const val AGENDA_ACTION = "com.ataraxia.notifications.DAILY_AGENDA"
private const val AGENDA_CHANNEL = "agenda_reminders"
private const val AGENDA_PREFS = "ataraxia_agenda_reminders"

class AndroidAgendaNotificationScheduler(context: Context) {
    private val context = context.applicationContext

    fun scheduleDailyReminders() {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val pending = PendingIntent.getBroadcast(context, 13000,
            Intent(context, AgendaReminderReceiver::class.java).setAction(AGENDA_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
            }
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }
}

/** Read the current database at delivery time, so edits and completed tasks are respected. */
class AgendaReminderReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AGENDA_ACTION) return
        AndroidAgendaNotificationScheduler(context).scheduleDailyReminders()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8000) {
                    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                    val preferences = context.getSharedPreferences(AGENDA_PREFS, Context.MODE_PRIVATE)
                    if (preferences.getString("last_delivery", null) == today.toString()) return@withTimeout
                    val end = today.plus(if (today.dayOfWeek == DayOfWeek.MONDAY) 13 else 0, DateTimeUnit.DAY)
                    val events = get<CalendarRepository>().observeEventsForRange(today, end).first()
                    val tasks = get<TaskRepository>().observeTasksByDateRange(today, end).first()
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.createNotificationChannel(NotificationChannel(AGENDA_CHANNEL, "Agenda diaria y semanal", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Agenda a las 08:00 y resúmenes de ambas semanas los lunes"
                    })
                    if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(AGENDA_CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return@withTimeout
                    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("open_agenda", true)
                    }?.let { PendingIntent.getActivity(context, 13500, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
                    agendaDigests(today, events, tasks).forEach { digest ->
                        manager.notify(digest.id, Notification.Builder(context, AGENDA_CHANNEL)
                            .setSmallIcon(android.R.drawable.ic_popup_reminder)
                            .setContentTitle(digest.title)
                            .setContentText(digest.message.lineSequence().first())
                            .setStyle(Notification.BigTextStyle().bigText(digest.message))
                            .setContentIntent(open)
                            .setCategory(Notification.CATEGORY_REMINDER)
                            .setVisibility(Notification.VISIBILITY_PRIVATE)
                            .setAutoCancel(true).build())
                    }
                    preferences.edit().putString("last_delivery", today.toString()).apply()
                }
            } catch (error: Exception) {
                Log.e("AgendaReminders", "No se pudo cargar la agenda", error)
            } finally {
                result.finish()
            }
        }
    }
}

class AgendaReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                AndroidAgendaNotificationScheduler(context).scheduleDailyReminders()
        }
    }
}
