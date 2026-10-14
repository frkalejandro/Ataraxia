package com.ataraxia.ui.habits

import com.ataraxia.ui.SectionTopAppBar
import com.ataraxia.ui.ConfirmRecordDeletion
import com.ataraxia.ui.RecordError
import com.ataraxia.domain.repository.HabitRepository
import kotlinx.coroutines.CancellationException

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.ataraxia.domain.model.*
import com.ataraxia.domain.usecase.*
import com.ataraxia.notifications.HabitNotificationSettings
import com.ataraxia.notifications.HabitReminderTime
import org.koin.compose.koinInject
import com.ataraxia.ui.dashboard.parseColor
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

data class HabitsUiState(
    val habits: List<HabitWithStatus> = emptyList(),
    val archived: List<Habit> = emptyList(),
    val isLoading: Boolean            = true,
    val showAddSheet: Boolean         = false,
    val editing: Habit? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

class HabitsViewModel(
    private val observeHabits: ObserveHabitsWithStatusUseCase,
    private val toggleEntry: ToggleHabitEntryUseCase,
    private val saveHabit: SaveHabitUseCase,
    private val archiveHabit: ArchiveHabitUseCase,
    private val repository: HabitRepository,
) : ScreenModel {

    private val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    private val _state = MutableStateFlow(HabitsUiState())
    val state: StateFlow<HabitsUiState> = _state.asStateFlow()

    init {
        screenModelScope.launch {
            combine(observeHabits(today), repository.observeArchivedHabits()) { list, archived -> list to archived }.collect { (list, archived) ->
                _state.update { it.copy(habits = list, archived = archived, isLoading = false) }
            }
        }
    }

    fun onToggle(habitId: String) {
        screenModelScope.launch { toggleEntry(habitId, today) }
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
    fun onArchive(habitId: String) = mutate { archiveHabit(habitId) }
    fun restore(habit: Habit) = mutate { repository.saveHabit(habit.copy(isArchived = false, updatedAt = Clock.System.now())) }
    fun onDelete(habitId: String) = mutate { repository.deleteHabit(habitId) }
    fun onEdit(habit: Habit) { _state.update { it.copy(editing = habit, showAddSheet = true) } }

    fun onSaveNewHabit(
        title: String, icon: String, color: String,
        freq: HabitFrequency, category: HabitCategory,
        targetDays: List<DayOfWeek>,
    ) {
        val original = state.value.editing
        mutate {
            val now = Clock.System.now()
            val base = original ?: Habit(id = uuid4().toString(), title = title, createdAt = now, updatedAt = now)
            saveHabit(base.copy(
                title      = title.trim(),
                icon       = icon,
                color      = color,
                frequency  = freq,
                category   = category,
                targetDays = when (freq) {
                    HabitFrequency.DAILY  -> DayOfWeek.entries
                    HabitFrequency.WEEKLY,
                    HabitFrequency.CUSTOM -> if (targetDays.isEmpty()) DayOfWeek.entries else targetDays
                },
                createdAt  = base.createdAt,
                updatedAt  = now,
            ))
            _state.update { it.copy(showAddSheet = false, editing = null) }
        }
    }

    fun showAddSheet() { _state.update { it.copy(showAddSheet = true, editing = null) } }
    fun hideAddSheet() { _state.update { it.copy(showAddSheet = false, editing = null) } }
}

// ─────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────

class HabitsScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: HabitsViewModel = koinScreenModel()
        val state by vm.state.collectAsState()

        // Tab seleccionada: 0=General, 1=Mañana, 2=Noche
        var deleting by remember { mutableStateOf<Habit?>(null) }
        var showArchived by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableIntStateOf(0) }
        var showReminders by remember { mutableStateOf(false) }
        val reminders: HabitNotificationSettings = koinInject()
        val tabs = listOf(

            Triple(HabitCategory.MORNING, "Por la mañana",  "☀️"),
            Triple(HabitCategory.GENERAL, "Durante el día", "🌇"),
            Triple(HabitCategory.NIGHT,   "Al acostarse",   "🌙"),
        )

        Scaffold(
            topBar = {
                SectionTopAppBar(title = if (showArchived) "Hábitos archivados" else "Hábitos", actions = {
                    TextButton(onClick = { showArchived = !showArchived }) { Text(if (showArchived) "Activos" else "Archivados") }
                    if (reminders.supported) IconButton(onClick = { showReminders = true }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Configurar recordatorios de hábitos")
                    }
                })
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick        = vm::showAddSheet,
                    containerColor = Color(0xFF6C63FF),
                    contentColor   = Color.White,
                ) { Icon(Icons.Default.Add, contentDescription = "Nuevo hábito") }
            },
        ) { padding ->
            Column(Modifier.padding(padding)) {

                // ── Tabs de categoría ───────────────
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Color(0xFF6C63FF),
                    indicator         = { tabPositions ->
                        val pos = tabPositions[selectedTab]
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.BottomStart)
                                .offset(x = pos.left)
                                .width(pos.width)
                                .height(3.dp)
                                .background(
                                    color = Color(0xFF6C63FF),
                                    shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                )
                        )
                    },
                ) {
                    tabs.forEachIndexed { idx, (_, label, emoji) ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick  = { selectedTab = idx },
                            text     = {
                                Text(
                                    text = "$emoji $label",
                                    fontWeight = if (selectedTab == idx) FontWeight.SemiBold
                                                 else FontWeight.Normal,
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }

                if (state.isLoading) {
                    Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color(0xFF6C63FF))
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }}else {
                    val currentCategory = tabs[selectedTab].first
                    val filtered = if (showArchived) state.archived.filter { it.category == currentCategory }
                        .map { HabitWithStatus(it, null, 0, 0f) }
                    else state.habits.filter { it.habit.category == currentCategory }

                    if (filtered.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(40.dp),
                            ) {
                                Text(tabs[selectedTab].third, fontSize = 48.sp)
                                Text(
                                    "Sin hábitos en esta categoría",
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    textAlign  = TextAlign.Center,
                                )
                                Text(
                                    "Toca + para añadir un hábito de ${tabs[selectedTab].second.lowercase()}.",
                                    style     = MaterialTheme.typography.bodySmall,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(filtered, key = { it.habit.id }) { status ->
                                HabitCard(
                                    status    = status,
                                    onToggle  = { vm.onToggle(status.habit.id) },
                                    onArchive = { if (showArchived) vm.restore(status.habit) else vm.onArchive(status.habit.id) },
                                    onEdit = { vm.onEdit(status.habit) },
                                    onDelete = { deleting = status.habit },
                                    enabled = !state.busy,
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }

        deleting?.let { habit ->
            ConfirmRecordDeletion(habit.title, "Se borrará este hábito y todo su historial de cumplimiento.", !state.busy,
                onDismiss = { deleting = null }, onConfirm = { vm.onDelete(habit.id); deleting = null })
        }
        if (showReminders) {
            AlertDialog(onDismissRequest = { showReminders = false },
                title = { Text("Recordatorios de hábitos") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Activa o desactiva cada horario. Los cambios se guardan al instante.")
                        HabitReminderTime.entries.forEach { time ->
                            var enabled by remember(time) { mutableStateOf(reminders.isEnabled(time)) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(time.label, Modifier.weight(1f))
                                Switch(checked = enabled, onCheckedChange = {
                                    reminders.setEnabled(time, it)
                                    enabled = it
                                })
                            }
                        }
                        Text("Los avisos necesitan el permiso de notificaciones de Android.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { showReminders = false }) { Text("Listo") } })
        }

        if (state.showAddSheet) {
            AddHabitSheet(
                initial = state.editing,
                busy = state.busy,
                onDismiss = vm::hideAddSheet,
                onSave    = { title, icon, color, freq, cat, days ->
                    vm.onSaveNewHabit(title, icon, color, freq, cat, days)
                },
            )
        }
        RecordError(state.error, vm::clearError)
    }
}

// ─────────────────────────────────────────────
//  Habit card  ← MEJORADO: color animado al hacer toggle

//  Stats header
// ─────────────────────────────────────────────

@Composable
private fun HabitsStatsHeader(habits: List<HabitWithStatus>) {
    val done  = habits.count { it.todayEntry != null }
    val total = habits.size
    val pct   = if (total > 0) (done.toFloat() / total * 100).toInt() else 0

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = Color(0xFF6C63FF).copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "$done / $total completados hoy",
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF6C63FF),
                )
            }
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress   = { pct / 100f },
                    modifier   = Modifier.size(52.dp),
                    color      = Color(0xFF6C63FF),
                    trackColor = Color(0xFF6C63FF).copy(alpha = 0.2f),
                    strokeWidth = 5.dp,
                )
                Text(
                    "$pct%",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF6C63FF),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Habit card
// ─────────────────────────────────────────────

@Composable
fun HabitCard(
    status: HabitWithStatus,
    onToggle: () -> Unit,
    onArchive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
) {
    val habit       = status.habit
    val done        = status.todayEntry != null
    val accentColor = parseColor(habit.color)
    var showMenu    by remember { mutableStateOf(false) }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (done) accentColor.copy(alpha = 0.12f)
                             else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(if (done) 0.dp else 1.dp),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = if (done) 0.25f else 0.1f)),
                contentAlignment = Alignment.Center,
            ) { Text(habit.icon, fontSize = 20.sp) }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    habit.title,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    if (status.currentStreak > 0) {
                        Text(
                            "🔥 ${status.currentStreak}d",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF5A623),
                        )
                    }
                    Text(
                        if (habit.isArchived) "Archivado" else "${(status.completionRate7d * 100).toInt()}% esta semana",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }, enabled = enabled) {
                    Icon(Icons.Default.MoreVert, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Editar") }, onClick = { showMenu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Borrar") }, onClick = { showMenu = false; onDelete() })
                    DropdownMenuItem(
                        text        = { Text(if (habit.isArchived) "Recuperar" else "Archivar") },
                        leadingIcon = { Icon(Icons.Default.Archive, null) },
                        onClick     = { showMenu = false; onArchive() },
                    )
                }
            }

            if (!habit.isArchived) IconButton(onClick = onToggle, enabled = enabled) {
                AnimatedContent(targetState = done, label = "toggle") { isDone ->
                    Icon(
                        imageVector        = if (isDone) Icons.Default.CheckCircle
                                             else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint               = if (isDone) accentColor
                                             else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Add Habit Sheet
// ─────────────────────────────────────────────

private val PRESET_ICONS   = listOf("⭐","💧","🏃","📚","🧘","🥗","💊","🎯","🎸","✍️","🛌","🌿","☕","🚶","🧹","🪴","🏋️","🎵")
private val PRESET_COLORS  = listOf(
    "#6C63FF","#2196F3","#4CAF82","#F5A623",
    "#E05252","#E91E8C","#00BCD4","#FF7043",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddHabitSheet(
    initial: Habit?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, HabitFrequency, HabitCategory, List<DayOfWeek>) -> Unit,
) {
    var title     by remember { mutableStateOf(initial?.title ?: "") }
    var icon      by remember { mutableStateOf(initial?.icon ?: "⭐") }
    var color     by remember { mutableStateOf(initial?.color ?: "#6C63FF") }
    var frequency by remember { mutableStateOf(initial?.frequency ?: HabitFrequency.DAILY) }
    var category  by remember { mutableStateOf(initial?.category ?: HabitCategory.GENERAL) }
    val selectedDays = remember { mutableStateListOf<DayOfWeek>().apply { addAll(initial?.targetDays.orEmpty()) } }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (initial == null) "Nuevo hábito" else "Editar hábito", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Nombre del hábito") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )

            // Categoría
            Text("Categoría", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(

                    Triple(HabitCategory.MORNING, "Por la mañana", "☀️"),
                    Triple(HabitCategory.GENERAL, "Durante el día", "🌇"),
                    Triple(HabitCategory.NIGHT, "Al acostarse", "🌙"),

                ).forEach { (cat, label, emoji) ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text("$emoji $label", maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF6C63FF).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFF6C63FF),
                        ),
                    )
                }
            }

            // Icono
            Text("Icono", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PRESET_ICONS) { i ->
                    val selected = i == icon
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(0xFF6C63FF).copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                width  = if (selected) 2.dp else 0.dp,
                                color  = if (selected) Color(0xFF6C63FF) else Color.Transparent,
                                shape  = CircleShape,
                            )
                            .clickable { icon = i },
                        contentAlignment = Alignment.Center,
                    ) { Text(i, fontSize = 20.sp) }
                }
            }

            // Color
            Text("Color", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PRESET_COLORS) { c ->
                    val selected = c == color
                    val cc       = parseColor(c)
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(cc)
                            .then(if (selected)
                                Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                            else Modifier)
                            .clickable { color = c }
                    )
                }
            }

            // Frecuencia
            Text("Frecuencia", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HabitFrequency.entries.forEach { f ->
                    FilterChip(
                        selected = frequency == f,
                        onClick = { frequency = f },
                        label = {
                            Text(
                                text = when (f) {
                                    HabitFrequency.DAILY -> "Todos los días"
                                    HabitFrequency.WEEKLY -> "Semanal"
                                    HabitFrequency.CUSTOM -> "Días específicos"
                                },
                                maxLines = 1,
                            )
                        },
                    )
                }
            }

            // Selector de dias — solo visible en CUSTOM
            if (frequency == HabitFrequency.CUSTOM) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selecciona los días", style = MaterialTheme.typography.labelLarge)
                    val dayLabels = listOf(
                        DayOfWeek.MONDAY    to "Lu",
                        DayOfWeek.TUESDAY   to "Ma",
                        DayOfWeek.WEDNESDAY to "Mi",
                        DayOfWeek.THURSDAY  to "Ju",
                        DayOfWeek.FRIDAY    to "Vi",
                        DayOfWeek.SATURDAY  to "Sa",
                        DayOfWeek.SUNDAY    to "Do",
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        dayLabels.forEach { (day, label) ->
                            val sel = selectedDays.contains(day)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (sel) Color(0xFF6C63FF)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable {
                                        if (sel) selectedDays.remove(day)
                                        else selectedDays.add(day)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (sel) Color.White
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (selectedDays.isEmpty()) {
                        Text(
                            "Selecciona al menos un día",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFE05252),
                        )
                    }
                }
            }

            Button(
                onClick  = {
                    val canSave = title.isNotBlank() &&
                        (frequency != HabitFrequency.CUSTOM || selectedDays.isNotEmpty())
                    if (canSave) onSave(title, icon, color, frequency, category, selectedDays.toList())
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C63FF)),
                enabled  = !busy && title.isNotBlank() &&
                    (frequency != HabitFrequency.CUSTOM || selectedDays.isNotEmpty()),
            ) { Text(if (busy) "Guardando…" else "Guardar hábito", fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") }
        }
    }
}
