@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.ataraxia.ui.reading

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import java.io.File
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import kotlin.test.*

class ReadingUiTest {
    private class Store : ReadingStore {
        var books = emptyList<ReadingProgress>()
        override fun load() = books
        override fun save(books: List<ReadingProgress>) { this.books = books }
    }
    private fun fixture(name: String): File {
        val file = File("build/reading-preview/$name.pdf").apply { parentFile.mkdirs() }
        PDDocument().use { document ->
            repeat(4) { index ->
                val page = PDPage(PDRectangle(300f, 440f))
                document.addPage(page)
                PDPageContentStream(document, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 22f)
                    content.newLineAtOffset(25f, 370f)
                    content.showText(name)
                    content.setFont(PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN), 12f)
                    repeat(12) { line ->
                        content.newLineAtOffset(0f, -22f)
                        content.showText("Lectura de prueba. Pagina ${index + 1}, linea ${line + 1}.")
                    }
                    content.endText()
                }
            }
            document.save(file)
        }
        return file
    }
    private fun ComposeUiTest.screenshot(name: String) {
        val file = File("build/reading-preview/$name.png").apply { parentFile.mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
    }

    @Test fun libraryAndFullscreenGestures() = runDesktopComposeUiTest(width = 420, height = 820) {
        val controller = ReadingController(Store())
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF8B83FF),
                surface = Color(0xFF1E1E2E), background = Color(0xFF16161F))) {
                CompositionLocalProvider(LocalReadingController provides controller) {
                    if (controller.active == null) ReadingScreen() else FullscreenReader(controller)
                }
            }
        }
        onNodeWithText("Sin contenido añadido").assertIsDisplayed()
        onNodeWithText("Toca + para añadir un archivo").assertIsDisplayed()
        onNodeWithContentDescription("Añadir archivo PDF").assertIsDisplayed()
        screenshot("empty")
        val first = fixture("LaDivinaComedia")
        val second = fixture("CartasALucilio")
        runOnIdle {
            controller.add(first.absolutePath, first.name)
            controller.add(second.absolutePath, second.name)
        }
        waitUntil(timeoutMillis = 10000) { onAllNodesWithContentDescription("Primera página de ${first.name}").fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 10000) { onAllNodesWithContentDescription("Primera página de ${second.name}").fetchSemanticsNodes().isNotEmpty() }
        val left = onNodeWithText(first.name).fetchSemanticsNode().boundsInRoot
        val right = onNodeWithText(second.name).fetchSemanticsNode().boundsInRoot
        assertEquals(left.top, right.top)
        assertTrue(left.left < right.left)
        screenshot("library")
        onNodeWithText(first.name).performClick()
        waitUntil(timeoutMillis = 10000) { controller.active?.pageCount == 4 }
        onNodeWithContentDescription("Añadir archivo PDF").assertDoesNotExist()
        screenshot("reader")
        onNodeWithTag("reading-page").performTouchInput { click(Offset(width * .8f, height * .5f)) }
        waitUntil(timeoutMillis = 10000) { controller.active?.page == 1 }
        onNodeWithTag("reading-page").performTouchInput { click(Offset(width * .2f, height * .5f)) }
        waitUntil(timeoutMillis = 10000) { controller.active?.page == 0 }
        onNodeWithTag("reading-page").performTouchInput {
            pinch(Offset(160f, 400f), Offset(60f, 400f), Offset(260f, 400f), Offset(360f, 400f))
        }
        waitForIdle()
        assertEquals(0, controller.active!!.page, "Pinching must not turn pages")
        val zoom = onNodeWithTag("reading-page").fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertNotEquals("Zoom 100%", zoom)
        onNodeWithTag("reading-page").performTouchInput { click(Offset(width * .8f, height * .5f)) }
        waitUntil(timeoutMillis = 10000) { controller.active?.page == 1 }
        assertEquals(zoom, onNodeWithTag("reading-page").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        onNodeWithTag("reading-page").performTouchInput { click(Offset(width * .2f, height * .5f)) }
        waitUntil(timeoutMillis = 10000) { controller.active?.page == 0 }
        assertEquals(zoom, onNodeWithTag("reading-page").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        onNodeWithContentDescription("Opciones de lectura").performClick()
        onNodeWithTag("reading-page-number").performTextReplacement("3")
        onNodeWithText("Ir").performClick()
        waitUntil(timeoutMillis = 10000) { controller.active?.page == 2 }
        assertEquals(zoom, onNodeWithTag("reading-page").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        onNodeWithContentDescription("Opciones de lectura").performClick()
        onNodeWithTag("reading-page-number").performTextReplacement("1")
        onNodeWithText("Ir").performClick()
        waitUntil(timeoutMillis = 10000) { controller.active?.page == 0 }
        onNodeWithTag("reading-page").performTouchInput {
            pinch(Offset(60f, 400f), Offset(180f, 400f), Offset(360f, 400f), Offset(240f, 400f))
        }
        waitForIdle()
        assertEquals("Zoom 100%", onNodeWithTag("reading-page").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        assertEquals(0, controller.active!!.page)
        onNodeWithContentDescription("Opciones de lectura").performClick()
        onNodeWithText("Invertir colores").assertIsDisplayed()
        onNodeWithText("Dejar hasta aquí").assertIsDisplayed()
        onNodeWithText("Invertir colores").performClick()
        runOnIdle { assertFalse(controller.active!!.inverted) }
        screenshot("reader-light")
        onNodeWithContentDescription("Opciones de lectura").performClick()
        onNodeWithText("Dejar hasta aquí").performClick()
        onNodeWithText(first.name).assertIsDisplayed()
        runOnIdle { assertNull(controller.active); assertEquals(.25f, controller.books.first().fraction) }
        waitUntil(timeoutMillis = 10000) { onAllNodesWithContentDescription("Primera página de ${first.name}").fetchSemanticsNodes().isNotEmpty() }
        screenshot("library-progress")
        onNodeWithText(first.name).performClick()
        runOnIdle { assertFalse(controller.active!!.inverted) }
        waitUntil(timeoutMillis = 10000) { onAllNodesWithTag("reading-page").fetchSemanticsNodes().isNotEmpty() }
        val menuButton = onNodeWithContentDescription("Opciones de lectura").fetchSemanticsNode().boundsInRoot
        assertTrue(menuButton.center.x > 300f && menuButton.center.y > 700f)
        onNodeWithContentDescription("Opciones de lectura").performClick()
        onNodeWithTag("reading-page-number").assertTextEquals("1")
        onNodeWithText("de 4").assertIsDisplayed()
        val numberRow = onNodeWithTag("reading-page-number").fetchSemanticsNode().boundsInRoot
        assertTrue(numberRow.top < onNodeWithText("Invertir colores").fetchSemanticsNode().boundsInRoot.top)
        onNodeWithTag("reading-page-number").performTextReplacement("0")
        onNodeWithText("Ir").assertIsNotEnabled()
        onNodeWithTag("reading-page-number").performTextReplacement("5")
        onNodeWithText("Ir").assertIsNotEnabled()
        onNodeWithTag("reading-page-number").performTextReplacement("4")
        onNodeWithText("Ir").performClick()
        waitUntil(timeoutMillis = 10000) { controller.active?.page == 3 }
        onNodeWithContentDescription("Opciones de lectura").performClick()
        onNodeWithTag("reading-page-number").assertTextEquals("4")
        onNodeWithText("de 4").assertIsDisplayed()
        onNodeWithText("Dejar hasta aquí").performClick()
        runOnIdle { assertEquals(3, controller.books.first().page); assertEquals(1f, controller.books.first().fraction) }
        onNodeWithText(first.name).performTouchInput { longClick() }
        runOnIdle { assertNull(controller.active) }
        onNodeWithText("Eliminar").performClick()
        onNodeWithText("Cancelar").performClick()
        onNodeWithText(first.name).assertIsDisplayed()
        onNodeWithText(first.name).performTouchInput { longClick() }
        onNodeWithText("Eliminar").performClick()
        onNodeWithText("Eliminar archivo").assertIsDisplayed()
        onNodeWithText("Eliminar").performClick()
        onNodeWithText(first.name).assertDoesNotExist()
        onNodeWithText(second.name).assertIsDisplayed()
        assertTrue(first.exists(), "Removing a library entry must preserve the original PDF")
        onNodeWithText(second.name).performTouchInput { longClick() }
        onNodeWithText("Eliminar").performClick()
        onNodeWithText("Eliminar").performClick()
        onNodeWithText("Sin contenido añadido").assertIsDisplayed()
    }
}
