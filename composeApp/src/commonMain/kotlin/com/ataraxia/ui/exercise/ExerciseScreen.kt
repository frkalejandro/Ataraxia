package com.ataraxia.ui.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.ataraxia.domain.model.*
import com.ataraxia.ui.TimerNotificationHint
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

class ExerciseScreen : Screen {
    @Composable
    override fun Content() {
        val vm: ExerciseViewModel = koinScreenModel()
        val state by vm.state.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.error) {
            state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
        }
        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.padding(padding)) {
                val draft = state.routineDraft
                if (draft != null) RoutineEditor(state, draft, vm)
                else ExerciseOverview(state, vm)
            }
        }
        state.exerciseDraft?.let { draft ->
            AlertDialog(
                onDismissRequest = { if (!state.busy) vm.editExercise(null) },
                title = { Text("Ejercicio · ${draft.area.label}") },
                text = {
                    OutlinedTextField(draft.name, { vm.editExercise(draft.copy(name = it)) },
                        label = { Text("Nombre del ejercicio") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
                },
                confirmButton = {
                    TextButton(onClick = vm::saveExercise, enabled = draft.name.isNotBlank() && !state.busy) { Text("Guardar") }
                },
                dismissButton = {
                    TextButton(onClick = { vm.editExercise(null) }, enabled = !state.busy) { Text("Cancelar") }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExerciseOverview(state: ExerciseUiState, vm: ExerciseViewModel) {
    var deleteExercise by remember { mutableStateOf<Exercise?>(null) }
    var deleteRoutine by remember { mutableStateOf<WorkoutRoutine?>(null) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(state.area?.label ?: "Ejercicio", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                if (state.area != null) IconButton(onClick = { vm.selectArea(null) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Todas las zonas")
                }
            })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.loadFailed) item {
                TextButton(onClick = vm::load) { Text("Reintentar carga") }
            }
            state.active?.let { active ->
                item(key = "active") { ActiveWorkoutCard(active, state.busy, vm) }
            }
            if (state.area == null) {
                item { LastWorkoutCard(lastWorkoutDay(state.sessions)) }
                item {
                    Text("Explora por zona", style = MaterialTheme.typography.titleLarge)
                    Text("Entra a una zona y agrega tus propios ejercicios.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(BodyArea.entries.chunked(2)) { areas ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        areas.forEach { area ->
                            OutlinedCard(onClick = { vm.selectArea(area) }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(area.label, fontWeight = FontWeight.SemiBold)
                                    val count = state.exercises.count { it.area == area }
                                    Text("$count ${if (count == 1) "ejercicio" else "ejercicios"}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (areas.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item {
                    Text("Mis rutinas", style = MaterialTheme.typography.titleLarge)
                    Text("Combina zonas y elige series o un tiempo total compartido.")
                    FilledTonalButton(onClick = { vm.editRoutine(RoutineDraft()) },
                        enabled = state.exercises.isNotEmpty() && !state.loading && !state.loadFailed && !state.busy) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Crear rutina")
                    }
                    if (state.exercises.isEmpty()) Text("Agrega un ejercicio en una zona para comenzar.",
                        style = MaterialTheme.typography.bodySmall)
                }
                items(state.routines, key = { "routine_${it.id}" }) { routine ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium)
                            RoutineDetails(routine)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.start(routine) }, enabled = state.active == null && !state.busy) { Text("Comenzar") }
                                TextButton(onClick = { vm.editRoutine(routine.toDraft()) }, enabled = !state.busy) { Text("Configurar") }
                                IconButton(onClick = { deleteRoutine = routine }, enabled = !state.busy) {
                                    Icon(Icons.Default.DeleteOutline, "Eliminar rutina ${routine.name}")
                                }
                            }
                        }
                    }
                }
            } else {
                val area = state.area
                item {
                    Text("Tus ejercicios de ${area.label.lowercase()}", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { vm.editExercise(ExerciseDraft(area = area)) }, enabled = !state.busy && !state.loading && !state.loadFailed) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar ejercicio")
                    }
                }
                val exercises = state.exercises.filter { it.area == area }
                if (exercises.isEmpty()) item { Text("Aún no tienes ejercicios en esta zona. Escribe el nombre del primero.") }
                items(exercises, key = { it.id }) { exercise ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = {
                                    vm.editRoutine(RoutineDraft(name = exercise.name, targets = listOf(ExerciseTarget(exercise))))
                                }, enabled = !state.busy) { Text("Preparar rutina") }
                                IconButton(onClick = { vm.editExercise(ExerciseDraft(exercise.id, area, exercise.name)) }, enabled = !state.busy) {
                                    Icon(Icons.Default.Edit, "Editar ${exercise.name}")
                                }
                                IconButton(onClick = { deleteExercise = exercise }, enabled = !state.busy) {
                                    Icon(Icons.Default.DeleteOutline, "Eliminar ${exercise.name}")
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
    deleteExercise?.let { exercise ->
        ConfirmDialog("Eliminar ${exercise.name}", "Se quitará de esta zona. Las rutinas guardadas y las sesiones realizadas lo conservarán.",
            "Eliminar", { deleteExercise = null }, { vm.deleteExercise(exercise.id); deleteExercise = null })
    }
    deleteRoutine?.let { routine ->
        ConfirmDialog("Eliminar ${routine.name}", "Las sesiones que ya realizaste permanecerán en el historial.",
            "Eliminar", { deleteRoutine = null }, { vm.deleteRoutine(routine.id); deleteRoutine = null })
    }
}

@Composable
private fun RoutineDetails(routine: WorkoutRoutine) {
    if (routine.mode == WorkoutMode.TIMED) {
        Text("${formatDuration(routine.durationSeconds)} en total · Máximas repeticiones", fontWeight = FontWeight.Medium)
    }
    routine.exercises.forEach { target ->
        Text(buildString {
            append("${target.exercise.name} · ${target.exercise.area.label}")
            if (routine.mode == WorkoutMode.SETS) append("\n${target.sets} series × ${target.reps} repeticiones")
        }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LastWorkoutCard(sessions: List<WorkoutSession>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tu último día de ejercicio", style = MaterialTheme.typography.titleMedium)
            if (sessions.isEmpty()) {
                Text("Aún no hay sesiones realizadas. Aquí verás los ejercicios del último día que entrenaste.")
            } else {
                val date = LocalDate.parse(sessions.first().date)
                Text("${date.dayOfMonth}/${date.monthNumber}/${date.year} · ${sessions.size} ${if (sessions.size == 1) "sesión" else "sesiones"}")
                sessions.forEach { session ->
                    HorizontalDivider()
                    Text(session.routine.name, fontWeight = FontWeight.SemiBold)
                    RoutineDetails(session.routine)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveWorkoutCard(active: ActiveWorkout, busy: Boolean, vm: ExerciseViewModel) {
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    var confirmComplete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        do {
            now = Clock.System.now().toEpochMilliseconds()
            delay(250)
        } while (active.deadlineMillis != null && active.remainingAt(now) > 0)
    }
    val remaining = ((active.remainingAt(now) + 999) / 1000).toInt()
    val timed = active.routine.mode == WorkoutMode.TIMED
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sesión en curso", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(active.routine.name, style = MaterialTheme.typography.titleLarge)
            RoutineDetails(active.routine)
            if (timed) {
                Text(formatDuration(remaining), style = MaterialTheme.typography.displayMedium)
                LinearProgressIndicator(progress = { 1f - remaining.toFloat() / active.routine.durationSeconds }, modifier = Modifier.fillMaxWidth())
                Text(if (remaining == 0) "Tiempo terminado. Confirma si realizaste la sesión."
                    else "Alterna estos ejercicios durante el tiempo total. Haz las repeticiones que puedas.")
                if (remaining > 0) OutlinedButton(onClick = vm::pauseOrResume, enabled = !busy) {
                    Text(if (active.deadlineMillis == null) "Continuar" else "Pausar")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { confirmComplete = true }, enabled = !busy && (!timed || remaining == 0)) { Text("Marcar como realizada") }
                TextButton(onClick = { confirmDiscard = true }, enabled = !busy) { Text("Descartar sesión") }
            }
            Text("La sesión se conserva al salir de la app. El contador se actualiza al volver.",
                style = MaterialTheme.typography.bodySmall)
            if (timed) TimerNotificationHint()
        }
    }
    if (confirmComplete) ConfirmDialog("Guardar sesión realizada", "Confirma que realizaste los ejercicios indicados. Se agregarán a tu último día de ejercicio.",
        "Guardar sesión", { confirmComplete = false }, { vm.complete(); confirmComplete = false })
    if (confirmDiscard) ConfirmDialog("Descartar sesión", "Esta sesión no se registrará como realizada.",
        "Descartar", { confirmDiscard = false }, { vm.discard(); confirmDiscard = false })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RoutineEditor(state: ExerciseUiState, draft: RoutineDraft, vm: ExerciseViewModel) {
    var area by remember { mutableStateOf<BodyArea?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val valid = draft.build() != null
    Scaffold(topBar = {
        TopAppBar(title = { Text("Configurar rutina") }, navigationIcon = {
            IconButton(onClick = { confirmDiscard = true }, enabled = !state.busy) { Icon(Icons.Default.Close, "Cerrar configuración") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                OutlinedTextField(draft.name, { vm.editRoutine(draft.copy(name = it)) }, label = { Text("Nombre de la rutina") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !state.busy)
            }
            item {
                Text("¿Cómo quieres entrenar?", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(draft.mode == WorkoutMode.SETS, { vm.editRoutine(draft.copy(mode = WorkoutMode.SETS)) },
                        label = { Text("Series y repeticiones") }, enabled = !state.busy)
                    FilterChip(draft.mode == WorkoutMode.TIMED, { vm.editRoutine(draft.copy(mode = WorkoutMode.TIMED)) },
                        label = { Text("Por tiempo") }, enabled = !state.busy)
                }
                Text("Puedes cambiar esta configuración antes de cada sesión.", style = MaterialTheme.typography.bodySmall)
            }
            if (draft.mode == WorkoutMode.TIMED) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tiempo total para todos los ejercicios", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(draft.minutes, { vm.editRoutine(draft.copy(minutes = it)) }, "Minutos", Modifier.weight(1f), !state.busy,
                            draft.minutes.toIntOrNull()?.let { it in 0..1440 } != true)
                        NumberField(draft.seconds, { vm.editRoutine(draft.copy(seconds = it)) }, "Segundos", Modifier.weight(1f), !state.busy,
                            draft.seconds.toIntOrNull()?.let { it in 0..59 } != true)
                    }
                    Text("Máximas repeticiones: por ejemplo, sentadillas, flexiones y abdominales dentro de los mismos 5 minutos. Duración: de 1 segundo a 24 horas.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            item { Text("Ejercicios de la rutina (${draft.targets.size})", style = MaterialTheme.typography.titleMedium) }
            if (draft.targets.isEmpty()) item { Text("Selecciona abajo uno o más ejercicios de cualquier zona.") }
            items(draft.targets, key = { it.exercise.id }) { target ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(target.exercise.name, fontWeight = FontWeight.SemiBold)
                                Text(target.exercise.area.label, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.editRoutine(draft.copy(targets = draft.targets.filterNot { it.exercise.id == target.exercise.id })) },
                                enabled = !state.busy) { Icon(Icons.Default.Close, "Quitar ${target.exercise.name}") }
                        }
                        if (draft.mode == WorkoutMode.SETS) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            NumberField(target.sets, { value ->
                                vm.editRoutine(draft.copy(targets = draft.targets.map { if (it.exercise.id == target.exercise.id) it.copy(sets = value) else it }))
                            }, "Series (sets)", Modifier.weight(1f), !state.busy, target.sets.toIntOrNull()?.let { it in 1..999 } != true)
                            NumberField(target.reps, { value ->
                                vm.editRoutine(draft.copy(targets = draft.targets.map { if (it.exercise.id == target.exercise.id) it.copy(reps = value) else it }))
                            }, "Repeticiones", Modifier.weight(1f), !state.busy, target.reps.toIntOrNull()?.let { it in 1..9999 } != true)
                        }
                    }
                }
            }
            item {
                Text("Agregar desde tus zonas", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(area == null, { area = null }, label = { Text("Todas") })
                    BodyArea.entries.forEach { zone -> FilterChip(area == zone, { area = zone }, label = { Text(zone.label) }) }
                }
            }
            val available = state.exercises.filter { exercise ->
                (area == null || exercise.area == area) && draft.targets.none { it.exercise.id == exercise.id }
            }
            if (available.isEmpty()) item { Text("No hay más ejercicios disponibles en esta selección.", style = MaterialTheme.typography.bodySmall) }
            items(available, key = { "add_${it.id}" }) { exercise ->
                OutlinedButton(onClick = { vm.editRoutine(draft.copy(targets = draft.targets + ExerciseTarget(exercise))) },
                    enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("${exercise.name} · ${exercise.area.label}")
                }
            }
            item {
                Button(onClick = vm::saveRoutine, enabled = valid && !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Guardar rutina") }
                if (!valid) Text("Agrega un nombre, al menos un ejercicio y valores válidos. Las series y repeticiones deben ser enteros mayores que cero.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (confirmDiscard) ConfirmDialog("Cerrar configuración", "Se descartarán los cambios que no hayas guardado.",
        "Descartar cambios", { confirmDiscard = false }, { vm.editRoutine(null); confirmDiscard = false })
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, enabled: Boolean, error: Boolean) {
    OutlinedTextField(value, onChange, label = { Text(label) }, modifier = modifier, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = enabled, isError = error)
}

@Composable
private fun ConfirmDialog(title: String, text: String, action: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm) { Text(action) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancelar") } })
}

private fun formatDuration(seconds: Int) = "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
