package com.ataraxia.di

import android.app.Application
import com.ataraxia.data.db.DatabaseDriverFactory
import com.ataraxia.notifications.AndroidSleepNotificationScheduler
import com.ataraxia.notifications.SleepNotificationScheduler
import org.koin.dsl.module

actual val platformModule = module {
    single { DatabaseDriverFactory() }
    single<SleepNotificationScheduler> {
        AndroidSleepNotificationScheduler(get<Application>())
    }
}
