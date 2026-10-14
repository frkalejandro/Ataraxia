@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ataraxia.ui.reading

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.*
import platform.PDFKit.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
actual fun rememberReadingStore(): ReadingStore = remember {
    object : ReadingStore {
        private val prefs = NSUserDefaults.standardUserDefaults
        override fun load() = decodeLibrary(prefs.stringForKey("reading.library"), ReadingProgress(prefs.stringForKey("reading.path") ?: "",
            prefs.stringForKey("reading.title") ?: "", prefs.integerForKey("reading.page").toInt().coerceAtLeast(0),
            if (prefs.objectForKey("reading.inverted") == null) true else prefs.boolForKey("reading.inverted")))
        override fun save(books: List<ReadingProgress>) {
            prefs.setObject(encodeLibrary(books), "reading.library")
        }
    }
}

@Composable
actual fun rememberPdfPicker(onPicked: (String, String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onError)
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                try {
                    val document = PDFDocument(url)
                    require(!document.isLocked() && document.pageCount > 0u)
                    val directory = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
                    val path = "$directory/reading-${NSUUID().UUIDString}.pdf"
                    val data = NSData.dataWithContentsOfURL(url) ?: error("No se pudo leer el PDF")
                    check(data.writeToFile(path, atomically = true))
                    picked(path, url.lastPathComponent ?: "Libro.pdf")
                } catch (_: Exception) { failed("No se pudo cargar el PDF. Comprueba que sea válido y sin contraseña.") }
            }
        }
    }
    return {
        val picker = UIDocumentPickerViewController(documentTypes = listOf("com.adobe.pdf"), inMode = UIDocumentPickerMode.UIDocumentPickerModeImport)
        picker.delegate = delegate
        var presenter = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (presenter?.presentedViewController != null) presenter = presenter.presentedViewController
        if (presenter == null) failed("No se pudo abrir el selector de archivos.")
        else presenter.presentViewController(picker, animated = true, completion = null)
    }
}

actual suspend fun renderPdfPage(path: String, page: Int, maxDimension: Int): PdfPage = withContext(Dispatchers.Default) {
    val document = PDFDocument(NSURL.fileURLWithPath(path))
    require(!document.isLocked() && page >= 0 && page.toULong() < document.pageCount)
    val pdfPage = document.pageAtIndex(page.toULong()) ?: error("Página no disponible")
    val bounds = pdfPage.boundsForBox(kPDFDisplayBoxCropBox)
    val size = bounds.useContents { size.width to size.height }
    val scale = minOf(2.0, maxDimension.toDouble() / maxOf(size.first, size.second))
    val thumbnail = pdfPage.thumbnailOfSize(CGSizeMake(size.first * scale, size.second * scale), forBox = kPDFDisplayBoxCropBox)
    val data = UIImagePNGRepresentation(thumbnail) ?: error("No se pudo renderizar la página")
    val bytes = ByteArray(data.length.toInt())
    bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    PdfPage(org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap(), document.pageCount.toInt())
}

@Composable
actual fun ReadingImmersiveMode() = Unit
