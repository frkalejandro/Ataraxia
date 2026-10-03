package com.ataraxia.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

private const val STATE_ACTION = "com.ataraxia.notifications.DAILY_STATE"
private const val STATE_CHANNEL = "daily_state_reminder"
private const val STATE_NOTIFICATION_ID = 15000

class AndroidStateNotificationScheduler(context: Context) {
    private val context = context.applicationContext

    fun scheduleDailyReminder() {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val trigger = nextStateReminder(Clock.System.now(), TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val pending = PendingIntent.getBroadcast(context, STATE_NOTIFICATION_ID,
            Intent(context, StateReminderReceiver::class.java).setAction(STATE_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        }
    }
}

class StateReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != STATE_ACTION) return
        AndroidStateNotificationScheduler(context).scheduleDailyReminder()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(STATE_CHANNEL,
            "Recordatorio de Estado", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Recordatorio diario a las 21:00 para rellenar tu estado"
        })
        if (!manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(STATE_CHANNEL).importance == NotificationManager.IMPORTANCE_NONE) return
        val prefs = context.getSharedPreferences("ataraxia_state_reminder", Context.MODE_PRIVATE)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        if (prefs.getString("last_delivery", null) == today) return
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_state", true)
        }?.let { PendingIntent.getActivity(context, STATE_NOTIFICATION_ID, it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        manager.notify(STATE_NOTIFICATION_ID, Notification.Builder(context, STATE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Monitorea tu estado hoy")
            .setContentText("Recuerda rellenar tu estado de hoy. ¿Cómo te sientes?")
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true).build())
        prefs.edit().putString("last_delivery", today).apply()
    }
}

class StateReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                AndroidStateNotificationScheduler(context).scheduleDailyReminder()
        }
    }
}
