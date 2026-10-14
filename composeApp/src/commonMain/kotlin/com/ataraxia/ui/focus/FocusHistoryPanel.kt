package com.ataraxia.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ataraxia.domain.model.FocusRecord
import com.ataraxia.domain.repository.FocusRepository
import com.ataraxia.ui.*
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import org.koin.compose.koinInject

@Composable
fun FocusHistoryPanel() {
    val repo: FocusRepository = koinInject()
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<FocusRecord>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var history by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FocusRecord?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(repo, reload) {
        loading = true
        try { repo.observeBlocks().collect { entries = it; loading = false } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { loading = false; error = "No se pudo cargar el historial de concentración." }
    }
    fun mutate(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "No se pudo guardar el cambio. Vuelve a intentarlo." }
            finally { busy = false }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Historial de concentración", style = MaterialTheme.typography.titleMedium)
            Text("Consulta, corrige o borra tus sesiones guardadas, completas o parciales. También puedes registrar una manualmente.")
            TextButton(onClick = { history = true }, enabled = !loading) { Text("Ver historial (${entries.size})") }
            OutlinedButton(onClick = { editing = null; editor = true }, enabled = !busy && !loading) { Text("Añadir registro") }
        }
    }
    if (history) AlertDialog(onDismissRequest = { if (!busy) history = false }, title = { Text("Concentración") },
        text = {
            if (entries.isEmpty()) Text("No hay bloques registrados.")
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(entries, key = { it.id }) { record ->
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(record.date.toString(), style = MaterialTheme.typography.titleSmall)
                                Text("${record.durationSeconds / 60} min ${record.durationSeconds % 60} s")
                            }
                            RecordActions("el bloque del ${record.date}", enabled = !busy,
                            onEdit = { editing = record; editor = true; history = false },
                            onDelete = { mutate { repo.deleteBlock(record.id) } })
                        }
                        HorizontalDivider()
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { history = false }, enabled = !busy) { Text("Cerrar") } })
    if (editor) FocusRecordDialog(editing, busy, onDismiss = { editor = false }, onSave = { date, seconds ->
        val existing = editing
        val timestamp = if (existing?.date == date) existing.completedAt else date.atTime(12, 0)
            .toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val record = FocusRecord(existing?.id ?: uuid4().toString(), date, seconds, timestamp)
        mutate {
            if (existing == null) repo.addBlock(record) else repo.updateBlock(record)
            editor = false
        }
    })
    RecordError(error) { error = null; reload++ }
}

@Composable
private fun FocusRecordDialog(initial: FocusRecord?, busy: Boolean, onDismiss: () -> Unit, onSave: (LocalDate, Long) -> Unit) {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    var dateText by remember { mutableStateOf((initial?.date ?: today).toString()) }
    var minutes by remember { mutableStateOf(((initial?.durationSeconds ?: 1500) / 60).toString()) }
    var seconds by remember { mutableStateOf(((initial?.durationSeconds ?: 1500) % 60).toString()) }
    val date = parseRecordDate(dateText)
    val min = minutes.toLongOrNull()
    val sec = seconds.toLongOrNull()
    val total = if (min != null && min in 0..1440 && sec != null && sec in 0..59) min * 60 + sec else 0
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (initial == null) "Añadir bloque" else "Editar bloque") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecordDateField(dateText, { dateText = it })
                OutlinedTextField(minutes, { minutes = it }, label = { Text("Minutos") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(seconds, { seconds = it }, label = { Text("Segundos (0–59)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Text("Duración: de 1 segundo a 24 horas. La fecha no puede ser futura.")
            }
        }, confirmButton = { TextButton(enabled = !busy && date != null && date <= today && total in 1..86400,
            onClick = { date?.let { onSave(it, total) } }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } })
}
