package com.ataraxia.ui.reading

import kotlin.test.*

class ReadingLibraryTest {
    @Test fun deletionPersistsWithoutAffectingOtherBooksAndFailureKeepsBook() {
        val store = Store()
        val controller = ReadingController(store)
        controller.add("one", "Uno")
        controller.add("two", "Dos")
        store.fail = true
        assertFalse(controller.remove("one"))
        assertEquals(2, controller.books.size)
        store.fail = false
        assertTrue(controller.remove("one"))
        assertEquals(listOf("two"), ReadingController(store).books.map { it.path })
        assertTrue(controller.remove("two"))
        assertTrue(ReadingController(store).books.isEmpty())
    }
    private class Store : ReadingStore {
        var books = emptyList<ReadingProgress>()
        var fail = false
        override fun load() = books
        override fun save(books: List<ReadingProgress>) {
            check(!fail)
            this.books = decodeLibrary(encodeLibrary(books), ReadingProgress())
        }
    }

    @Test fun importsMultipleBooksAndRestoresIndependentPositionsAndColors() {
        val store = Store()
        val controller = ReadingController(store)
        controller.add("one.pdf", "LaDivinaComedia.pdf")
        controller.add("two.pdf", "CartasALucilio.pdf")
        controller.open("one.pdf")
        controller.update(controller.active!!.viewed(39, 100).copy(inverted = false))
        controller.leave()
        assertNull(controller.active)
        val restored = ReadingController(store)
        assertEquals(2, restored.books.size)
        assertEquals(.4f, restored.books.first().fraction)
        assertEquals(0f, restored.books.last().fraction)
        restored.open("one.pdf")
        assertEquals(39, restored.active!!.page)
        assertFalse(restored.active!!.inverted)
        restored.leave()
        restored.open("two.pdf")
        assertEquals(0, restored.active!!.page)
        assertTrue(restored.active!!.inverted)
        restored.update(restored.active!!.viewed(9, 10))
        assertEquals(1f, restored.active!!.fraction)
    }

    @Test fun legacyBookMigratesAndUnicodeNamesRoundTrip() {
        val legacy = ReadingProgress("C:\\libros\\libro.pdf", "Lectura \"ñ\" 📖.pdf", page = 18, inverted = false)
        assertEquals(listOf(legacy), decodeLibrary(null, legacy))
        assertEquals(listOf(legacy), decodeLibrary(encodeLibrary(listOf(legacy)), ReadingProgress()))
        assertEquals(emptyList(), decodeLibrary(null, ReadingProgress()))
        assertEquals(emptyList(), decodeLibrary("[]", legacy))
    }

    @Test fun failedSaveKeepsReaderOpenAndCanBeRetriedWithoutLosingProgress() {
        val store = Store()
        val controller = ReadingController(store)
        controller.add("one", "Libro")
        controller.open("one")
        store.fail = true
        controller.update(controller.active!!.viewed(7, 10))
        controller.leave()
        assertNotNull(controller.active)
        assertNotNull(controller.error)
        store.fail = false
        controller.leave()
        assertNull(controller.active)
        assertEquals(7, store.books.single().page)
    }
}
