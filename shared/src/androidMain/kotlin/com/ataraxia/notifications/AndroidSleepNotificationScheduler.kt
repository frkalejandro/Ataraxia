package com.ataraxia.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

private const val PREFS_NAME = "ataraxia_sleep_reminders"
private const val KEY_ENABLED = "enabled"
private const val KEY_BEDTIME_HOUR = "bedtime_hour"
private const val KEY_BEDTIME_MINUTE = "bedtime_minute"

private const val CHANNEL_ID = "sleep_cycle_reminders"
private const val CHANNEL_NAME = "Recordatorios de sueño"
private const val ACTION_SLEEP_REMINDER = "com.ataraxia.notifications.SLEEP_REMINDER"

private const val EXTRA_OFFSET_MINUTES = "offset_minutes"
private const val EXTRA_BEDTIME_HOUR = "bedtime_hour"
private const val EXTRA_BEDTIME_MINUTE = "bedtime_minute"

private val REMINDER_OFFSETS = SleepReminder.entries.map { it.offsetMinutes }

class AndroidSleepNotificationScheduler(
    context: Context,
) : SleepNotificationScheduler {

    private val appContext = context.applicationContext
    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val preferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun scheduleBedtimeReminders(
        bedtime: LocalTime,
        requestExactAlarmPermission: Boolean,
    ) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_BEDTIME_HOUR, bedtime.hour)
            .putInt(KEY_BEDTIME_MINUTE, bedtime.minute)
            .apply()

        createNotificationChannel(appContext)
        cancelPendingAlarms()

        REMINDER_OFFSETS.forEach { offsetMinutes ->
            scheduleSingleReminder(
                offsetMinutes = offsetMinutes,
                bedtimeHour = bedtime.hour,
                bedtimeMinute = bedtime.minute,
            )
        }

        if (requestExactAlarmPermission && !canScheduleExactAlarms()) {
            openExactAlarmSettings()
        }
    }

    override fun rescheduleSavedReminders() {
        if (!preferences.getBoolean(KEY_ENABLED, false)) return

        val bedtimeHour = preferences.getInt(KEY_BEDTIME_HOUR, -1)
        val bedtimeMinute = preferences.getInt(KEY_BEDTIME_MINUTE, -1)

        if (bedtimeHour !in 0..23 || bedtimeMinute !in 0..59) return

        scheduleBedtimeReminders(
            bedtime = LocalTime(bedtimeHour, bedtimeMinute),
            requestExactAlarmPermission = false,
        )
    }

    override fun cancelBedtimeReminders() {
        cancelPendingAlarms()
        preferences.edit().clear().apply()
    }

    /**
     * Programa solo uno de los avisos. El receiver la usa después de mostrar
     * una notificación para dejar preparada la del día siguiente.
     */
    internal fun scheduleSingleReminder(
        offsetMinutes: Int,
        bedtimeHour: Int,
        bedtimeMinute: Int,
    ) {
        val triggerAtMillis = nextTriggerMillis(
            bedtimeHour = bedtimeHour,
            bedtimeMinute = bedtimeMinute,
            offsetMinutes = offsetMinutes,
        )
        val pendingIntent = reminderPendingIntent(
            offsetMinutes = offsetMinutes,
            bedtimeHour = bedtimeHour,
            bedtimeMinute = bedtimeMinute,
        )

        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                // Respaldo mientras el usuario concede “Alarmas y recordatorios”.
                // Android puede desplazar ligeramente una alarma no exacta.
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
        REMINDER_OFFSETS.forEach { offsetMinutes ->
            alarmManager.cancel(
                reminderPendingIntent(
                    offsetMinutes = offsetMinutes,
                    bedtimeHour = preferences.getInt(KEY_BEDTIME_HOUR, 0),
                    bedtimeMinute = preferences.getInt(KEY_BEDTIME_MINUTE, 0),
                )
            )
        }
    }

    private fun reminderPendingIntent(
        offsetMinutes: Int,
        bedtimeHour: Int,
        bedtimeMinute: Int,
    ): PendingIntent {
        val intent = Intent(appContext, SleepReminderReceiver::class.java).apply {
            action = ACTION_SLEEP_REMINDER
            putExtra(EXTRA_OFFSET_MINUTES, offsetMinutes)
            putExtra(EXTRA_BEDTIME_HOUR, bedtimeHour)
            putExtra(EXTRA_BEDTIME_MINUTE, bedtimeMinute)
        }

        return PendingIntent.getBroadcast(
            appContext,
            requestCode(offsetMinutes),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val requestIntent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            appContext.startActivity(requestIntent)
        } catch (_: ActivityNotFoundException) {
            val detailsIntent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${appContext.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(detailsIntent)
        }
    }
}

/** Recibe una alarma, muestra el aviso y programa el mismo aviso para mañana. */
class SleepReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SLEEP_REMINDER) return

        val offsetMinutes = intent.getIntExtra(EXTRA_OFFSET_MINUTES, -1)
        val bedtimeHour = intent.getIntExtra(EXTRA_BEDTIME_HOUR, -1)
        val bedtimeMinute = intent.getIntExtra(EXTRA_BEDTIME_MINUTE, -1)

        if (
            offsetMinutes !in REMINDER_OFFSETS ||
            bedtimeHour !in 0..23 ||
            bedtimeMinute !in 0..59
        ) {
            return
        }

        showSleepNotification(
            context = context,
            offsetMinutes = offsetMinutes,
            bedtimeHour = bedtimeHour,
            bedtimeMinute = bedtimeMinute,
        )

        AndroidSleepNotificationScheduler(context).scheduleSingleReminder(
            offsetMinutes = offsetMinutes,
            bedtimeHour = bedtimeHour,
            bedtimeMinute = bedtimeMinute,
        )
    }
}

/** Reconstruye las alarmas después de reinicios, cambios de hora o actualizaciones. */
class SleepReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
            -> AndroidSleepNotificationScheduler(context).rescheduleSavedReminders()
        }
    }
}

private fun nextTriggerMillis(
    bedtimeHour: Int,
    bedtimeMinute: Int,
    offsetMinutes: Int,
): Long {
    return nextSleepReminder(
        reminder = SleepReminder.entries.first { it.offsetMinutes == offsetMinutes },
        bedtime = LocalTime(bedtimeHour, bedtimeMinute),
        now = Clock.System.now(),
        zone = TimeZone.currentSystemDefault(),
    ).toEpochMilliseconds()
}

private fun showSleepNotification(
    context: Context,
    offsetMinutes: Int,
    bedtimeHour: Int,
    bedtimeMinute: Int,
) {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    createNotificationChannel(context)

    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val reminder = SleepReminder.entries.first { it.offsetMinutes == offsetMinutes }
    val message = reminder.message(LocalTime(bedtimeHour, bedtimeMinute))

    val launchIntent = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    val contentIntent = launchIntent?.let {
        PendingIntent.getActivity(
            context,
            9100,
            it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    val notification = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(reminder.title)
        .setContentText(message)
        .setStyle(
            Notification.BigTextStyle().bigText(
                message
            )
        )
        .setCategory(Notification.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setContentIntent(contentIntent)
        .build()

    manager.notify(notificationId(offsetMinutes), notification)
}

private fun createNotificationChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val channel = NotificationChannel(
        CHANNEL_ID,
        CHANNEL_NAME,
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Avisos de última comida, último buen vaso de agua y preparación para dormir"
        enableVibration(true)
    }

    manager.createNotificationChannel(channel)
}

private fun requestCode(offsetMinutes: Int): Int = 8000 + offsetMinutes

private fun notificationId(offsetMinutes: Int): Int = 9000 + offsetMinutes
