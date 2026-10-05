package com.ataraxia.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ataraxia.domain.model.FocusPhase
import com.ataraxia.domain.model.FocusTimer
import com.ataraxia.domain.repository.FocusRepository
import com.ataraxia.notifications.*
import org.koin.compose.koinInject
import com.ataraxia.ui.TimerNotificationHint
import com.ataraxia.ui.WellnessSection
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

class FocusController(
    initial: FocusTimer,
    private val notifications: TimerNotificationScheduler,
    private val onFocusCompleted: (FocusTimer) -> Unit = {},
) {
    private var currentTimer by mutableStateOf(initial)
    var timer: FocusTimer
        get() = currentTimer
        set(value) {
            val previous = currentTimer
            if (value.completedSessions > previous.completedSessions) onFocusCompleted(previous)
            currentTimer = value
            // Running ticks only change the display; persist deadlines and user actions.
            if (previous.deadlineMillis != value.deadlineMillis || value.deadlineMillis == null) {
                if (previous != value) {
                    notifications.saveFocus(value)
                    notifications.update(TimerKind.FOCUS, value.notification())
                }
            }
        }
    var intention by mutableStateOf("")
}

val LocalFocusController = staticCompositionLocalOf<FocusController> { error("Falta el temporizador") }

/** Lives above the tabs, preserving a running session while navigating. */
@Composable
fun rememberFocusController(): FocusController {
    val notifications: TimerNotificationScheduler = koinInject()
    val focusRepository: FocusRepository = koinInject()
    val recordCompletion: (FocusTimer) -> Unit = { timer ->
        focusRepository.recordElapsedBlock(timer, Clock.System.now().toEpochMilliseconds())
    }
    val saver = listSaver<FocusController, Any>(
        save = { listOf(it.timer.focusMinutes, it.timer.phase.name, it.timer.remainingSeconds,
            it.timer.deadlineMillis ?: -1L, it.timer.completedSessions, it.timer.awaitingNext, it.intention) },
        restore = { FocusController(FocusTimer(it[0] as Int, FocusPhase.valueOf(it[1] as String),
            it[2] as Int, (it[3] as Long).takeIf { value -> value >= 0 }, it[4] as Int, it[5] as Boolean), notifications, recordCompletion)
            .apply { intention = it[6] as String } },
    )
    val controller = rememberSaveable(saver = saver) {
        FocusController(notifications.restoreFocus() ?: FocusTimer(), notifications, recordCompletion)
    }
    LaunchedEffect(controller) {
        notifications.update(TimerKind.FOCUS, controller.timer.notification())
        while (true) {
            notifications.checkDue()
            controller.timer = controller.timer.tick(Clock.System.now().toEpochMilliseconds())
            delay(250)
        }
    }
    return controller
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen() {
    val controller = LocalFocusController.current
    val timer = controller.timer
    val running = timer.deadlineMillis != null
    val label = when (timer.phase) {
        FocusPhase.FOCUS -> "Concentración"
        FocusPhase.SHORT_BREAK -> "Descanso corto"
        FocusPhase.LONG_BREAK -> "Descanso largo"
    }
    val purple = Color(0xFF6C63FF)
    val green = Color(0xFF40B88E)
    val accent = when (timer.phase) {
        FocusPhase.FOCUS -> purple
        FocusPhase.SHORT_BREAK -> green
        FocusPhase.LONG_BREAK -> Color(0xFF4D8EDB)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Concentración", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Una cosa a la vez. Encuentra tu ritmo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        WellnessSection("Dale una intención", "Haz espacio para lo importante",
            Icons.Default.AutoAwesome, purple) {
            OutlinedTextField(value = controller.intention, onValueChange = { controller.intention = it },
                label = { Text("¿En qué te vas a concentrar?") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 25, 50).forEach { minutes ->
                    FilterChip(selected = timer.focusMinutes == minutes, enabled = !running && timer.phase == FocusPhase.FOCUS,
                        onClick = { controller.timer = timer.setMinutes(minutes) }, label = { Text("$minutes min") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = purple.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface),
                        leadingIcon = if (timer.focusMinutes == minutes) {
                            { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                        } else null)
                }
            }
        }
        WellnessSection(label, if (running) "Este momento es para ti" else "Comienza cuando estés listo",
            if (timer.phase == FocusPhase.FOCUS) Icons.Default.Timer else Icons.Default.Spa, accent) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(224.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(196.dp).background(accent.copy(alpha = 0.06f), CircleShape))
                CircularProgressIndicator(
                    progress = { (1f - timer.remainingSeconds.toFloat() / timer.durationSeconds).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(), color = accent,
                    trackColor = accent.copy(alpha = 0.14f), strokeWidth = 8.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${(timer.remainingSeconds / 60).toString().padStart(2, '0')}:${(timer.remainingSeconds % 60).toString().padStart(2, '0')}",
                        fontSize = 48.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(if (running) "EN CURSO" else if (timer.awaitingNext) "BLOQUE LISTO" else "A TU RITMO",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (timer.awaitingNext) Text(if (timer.phase == FocusPhase.FOCUS)
                "Descanso terminado. Cuando quieras, comienza otro bloque."
                else "¡Sesión completada! Es momento de descansar.")
            FlowRow(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val now = Clock.System.now().toEpochMilliseconds()
                    controller.timer = if (running) timer.pause(now) else timer.start(now)
                }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(if (running) Icons.Default.Pause else Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (running) "Pausar" else if (timer.remainingSeconds < timer.durationSeconds) "Continuar" else "Comenzar")
                }
                OutlinedButton(onClick = { controller.timer = timer.reset() }, shape = RoundedCornerShape(14.dp)) {
                    Text("Reiniciar")
                }
            }
        }
        WellnessSection("Cada bloque cuenta", "${timer.completedSessions} sesiones completadas",
            Icons.Default.CheckCircle, green) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val completedInCycle = timer.completedSessions % 4
                repeat(4) { index ->
                    val done = index < completedInCycle || (timer.completedSessions > 0 && completedInCycle == 0 && timer.phase == FocusPhase.LONG_BREAK)
                    Box(Modifier.size(36.dp).background(if (done) green.copy(alpha = 0.2f) else green.copy(alpha = 0.06f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        if (done) Icon(Icons.Default.Check, null, tint = green, modifier = Modifier.size(20.dp))
                        else Text("${index + 1}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text("Tras cada bloque, descansa 5 minutos. Cada 4 sesiones, disfruta una pausa de 15 minutos.",
                style = MaterialTheme.typography.bodyMedium)
            Text("Tú decides cuándo iniciar cada bloque. El temporizador continúa al cambiar de sección.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TimerNotificationHint()
        }
    }
}
