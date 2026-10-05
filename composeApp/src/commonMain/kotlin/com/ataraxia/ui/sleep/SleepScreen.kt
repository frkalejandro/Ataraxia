@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ataraxia.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.ataraxia.domain.model.*
import com.ataraxia.domain.usecase.*
import com.ataraxia.notifications.SleepNotificationScheduler
import com.ataraxia.notifications.SleepReminder
import com.ataraxia.ui.dashboard.formatTime
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*

// ─────────────────────────────────────────────
//  Lógica de ciclos circadianos
//  Un ciclo = 90 minutos
//  Opciones: 2, 3, 4, 5 ciclos (3h, 4.5h, 6h, 7.5h)
// ─────────────────────────────────────────────

private const val CYCLE_MINUTES = 90

data class SleepCycleOption(
    val cycles: Int,           // cantidad de ciclos
    val totalMinutes: Int,     // cycles * 90
    val sleepAtMinutes: Int,   // hora de dormirse (minutos desde medianoche)
    val bedAtMinutes: Int,     // hora de acostarse = sleepAt - fallAsleepOffset
)

/**
 * Calcula las opciones de hora de acostarse dado:
 * @param wakeHour / wakeMinute  — hora de despertar deseada
 * @param fallAsleepOffsetMin    — cuántos minutos tardas en dormirte (default 30)
 * Devuelve opciones de 2 a 6 ciclos (3h a 9h), de mayor a menor ciclos
 */
fun calcSleepOptions(
    wakeHour: Int,
    wakeMinute: Int,
    fallAsleepOffsetMin: Int,
): List<SleepCycleOption> {
    val wakeTotal = wakeHour * 60 + wakeMinute
    return (6 downTo 2).map { cycles ->
        val totalMin    = cycles * CYCLE_MINUTES
        // hora de dormirse = hora despertar - duración sueño (hacia atrás, en módulo 24h)
        val sleepAt     = ((wakeTotal - totalMin) % (24 * 60) + 24 * 60) % (24 * 60)
        val bedAt       = ((sleepAt - fallAsleepOffsetMin) % (24 * 60) + 24 * 60) % (24 * 60)
        SleepCycleOption(
            cycles           = cycles,
            totalMinutes     = totalMin,
            sleepAtMinutes   = sleepAt,
            bedAtMinutes     = bedAt,
        )
    }
}

fun minutesToHM(totalMin: Int): Pair<Int, Int> = totalMin / 60 to totalMin % 60

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

data class SleepUiState(
    val entries: List<SleepEntry>     = emptyList(),
    val goal: SleepGoal?              = null,
    val stats: SleepStats?            = null,
    val isLoading: Boolean            = true,
    val showLogSheet: Boolean         = false,
    val showCycleSheet: Boolean       = false,
)

class SleepViewModel(
    private val observeEntries: ObserveSleepEntriesUseCase,
    private val observeGoal: ObserveSleepGoalUseCase,
    private val saveEntry: SaveSleepEntryUseCase,
    private val saveGoal: SaveSleepGoalUseCase,
    private val getStats: GetSleepStatsUseCase,
    private val notificationScheduler: SleepNotificationScheduler,
) : ScreenModel {

    private val _state = MutableStateFlow(SleepUiState())
    val state: StateFlow<SleepUiState> = _state.asStateFlow()

    private var schedulerInitialized = false
    private var scheduledBedtime: LocalTime? = null

    init {
        screenModelScope.launch {
            combine(observeEntries(30), observeGoal()) { entries, goal -> entries to goal }
                .collect { (entries, goal) ->
                    val stats = getStats(7)
                    _state.update {
                        it.copy(
                            entries = entries,
                            goal = goal,
                            stats = stats,
                            isLoading = false,
                        )
                    }

                    val activeBedtime = goal
                        ?.takeIf { it.isActive }
                        ?.bedtime

                    if (!schedulerInitialized || activeBedtime != scheduledBedtime) {
                        if (activeBedtime != null) {
                            notificationScheduler.scheduleBedtimeReminders(
                                bedtime = activeBedtime,
                                requestExactAlarmPermission = false,
                            )
                        } else {
                            notificationScheduler.cancelBedtimeReminders()
                        }

                        schedulerInitialized = true
                        scheduledBedtime = activeBedtime
                    }
                }
        }
    }

    fun onLogSleep(
        date: LocalDate,
        bedtimeHour: Int, bedtimeMin: Int,
        wakeHour: Int, wakeMin: Int,
        quality: Int,
        note: String,
    ) {
        screenModelScope.launch {
            val tz       = TimeZone.currentSystemDefault()
            val bedTotalMin  = bedtimeHour * 60 + bedtimeMin
            val wakeTotalMin = wakeHour * 60 + wakeMin
            // Si bedtime >= waketime el usuario se durmio antes de medianoche
            // → bedtime es del dia anterior al despertar.
            // Si bedtime < waketime ambos son del mismo dia (siesta o registro atipico).
            val bedDate  = if (bedTotalMin >= wakeTotalMin)
                date.minus(1, DateTimeUnit.DAY)
            else
                date
            val bedtime  = LocalDateTime(bedDate, LocalTime(bedtimeHour, bedtimeMin)).toInstant(tz)
            val wake     = LocalDateTime(date, LocalTime(wakeHour, wakeMin)).toInstant(tz)
            // Duracion calculada aritmeticamente para evitar problemas de DST
            val duration = if (bedTotalMin >= wakeTotalMin)
                (24 * 60 - bedTotalMin + wakeTotalMin)
            else
                (wakeTotalMin - bedTotalMin)
            saveEntry(
                SleepEntry(
                    id              = uuid4().toString(),
                    date            = date,
                    bedtime         = bedtime,
                    wakeTime        = wake,
                    durationMinutes = duration,
                    quality         = quality,
                    note            = note,
                    createdAt       = Clock.System.now(),
                )
            )
            _state.update { it.copy(showLogSheet = false) }
        }
    }

    fun onSaveCycleGoal(wakeHour: Int, wakeMin: Int, cycles: Int, fallAsleepOffset: Int) {
        screenModelScope.launch {
            val options     = calcSleepOptions(wakeHour, wakeMin, fallAsleepOffset)
            val chosen      = options.firstOrNull { it.cycles == cycles } ?: options[1]
            val (bH, bM)    = minutesToHM(chosen.bedAtMinutes)
            val (wH, wM)    = wakeHour to wakeMin
            val targetHours = chosen.totalMinutes / 60f
            val bedtime = LocalTime(bH, bM)

            saveGoal(
                SleepGoal(
                    id          = _state.value.goal?.id ?: uuid4().toString(),
                    bedtime     = bedtime,
                    wakeTime    = LocalTime(wH, wM),
                    targetHours = targetHours,
                    updatedAt   = Clock.System.now(),
                )
            )

            notificationScheduler.scheduleBedtimeReminders(
                bedtime = bedtime,
                requestExactAlarmPermission = true,
            )
            schedulerInitialized = true
            scheduledBedtime = bedtime

            _state.update { it.copy(showCycleSheet = false) }
        }
    }

    fun showLogSheet()    { _state.update { it.copy(showLogSheet    = true) } }
    fun hideLogSheet()    { _state.update { it.copy(showLogSheet    = false) } }
    fun showCycleSheet()  { _state.update { it.copy(showCycleSheet  = true) } }
    fun hideCycleSheet()  { _state.update { it.copy(showCycleSheet  = false) } }
}

// ─────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────

class SleepScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val vm: SleepViewModel = koinScreenModel()
        val state by vm.state.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Sueño", fontWeight = FontWeight.SemiBold) },
                    actions = {
                        // Botón para configurar ciclos
                        IconButton(onClick = vm::showCycleSheet) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = "Configurar ciclos",
                                tint = Color(0xFF2196F3),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick        = vm::showLogSheet,
                    containerColor = Color(0xFF2196F3),
                    contentColor   = Color.White,
                ) { Icon(Icons.Default.Add, contentDescription = "Registrar sueño") }
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { SleepGoalCard(state.goal, onConfigure = vm::showCycleSheet) }
                item { LastMealCard(state.goal) }
                item { SleepStatsCard(state.stats) }
                item {
                    Text(
                        "Historial",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (state.entries.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("🌙", fontSize = 48.sp)
                                Text(
                                    "Sin registros aún",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(state.entries, key = { it.id }) { entry ->
                        SleepEntryCard(entry, goal = state.goal)
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        if (state.showLogSheet) {
            LogSleepSheet(
                goal      = state.goal,
                onDismiss = vm::hideLogSheet,
                onSave    = { d, bh, bm, wh, wm, q, n ->
                    vm.onLogSleep(d, bh, bm, wh, wm, q, n)
                },
            )
        }

        if (state.showCycleSheet) {
            CycleConfigSheet(
                currentGoal = state.goal,
                onDismiss   = vm::hideCycleSheet,
                onSave      = { wH, wM, cycles, offset ->
                    vm.onSaveCycleGoal(wH, wM, cycles, offset)
                },
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Goal Card
// ─────────────────────────────────────────────

@Composable
private fun SleepGoalCard(goal: SleepGoal?, onConfigure: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2196F3).copy(alpha = 0.10f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        if (goal == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConfigure() }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Configurar ciclos de sueño",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2196F3),
                    )
                    Text(
                        "Elige tu hora de despertar y cuántos ciclos quieres dormir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF2196F3),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConfigure() }
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Tu meta de sueño",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF2196F3),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Editar",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF2196F3),
                        )
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                val cyclesCount = (goal.targetHours * 60 / CYCLE_MINUTES).toInt()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 3,
                ) {
                    GoalTimeChip(
                        label = "Te acuestas",
                        time = formatTime(goal.bedtime.hour, goal.bedtime.minute),
                        icon = "🛌",
                        modifier = Modifier.widthIn(min = 84.dp),
                    )
                    GoalTimeChip(
                        label = "Te despiertas",
                        time = formatTime(goal.wakeTime.hour, goal.wakeTime.minute),
                        icon = "⏰",
                        modifier = Modifier.widthIn(min = 84.dp),
                    )
                    GoalTimeChip(
                        label = "Ciclos",
                        time = "$cyclesCount × 1.5h",
                        icon = "🔁",
                        modifier = Modifier.widthIn(min = 84.dp),
                    )
                }

                val totalH = goal.targetHours.toInt()
                val totalM = ((goal.targetHours * 60) % 60).toInt()
                Text(
                    "${totalH}h ${if (totalM > 0) "${totalM}min" else ""} de sueño",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun LastMealCard(goal: SleepGoal?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Hora de última comida",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (goal == null || !goal.isActive) {
                Text(
                    "Configura una meta de sueño activa para calcular la hora límite de tu última comida y tu último buen vaso de agua.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Según tu meta de acostarte a las ${formatTime(goal.bedtime.hour, goal.bedtime.minute)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(SleepReminder.LAST_MEAL, SleepReminder.LAST_WATER).forEach { reminder ->
                    val time = reminder.timeBefore(goal.bedtime)
                    val previousDay = time > goal.bedtime
                    val label = if (reminder == SleepReminder.LAST_MEAL) "Última comida" else reminder.title
                    val lead = if (reminder == SleepReminder.LAST_MEAL) "2 h 30 min" else "2 h"
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "A más tardar a las ${formatTime(time.hour, time.minute)}${if (previousDay) " (día anterior)" else ""}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3),
                        )
                        Text(
                            "$lead antes de acostarte",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalTimeChip(
    label: String,
    time: String,
    icon: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(icon, fontSize = 20.sp)
        Text(
            time,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3),
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────
//  Stats Card
// ─────────────────────────────────────────────

@Composable
private fun SleepStatsCard(stats: SleepStats?) {
    if (stats == null || stats.last7Days.isEmpty()) return
    val avgH = stats.avgDurationMinutes.toInt() / 60
    val avgM = stats.avgDurationMinutes.toInt() % 60
    val avgStr = if (avgM > 0) "${avgH}h ${avgM}min" else "${avgH}h"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 3,
            ) {
                StatPill("Promedio", avgStr, Color(0xFF2196F3), Modifier.widthIn(min = 76.dp))
                StatPill("Calidad", "${stats.avgQuality.toInt()}/5", Color(0xFFF5A623), Modifier.widthIn(min = 76.dp))
                StatPill("Racha", "${stats.longestStreak}d", Color(0xFF4CAF82), Modifier.widthIn(min = 76.dp))
            }

            val maxMin = stats.last7Days.maxOf { it.durationMinutes }.toFloat().coerceAtLeast(1f)
            Text(
                "Últimos 7 días",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                stats.last7Days.forEach { entry ->
                    val frac = entry.durationMinutes / maxMin
                    val barColor = when {
                        entry.durationMinutes >= 480 -> Color(0xFF4CAF82)
                        entry.durationMinutes >= 360 -> Color(0xFFF5A623)
                        else -> Color(0xFFE05252)
                    }
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(frac)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor.copy(alpha = 0.8f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────
//  Sleep Entry Card
// ─────────────────────────────────────────────

@Composable
private fun SleepEntryCard(entry: SleepEntry, goal: SleepGoal?) {
    val target = ((goal?.targetHours ?: 7.5f) * 60).toInt()
    val ok = entry.durationMinutes >= target
    val accent = if (ok) Color(0xFF4CAF82) else Color(0xFFE05252)
    val tz = TimeZone.currentSystemDefault()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp, 64.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    entry.date.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val dH = entry.durationMinutes / 60
                val dM = entry.durationMinutes % 60
                Text(
                    "${dH}h ${dM}min",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                val bed = entry.bedtime.toLocalDateTime(tz).time
                val wake = entry.wakeTime.toLocalDateTime(tz).time
                Text(
                    "${formatTime(bed.hour, bed.minute)} → ${formatTime(wake.hour, wake.minute)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    repeat(5) { i ->
                        Icon(
                            imageVector = if (i < entry.quality) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = Color(0xFFF5A623),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  Cycle Config Sheet — corazón de la pantalla
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleConfigSheet(
    currentGoal: SleepGoal?,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Int) -> Unit,
) {
    var wakeH by remember { mutableStateOf(currentGoal?.wakeTime?.hour ?: 7) }
    var wakeM by remember { mutableStateOf(currentGoal?.wakeTime?.minute ?: 0) }
    var fallAsleepOffset by remember { mutableStateOf(15) }
    val currentCycles = currentGoal?.let {
        ((it.targetHours * 60) / CYCLE_MINUTES).toInt().coerceIn(2, 6)
    } ?: 5
    var selectedCycles by remember { mutableStateOf(currentCycles) }

    val options by remember(wakeH, wakeM, fallAsleepOffset) {
        derivedStateOf { calcSleepOptions(wakeH, wakeM, fallAsleepOffset) }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Ciclos de sueño",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "¿A qué hora quieres despertar?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TimeWheel(
                        label = "Hora",
                        value = wakeH,
                        range = 0..23,
                        display = { formatTime(it, 0).substring(0, 2) },
                        onChange = { wakeH = it },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        ":",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TimeWheel(
                        label = "Minutos",
                        value = wakeM,
                        range = 0..59,
                        step = 5,
                        display = { formatTime(0, it).substring(3, 5) },
                        onChange = { wakeM = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "¿Cuánto tardas en dormirte?",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Esto ajusta la hora a la que te acuestas",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        "${fallAsleepOffset}min",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3),
                        maxLines = 1,
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(15, 30).forEach { mins ->
                        FilterChip(
                            selected = fallAsleepOffset == mins,
                            onClick = { fallAsleepOffset = mins },
                            label = { Text("${mins} min", maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2196F3).copy(alpha = 0.15f),
                                selectedLabelColor = Color(0xFF2196F3),
                            ),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "¿Cuántos ciclos quieres dormir?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Cada ciclo dura 90 minutos. Despertarte al final de un ciclo es más descansado.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        val selected = option.cycles == selectedCycles
                        val (bedH2, bedM2) = minutesToHM(option.bedAtMinutes)
                        val (sleepH, sleepM) = minutesToHM(option.sleepAtMinutes)
                        val totalH = option.totalMinutes / 60
                        val totalM = option.totalMinutes % 60

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCycles = option.cycles },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    Color(0xFF2196F3).copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                            elevation = CardDefaults.cardElevation(if (selected) 0.dp else 1.dp),
                            border = if (selected) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2196F3))
                            } else {
                                null
                            },
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            "${option.cycles} ciclos",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color(0xFF2196F3)
                                                    else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            "${totalH}h${if (totalM > 0) " ${totalM}min" else ""} de sueño",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF2196F3),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CycleTimeLabel("🛌", "Acostarte", formatTime(bedH2, bedM2))
                                    CycleTimeLabel("💤", "Dormirte", formatTime(sleepH, sleepM))
                                    CycleTimeLabel("⏰", "Despertar", formatTime(wakeH, wakeM))
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onSave(wakeH, wakeM, selectedCycles, fallAsleepOffset) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
            ) {
                Text("Guardar meta de sueño", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CycleTimeLabel(icon: String, label: String, time: String) {
    Column(
        modifier = Modifier.widthIn(min = 92.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "$icon $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            time,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

private fun nextSteppedValue(value: Int, range: IntRange, step: Int): Int {
    require(step > 0) { "step debe ser mayor que cero" }

    val first = range.first
    val lastValid = range.last - ((range.last - first) % step)
    val offset = (value - first).coerceAtLeast(0)
    val next = if (offset % step == 0) {
        value + step
    } else {
        first + ((offset / step) + 1) * step
    }

    return if (next > lastValid) first else next
}

private fun previousSteppedValue(value: Int, range: IntRange, step: Int): Int {
    require(step > 0) { "step debe ser mayor que cero" }

    val first = range.first
    val lastValid = range.last - ((range.last - first) % step)
    val offset = (value - first).coerceAtLeast(0)
    val previous = if (offset % step == 0) {
        value - step
    } else {
        first + (offset / step) * step
    }

    return if (previous < first) lastValid else previous
}

// ─────────────────────────────────────────────
//  TimeWheel — selector de hora tipo scroll
// ─────────────────────────────────────────────

@Composable
private fun TimeWheel(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    display: (Int) -> String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier            = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Botón arriba
        IconButton(
            onClick = {
                onChange(nextSteppedValue(value, range, step))
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Default.KeyboardArrowUp, null)
        }

        // Valor central
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2196F3).copy(alpha = 0.12f))
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                display(value),
                style      = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF2196F3),
                ),
            )
        }

        // Botón abajo
        IconButton(
            onClick = {
                onChange(previousSteppedValue(value, range, step))
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(Icons.Default.KeyboardArrowDown, null)
        }

        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────
//  Log Sleep Sheet — registrar una noche
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSleepSheet(
    goal: SleepGoal?,
    onDismiss: () -> Unit,
    onSave: (LocalDate, Int, Int, Int, Int, Int, String) -> Unit,
) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    var bedH by remember { mutableStateOf(goal?.bedtime?.hour ?: 22) }
    var bedM by remember { mutableStateOf(goal?.bedtime?.minute ?: 30) }
    var wakeH by remember { mutableStateOf(goal?.wakeTime?.hour ?: 7) }
    var wakeM by remember { mutableStateOf(goal?.wakeTime?.minute ?: 0) }
    var quality by remember { mutableStateOf(3) }
    var note by remember { mutableStateOf("") }

    val durationMin = remember(bedH, bedM, wakeH, wakeM) {
        val b = bedH * 60 + bedM
        val w = wakeH * 60 + wakeM
        if (w > b) w - b else (24 * 60 - b + w)
    }
    val dH = durationMin / 60
    val dM = durationMin % 60
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            Text(
                "Registrar noche",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            LogTimeSelector(
                title = "Me acosté",
                hour = bedH,
                minute = bedM,
                onHourChange = { bedH = it },
                onMinuteChange = { bedM = it },
            )
            LogTimeSelector(
                title = "Me desperté",
                hour = wakeH,
                minute = wakeM,
                onHourChange = { wakeH = it },
                onMinuteChange = { wakeM = it },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (durationMin >= 360) Color(0xFF4CAF82).copy(alpha = 0.1f)
                        else Color(0xFFE05252).copy(alpha = 0.1f)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Duración",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${dH}h ${dM}min",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (durationMin >= 360) Color(0xFF4CAF82) else Color(0xFFE05252),
                )
            }

            Text("Calidad del sueño", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(5) { i ->
                    IconButton(
                        onClick = { quality = i + 1 },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = if (i < quality) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = Color(0xFFF5A623),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota opcional") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                maxLines = 2,
            )

            Button(
                onClick = { onSave(today, bedH, bedM, wakeH, wakeM, quality, note) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
            ) {
                Text("Guardar", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LogTimeSelector(
    title: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                LogStepper(hour, 0..23, onChange = onHourChange)
                Text(
                    ":",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                LogStepper(minute, 0..59, 5, onMinuteChange)
            }
        }
    }
}

@Composable
private fun LogStepper(value: Int, range: IntRange, step: Int = 1, onChange: (Int) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        IconButton(
            onClick = {
                onChange(nextSteppedValue(value, range, step))
            },
            modifier = Modifier.size(28.dp),
        ) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp)) }

        Text(
            // KMP-safe — sin String.format
            value.toString().padStart(2, '0'),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        IconButton(
            onClick = {
                onChange(previousSteppedValue(value, range, step))
            },
            modifier = Modifier.size(28.dp),
        ) { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp)) }
    }
}
