package com.ataraxia.ui.reading

import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.nio.file.Files
import kotlin.test.*

class PdfReaderTest {
    @Test fun rendersDifferentPagesAndRejectsOutOfRangeWithoutKeepingFileOpen() = runBlocking {
        val file = Files.createTempFile("ataraxia-reader", ".pdf").toFile()
        try {
            PDDocument().use { document ->
                document.addPage(PDPage(PDRectangle(300f, 400f)))
                document.addPage(PDPage(PDRectangle(400f, 300f)))
                document.save(file)
            }
            val first = renderPdfPage(file.absolutePath, 0)
            assertEquals(2, first.count)
            assertTrue(first.image.height > first.image.width)
            val second = renderPdfPage(file.absolutePath, 1)
            assertTrue(second.image.width > second.image.height)
            assertFailsWith<IllegalArgumentException> { renderPdfPage(file.absolutePath, 2) }
            assertTrue(file.delete(), "The reader must release the document after rendering")
        } finally { file.delete() }
    }

    @Test fun rejectsDamagedDocumentAndCapsLargePageBitmap() = runBlocking {
        val file = Files.createTempFile("ataraxia-reader", ".pdf").toFile()
        try {
            file.writeText("This is not a PDF")
            assertFailsWith<java.io.IOException> { renderPdfPage(file.absolutePath, 0) }
            PDDocument().use { document ->
                document.addPage(PDPage(PDRectangle(10000f, 8000f)))
                document.save(file)
            }
            val page = renderPdfPage(file.absolutePath, 0)
            assertTrue(page.image.width <= 2400)
            assertTrue(page.image.height <= 2400)
        } finally { file.delete() }
    }
}
