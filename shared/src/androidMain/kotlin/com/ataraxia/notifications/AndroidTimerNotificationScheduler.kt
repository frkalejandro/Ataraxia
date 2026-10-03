package com.ataraxia.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ataraxia.domain.model.FocusTimer
import com.ataraxia.domain.repository.FocusRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TIMER_ACTION = "com.ataraxia.notifications.TIMER_FINISHED"
private const val RUNNING_CHANNEL = "active_timers"
private const val FINISHED_CHANNEL = "timer_completion"

/** AlarmManager owns delivery; Android's chronometer updates even when our process is gone. */
class AndroidTimerNotificationScheduler(context: Context) : TimerNotificationScheduler, KoinComponent {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("ataraxia_timers", Context.MODE_PRIVATE)
    private val alarms = this.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val manager = this.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override val supported = true
    override val exactAlarmsAllowed: Boolean
        get() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    override fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31 && !exactAlarmsAllowed) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
        }
    }

    override fun restoreFocus(): FocusTimer? = prefs.getString("focus_state", null)?.let {
        runCatching { json.decodeFromString<FocusTimer>(it) }.getOrNull()
    }

    override fun saveFocus(timer: FocusTimer) {
        prefs.edit().putString("focus_state", json.encodeToString(timer)).apply()
    }

    override fun update(kind: TimerKind, notice: TimerNotice?) {
        val old = saved(kind)
        if (old == notice) {
            if (notice != null) finishIfDue(kind, notice)
            return
        }
        // A UI tick may have advanced the phase before the broadcast arrives.
        if (old != null) finishIfDue(kind, old)
        alarms.cancel(pending(kind))
        manager.cancel(activeId(kind))
        prefs.edit().apply {
            if (notice == null) remove(kind.name) else putString(kind.name, json.encodeToString(notice))
        }.apply()
        if (notice != null) publishAndSchedule(kind, notice)
    }

    override fun checkDue() {
        TimerKind.entries.forEach { kind -> saved(kind)?.let { finishIfDue(kind, it) } }
    }

    fun rescheduleSavedTimers() {
        TimerKind.entries.forEach { kind -> saved(kind)?.let { publishAndSchedule(kind, it) } }
    }

    private fun saved(kind: TimerKind): TimerNotice? = prefs.getString(kind.name, null)?.let {
        runCatching { json.decodeFromString<TimerNotice>(it) }.getOrNull()
    }

    private fun delivered(kind: TimerKind, notice: TimerNotice) =
        prefs.getString("delivered_${kind.name}", null) == json.encodeToString(notice)

    private fun finishIfDue(kind: TimerKind, notice: TimerNotice): Boolean {
        if (System.currentTimeMillis() < notice.deadlineMillis) return false
        if (!delivered(kind, notice)) {
            if (kind == TimerKind.FOCUS) restoreFocus()?.let { timer ->
                if (timer.deadlineMillis == notice.deadlineMillis) {
                    get<FocusRepository>().recordElapsedBlock(timer, System.currentTimeMillis())
                }
            }
            manager.cancel(activeId(kind))
            // Both foreground ticks and the receiver run on the main thread. Persist before alerting.
            prefs.edit().putString("delivered_${kind.name}", json.encodeToString(notice)).apply()
            alarms.cancel(pending(kind))
            createChannels()
            if (canNotify()) manager.notify(finishedId(kind), Notification.Builder(context, FINISHED_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(notice.completion)
                .setContentText(notice.title)
                .setStyle(Notification.BigTextStyle().bigText("${notice.completion}.\n${notice.title}"))
                .setContentIntent(openTimer(kind))
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true).build())
        }
        return true
    }

    private fun publishAndSchedule(kind: TimerKind, notice: TimerNotice) {
        if (finishIfDue(kind, notice) || delivered(kind, notice)) return
        createChannels()
        manager.cancel(finishedId(kind))
        if (canNotify()) manager.notify(activeId(kind), Notification.Builder(context, RUNNING_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(notice.title)
            .setContentText("Tiempo restante")
            .setWhen(notice.deadlineMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setContentIntent(openTimer(kind)).build())
        try {
            if (exactAlarmsAllowed) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                notice.deadlineMillis, pending(kind))
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notice.deadlineMillis, pending(kind))
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notice.deadlineMillis, pending(kind))
        }
    }

    private fun pending(kind: TimerKind) = PendingIntent.getBroadcast(context, activeId(kind),
        Intent(context, TimerFinishedReceiver::class.java).setAction(TIMER_ACTION).putExtra("kind", kind.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun openTimer(kind: TimerKind): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_timer", kind.name)
        }?.let { PendingIntent.getActivity(context, activeId(kind), it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }

    private fun canNotify() = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannels() {
        manager.createNotificationChannel(NotificationChannel(RUNNING_CHANNEL,
            "Temporizadores activos", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Cuenta regresiva de enfoque y ejercicio"
            setSound(null, null)
        })
        manager.createNotificationChannel(NotificationChannel(FINISHED_CHANNEL,
            "Temporizadores terminados", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Sonido al terminar la concentración, el descanso o el ejercicio"
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
        })
    }

    private fun activeId(kind: TimerKind) = 14000 + kind.ordinal
    private fun finishedId(kind: TimerKind) = 14100 + kind.ordinal
}

class TimerFinishedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TIMER_ACTION) AndroidTimerNotificationScheduler(context).checkDue()
    }
}

class TimerRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                AndroidTimerNotificationScheduler(context).rescheduleSavedTimers()
        }
    }
}
