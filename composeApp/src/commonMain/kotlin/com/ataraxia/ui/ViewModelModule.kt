package com.ataraxia.ui

import com.ataraxia.ui.agenda.AgendaViewModel
import com.ataraxia.ui.dashboard.DashboardViewModel
import com.ataraxia.ui.habits.HabitsViewModel
import com.ataraxia.ui.journal.JournalViewModel
import com.ataraxia.ui.sleep.SleepViewModel
import com.ataraxia.ui.state.StateViewModel
import com.ataraxia.ui.exercise.ExerciseViewModel
import org.koin.dsl.module

val viewModelModule = module {
    factory { ExerciseViewModel(get()) }
    factory {
        DashboardViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    factory {
        HabitsViewModel(get(), get(), get(), get())
    }
    factory {
        SleepViewModel(get(), get(), get(), get(), get(), get())
    }
    factory {
        AgendaViewModel(get(), get(), get(), get(), get(), get(), get())
    }
    factory {
        JournalViewModel(get())
    }
    factory {
        StateViewModel(get(), get())
    }
}
