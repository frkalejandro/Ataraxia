package com.ataraxia.ui.dashboard

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ataraxia.domain.model.*
import com.ataraxia.domain.usecase.*
import com.ataraxia.domain.repository.FocusRepository
import com.ataraxia.domain.repository.WorkoutRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    val exercisedToday: Boolean = false,
    val focusSecondsToday: Long = 0,
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
    private val workouts: WorkoutRepository,
    private val focus: FocusRepository,
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeDashboard() {
        screenModelScope.launch {
            // Refresh the daily subscriptions at midnight and after returning from background.
            flow {
                while (true) {
                    emit(Clock.System.todayIn(TimeZone.currentSystemDefault()))
                    delay(1000)
                }
            }.distinctUntilChanged().flatMapLatest { date ->
                combine(
                    observeHabits(date),
                    observeSleepEntries(7),
                    observeSleepGoal(),
                    observeTasks(date),
                    observeEvents(date),
                ) { habits, sleepEntries, goal, tasks, (events, _) ->
                    DashboardUiState(
                        today       = date,
                        stoicQuote  = selectedQuote,
                        habits      = habits,
                        sleepEntry  = sleepEntries.firstOrNull { it.date == date },
                        sleepGoal   = goal,
                        todayTasks  = tasks,
                        todayEvents = events,
                        isLoading   = false,
                    )
                }.combine(workouts.observeSessions()) { dashboard, sessions ->
                    dashboard.copy(exercisedToday = sessions.any { it.date == date.toString() })
                }.combine(focus.observeTotalSeconds(date)) { dashboard, total ->
                    dashboard.copy(focusSecondsToday = total)
                }
            }.collect { _state.value = it }
        }
    }

    init { observeDashboard() }

    fun onToggleHabit(habitId: String) {
        val date = state.value.today
        screenModelScope.launch { toggleHabit(habitId, date) }
    }

    fun onCompleteTask(taskId: String) {
        screenModelScope.launch { completeTask(taskId) }
    }
}
