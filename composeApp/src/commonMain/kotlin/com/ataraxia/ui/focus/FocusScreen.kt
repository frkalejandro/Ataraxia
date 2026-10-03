package com.ataraxia.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ataraxia.domain.model.FocusPhase
import com.ataraxia.domain.model.FocusTimer
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

class FocusController(initial: FocusTimer) {
    var timer by mutableStateOf(initial)
    var intention by mutableStateOf("")
}

val LocalFocusController = staticCompositionLocalOf<FocusController> { error("Falta el temporizador") }

/** Lives above the tabs, preserving a running session while navigating. */
@Composable
fun rememberFocusController(): FocusController {
    val saver = listSaver<FocusController, Any>(
        save = { listOf(it.timer.focusMinutes, it.timer.phase.name, it.timer.remainingSeconds,
            it.timer.deadlineMillis ?: -1L, it.timer.completedSessions, it.timer.awaitingNext, it.intention) },
        restore = { FocusController(FocusTimer(it[0] as Int, FocusPhase.valueOf(it[1] as String),
            it[2] as Int, (it[3] as Long).takeIf { value -> value >= 0 }, it[4] as Int, it[5] as Boolean))
            .apply { intention = it[6] as String } },
    )
    val controller = rememberSaveable(saver = saver) { FocusController(FocusTimer()) }
    LaunchedEffect(controller) {
        while (true) {
            controller.timer = controller.timer.tick(Clock.System.now().toEpochMilliseconds())
            delay(250)
        }
    }
    return controller
}

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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Tu espacio de concentración", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Una cosa a la vez. Dedica un bloque a lo importante y luego descansa.")
        OutlinedTextField(value = controller.intention, onValueChange = { controller.intention = it },
            label = { Text("¿En qué te vas a concentrar?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 50).forEach { minutes ->
                FilterChip(selected = timer.focusMinutes == minutes, enabled = !running && timer.phase == FocusPhase.FOCUS,
                    onClick = { controller.timer = timer.setMinutes(minutes) }, label = { Text("$minutes min") })
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(label, style = MaterialTheme.typography.titleLarge)
                Text("${(timer.remainingSeconds / 60).toString().padStart(2, '0')}:${(timer.remainingSeconds % 60).toString().padStart(2, '0')}",
                    fontSize = 64.sp, fontWeight = FontWeight.Light)
                LinearProgressIndicator(progress = { 1f - timer.remainingSeconds.toFloat() / timer.durationSeconds }, modifier = Modifier.fillMaxWidth())
                if (timer.awaitingNext) Text(if (timer.phase == FocusPhase.FOCUS)
                    "Descanso terminado. Cuando quieras, comienza otro bloque."
                    else "¡Sesión completada! Es momento de descansar.")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        val now = Clock.System.now().toEpochMilliseconds()
                        controller.timer = if (running) timer.pause(now) else timer.start(now)
                    }) { Text(if (running) "Pausar" else if (timer.remainingSeconds < timer.durationSeconds) "Continuar" else "Comenzar") }
                    OutlinedButton(onClick = { controller.timer = timer.reset() }) { Text("Reiniciar") }
                }
            }
        }
        Text("${timer.completedSessions} sesiones completadas", fontWeight = FontWeight.SemiBold)
        Text("Método Pomodoro: 25 minutos de enfoque, 5 de descanso y una pausa de 15 minutos cada 4 sesiones. Tú decides cuándo iniciar cada bloque.")
        Text("El temporizador continúa al cambiar de sección. Al volver a la app se actualiza el tiempo restante.",
            style = MaterialTheme.typography.bodySmall)
    }
}
