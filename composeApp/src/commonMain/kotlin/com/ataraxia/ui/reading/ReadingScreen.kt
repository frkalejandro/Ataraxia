package com.ataraxia.ui.reading

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ataraxia.ui.SectionTopAppBar
import kotlinx.coroutines.CancellationException

data class PdfPage(val image: ImageBitmap, val count: Int)

@Composable
expect fun rememberReadingStore(): ReadingStore
@Composable
expect fun rememberPdfPicker(onPicked: (String, String) -> Unit, onError: (String) -> Unit): () -> Unit
expect suspend fun renderPdfPage(path: String, page: Int, maxDimension: Int = 2400): PdfPage
@Composable
expect fun ReadingImmersiveMode()

val LocalReadingController = staticCompositionLocalOf<ReadingController> { error("Falta la biblioteca") }

@Composable
fun ReadingScreen() {
    val controller = LocalReadingController.current
    var deleting by remember { mutableStateOf<ReadingProgress?>(null) }
    val pick = rememberPdfPicker(onPicked = controller::add, onError = { controller.error = it })
    Scaffold(
        topBar = { SectionTopAppBar(title = "Lectura") },
        floatingActionButton = {
            FloatingActionButton(onClick = pick, containerColor = Color(0xFF6C63FF), contentColor = Color.White) {
                Icon(Icons.Default.Add, contentDescription = "Añadir archivo PDF")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            controller.error?.let { message ->
                Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { controller.error = null }) { Text("Cerrar") }
            }
            if (controller.books.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    // Same typography, spacing and FAB as the empty category in Habits.
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(40.dp)) {
                        Text("📖", fontSize = 48.sp)
                        Text("Sin contenido añadido", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        Text("Toca + para añadir un archivo", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(controller.books, key = { it.path }) { book ->
                        BookCard(book, onOpen = { controller.open(book.path) }, onDelete = { deleting = book })
                    }
                }
            }
        }
    }
    deleting?.let { book ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("Eliminar archivo") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se quitará «${book.title}» de tu biblioteca y se borrará su progreso de lectura. El PDF original se conserva.")
                controller.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = {
                if (controller.remove(book.path)) deleting = null
            }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookCard(book: ReadingProgress, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menu by remember(book.path) { mutableStateOf(false) }
    var cover by remember(book.path) { mutableStateOf<PdfPage?>(null) }
    var failed by remember(book.path) { mutableStateOf(false) }
    LaunchedEffect(book.path) {
        try { cover = renderPdfPage(book.path, 0, maxDimension = 600) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    }
    Box {
    Card(modifier = Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(
        onClick = onOpen, onLongClickLabel = "Opciones del archivo", onLongClick = { menu = true }),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(.7f).clip(RoundedCornerShape(10.dp)).background(Color.White),
                contentAlignment = Alignment.Center) {
                cover?.let { Image(it.image, "Primera página de ${book.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    ?: if (failed) Text("📖", fontSize = 40.sp) else CircularProgressIndicator(Modifier.size(24.dp), color = Color(0xFF6C63FF))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(book.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ReadingRing(book.fraction)
            }
            if (failed) Text("No se pudo cargar la portada", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        DropdownMenuItem(text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { menu = false; onDelete() })
    }
    }
}

@Composable
internal fun ReadingRing(fraction: Float) {
    val percent = (fraction * 100).toInt()
    Box(Modifier.size(46.dp).semantics { contentDescription = "$percent% de lectura completada" },
        contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxSize(),
            color = Color(0xFF8B83FF), trackColor = Color(0xFF3D3580), strokeWidth = 4.dp,
            strokeCap = StrokeCap.Butt)
        Text("$percent%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}
