package com.ataraxia.di

import com.ataraxia.data.db.DatabaseDriverFactory
import com.ataraxia.notifications.NoOpSleepNotificationScheduler
import com.ataraxia.notifications.SleepNotificationScheduler
import org.koin.dsl.module

actual val platformModule = module {
    single { DatabaseDriverFactory() }
    single<SleepNotificationScheduler> { NoOpSleepNotificationScheduler() }
}
