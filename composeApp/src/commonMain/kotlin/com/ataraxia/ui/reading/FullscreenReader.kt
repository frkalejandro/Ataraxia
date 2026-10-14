package com.ataraxia.ui.reading

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

private val invertedColors = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)))

@Composable
fun FullscreenReader(controller: ReadingController) {
    val book = controller.active ?: return
    ReadingImmersiveMode()
    var page by remember(book.path) { mutableStateOf(book.page) }
    var rendered by remember(book.path) { mutableStateOf<PdfPage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var pageInput by remember(book.path) { mutableStateOf((page + 1).toString()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var retry by remember { mutableIntStateOf(0) }
    var scale by remember(book.path) { mutableFloatStateOf(1f) }
    var offset by remember(book.path) { mutableStateOf(Offset.Zero) }
    val currentBook by rememberUpdatedState(book)
    LaunchedEffect(book.path, page, retry) {
        loading = true
        rendered = null
        error = null
        try {
            val result = renderPdfPage(book.path, page)
            rendered = result
            controller.update(currentBook.viewed(page, result.count))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "No se pudo leer esta página. Comprueba que el PDF siga disponible y no esté protegido." }
        finally { loading = false }
    }
    fun turn(delta: Int): Boolean {
        val count = rendered?.count ?: return false
        if (loading) return false
        val target = (page + delta).coerceIn(0, count - 1)
        if (target == page) return false
        loading = true
        page = target
        return true
    }
    val pageCount = rendered?.count ?: book.pageCount
    val requestedPage = pageInput.toIntOrNull()
    val validPage = requestedPage != null && requestedPage in 1..pageCount
    fun jumpToPage() {
        if (loading || !validPage) return
        turn(requestedPage!! - 1 - page)
        keyboard?.hide()
        focusManager.clearFocus()
        menu = false
    }
    val foreground = if (book.inverted) Color.White else Color.Black
    Box(Modifier.fillMaxSize().background(if (book.inverted) Color.Black else Color.White).clipToBounds()) {
        rendered?.let { pdf ->
            Box(Modifier.fillMaxSize().testTag("reading-page")
                .semantics {
                    stateDescription = "Zoom ${(scale * 100).toInt()}%"
                    customActions = listOf(CustomAccessibilityAction("Página anterior") { turn(-1) },
                        CustomAccessibilityAction("Página siguiente") { turn(1) })
                }
                .pointerInput(book.path, page, loading) {
                    detectTapGestures { position -> turn(if (position.x < size.width / 2f) -1 else 1) }
                }
                .pointerInput(book.path, page) {
                    // A consumed pinch/pan is never interpreted as a page tap.
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val previous = scale
                        val next = (previous * zoom).coerceIn(1f, 5f)
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val shifted = (offset - (centroid - center)) * (next / previous) + (centroid - center) + pan
                        val maxX = size.width * (next - 1f) / 2f
                        val maxY = size.height * (next - 1f) / 2f
                        offset = Offset(shifted.x.coerceIn(-maxX, maxX), shifted.y.coerceIn(-maxY, maxY))
                        scale = next
                    }
                }) {
                Image(pdf.image, "${book.title}, página ${page + 1} de ${pdf.count}",
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                    }, contentScale = ContentScale.Fit, colorFilter = if (book.inverted) invertedColors else null)
            }
        }
        if (loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color(0xFF8B83FF))
        val message = error ?: controller.error
        message?.let {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(it, color = foreground)
                if (error != null) TextButton(onClick = { retry++ }) { Text("Reintentar") }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)) {
            IconButton(onClick = { pageInput = (page + 1).toString(); menu = true }, modifier = Modifier.background(
                if (book.inverted) Color.Black.copy(alpha = .65f) else Color.White.copy(alpha = .8f), CircleShape)) {
                Icon(Icons.Default.MoreVert, "Opciones de lectura", tint = foreground)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = pageInput,
                            onValueChange = { value -> if (value.all { it in '0'..'9' }) pageInput = value },
                            modifier = Modifier.width(84.dp).testTag("reading-page-number"),
                            singleLine = true, enabled = !loading && pageCount > 0,
                            isError = !validPage && pageCount > 0,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { jumpToPage() }))
                        Text("de ${pageCount.takeIf { it > 0 } ?: "…"}")
                        TextButton(onClick = ::jumpToPage, enabled = !loading && validPage) { Text("Ir") }
                    }
                    if (!validPage && pageCount > 0) Text("Introduce una página entre 1 y $pageCount",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp))
                }
                DropdownMenuItem(text = { Text("Invertir colores") },
                    trailingIcon = { Switch(checked = book.inverted, onCheckedChange = null) },
                    onClick = { controller.update(book.copy(inverted = !book.inverted)); menu = false })
                DropdownMenuItem(text = { Text("Dejar hasta aquí") }, onClick = { controller.leave(); menu = false })
            }
        }
    }
}
