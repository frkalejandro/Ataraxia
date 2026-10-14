package com.ataraxia.ui.dashboard

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.ataraxia.domain.model.*
import com.ataraxia.domain.usecase.*
import com.ataraxia.domain.repository.FocusRepository
import com.ataraxia.domain.repository.FinanceRepository
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
    val weekTasks: List<Task> = emptyList(),
    val weekEvents: List<CalendarEvent> = emptyList(),
    val monthTasks: List<Task> = emptyList(),
    val monthEvents: List<CalendarEvent> = emptyList(),
    val exercisedToday: Boolean = false,
    val focusSecondsToday: Long = 0,
    val finances: FinanceSummary? = null,
    val isLoading: Boolean = true,
)

class DashboardViewModel(
    private val observeHabits: ObserveHabitsWithStatusUseCase,
    private val observeSleepEntries: ObserveSleepEntriesUseCase,
    private val observeSleepGoal: ObserveSleepGoalUseCase,
    private val tasks: com.ataraxia.domain.repository.TaskRepository,
    private val calendar: com.ataraxia.domain.repository.CalendarRepository,
    private val toggleHabit: ToggleHabitEntryUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val workouts: WorkoutRepository,
    private val focus: FocusRepository,
    private val finances: FinanceRepository,
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
                val weekStart = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
                val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
                val monthStart = LocalDate(date.year, date.monthNumber, 1)
                val monthEnd = monthStart.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
                combine(
                    observeHabits(date),
                    observeSleepEntries(7),
                    observeSleepGoal(),
                    tasks.observeTasksByDateRange(minOf(weekStart, monthStart), maxOf(weekEnd, monthEnd)),
                    calendar.observeEventsForRange(minOf(weekStart, monthStart), maxOf(weekEnd, monthEnd)),
                ) { habits, sleepEntries, goal, tasks, events ->
                    DashboardUiState(
                        today       = date,
                        stoicQuote  = selectedQuote,
                        habits      = habits,
                        sleepEntry  = sleepEntries.firstOrNull { it.date == date },
                        sleepGoal   = goal,
                        todayTasks  = tasks.filter { it.dueDate == date },
                        todayEvents = events.filter { it.date == date },
                        weekTasks = tasks.filter { it.dueDate?.let { d -> d in weekStart..weekEnd } == true },
                        weekEvents = events.filter { it.date in weekStart..weekEnd },
                        monthTasks = tasks.filter { it.dueDate?.let { d -> d in monthStart..monthEnd } == true },
                        monthEvents = events.filter { it.date in monthStart..monthEnd },
                        isLoading   = false,
                    )
                }.combine(flow {
                    while (true) {
                        emit(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()))
                        delay(1000)
                    }
                }) { dashboard, now ->
                    dashboard.copy(
                        todayTasks = dashboard.todayTasks.filterNot { it.isCompleted },
                        weekTasks = dashboard.weekTasks.filterNot { it.isCompleted },
                        monthTasks = dashboard.monthTasks.filterNot { it.isCompleted },
                        todayEvents = dashboard.todayEvents.filter { it.isUpcoming(now) },
                        weekEvents = dashboard.weekEvents.filter { it.isUpcoming(now) },
                        monthEvents = dashboard.monthEvents.filter { it.isUpcoming(now) },
                    )
                }.combine(workouts.observeSessions()) { dashboard, sessions ->
                    dashboard.copy(exercisedToday = sessions.any { it.date == date.toString() })
                }.combine(focus.observeTotalSeconds(date)) { dashboard, total ->
                    dashboard.copy(focusSecondsToday = total)
                }.combine(finances.observeEntries().map<List<FinanceEntry>, FinanceSummary?> { FinanceSummary(it) }
                    .catch { emit(null) }) { dashboard, summary ->
                    dashboard.copy(finances = summary)
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
