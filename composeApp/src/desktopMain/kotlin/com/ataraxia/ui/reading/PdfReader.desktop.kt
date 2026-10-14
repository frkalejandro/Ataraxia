package com.ataraxia.ui.reading

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
actual fun rememberReadingStore(): ReadingStore = remember {
    object : ReadingStore {
        private val prefs = Preferences.userRoot().node("com/ataraxia/reading")
        private val library = File(System.getProperty("user.home"), ".ataraxia/reading-library.json")
        override fun load() = decodeLibrary(if (library.exists()) library.readText() else null,
            ReadingProgress(prefs.get("path", ""), prefs.get("title", ""),
                prefs.getInt("page", 0).coerceAtLeast(0), prefs.getBoolean("inverted", true)))
        override fun save(books: List<ReadingProgress>) {
            library.parentFile.mkdirs()
            val temporary = File(library.parentFile, "reading-library.tmp")
            temporary.writeText(encodeLibrary(books))
            java.nio.file.Files.move(temporary.toPath(), library.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        }
    }
}

@Composable
actual fun rememberPdfPicker(onPicked: (String, String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    return {
        if (!picking) scope.launch {
            picking = true
            try {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Cargar PDF"
                    fileFilter = FileNameExtensionFilter("Libros PDF", "pdf")
                    isAcceptAllFileFilterUsed = false
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    withContext(Dispatchers.IO) { Loader.loadPDF(file).use { require(it.numberOfPages > 0) } }
                    onPicked(file.absolutePath, file.name)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { onError("No se pudo cargar el PDF. Comprueba que sea un archivo válido y sin contraseña.") }
            finally { picking = false }
        }
    }
}

actual suspend fun renderPdfPage(path: String, page: Int, maxDimension: Int): PdfPage = withContext(Dispatchers.IO) {
    Loader.loadPDF(File(path)).use { document ->
        require(page in 0 until document.numberOfPages)
        val box = document.getPage(page).cropBox
        val scale = minOf(2f, maxDimension.toFloat() / maxOf(box.width, box.height))
        val renderer = PDFRenderer(document).apply { isSubsamplingAllowed = true }
        PdfPage(renderer.renderImage(page, scale).toComposeImageBitmap(), document.numberOfPages)
    }
}

@Composable
actual fun ReadingImmersiveMode() = Unit
