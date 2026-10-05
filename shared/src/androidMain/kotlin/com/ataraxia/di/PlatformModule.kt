package com.ataraxia.di

import android.app.Application
import com.ataraxia.data.db.DatabaseDriverFactory
import com.ataraxia.notifications.AndroidSleepNotificationScheduler
import com.ataraxia.notifications.SleepNotificationScheduler
import com.ataraxia.notifications.*
import org.koin.dsl.module

actual val platformModule = module {
    single { DatabaseDriverFactory() }
    single<TimerNotificationScheduler> { AndroidTimerNotificationScheduler(get<Application>()) }
    single<HabitNotificationSettings> { AndroidHabitNotificationScheduler(get<Application>()) }
    single<ExerciseReminderSettings> { AndroidExerciseReminderScheduler(get<Application>()) }
    single<SleepNotificationScheduler> {
        AndroidSleepNotificationScheduler(get<Application>())
    }
}
