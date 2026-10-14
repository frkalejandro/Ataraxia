package com.ataraxia.ui.finance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.ataraxia.domain.model.*
import com.ataraxia.ui.SectionTopAppBar
import com.ataraxia.ui.RecordActions
import com.ataraxia.ui.RecordError
import kotlinx.datetime.*
import kotlin.math.*

private val IncomeColor = Color(0xFF238765)
private val ExpenseColor = Color(0xFFC44857)

class FinanceScreen : Screen {
    @Composable
    override fun Content() {
        val vm: FinanceViewModel = koinScreenModel()
        val state by vm.state.collectAsState()
        var form by rememberSaveable { mutableStateOf<String?>(null) }
        var history by rememberSaveable { mutableStateOf(false) }
        var category by rememberSaveable { mutableStateOf<String?>(null) }
        var editing by remember { mutableStateOf<FinanceEntry?>(null) }
        val summary = state.summary
        val editEntry: (FinanceEntry) -> Unit = { entry ->
            vm.clearError(); editing = entry; history = false; category = null; form = entry.kind.name
        }

        Scaffold(topBar = { SectionTopAppBar("Finanzas") }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (state.loading) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                } else if (state.error != null && form == null) {
                    item {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = vm::reload) { Text("Reintentar") }
                    }
                } else {
                    item {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.AccountBalanceWallet, null)
                                    Text("Dinero disponible", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(formatPesos(summary.available), style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (summary.available < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Pesos chilenos · CLP", style = MaterialTheme.typography.labelMedium)
                                Text("Inicial ${formatPesos(summary.initial)}  +  ingresos ${formatPesos(summary.income)}  −  gastos ${formatPesos(summary.expenses)}",
                                    style = MaterialTheme.typography.bodySmall)
                                if (!summary.hasInitial) {
                                    Text("Indica con cuánto dinero comienzas. Si es tu sueldo actual, regístralo una sola vez aquí.",
                                        style = MaterialTheme.typography.bodyMedium)
                                    FilledTonalButton(onClick = { vm.clearError(); editing = null; form = FinanceKind.INITIAL.name }) {
                                        Text("Definir saldo inicial")
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tus gastos", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                    TextButton(onClick = { history = true }) { Text("Historial completo") }
                                }
                                ExpenseDonut(summary.shares, Modifier.align(Alignment.CenterHorizontally)) { category = it.name }
                                if (summary.shares.isEmpty()) {
                                    Text("Todavía no hay gastos. Al añadir uno, verás aquí cómo se distribuye tu dinero.",
                                        style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    Text("Porcentaje del total gastado. Toca una porción o categoría para ver sus gastos.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    summary.shares.forEach { share ->
                                        Surface(onClick = { category = share.category.name }, shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
                                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Box(Modifier.size(12.dp).background(categoryColor(share.category), CircleShape))
                                                Column(Modifier.weight(1f)) {
                                                    Text(share.category.label, style = MaterialTheme.typography.bodyMedium)
                                                    Text(formatPesos(share.amount), style = MaterialTheme.typography.bodySmall)
                                                }
                                                Text(formatPercent(share.fraction), fontWeight = FontWeight.SemiBold)
                                                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FinanceAction("Añadir nuevo ingreso", Icons.Default.Add, IncomeColor, Modifier.weight(1f)) {
                                vm.clearError(); editing = null; form = FinanceKind.INCOME.name
                            }
                            FinanceAction("Añadir nuevo gasto", Icons.Default.Remove, ExpenseColor, Modifier.weight(1f)) {
                                vm.clearError(); editing = null; form = FinanceKind.EXPENSE.name
                            }
                        }
                    }
                }
            }
        }

        form?.let { kind ->
            FinanceEntryDialog(FinanceKind.valueOf(kind), state.saving, state.error, initial = editing,
                onDismiss = { if (!state.saving) { form = null; vm.clearError() } },
                onAdd = { name, type, amount ->
                    val original = editing
                    if (original == null) vm.add(name, type, amount) { form = null }
                    else vm.update(original.copy(name = name, category = type, amount = amount)) { form = null; editing = null }
                })
        }
        if (history) FinanceHistoryDialog("Historial completo", summary.entries, state.saving, editEntry,
            { entry -> vm.delete(entry.id) {} }) { history = false }
        if (form == null) RecordError(state.error, vm::clearError)
        category?.let { selected ->
            val type = FinanceCategory.valueOf(selected)
            FinanceHistoryDialog(type.label, summary.entries.filter { it.category == type }, state.saving, editEntry,
                { entry -> vm.delete(entry.id) {} }) { category = null }
        }
    }
}

@Composable
private fun FinanceAction(title: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.heightIn(min = 132.dp), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = color)
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
    }
}

private fun categoryColor(category: FinanceCategory): Color = when (category) {
    FinanceCategory.CLOTHING -> Color(0xFF7661C4)
    FinanceCategory.FOOD -> Color(0xFF238765)
    FinanceCategory.FRIENDS -> Color(0xFFAD6424)
    FinanceCategory.GAMES -> Color(0xFF347BB5)
    FinanceCategory.APPLIANCES -> Color(0xFFBB496C)
    FinanceCategory.TOOLS -> Color(0xFF567C80)
    else -> IncomeColor
}

internal fun formatPercent(fraction: Float): String {
    val tenths = (fraction * 1000).roundToInt()
    return when {
        tenths == 0 && fraction > 0 -> "<0,1 %"
        tenths % 10 == 0 -> "${tenths / 10} %"
        else -> "${tenths / 10},${tenths % 10} %"
    }
}

/** Angle starts at twelve o'clock and grows clockwise, as in drawArc below. */
internal fun expenseAtPoint(x: Float, y: Float, size: Float, shares: List<ExpenseShare>): FinanceCategory? {
    val dx = x - size / 2
    val dy = y - size / 2
    val distance = sqrt(dx * dx + dy * dy)
    if (distance < size * 0.28f || distance > size * 0.48f) return null
    val fraction = ((atan2(dy, dx) * 180f / PI.toFloat() + 450f) % 360f) / 360f
    var end = 0f
    return shares.firstOrNull { end += it.fraction; fraction < end }?.category
}

@Composable
private fun ExpenseDonut(shares: List<ExpenseShare>, modifier: Modifier, onSelect: (FinanceCategory) -> Unit) {
    val measurer = rememberTextMeasurer()
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.widthIn(max = 320.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription = "Distribución de gastos. Las categorías y porcentajes también están disponibles debajo."
        }.pointerInput(shares) {
            detectTapGestures { point ->
                expenseAtPoint(point.x, point.y, size.width.toFloat(), shares)?.let(onSelect)
            }
        }) {
            val side = size.minDimension
            val stroke = side * 0.20f
            val radius = side * 0.38f
            val topLeft = Offset(side * 0.12f, side * 0.12f)
            val arcSize = Size(radius * 2, radius * 2)
            if (shares.isEmpty()) drawCircle(emptyColor, radius, style = Stroke(stroke))
            var start = -90f
            shares.forEach { share ->
                val sweep = share.fraction * 360f
                drawArc(categoryColor(share.category), start, sweep, false, topLeft, arcSize, style = Stroke(stroke))
                if (share.fraction >= 0.07f) {
                    val angle = (start + sweep / 2) * PI / 180
                    val label = measurer.measure(formatPercent(share.fraction), TextStyle(color = Color.White,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    drawText(label, topLeft = Offset(center.x + cos(angle).toFloat() * radius - label.size.width / 2,
                        center.y + sin(angle).toFloat() * radius - label.size.height / 2))
                }
                start += sweep
            }
        }
        Column(Modifier.fillMaxWidth(0.52f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Total gastado", style = MaterialTheme.typography.labelMedium)
            Text(formatPesos(shares.sumOf { it.amount }), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceEntryDialog(kind: FinanceKind, saving: Boolean, error: String?, initial: FinanceEntry?, onDismiss: () -> Unit,
    onAdd: (String, FinanceCategory, Long) -> Unit) {
    val categories = FinanceCategory.entries.filter { it.kind == kind }
    var name by rememberSaveable(kind) { mutableStateOf(initial?.name ?: if (kind == FinanceKind.INITIAL) "Saldo inicial" else "") }
    var amount by rememberSaveable(kind) { mutableStateOf(initial?.amount?.toString() ?: "") }
    var selected by rememberSaveable(kind) { mutableStateOf(initial?.category?.name ?: categories.first().name) }
    var expanded by remember { mutableStateOf(false) }
    val parsed = parseFinanceAmount(amount)
    val validAmount = parsed != null && (kind == FinanceKind.INITIAL || parsed > 0)
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (initial != null) "Editar ${kind.label.lowercase()}" else when (kind) {
            FinanceKind.INITIAL -> "Definir saldo inicial"
            FinanceKind.INCOME -> "Añadir nuevo ingreso"
            FinanceKind.EXPENSE -> "Añadir nuevo gasto"
        }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Monto en pesos (CLP)") },
                    modifier = Modifier.fillMaxWidth(), enabled = !saving, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amount.isNotEmpty() && !validAmount,
                    supportingText = { Text(if (amount.isNotEmpty() && !validAmount)
                        "Ingresa ${if (kind == FinanceKind.INITIAL) "cero o " else ""}un monto positivo, sin decimales (máximo ${formatPesos(MAX_FINANCE_AMOUNT)})."
                        else "Ejemplo: 500000 o 500.000") })
                OutlinedTextField(name, { if (it.length <= 100) name = it }, label = { Text("Nombre") },
                    modifier = Modifier.fillMaxWidth(), enabled = !saving, singleLine = true)
                if (kind != FinanceKind.INITIAL) {
                    ExposedDropdownMenuBox(expanded, { if (!saving) expanded = !expanded }) {
                        OutlinedTextField(FinanceCategory.valueOf(selected).label, {}, readOnly = true, enabled = !saving,
                            label = { Text(if (kind == FinanceKind.INCOME) "Tipo de ingreso" else "Tipo de gasto") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = !saving).fillMaxWidth())
                        ExposedDropdownMenu(expanded, { expanded = false }) {
                            categories.forEach { type ->
                                DropdownMenuItem(text = { Text(type.label) }, onClick = { selected = type.name; expanded = false })
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !saving && validAmount && name.isNotBlank(),
            onClick = { parsed?.let { onAdd(name.trim(), FinanceCategory.valueOf(selected), it) } }) {
            Text(if (saving) "Guardando…" else if (initial != null) "Guardar cambios" else "Añadir")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } })
}

@Composable
private fun FinanceHistoryDialog(title: String, entries: List<FinanceEntry>, busy: Boolean,
    onEdit: (FinanceEntry) -> Unit, onDelete: (FinanceEntry) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            if (entries.isEmpty()) Text("Aún no hay movimientos registrados.")
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.name, style = MaterialTheme.typography.titleSmall)
                                Text("${entry.kind.label} · ${entry.category.label}", style = MaterialTheme.typography.labelMedium)
                                Text("${if (entry.kind == FinanceKind.EXPENSE) "−" else "+"}${formatPesos(entry.amount)}",
                                    color = if (entry.kind == FinanceKind.EXPENSE) MaterialTheme.colorScheme.error
                                        else if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF70D6AA) else IncomeColor,
                                    fontWeight = FontWeight.SemiBold)
                                val date = Instant.fromEpochMilliseconds(entry.createdAt).toLocalDateTime(TimeZone.currentSystemDefault())
                                Text("${date.dayOfMonth}/${date.monthNumber}/${date.year} · ${date.hour.toString().padStart(2, '0')}:${date.minute.toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            RecordActions(entry.name, enabled = !busy,
                                deleteMessage = "Se borrará «${entry.name}» (${formatPesos(entry.amount)}). El saldo y el gráfico se recalcularán.",
                                onEdit = { onEdit(entry) }, onDelete = { onDelete(entry) })
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
