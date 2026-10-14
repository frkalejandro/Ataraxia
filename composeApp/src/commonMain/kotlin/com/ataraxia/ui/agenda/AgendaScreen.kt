package com.ataraxia.ui.agenda

import com.ataraxia.ui.SectionTopAppBar
import com.ataraxia.ui.RecordActions
import com.ataraxia.ui.RecordError
import com.ataraxia.ui.RecordDateField
import com.ataraxia.ui.parseRecordDate
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.CancellationException

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.ataraxia.domain.model.*
import com.ataraxia.domain.repository.CalendarRepository
import com.ataraxia.domain.usecase.*
import com.ataraxia.ui.dashboard.formatTime
import com.ataraxia.ui.dashboard.parseColor
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import com.ataraxia.domain.repository.TaskRepository
// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

enum class AgendaViewMode { WEEK, MONTH }

data class AgendaUiState(
    val selectedDate: LocalDate              = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val viewMode: AgendaViewMode             = AgendaViewMode.WEEK,
    val tasks: List<Task>                    = emptyList(),
    val events: List<CalendarEvent>          = emptyList(),
    // Fechas del mes con eventos (punto morado) y con tareas (punto naranja)
    val monthDatesWithEvents: Set<LocalDate> = emptySet(),
    val monthEventColors: Map<LocalDate, String> = emptyMap(),
    val monthDatesWithTasks: Set<LocalDate>  = emptySet(),
    val isLoading: Boolean                   = true,
    val showAddTaskSheet: Boolean            = false,
    val showAddEventSheet: Boolean           = false,
    val editingTask: Task? = null,
    val editingEvent: CalendarEvent? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

class AgendaViewModel(
    private val observeTasksForDate: ObserveTasksForDateUseCase,
    private val observeEventsForDate: ObserveCalendarEventsUseCase,
    private val saveTask: SaveTaskUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val saveEvent: SaveCalendarEventUseCase,
    private val calendarRepo: CalendarRepository,
    private val taskRepo: TaskRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(AgendaUiState())
    val state: StateFlow<AgendaUiState> = _state.asStateFlow()

    private var dayJob:   Job? = null
    private var monthJob: Job? = null

    init {
        collectForDate(_state.value.selectedDate)
        collectMonthContent(_state.value.selectedDate)
    }

    fun onSelectDate(date: LocalDate) {
        // Capturamos el mes anterior ANTES de actualizar el estado
        val prevDate = _state.value.selectedDate
        _state.update { it.copy(selectedDate = date, isLoading = true) }
        collectForDate(date)
        // Recargamos el mes solo si cambió
        if (date.year != prevDate.year || date.monthNumber != prevDate.monthNumber) {
            collectMonthContent(date)
        }
    }

    fun onSwitchView(mode: AgendaViewMode) {
        _state.update { it.copy(viewMode = mode) }
        if (mode == AgendaViewMode.MONTH) {
            collectMonthContent(_state.value.selectedDate)
        }
    }

    private fun collectForDate(date: LocalDate) {
        dayJob?.cancel()
        dayJob = screenModelScope.launch {
            combine(
                observeTasksForDate(date),
                observeEventsForDate(date),
            ) { tasks, (events, _) -> tasks to events }
                .collect { (tasks, events) ->
                    _state.update { it.copy(tasks = tasks, events = events, isLoading = false) }
                }
        }
    }

    private fun collectMonthContent(date: LocalDate) {
        monthJob?.cancel()
        monthJob = screenModelScope.launch {
            val firstDay = LocalDate(date.year, date.monthNumber, 1)
            val lastDay  = firstDay.plus(DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
            combine(
                calendarRepo.observeEventsForRange(firstDay, lastDay),
                taskRepo.observeTasksByDateRange(firstDay, lastDay),
            ) { events, tasks -> events to tasks }
                .collect { (events, tasks) ->
                    _state.update { it.copy(
                        monthDatesWithEvents = events.map { e -> e.date }.toSet(),
                        monthEventColors = events.associate { it.date to it.color },
                        monthDatesWithTasks  = tasks
                            .filter { t -> val d = t.dueDate; d != null && !t.isCompleted }
                            .mapNotNull { it.dueDate }
                            .toSet(),
                    ) }
                }
        }
    }

    fun onCompleteTask(id: String, completed: Boolean) {
        screenModelScope.launch { completeTask(id, completed) }
    }

    private fun mutate(action: suspend () -> Unit) {
        if (state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        screenModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _state.update { it.copy(error = "No se pudo guardar el cambio. Vuelve a intentarlo.") } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun clearError() { _state.update { it.copy(error = null) } }
    fun editTask(task: Task) { _state.update { it.copy(editingTask = task, showAddTaskSheet = true) } }
    fun editEvent(event: CalendarEvent) = mutate {
        val series = calendarRepo.getEventById(event.id) ?: return@mutate
        _state.update { it.copy(editingEvent = series, showAddEventSheet = true) }
    }
    fun deleteTask(task: Task) = mutate { taskRepo.deleteTask(task.id) }
    fun deleteEvent(event: CalendarEvent) = mutate { calendarRepo.deleteEvent(event.id) }

    fun onSaveTask(
        date: LocalDate,
        title: String,
        description: String,
        priority: TaskPriority,
        energy: EnergyLevel,
    ) {
        val original = state.value.editingTask
        mutate {
            val now = Clock.System.now()
            val base = original ?: Task(id = uuid4().toString(), title = title, createdAt = now, updatedAt = now)
            saveTask(base.copy(
                title       = title,
                description = description,
                dueDate     = date,
                priority    = priority,
                energyLevel = energy,
                createdAt   = base.createdAt,
                updatedAt   = now,
            ))
            _state.update { it.copy(showAddTaskSheet = false, editingTask = null) }
        }
    }

    fun onSaveEvent(
        date: LocalDate,
        title: String,
        startTime: LocalTime?,
        endTime: LocalTime?,
        isAllDay: Boolean,
        color: String,
        weekly: Boolean,
        recurrenceEnd: LocalDate?,
    ) {
        val original = state.value.editingEvent
        mutate {
            val now = Clock.System.now()
            val selectedDate = _state.value.selectedDate
            val base = original ?: CalendarEvent(id = uuid4().toString(), title = title, date = date, createdAt = now, updatedAt = now)
            saveEvent(base.copy(
                title     = title,
                date      = date,
                startTime = startTime,
                endTime   = endTime,
                isAllDay  = isAllDay,
                color     = color,
                recurrence = if (weekly) Recurrence.WEEKLY else Recurrence.NONE,
                recurrenceEnd = if (weekly) recurrenceEnd else null,
                createdAt = base.createdAt,
                updatedAt = now,
            ))
            _state.update { it.copy(showAddEventSheet = false, editingEvent = null) }
            collectMonthContent(selectedDate)
        }
    }

    fun showAddTaskSheet()  { _state.update { it.copy(showAddTaskSheet  = true, editingTask = null) } }
    fun hideAddTaskSheet()  { _state.update { it.copy(showAddTaskSheet  = false, editingTask = null) } }
    fun showAddEventSheet() { _state.update { it.copy(showAddEventSheet = true, editingEvent = null) } }
    fun hideAddEventSheet() { _state.update { it.copy(showAddEventSheet = false, editingEvent = null) } }
}

// ─────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────

class AgendaScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: AgendaViewModel = koinScreenModel()
        val state by vm.state.collectAsState()

        Scaffold(
            topBar = {
                SectionTopAppBar(
                    title = "Agenda",
                    actions = {
                        IconButton(onClick = {
                            vm.onSwitchView(
                                if (state.viewMode == AgendaViewMode.WEEK)
                                    AgendaViewMode.MONTH
                                else
                                    AgendaViewMode.WEEK
                            )
                        }) {
                            Icon(
                                imageVector = if (state.viewMode == AgendaViewMode.WEEK)
                                    Icons.Default.CalendarMonth
                                else
                                    Icons.Default.ViewWeek,
                                contentDescription = "Cambiar vista",
                                tint = Color(0xFF6C63FF),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(
                        onClick        = vm::showAddEventSheet,
                        containerColor = Color(0xFF4CAF82),
                        contentColor   = Color.White,
                    ) { Icon(Icons.Default.Event, contentDescription = "Nuevo evento") }
                    FloatingActionButton(
                        onClick        = vm::showAddTaskSheet,
                        containerColor = Color(0xFF6C63FF),
                        contentColor   = Color.White,
                    ) { Icon(Icons.Default.Add, contentDescription = "Nueva tarea") }
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {
                // Cinta semanal — siempre visible
                WeekStrip(
                    selectedDate   = state.selectedDate,
                    datesWithTasks = state.monthDatesWithTasks,
                    onDateSelected = vm::onSelectDate,
                )
                // Vista mensual — se muestra encima del contenido cuando está activa
                if (state.viewMode == AgendaViewMode.MONTH) {
                    MonthCalendar(
                        selectedDate     = state.selectedDate,
                        datesWithContent =
                            state.monthDatesWithEvents + state.monthDatesWithTasks,
                        eventColors      = state.monthEventColors,
                        onMonthChanged   = vm::onSelectDate,
                        onDateSelected   = vm::onSelectDate,
                    )
                    HorizontalDivider()
                } else {
                    HorizontalDivider()
                }
                // Contenido del día seleccionado
                DayContent(
                    state      = state,
                    onComplete = vm::onCompleteTask,
                    onEditTask = vm::editTask, onDeleteTask = vm::deleteTask,
                    onEditEvent = vm::editEvent, onDeleteEvent = vm::deleteEvent,
                )
            }
        }

        if (state.showAddTaskSheet) {
            AddTaskSheet(
                initial = state.editingTask, selectedDate = state.selectedDate, busy = state.busy,
                onDismiss = vm::hideAddTaskSheet,
                onSave    = { date, t, d, p, e -> vm.onSaveTask(date, t, d, p, e) },
            )
        }
        if (state.showAddEventSheet) {
            AddEventSheet(
                initial = state.editingEvent, selectedDate = state.selectedDate, busy = state.busy,
                onDismiss = vm::hideAddEventSheet,
                onSave    = { date, t, st, et, allDay, c, weekly, end -> vm.onSaveEvent(date, t, st, et, allDay, c, weekly, end) },
            )
        }
        RecordError(state.error, vm::clearError)
    }
}

// ─────────────────────────────────────────────
//  Week strip
// ─────────────────────────────────────────────

@Composable
private fun WeekStrip(
    selectedDate: LocalDate,
    datesWithTasks: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
) {
    val today     = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val weekStart = selectedDate.minus(
        (selectedDate.dayOfWeek.isoDayNumber - 1).toLong(), DateTimeUnit.DAY
    )
    val days      = (0..6).map { weekStart.plus(it, DateTimeUnit.DAY) }
    val labels    = listOf("L", "M", "X", "J", "V", "S", "D")

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEachIndexed { idx, date ->
            val isSelected = date == selectedDate
            val isToday    = date == today
            val hasTasks   = datesWithTasks.contains(date)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.clickable { onDateSelected(date) },
            ) {
                Text(
                    labels[idx],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Color(0xFF6C63FF)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSelected -> Color(0xFF6C63FF)
                                isToday    -> Color(0xFF6C63FF).copy(alpha = 0.15f)
                                else       -> Color.Transparent
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${date.dayOfMonth}",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                            color      = when {
                                isSelected -> Color.White
                                isToday    -> Color(0xFF6C63FF)
                                else       -> MaterialTheme.colorScheme.onBackground
                            },
                        )
                        if (hasTasks && !isSelected) {
                            Box(
                                Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF5A623))
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Month calendar
// ─────────────────────────────────────────────

@Composable
private fun MonthCalendar(
    selectedDate: LocalDate,
    datesWithContent: Set<LocalDate>,
    eventColors: Map<LocalDate, String>,
    onMonthChanged: (LocalDate) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

    // Estado interno para navegar entre meses
    var displayMonth by remember(selectedDate) {
        mutableStateOf(LocalDate(selectedDate.year, selectedDate.monthNumber, 1))
    }

    val months = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
    )

    val daysInMonth  = displayMonth.plus(DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
    // isoDayNumber: Lunes=1 … Domingo=7 → offset para empezar en lunes
    val startOffset  = displayMonth.dayOfWeek.isoDayNumber - 1
    val cellCount    = startOffset + daysInMonth
    val totalRows    = (cellCount + 6) / 7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Cabecera mes + navegación
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                displayMonth = displayMonth.minus(1, DateTimeUnit.MONTH)
                onMonthChanged(displayMonth)
            }) {
                Icon(Icons.Default.ChevronLeft, null, modifier = Modifier.size(18.dp))
            }
            Text(
                "${months[displayMonth.monthNumber - 1]} ${displayMonth.year}",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = {
                displayMonth = displayMonth.plus(1, DateTimeUnit.MONTH)
                onMonthChanged(displayMonth)
            }) {
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
            }
        }

        // Cabecera días de semana
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { label ->
                Text(
                    label,
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Celdas del mes
        val cells = (0 until totalRows * 7).map { idx ->
            val dayNum = idx - startOffset + 1
            if (dayNum < 1 || dayNum > daysInMonth) null
            else LocalDate(displayMonth.year, displayMonth.monthNumber, dayNum)
        }

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        modifier         = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            val isSelected = date == selectedDate
                            val isToday    = date == today
                            val hasContent = datesWithContent.contains(date)

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> Color(0xFF6C63FF)
                                            isToday    -> Color(0xFF6C63FF).copy(alpha = 0.15f)
                                            else       -> Color.Transparent
                                        }
                                    )
                                    .clickable { onDateSelected(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${date.dayOfMonth}",
                                        style      = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold
                                                     else FontWeight.Normal,
                                        color      = when {
                                            isSelected -> Color.White
                                            isToday    -> Color(0xFF6C63FF)
                                            else       -> MaterialTheme.colorScheme.onBackground
                                        },
                                    )
                                    if (hasContent && !isSelected) {
                                        Box(
                                            Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(eventColors[date]?.let(::parseColor) ?: Color(0xFF6C63FF).copy(alpha = 0.7f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Day content
// ─────────────────────────────────────────────

@Composable
private fun DayContent(
    state: AgendaUiState,
    onComplete: (String, Boolean) -> Unit,
    onEditTask: (Task) -> Unit, onDeleteTask: (Task) -> Unit,
    onEditEvent: (CalendarEvent) -> Unit, onDeleteEvent: (CalendarEvent) -> Unit,
) {
    var showFinished by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            delay(1000)
        }
    }
    val visibleEvents = state.events.filter { showFinished || it.isUpcoming(now) }
    val pending = state.tasks.filter { !it.isCompleted }
    val completed = state.tasks.filter { it.isCompleted }
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Mostrar finalizadas", Modifier.weight(1f))
                Switch(checked = showFinished, onCheckedChange = { showFinished = it })
            }
        }
        // Eventos
        if (visibleEvents.isNotEmpty()) {
            item {
                Text(
                    "Eventos",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(visibleEvents, key = { "ev_${it.id}" }) { event ->
                EventChip(event) {
                    RecordActions(event.title, enabled = !state.busy,
                        onEdit = { onEditEvent(event) }, onDelete = { onDeleteEvent(event) })
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }

        // Cabecera tareas
        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    "Tareas",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val done  = state.tasks.count { it.isCompleted }
                val total = state.tasks.size
                if (total > 0) {
                    Text(
                        "$done/$total",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF6C63FF),
                    )
                }
            }
        }

        // Estado vacío
        if (pending.isEmpty() && (!showFinished || completed.isEmpty()) && visibleEvents.isEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("✨", fontSize = 40.sp)
                    Text("Día libre", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "No hay tareas ni eventos pendientes.",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        items(pending,   key = { "task_${it.id}" })  { task ->
            AgendaTaskRow(task, onComplete = { onComplete(task.id, it) }) {
                RecordActions(task.title, enabled = !state.busy,
                    onEdit = { onEditTask(task) }, onDelete = { onDeleteTask(task) })
            }
        }
        if (showFinished && completed.isNotEmpty()) {
            item {
                Text(
                    "Completadas",
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(completed, key = { "done_${it.id}" }) { task ->
                AgendaTaskRow(task, onComplete = { onComplete(task.id, it) }) {
                    RecordActions(task.title, enabled = !state.busy,
                        onEdit = { onEditTask(task) }, onDelete = { onDeleteTask(task) })
                }
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

// ─────────────────────────────────────────────
//  Event chip
// ─────────────────────────────────────────────

@Composable
private fun EventChip(event: CalendarEvent, actions: @Composable () -> Unit) {
    val color = parseColor(event.color)
    // Variables locales para evitar smart cast imposible desde módulo externo
    val startTime = event.startTime
    val endTime   = event.endTime

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Text(
                event.title,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier   = Modifier.weight(1f),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            val timeLabel = when {
                event.isAllDay                       -> "Todo el día"
                startTime != null && endTime != null ->
                    "${formatTime(startTime.hour, startTime.minute)} – ${formatTime(endTime.hour, endTime.minute)}"
                startTime != null                    ->
                    formatTime(startTime.hour, startTime.minute)
                else -> ""
            }
            if (timeLabel.isNotBlank()) {
                Text(
                    timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            actions()
        }
    }
}

// ─────────────────────────────────────────────
//  Task row
// ─────────────────────────────────────────────

@Composable
private fun AgendaTaskRow(task: Task, onComplete: (Boolean) -> Unit, actions: @Composable () -> Unit) {
    val priorityColor = when (task.priority) {
        TaskPriority.CRITICAL -> Color(0xFFE05252)
        TaskPriority.HIGH     -> Color(0xFFF5A623)
        TaskPriority.MEDIUM   -> Color(0xFF6C63FF)
        TaskPriority.LOW      -> Color(0xFF9E9E9E)
    }
    val energyIcon = when (task.energyLevel) {
        EnergyLevel.HIGH   -> "⚡"
        EnergyLevel.MEDIUM -> "🔋"
        EnergyLevel.LOW    -> "🪫"
    }
    // Variable local para dueTime — evita smart cast imposible
    val dueTime = task.dueTime

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (task.isCompleted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(if (task.isCompleted) 0.dp else 1.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(4.dp, 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style          = MaterialTheme.typography.bodyMedium,
                    fontWeight     = FontWeight.Medium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color          = if (task.isCompleted)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines       = 1,
                    overflow       = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(energyIcon, fontSize = 12.sp)
                    if (dueTime != null) {
                        Text(
                            formatTime(dueTime.hour, dueTime.minute),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (task.description.isNotBlank()) {
                        Text(
                            task.description,
                            style    = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Checkbox(
                checked         = task.isCompleted,
                onCheckedChange = onComplete,
                colors          = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF82)),
            )
            actions()
        }
    }
}

// ─────────────────────────────────────────────
//  Add Task Sheet
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskSheet(
    initial: Task?, selectedDate: LocalDate, busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, String, TaskPriority, EnergyLevel) -> Unit,
) {
    var dateText by remember { mutableStateOf(selectedDate.toString()) }
    val date = parseRecordDate(dateText)
    var title    by remember { mutableStateOf(initial?.title ?: "") }
    var desc     by remember { mutableStateOf(initial?.description ?: "") }
    var priority by remember { mutableStateOf(initial?.priority ?: TaskPriority.MEDIUM) }
    var energy   by remember { mutableStateOf(initial?.energyLevel ?: EnergyLevel.MEDIUM) }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier            = Modifier
                .verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom     = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (initial == null) "Nueva tarea" else "Editar tarea", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Título") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = desc, onValueChange = { desc = it },
                label = { Text("Descripción") }, maxLines = 2,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )

            RecordDateField(dateText, { dateText = it })
            Text("Prioridad", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { p ->
                    val pColor = when (p) {
                        TaskPriority.CRITICAL -> Color(0xFFE05252)
                        TaskPriority.HIGH     -> Color(0xFFF5A623)
                        TaskPriority.MEDIUM   -> Color(0xFF6C63FF)
                        TaskPriority.LOW      -> Color(0xFF9E9E9E)
                    }
                    FilterChip(
                        selected = priority == p,
                        onClick  = { priority = p },
                        label    = {
                            Text(when (p) {
                                TaskPriority.CRITICAL -> "Crítica"
                                TaskPriority.HIGH     -> "Alta"
                                TaskPriority.MEDIUM   -> "Media"
                                TaskPriority.LOW      -> "Baja"
                            })
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = pColor.copy(alpha = 0.15f),
                            selectedLabelColor     = pColor,
                        ),
                    )
                }
                }
            }

            Text("Energía requerida", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EnergyLevel.entries.forEach { e ->
                    FilterChip(
                        selected = energy == e,
                        onClick  = { energy = e },
                        label    = {
                            Text(when (e) {
                                EnergyLevel.HIGH   -> "⚡ Alta"
                                EnergyLevel.MEDIUM -> "🔋 Media"
                                EnergyLevel.LOW    -> "🪫 Baja"
                            })
                        },
                    )
                }
            }
            }

            Button(
                onClick  = { date?.let { onSave(it, title.trim(), desc, priority, energy) } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                enabled  = !busy && title.isNotBlank() && date != null,
            ) { Text("Guardar tarea", fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
        }
    }
}

// ─────────────────────────────────────────────
//  Add Event Sheet
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventSheet(
    initial: CalendarEvent?, selectedDate: LocalDate, busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (LocalDate, String, LocalTime?, LocalTime?, Boolean, String, Boolean, LocalDate?) -> Unit,
) {
    var dateText by remember { mutableStateOf((initial?.date ?: selectedDate).toString()) }
    val date = parseRecordDate(dateText)
    var title    by remember { mutableStateOf(initial?.title ?: "") }
    var weekly by remember { mutableStateOf(initial?.recurrence == Recurrence.WEEKLY) }
    var indefinite by remember { mutableStateOf(initial?.recurrenceEnd == null) }
    var untilText by remember { mutableStateOf((initial?.recurrenceEnd ?: selectedDate.plus(1, DateTimeUnit.YEAR)).toString()) }
    val until = parseRecordDate(untilText)
    val recurrenceValid = !weekly || indefinite || (until != null && date != null && until >= date)
    var isAllDay by remember { mutableStateOf(initial?.isAllDay ?: false) }
    var startH   by remember { mutableStateOf(initial?.startTime?.hour ?: 9) }
    var startM   by remember { mutableStateOf(initial?.startTime?.minute ?: 0) }
    var endH     by remember { mutableStateOf(initial?.endTime?.hour ?: 10) }
    var endM     by remember { mutableStateOf(initial?.endTime?.minute ?: 0) }
    var color    by remember { mutableStateOf(initial?.color ?: "#4CAF82") }

    val eventColors = listOf("#4CAF82", "#2196F3", "#6C63FF", "#F5A623", "#E05252", "#E91E8C", "#00ACC1", "#7E57C2", "#00897B", "#FB8C00", "#D81B60", "#5E35B1")

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier            = Modifier
                .verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom     = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (initial == null) "Nuevo evento" else "Editar evento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Título del evento") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )

            RecordDateField(dateText, { dateText = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Todo el día", Modifier.weight(1f))
                Switch(checked = isAllDay, onCheckedChange = { isAllDay = it })
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Evento semanal", Modifier.weight(1f))
                Switch(checked = weekly, onCheckedChange = { weekly = it })
            }
            if (weekly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¿Semanal indefinida?", Modifier.weight(1f))
                    Switch(checked = indefinite, onCheckedChange = { indefinite = it })
                }
                if (!indefinite) {
                    Text("Repetir hasta (inclusive)")
                    RecordDateField(untilText, { untilText = it })
                    if (!recurrenceValid) Text("El término debe ser igual o posterior al inicio.", color = MaterialTheme.colorScheme.error)
                }
            }
            if (initial?.recurrence == Recurrence.WEEKLY) {
                Text("Los cambios se aplican a toda la serie semanal. Eliminar el evento elimina toda la serie.", style = MaterialTheme.typography.bodySmall)
            }

            if (!isAllDay) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Inicio",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EventStepper(startH, 0..23)    { startH = it }
                            Text(":", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                            EventStepper(startM, 0..59, 5) { startM = it }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Fin",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EventStepper(endH, 0..23)    { endH = it }
                            Text(":", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                            EventStepper(endM, 0..59, 5) { endM = it }
                        }
                    }
                }
            }

            Text("Color", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                eventColors.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { c ->
                    val cc       = parseColor(c)
                    val selected = c == color
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(cc)
                            .then(
                                if (selected)
                                    Modifier.border(
                                        width  = 3.dp,
                                        color  = MaterialTheme.colorScheme.onBackground,
                                        shape  = CircleShape,
                                    )
                                else Modifier
                            )
                            .clickable { color = c }
                    )
                }
                }
            }
            }

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val st = if (!isAllDay) LocalTime(startH, startM) else null
                        val et = if (!isAllDay) LocalTime(endH, endM) else null
                        date?.let { onSave(it, title.trim(), st, et, isAllDay, color, weekly, if (weekly && !indefinite) until else null) }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF82)),
                enabled  = !busy && title.isNotBlank() && date != null && recurrenceValid,
            ) { Text("Guardar evento", fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
        }
    }
}

@Composable
private fun EventStepper(value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick  = { onChange(range.first + (value - range.first + step) % (range.last - range.first + 1)) },
            modifier = Modifier.size(28.dp),
        ) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp)) }
        Text(
            value.toString().padStart(2, '0'),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick  = { onChange(range.first + (value - range.first - step + (range.last - range.first + 1)) % (range.last - range.first + 1)) },
            modifier = Modifier.size(28.dp),
        ) { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp)) }
    }
}
