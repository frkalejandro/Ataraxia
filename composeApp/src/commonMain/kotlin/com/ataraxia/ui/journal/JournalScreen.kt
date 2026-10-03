package com.ataraxia.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.ataraxia.domain.model.JournalEntry
import com.ataraxia.domain.repository.JournalRepository
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.*

// ─────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────

data class JournalUiState(
    val entries: List<JournalEntry> = emptyList(),
    val isLoading: Boolean = true,
    val editingEntry: JournalEntry? = null,
    val isNewEntry: Boolean = false,
)

class JournalViewModel(
    private val repo: JournalRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(JournalUiState())
    val state: StateFlow<JournalUiState> = _state.asStateFlow()

    init {
        screenModelScope.launch {
            repo.observeAllEntries().collect { entries ->
                _state.update { it.copy(entries = entries, isLoading = false) }
            }
        }
    }

    fun onNewEntry() {
        val now = Clock.System.now()
        _state.update {
            it.copy(
                editingEntry = JournalEntry(
                    id        = uuid4().toString(),
                    content   = "",
                    mood      = null,
                    tags      = emptyList(),
                    createdAt = now,
                    updatedAt = now,
                ),
                isNewEntry = true,
            )
        }
    }

    fun onEditEntry(entry: JournalEntry) {
        _state.update { it.copy(editingEntry = entry, isNewEntry = false) }
    }

    fun onSaveEntry(content: String, mood: String?, tags: List<String>) {
        val editing = _state.value.editingEntry ?: return
        if (content.isBlank()) { onDiscardEntry(); return }
        screenModelScope.launch {
            val now = Clock.System.now()
            repo.saveEntry(
                editing.copy(
                    content   = content.trim(),
                    mood      = mood,
                    tags      = tags,
                    updatedAt = now,
                )
            )
            _state.update { it.copy(editingEntry = null, isNewEntry = false) }
        }
    }

    fun onDeleteEntry(id: String) {
        screenModelScope.launch {
            repo.deleteEntry(id)
            _state.update { it.copy(editingEntry = null) }
        }
    }

    fun onDiscardEntry() {
        _state.update { it.copy(editingEntry = null, isNewEntry = false) }
    }
}

// ─────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────

class JournalScreen : Screen {
    @Composable
    override fun Content() {
        val vm: JournalViewModel = koinScreenModel()
        val state by vm.state.collectAsState()

        val editing = state.editingEntry
        if (editing != null) {
            EntryEditorScreen(
                entry     = editing,
                isNew     = state.isNewEntry,
                onSave    = vm::onSaveEntry,
                onDelete  = { vm.onDeleteEntry(editing.id) },
                onDiscard = vm::onDiscardEntry,
            )
        } else {
            EntryListScreen(
                entries   = state.entries,
                isLoading = state.isLoading,
                onNewEntry  = vm::onNewEntry,
                onEditEntry = vm::onEditEntry,
            )
        }
    }
}

// ─────────────────────────────────────────────
//  Lista de entradas
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryListScreen(
    entries: List<JournalEntry>,
    isLoading: Boolean,
    onNewEntry: () -> Unit,
    onEditEntry: (JournalEntry) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Diario", fontWeight = FontWeight.SemiBold) })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = onNewEntry,
                containerColor = Color(0xFF6C63FF),
                contentColor   = Color.White,
            ) { Icon(Icons.Default.Edit, contentDescription = "Nueva entrada") }
        },
    ) { padding ->
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            entries.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(40.dp),
                    ) {
                        Text("📓", fontSize = 56.sp)
                        Text(
                            "Tu diario está vacío",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Este es tu espacio. Escribe lo que quieras — pensamientos, ideas, lo que sea.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            else -> {
                val grouped = entries.groupBy {
                    it.createdAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
                }
                LazyColumn(
                    modifier        = Modifier.padding(padding),
                    contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    grouped.forEach { (date, dayEntries) ->
                        item(key = "header_$date") { DateHeader(date) }
                        items(dayEntries, key = { it.id }) { entry ->
                            EntryCard(entry = entry, onClick = { onEditEntry(entry) })
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val today     = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    val months    = listOf("", "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    val label = when (date) {
        today     -> "Hoy"
        yesterday -> "Ayer"
        else      -> "${date.dayOfMonth} ${months[date.monthNumber]}, ${date.year}"
    }
    Text(
        text     = label,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun EntryCard(entry: JournalEntry, onClick: () -> Unit) {
    val dt      = entry.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
    // KMP-safe time formatting — sin String.format de Java
    val h       = dt.hour.toString().padStart(2, '0')
    val m       = dt.minute.toString().padStart(2, '0')
    val timeStr = "$h:$m"
    val mood    = entry.mood

    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    if (mood != null) {
                        Text(mood, fontSize = 18.sp)
                    }
                    Text(
                        timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text     = entry.content,
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                color    = MaterialTheme.colorScheme.onSurface,
            )
            if (entry.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.tags.take(3).forEach { tag -> TagPill(tag) }
                }
            }
        }
    }
}

@Composable
private fun TagPill(tag: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF6C63FF).copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text  = tag,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF6C63FF),
        )
    }
}

// ─────────────────────────────────────────────
//  Editor de entrada
// ─────────────────────────────────────────────

private val MOOD_OPTIONS = listOf("😌", "😊", "😐", "😔", "😤", "😴", "🤔", "✨", "🔥", "💭")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryEditorScreen(
    entry: JournalEntry,
    isNew: Boolean,
    onSave: (String, String?, List<String>) -> Unit,
    onDelete: () -> Unit,
    onDiscard: () -> Unit,
) {
    var content      by remember { mutableStateOf(entry.content) }
    var selectedMood by remember { mutableStateOf(entry.mood) }
    var tagInput     by remember { mutableStateOf("") }
    // mutableStateListOf para que Compose detecte cambios en la lista
    val tags = remember { mutableStateListOf(*entry.tags.toTypedArray()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isNew) "Nueva entrada" else "Editar",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDiscard) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Eliminar",
                                tint = Color(0xFFE05252),
                            )
                        }
                    }
                    TextButton(onClick = { onSave(content, selectedMood, tags.toList()) }) {
                        Text("Guardar", fontWeight = FontWeight.SemiBold, color = Color(0xFF6C63FF))
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier        = Modifier.padding(padding),
            contentPadding  = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                OutlinedTextField(
                    value         = content,
                    onValueChange = { content = it },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 200.dp),
                    placeholder   = {
                        Text(
                            "¿Que tienes en mente?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    },
                    shape  = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Color(0xFF6C63FF),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    ),
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "¿Como te sientes? (opcional)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier              = Modifier.fillMaxWidth(),
                    ) {
                        MOOD_OPTIONS.forEach { mood ->
                            val selected = selectedMood == mood
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) Color(0xFF6C63FF).copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .clickable {
                                        selectedMood = if (selected) null else mood
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(mood, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Etiquetas (opcional)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value         = tagInput,
                            onValueChange = { tagInput = it },
                            modifier      = Modifier.weight(1f),
                            placeholder   = { Text("idea, reflexión, sueño...") },
                            shape         = RoundedCornerShape(12.dp),
                            singleLine    = true,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6C63FF),
                            ),
                        )
                        IconButton(
                            onClick = {
                                val t = tagInput.trim().lowercase()
                                if (t.isNotBlank() && !tags.contains(t)) {
                                    tags.add(t)
                                }
                                tagInput = ""
                            }
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Añadir etiqueta",
                                tint = Color(0xFF6C63FF),
                            )
                        }
                    }
                    if (tags.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier              = Modifier.fillMaxWidth(),
                        ) {
                            tags.forEach { tag ->
                                InputChip(
                                    selected     = false,
                                    onClick      = { tags.remove(tag) },
                                    label        = { Text(tag) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        containerColor = Color(0xFF6C63FF).copy(alpha = 0.12f),
                                        labelColor     = Color(0xFF6C63FF),
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    if (showDeleteDialog) {
        val dismissDialog: () -> Unit = { showDeleteDialog = false }
        AlertDialog(
            onDismissRequest = dismissDialog,
            title   = { Text("Eliminar entrada") },
            text    = { Text("Esta entrada se eliminará permanentemente.") },
            confirmButton = {
                TextButton(onClick = { dismissDialog(); onDelete() }) {
                    Text("Eliminar", color = Color(0xFFE05252))
                }
            },
            dismissButton = {
                TextButton(onClick = dismissDialog) { Text("Cancelar") }
            },
        )
    }
}
