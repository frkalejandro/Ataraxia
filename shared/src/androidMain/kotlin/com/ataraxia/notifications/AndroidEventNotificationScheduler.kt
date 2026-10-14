package com.ataraxia.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.ataraxia.domain.model.CalendarEvent
import com.ataraxia.domain.model.Recurrence
import com.ataraxia.domain.model.occurrences
import com.ataraxia.domain.repository.CalendarRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.*
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

private const val EVENT_ACTION = "com.ataraxia.notifications.EVENT_REMINDER"
private const val EVENT_CHANNEL = "calendar_event_reminders"
private val EVENT_OFFSETS = listOf(60, 30, 0)

class AndroidEventNotificationScheduler(context: Context) : EventNotificationScheduler, KoinComponent {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(id: String, offset: Int, start: Long = 0): PendingIntent =
        PendingIntent.getBroadcast(context, 0,
            Intent(context, EventReminderReceiver::class.java)
                .setAction(EVENT_ACTION)
                .setData(Uri.Builder().scheme("ataraxia").authority("event-reminder")
                    .appendPath(id).appendPath(offset.toString()).build())
                .putExtra("event_id", id).putExtra("offset", offset).putExtra("start", start),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    override fun cancel(id: String) {
        EVENT_OFFSETS.forEach { offset ->
            val intent = pending(id, offset)
            alarms.cancel(intent)
            intent.cancel()
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id, 14000)
    }

    override fun schedule(event: CalendarEvent) {
        cancel(event.id)
        scheduleFutureReminders(event)
    }

    internal fun scheduleFutureReminders(event: CalendarEvent) {
        val now = System.currentTimeMillis()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        EVENT_OFFSETS.forEach { offset ->
            val next = if (event.recurrence == Recurrence.WEEKLY) {
                event.occurrences(today, today.plus(7, DateTimeUnit.DAY)).firstOrNull {
                    (eventStart(it) ?: 0L) - offset * 60_000L > now
                } ?: return@forEach
            } else event
            val start = eventStart(next) ?: return@forEach
            val trigger = start - offset * 60_000L
            if (trigger <= now) return@forEach
            val intent = pending(event.id, offset, start)
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
                } else {
                    alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
                }
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, intent)
            }
        }
    }

    suspend fun reschedule() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        // Read series definitions without expanding thousands of years of occurrences.
        val repo = get<CalendarRepository>()
        get<com.ataraxia.db.AtaraxiaDatabase>().agendaQueries
            .selectEventsForDateRange(today.toString(), "9999-12-31").executeAsList()
            .forEach { row -> repo.getEventById(row.id)?.let { scheduleFutureReminders(it) } }
    }

    fun rescheduleAsync() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { reschedule() }
            catch (error: Exception) { Log.e("EventReminders", "No se pudieron reprogramar los eventos", error) }
        }
    }
}

private fun eventStart(event: CalendarEvent): Long? {
    if (event.isAllDay) return null
    val time = event.startTime ?: return null
    return LocalDateTime(event.date, time).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}

class EventReminderReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EVENT_ACTION) return
        val id = intent.getStringExtra("event_id") ?: return
        val offset = intent.getIntExtra("offset", -1)
        if (offset !in EVENT_OFFSETS) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8000) {
                    val event = get<CalendarRepository>().getEventById(id) ?: return@withTimeout
                    val deliveredStart = intent.getLongExtra("start", -1)
                    if (deliveredStart < 0) return@withTimeout
                    val deliveredDate = Instant.fromEpochMilliseconds(deliveredStart).toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val occurrence = event.occurrences(deliveredDate, deliveredDate).firstOrNull() ?: return@withTimeout
                    val start = eventStart(occurrence) ?: return@withTimeout
                    if (start != deliveredStart) return@withTimeout
                    // Re-arm each offset independently, even if notification permission is disabled.
                    (get<EventNotificationScheduler>() as? AndroidEventNotificationScheduler)?.scheduleFutureReminders(event)
                    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@withTimeout
                    val now = System.currentTimeMillis()
                    val trigger = start - offset * 60_000L
                    // Discard stale deliveries instead of reporting an incorrect countdown.
                    if (now < trigger || now - trigger > 5 * 60_000L) return@withTimeout
                    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.createNotificationChannel(NotificationChannel(EVENT_CHANNEL, "Recordatorios de eventos", NotificationManager.IMPORTANCE_HIGH))
                    if (!manager.areNotificationsEnabled() || manager.getNotificationChannel(EVENT_CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return@withTimeout
                    val message = when (offset) {
                        60 -> "Comienza en una hora, a las ${event.startTime}."
                        30 -> "Comienza en media hora, a las ${event.startTime}."
                        else -> "Comienza ahora, a las ${event.startTime}."
                    }
                    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("open_agenda", true)
                    }?.let { PendingIntent.getActivity(context, 14500, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
                    manager.notify(id, 14000, Notification.Builder(context, EVENT_CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_popup_reminder)
                        .setContentTitle(event.title).setContentText(message)
                        .setStyle(Notification.BigTextStyle().bigText(message))
                        .setContentIntent(open).setCategory(Notification.CATEGORY_REMINDER)
                        .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).build())
                }
            } catch (error: Exception) {
                Log.e("EventReminders", "No se pudo notificar el evento", error)
            } finally { result.finish() }
        }
    }
}
