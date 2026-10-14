package com.ataraxia.ui.reading

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.roundToInt

@Composable
actual fun rememberReadingStore(): ReadingStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        object : ReadingStore {
            private val prefs = context.getSharedPreferences("reading", Context.MODE_PRIVATE)
            override fun load() = decodeLibrary(prefs.getString("library", null), ReadingProgress(
                prefs.getString("path", "") ?: "", prefs.getString("title", "") ?: "",
                prefs.getInt("page", 0).coerceAtLeast(0), prefs.getBoolean("inverted", true)))
            override fun save(books: List<ReadingProgress>) {
                check(prefs.edit().putString("library", encodeLibrary(books)).commit())
            }
        }
    }
}

@Composable
actual fun rememberPdfPicker(onPicked: (String, String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val title = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "Libro.pdf"
                    val directory = File(context.filesDir, "reading").apply { mkdirs() }
                    val temporary = File.createTempFile("import-", ".pdf", directory)
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input -> temporary.outputStream().use { input.copyTo(it) } }
                            ?: error("No se pudo abrir el archivo")
                        ParcelFileDescriptor.open(temporary, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            PdfRenderer(descriptor).use { require(it.pageCount > 0) }
                        }
                        // Each import owns a private file; existing books and their progress stay intact.
                        val book = File(directory, "${java.util.UUID.randomUUID()}.pdf")
                        check(temporary.renameTo(book))
                        book.absolutePath to title
                    } finally { temporary.delete() }
                }
                picked(result.first, result.second)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed("No se pudo cargar el PDF. Comprueba que sea un archivo válido y sin contraseña.") }
        }
    }
    return { launcher.launch(arrayOf("application/pdf")) }
}

actual suspend fun renderPdfPage(path: String, page: Int, maxDimension: Int): PdfPage = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            require(page in 0 until renderer.pageCount)
            renderer.openPage(page).use { pdfPage ->
                val scale = minOf(2f, maxDimension.toFloat() / maxOf(pdfPage.width, pdfPage.height))
                val bitmap = Bitmap.createBitmap((pdfPage.width * scale).roundToInt().coerceAtLeast(1),
                    (pdfPage.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                PdfPage(bitmap.asImageBitmap(), renderer.pageCount)
            }
        }
    }
}

@Composable
actual fun ReadingImmersiveMode() {
    val context = LocalContext.current
    BackHandler { /* Exit through “Dejar hasta aquí” so the reading position is saved. */ }
    DisposableEffect(context) {
        var current = context
        while (current is ContextWrapper && current !is Activity) current = current.baseContext
        val window = (current as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            previousBehavior?.let { controller?.systemBarsBehavior = it }
        }
    }
}
