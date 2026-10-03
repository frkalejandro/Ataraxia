package com.ataraxia.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.ataraxia.domain.model.*
import kotlinx.datetime.*

class DashboardScreen : Screen {
    @Composable
    override fun Content() {
        val vm: DashboardViewModel = koinScreenModel()
        val state by vm.state.collectAsState()

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DayGreeting(state.today)
                    StoicQuoteCard(state.stoicQuote)
                }
            }

            // ── Cinta de agenda ────────────────────
            item { SectionHeader("Hoy") }
            item {
                AgendaStrip(
                    events = state.todayEvents,
                    tasks  = state.todayTasks,
                    onCompleteTask = vm::onCompleteTask,
                )
            }

            // ── Hábitos ────────────────────────────
            item { SectionHeader("Hábitos") }
            item {
                if (state.habits.isEmpty()) {
                    DashboardEmptyState(
                        icon = Icons.Default.AutoAwesome,
                        message = "Añade hábitos desde la pestaña Hábitos.",
                    )
                } else {
                    HabitsRow(state.habits, onToggle = vm::onToggleHabit)
                }
            }

            // ── Sueño ──────────────────────────────
            item { SectionHeader("Sueño") }
            item { SleepCard(state.sleepEntry, state.sleepGoal) }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ── Saludo ─────────────────────────────────────

@Composable
private fun DayGreeting(today: LocalDate) {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val greeting = when {
        hour < 5 -> "A acostarse!"
        hour < 12 -> "Buenos días"
        hour < 19 -> "Buenas tardes"
        else      -> "Buenas noches"
    }
    val days = mapOf(
        DayOfWeek.MONDAY    to "Lunes",    DayOfWeek.TUESDAY  to "Martes",
        DayOfWeek.WEDNESDAY to "Miércoles",DayOfWeek.THURSDAY to "Jueves",
        DayOfWeek.FRIDAY    to "Viernes",  DayOfWeek.SATURDAY to "Sábado",
        DayOfWeek.SUNDAY    to "Domingo",
    )
    val months = listOf("", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text  = greeting,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text  = "${days[today.dayOfWeek]}, ${today.dayOfMonth} de ${months[today.monthNumber]}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StoicQuoteCard(quote: StoicQuote) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Alguna vez una persona muy sabia dijo...",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "“${quote.text}”",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp,
            )
            Text(
                text = "— ${quote.author} · ${quote.work}",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}


// ── Hábitos ────────────────────────────────────

@Composable
fun HabitsRow(habits: List<HabitWithStatus>, onToggle: (String) -> Unit) {
    val orderedHabits = habits.sortedBy { status ->
        when (status.habit.category) {
            HabitCategory.MORNING -> 0
            HabitCategory.GENERAL -> 1
            HabitCategory.NIGHT -> 2
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(orderedHabits, key = { it.habit.id }) { status ->
            HabitChip(status, onClick = { onToggle(status.habit.id) })
        }
    }
}

@Composable
fun HabitChip(status: HabitWithStatus, onClick: () -> Unit) {
    val done = status.todayEntry != null

    // Color base segun categoría — cuando esta completado usa el color del habito
    val categoryColor = when (status.habit.category) {
        HabitCategory.MORNING -> Color(0xFFF5A623) // amarillo mañana
        HabitCategory.NIGHT   -> Color(0xFF1A3A5C) // azul marino noche
        HabitCategory.GENERAL -> Color(0xFF6E6E8A) // gris general
    }
    val habitColor = parseColor(status.habit.color)
    val bgColor = if (done) habitColor else categoryColor
    val textColor = Color.White

    Card(
        onClick   = onClick,
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier  = Modifier.width(104.dp),
    ) {
        Column(
            modifier  = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Indicador de categoría — pequeño punto de color en la esquina superior
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.75f))
                )
                if (done) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint     = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Text(status.habit.icon, fontSize = 20.sp)
            Text(
                text      = status.habit.title,
                style     = MaterialTheme.typography.labelSmall,
                color     = textColor,
                maxLines  = 2,
                textAlign = TextAlign.Center,
            )
            if (status.currentStreak > 1) {
                Text(
                    text  = "🔥 ${status.currentStreak}",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.9f),
                )
            }
        }
    }
}

// ── Sueño ──────────────────────────────────────

@Composable
fun SleepCard(entry: SleepEntry?, goal: SleepGoal?) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            modifier          = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector        = Icons.Default.Bedtime,
                contentDescription = null,
                tint               = Color(0xFF2196F3),
                modifier           = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(16.dp))
            if (entry == null) {
                Column {
                    Text("Sin registro de sueño", style = MaterialTheme.typography.titleSmall)
                    val goalText = if (goal != null) {
                        val wakeStr   = formatTime(goal.wakeTime.hour, goal.wakeTime.minute)
                        val sleepStr  = formatTime(goal.bedtime.hour, goal.bedtime.minute)
                        "Despertar: $wakeStr · Dormir: $sleepStr · ${goal.targetHours}h"
                    } else "Sin meta configurada"
                    Text(
                        goalText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column {
                    val dH = entry.durationMinutes / 60
                    val dM = entry.durationMinutes % 60
                    Text(
                        "${dH}h ${dM}min dormido",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) { i ->
                            Icon(
                                imageVector        = if (i < entry.quality) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint               = Color(0xFFF5A623),
                                modifier           = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Tarea ──────────────────────────────────────

@Composable
fun TaskItem(task: Task, onComplete: () -> Unit) {
    val priorityColor = when (task.priority) {
        TaskPriority.CRITICAL -> Color(0xFFE05252)
        TaskPriority.HIGH     -> Color(0xFFF5A623)
        TaskPriority.MEDIUM   -> Color(0xFF6C63FF)
        TaskPriority.LOW      -> Color(0xFF9E9E9E)
    }
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
            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(4.dp, 36.dp)
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
                task.dueTime?.let {
                    Text(
                        formatTime(it.hour, it.minute),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

// ── Helpers ────────────────────────────────────

@Composable
fun SectionHeader(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun DashboardEmptyState(icon: ImageVector, message: String) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
        )
        Text(
            message,
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
    }
}


// ── Cinta de agenda ────────────────────────────

@Composable
fun AgendaStrip(
    events: List<CalendarEvent>,
    tasks: List<Task>,
    onCompleteTask: (String) -> Unit,
) {
    val pendingTasks = tasks.filter { !it.isCompleted }
    val allDone      = tasks.isNotEmpty() && tasks.all { it.isCompleted }
    val isEmpty      = events.isEmpty() && tasks.isEmpty()

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                isEmpty -> {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("✨", fontSize = 20.sp)
                        Text(
                            "Día libre — sin eventos ni tareas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                allDone -> {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint     = Color(0xFF4CAF82),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "Todo completado por hoy",
                            style  = MaterialTheme.typography.bodyMedium,
                            color  = Color(0xFF4CAF82),
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                else -> {
                    // Eventos del día (máx 2)
                    events.take(2).forEach { event ->
                        val color = parseColor(event.color)
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(4.dp, 28.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    event.title,
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines   = 1,
                                    overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                // ← variables locales para evitar smart cast imposible
                                val st = event.startTime
                                if (st != null) {
                                    Text(
                                        formatTime(st.hour, st.minute),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    // Tareas pendientes (máx 3)
                    pendingTasks.take(3).forEach { task ->
                        val pColor = when (task.priority) {
                            TaskPriority.CRITICAL -> Color(0xFFE05252)
                            TaskPriority.HIGH     -> Color(0xFFF5A623)
                            TaskPriority.MEDIUM   -> Color(0xFF6C63FF)
                            TaskPriority.LOW      -> Color(0xFF9E9E9E)
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(pColor)
                            )
                            Text(
                                task.title,
                                style    = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            Checkbox(
                                checked         = false,
                                onCheckedChange = { onCompleteTask(task.id) },
                                modifier        = Modifier.size(20.dp),
                                colors          = CheckboxDefaults.colors(
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    checkedColor   = Color(0xFF4CAF82),
                                ),
                            )
                        }
                    }
                    // Indicador de cuántas más hay
                    val extraTasks  = (pendingTasks.size - 3).coerceAtLeast(0)
                    val extraEvents = (events.size - 2).coerceAtLeast(0)
                    if (extraTasks > 0 || extraEvents > 0) {
                        val parts = buildList {
                            if (extraEvents > 0) add("$extraEvents evento${if (extraEvents > 1) "s" else ""} más")
                            if (extraTasks  > 0) add("$extraTasks tarea${if (extraTasks  > 1) "s" else ""} más")
                        }
                        Text(
                            parts.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** KMP-safe time formatting — no usa String.format de Java */
fun formatTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

fun parseColor(hex: String): Color = try {
    val c = if (hex.startsWith("#")) hex else "#$hex"
    Color(c.substring(1, 3).toInt(16), c.substring(3, 5).toInt(16), c.substring(5, 7).toInt(16))
} catch (_: Exception) { Color(0xFF6C63FF) }
