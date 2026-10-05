package com.ataraxia.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

private const val EXERCISE_ACTION = "com.ataraxia.notifications.EXERCISE_REMINDER"
private const val EXERCISE_CHANNEL = "daily_exercise_reminder"
private const val EXERCISE_ID = 16000

class AndroidExerciseReminderScheduler(context: Context) : ExerciseReminderSettings {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("ataraxia_exercise_reminder", Context.MODE_PRIVATE)
    private val alarms = this.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    override val supported = true

    override fun load() = ExerciseReminderConfig(
        enabled = prefs.getBoolean("enabled", false),
        time = LocalTime(prefs.getInt("hour", 18).coerceIn(0, 23), prefs.getInt("minute", 0).coerceIn(0, 59)),
    )

    override fun save(config: ExerciseReminderConfig) {
        prefs.edit().putBoolean("enabled", config.enabled)
            .putInt("hour", config.time.hour).putInt("minute", config.time.minute).apply()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(EXERCISE_ID)
        reschedule()
    }

    fun reschedule() {
        val config = load()
        val pending = PendingIntent.getBroadcast(context, EXERCISE_ID,
            Intent(context, ExerciseReminderReceiver::class.java).setAction(EXERCISE_ACTION)
                .putExtra("hour", config.time.hour).putExtra("minute", config.time.minute),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(pending)
        val trigger = nextExerciseReminder(config, Clock.System.now(), TimeZone.currentSystemDefault())
            ?.toEpochMilliseconds() ?: return
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

class ExerciseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EXERCISE_ACTION) return
        val scheduler = AndroidExerciseReminderScheduler(context)
        val config = scheduler.load()
        if (!config.enabled || intent.getIntExtra("hour", -1) != config.time.hour ||
            intent.getIntExtra("minute", -1) != config.time.minute) return
        scheduler.reschedule()
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(EXERCISE_CHANNEL,
            "Recordatorio de ejercicio", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Aviso diario para hacer ejercicio a la hora que elijas"
        })
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_timer", TimerKind.EXERCISE.name)
        }?.let { PendingIntent.getActivity(context, EXERCISE_ID, it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        manager.notify(EXERCISE_ID, Notification.Builder(context, EXERCISE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Es hora de hacer ejercicio")
            .setContentText("Dedica un momento a moverte. Elige una rutina y comienza a tu ritmo.")
            .setContentIntent(open).setCategory(Notification.CATEGORY_REMINDER).setAutoCancel(true).build())
    }
}

class ExerciseReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                AndroidExerciseReminderScheduler(context).reschedule()
        }
    }
}
