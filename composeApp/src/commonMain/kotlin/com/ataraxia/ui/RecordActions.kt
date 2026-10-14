package com.ataraxia.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate

/** Shared actions for saved records; deletion always identifies the selected record. */
@Composable
fun RecordActions(name: String, enabled: Boolean = true, deleteMessage: String = "Se borrará «$name». Esta acción no se puede deshacer.",
    onEdit: (() -> Unit)? = null, onDelete: () -> Unit) {
    var confirm by remember(name) { mutableStateOf(false) }
    var expanded by remember(name) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, "Opciones de $name", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (onEdit != null) DropdownMenuItem(
                text = { Text("Editar") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                enabled = enabled,
                onClick = { expanded = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text("Borrar", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                enabled = enabled,
                onClick = { expanded = false; confirm = true },
            )
        }
    }
    if (confirm) ConfirmRecordDeletion(name, deleteMessage, enabled,
        onDismiss = { confirm = false }, onConfirm = { confirm = false; onDelete() })
}

@Composable
fun ConfirmRecordDeletion(name: String, message: String, enabled: Boolean = true, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = { if (enabled) onDismiss() }, title = { Text("Borrar $name") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = enabled) { Text("Borrar", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = enabled) { Text("Cancelar") } })
}

@Composable
fun RecordError(message: String?, onDismiss: () -> Unit) {
    if (message != null) AlertDialog(onDismissRequest = onDismiss, title = { Text("No se pudo completar el cambio") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

fun parseRecordDate(text: String): LocalDate? = try { LocalDate.parse(text.trim()) } catch (_: IllegalArgumentException) { null }

@Composable
fun RecordDateField(value: String, onChange: (String) -> Unit, label: String = "Fecha (AAAA-MM-DD)") {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), isError = parseRecordDate(value) == null,
        supportingText = { Text("Ejemplo: 2026-10-06") })
}
