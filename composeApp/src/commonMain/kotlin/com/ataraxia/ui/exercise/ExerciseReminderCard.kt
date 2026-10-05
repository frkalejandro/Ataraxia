package com.ataraxia.ui.exercise

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ataraxia.notifications.ExerciseReminderSettings
import com.ataraxia.notifications.TimerNotificationScheduler
import com.ataraxia.ui.WellnessSection
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalTime
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseReminderCard() {
    val settings: ExerciseReminderSettings = koinInject()
    val alarms: TimerNotificationScheduler = koinInject()
    var config by remember { mutableStateOf(settings.load()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var exactAllowed by remember { mutableStateOf(alarms.exactAlarmsAllowed) }
    var error by remember { mutableStateOf<String?>(null) }
    val accent = Color(0xFF9C6ADE)
    fun save(enabled: Boolean = config.enabled, time: LocalTime = config.time) {
        try {
            val updated = config.copy(enabled = enabled, time = time)
            settings.save(updated)
            config = updated
            error = null
        } catch (_: Exception) {
            error = "No se pudo guardar el recordatorio. Vuelve a intentarlo."
        }
    }
    LaunchedEffect(config.enabled) {
        if (settings.supported && config.enabled) while (true) {
            exactAllowed = alarms.exactAlarmsAllowed
            delay(1000)
        }
    }
    WellnessSection("Tu momento de moverte", "Un recordatorio diario a tu hora",
        Icons.Default.NotificationsActive, accent) {
        if (settings.supported) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (config.enabled) "Recordatorio activado" else "Recordatorio desactivado",
                        fontWeight = FontWeight.Medium)
                    Text("Todos los días · ${config.time.hour.toString().padStart(2, '0')}:${config.time.minute.toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(config.enabled, { save(enabled = it) },
                    modifier = Modifier.semantics { contentDescription = "Activar recordatorio de ejercicio" },
                    colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = Color.White))
            }
            OutlinedButton(onClick = { editing = true }) { Text("Cambiar hora") }
            if (config.enabled) {
                Text("Recibirás un aviso para hacer ejercicio. Permite las notificaciones en los ajustes de Android.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!exactAllowed) {
                    Text("Android puede retrasar el aviso si no permites alarmas exactas.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = alarms::requestExactAlarmPermission) { Text("Permitir alarmas exactas") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        } else {
            Text("Los recordatorios de ejercicio están disponibles en Android.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (editing) {
        val time = rememberTimePickerState(config.time.hour, config.time.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { editing = false }, title = { Text("Hora de ejercicio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Elige a qué hora quieres recibir el aviso diario.")
                    TimeInput(state = time)
                }
            },
            confirmButton = { TextButton(onClick = { save(time = LocalTime(time.hour, time.minute)); editing = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } })
    }
}
