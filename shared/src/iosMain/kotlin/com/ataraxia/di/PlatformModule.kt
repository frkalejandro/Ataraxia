package com.ataraxia.di

import com.ataraxia.data.db.DatabaseDriverFactory
import com.ataraxia.notifications.NoOpSleepNotificationScheduler
import com.ataraxia.notifications.SleepNotificationScheduler
import com.ataraxia.notifications.*
import org.koin.dsl.module

actual val platformModule = module {
    single { DatabaseDriverFactory() }
    single<TimerNotificationScheduler> { NoOpTimerNotificationScheduler() }
    single<HabitNotificationSettings> { NoOpHabitNotificationSettings() }
    single<SleepNotificationScheduler> { NoOpSleepNotificationScheduler() }
}
