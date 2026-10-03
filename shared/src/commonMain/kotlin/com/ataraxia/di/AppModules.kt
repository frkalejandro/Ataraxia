package com.ataraxia.di

import com.ataraxia.data.repository.*
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.repository.*
import com.ataraxia.domain.usecase.*
import org.koin.dsl.module

val databaseModule = module {
    single<AtaraxiaDatabase> {
        AtaraxiaDatabase(get<com.ataraxia.data.db.DatabaseDriverFactory>().createDriver())
    }
}

val repositoryModule = module {
    single<WorkoutRepository> { WorkoutRepositoryImpl(get()) }
    single<HabitRepository>    { HabitRepositoryImpl(get()) }
    single<SleepRepository>    { SleepRepositoryImpl(get()) }
    single<TaskRepository>     { TaskRepositoryImpl(get()) }
    single<CalendarRepository> { CalendarRepositoryImpl(get()) }
    single<JournalRepository>  { JournalRepositoryImpl(get()) }
    single<DailyStateRepository> { DailyStateRepositoryImpl(get()) }
}

val useCaseModule = module {
    factory { ObserveHabitsWithStatusUseCase(get()) }
    factory { ToggleHabitEntryUseCase(get()) }
    factory { SaveHabitUseCase(get()) }
    factory { ArchiveHabitUseCase(get()) }

    factory { ObserveSleepEntriesUseCase(get()) }
    factory { SaveSleepEntryUseCase(get()) }
    factory { GetSleepStatsUseCase(get()) }
    factory { ObserveSleepGoalUseCase(get()) }
    factory { SaveSleepGoalUseCase(get()) }

    factory { ObserveActiveTasksUseCase(get()) }
    factory { ObserveTasksForDateUseCase(get()) }
    factory { SaveTaskUseCase(get()) }
    factory { CompleteTaskUseCase(get()) }
    factory { ObserveCalendarEventsUseCase(get(), get()) }
    factory { SaveCalendarEventUseCase(get()) }
}

val sharedModules = listOf(platformModule, databaseModule, repositoryModule, useCaseModule)
