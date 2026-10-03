package com.ataraxia.ui.dashboard

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ataraxia.domain.model.*
import com.ataraxia.domain.usecase.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*

data class DashboardUiState(
    val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val stoicQuote: StoicQuote = StoicQuotes.first(),
    val habits: List<HabitWithStatus> = emptyList(),
    val sleepEntry: SleepEntry? = null,
    val sleepGoal: SleepGoal? = null,
    val todayTasks: List<Task> = emptyList(),
    val todayEvents: List<CalendarEvent> = emptyList(),
    val isLoading: Boolean = true,
)

class DashboardViewModel(
    private val observeHabits: ObserveHabitsWithStatusUseCase,
    private val observeSleepEntries: ObserveSleepEntriesUseCase,
    private val observeSleepGoal: ObserveSleepGoalUseCase,
    private val observeTasks: ObserveTasksForDateUseCase,
    private val observeEvents: ObserveCalendarEventsUseCase,
    private val toggleHabit: ToggleHabitEntryUseCase,
    private val completeTask: CompleteTaskUseCase,
) : ScreenModel {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val selectedQuote = StoicQuotes.random()
    private val _state = MutableStateFlow(
        DashboardUiState(
            today = today,
            stoicQuote = selectedQuote,
        )
    )
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        screenModelScope.launch {
            combine(
                observeHabits(today),
                observeSleepEntries(7),
                observeSleepGoal(),
                observeTasks(today),
                observeEvents(today),
            ) { habits, sleepEntries, goal, tasks, (events, _) ->
                DashboardUiState(
                    today       = today,
                    stoicQuote  = selectedQuote,
                    habits      = habits,
                    sleepEntry  = sleepEntries.firstOrNull { it.date == today },
                    sleepGoal   = goal,
                    todayTasks  = tasks,
                    todayEvents = events,
                    isLoading   = false,
                )
            }.collect { _state.value = it }
        }
    }

    fun onToggleHabit(habitId: String) {
        screenModelScope.launch { toggleHabit(habitId, today) }
    }

    fun onCompleteTask(taskId: String) {
        screenModelScope.launch { completeTask(taskId) }
    }
}
