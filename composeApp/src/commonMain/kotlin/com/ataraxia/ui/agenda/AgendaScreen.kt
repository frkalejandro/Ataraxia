package com.ataraxia.ui.agenda

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
    val monthDatesWithTasks: Set<LocalDate>  = emptySet(),
    val isLoading: Boolean                   = true,
    val showAddTaskSheet: Boolean            = false,
    val showAddEventSheet: Boolean           = false,
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
                        monthDatesWithTasks  = tasks
                            .filter { t -> val d = t.dueDate; d != null && !t.isCompleted }
                            .mapNotNull { it.dueDate }
                            .toSet(),
                    ) }
                }
        }
    }

    fun onCompleteTask(id: String) {
        screenModelScope.launch { completeTask(id) }
    }

    fun onSaveTask(
        title: String,
        description: String,
        priority: TaskPriority,
        energy: EnergyLevel,
    ) {
        screenModelScope.launch {
            val now = Clock.System.now()
            saveTask(Task(
                id          = uuid4().toString(),
                title       = title,
                description = description,
                dueDate     = _state.value.selectedDate,
                priority    = priority,
                energyLevel = energy,
                createdAt   = now,
                updatedAt   = now,
            ))
            _state.update { it.copy(showAddTaskSheet = false) }
        }
    }

    fun onSaveEvent(
        title: String,
        startTime: LocalTime?,
        endTime: LocalTime?,
        isAllDay: Boolean,
        color: String,
    ) {
        screenModelScope.launch {
            val now = Clock.System.now()
            val selectedDate = _state.value.selectedDate
            saveEvent(CalendarEvent(
                id        = uuid4().toString(),
                title     = title,
                date      = selectedDate,
                startTime = startTime,
                endTime   = endTime,
                isAllDay  = isAllDay,
                color     = color,
                createdAt = now,
                updatedAt = now,
            ))
            _state.update { it.copy(showAddEventSheet = false) }
            collectMonthContent(selectedDate)
        }
    }

    fun showAddTaskSheet()  { _state.update { it.copy(showAddTaskSheet  = true) } }
    fun hideAddTaskSheet()  { _state.update { it.copy(showAddTaskSheet  = false) } }
    fun showAddEventSheet() { _state.update { it.copy(showAddEventSheet = true) } }
    fun hideAddEventSheet() { _state.update { it.copy(showAddEventSheet = false) } }
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
                TopAppBar(
                    title = { Text("Agenda", fontWeight = FontWeight.SemiBold) },
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
                )
            }
        }

        if (state.showAddTaskSheet) {
            AddTaskSheet(
                onDismiss = vm::hideAddTaskSheet,
                onSave    = { t, d, p, e -> vm.onSaveTask(t, d, p, e) },
            )
        }
        if (state.showAddEventSheet) {
            AddEventSheet(
                onDismiss = vm::hideAddEventSheet,
                onSave    = { t, st, et, allDay, c -> vm.onSaveEvent(t, st, et, allDay, c) },
            )
        }
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
                                                .background(Color(0xFF6C63FF).copy(alpha = 0.7f))
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
    onComplete: (String) -> Unit,
) {
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Eventos
        if (state.events.isNotEmpty()) {
            item {
                Text(
                    "Eventos",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.events, key = { "ev_${it.id}" }) { event ->
                EventChip(event)
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
        if (state.tasks.isEmpty() && state.events.isEmpty()) {
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
                        "No hay tareas ni eventos.",
                        style     = MaterialTheme.typography.bodySmall,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        val pending   = state.tasks.filter { !it.isCompleted }
        val completed = state.tasks.filter { it.isCompleted }

        items(pending,   key = { "task_${it.id}" })  { task ->
            AgendaTaskRow(task, onComplete = { onComplete(task.id) })
        }
        if (completed.isNotEmpty()) {
            item {
                Text(
                    "Completadas",
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(completed, key = { "done_${it.id}" }) { task ->
                AgendaTaskRow(task, onComplete = {})
            }
        }

        item { Spacer(Modifier.height(96.dp)) }
    }
}

// ─────────────────────────────────────────────
//  Event chip
// ─────────────────────────────────────────────

@Composable
private fun EventChip(event: CalendarEvent) {
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
        }
    }
}

// ─────────────────────────────────────────────
//  Task row
// ─────────────────────────────────────────────

@Composable
private fun AgendaTaskRow(task: Task, onComplete: () -> Unit) {
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
                onCheckedChange = { if (!task.isCompleted) onComplete() },
                colors          = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF82)),
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Add Task Sheet
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskSheet(
    onDismiss: () -> Unit,
    onSave: (String, String, TaskPriority, EnergyLevel) -> Unit,
) {
    var title    by remember { mutableStateOf("") }
    var desc     by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }
    var energy   by remember { mutableStateOf(EnergyLevel.MEDIUM) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier            = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom     = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Nueva tarea", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

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

            Text("Prioridad", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { p ->
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

            Button(
                onClick  = { if (title.isNotBlank()) onSave(title, desc, priority, energy) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                enabled  = title.isNotBlank(),
            ) { Text("Guardar tarea", fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ─────────────────────────────────────────────
//  Add Event Sheet
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventSheet(
    onDismiss: () -> Unit,
    onSave: (String, LocalTime?, LocalTime?, Boolean, String) -> Unit,
) {
    var title    by remember { mutableStateOf("") }
    var isAllDay by remember { mutableStateOf(false) }
    var startH   by remember { mutableStateOf(9) }
    var startM   by remember { mutableStateOf(0) }
    var endH     by remember { mutableStateOf(10) }
    var endM     by remember { mutableStateOf(0) }
    var color    by remember { mutableStateOf("#4CAF82") }

    val eventColors = listOf("#4CAF82", "#2196F3", "#6C63FF", "#F5A623", "#E05252", "#E91E8C")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape            = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier            = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom     = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Nuevo evento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Título del evento") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Todo el día", Modifier.weight(1f))
                Switch(checked = isAllDay, onCheckedChange = { isAllDay = it })
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                eventColors.forEach { c ->
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

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val st = if (!isAllDay) LocalTime(startH, startM) else null
                        val et = if (!isAllDay) LocalTime(endH, endM) else null
                        onSave(title, st, et, isAllDay, color)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF82)),
                enabled  = title.isNotBlank(),
            ) { Text("Guardar evento", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun EventStepper(value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick  = { onChange(if (value + step > range.last) range.first else value + step) },
            modifier = Modifier.size(28.dp),
        ) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp)) }
        Text(
            value.toString().padStart(2, '0'),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick  = { onChange(if (value - step < range.first) range.last else value - step) },
            modifier = Modifier.size(28.dp),
        ) { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp)) }
    }
}
