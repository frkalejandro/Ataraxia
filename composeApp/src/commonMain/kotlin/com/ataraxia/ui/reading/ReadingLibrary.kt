package com.ataraxia.ui.reading

import androidx.compose.runtime.*
import kotlinx.serialization.json.*

data class ReadingProgress(
    val path: String = "", val title: String = "", val page: Int = 0, val inverted: Boolean = true,
    val pageCount: Int = 0, val readPages: Int = 0,
) {
    val fraction: Float get() = if (pageCount > 0) (readPages.toFloat() / pageCount).coerceIn(0f, 1f) else 0f
    fun viewed(page: Int, count: Int): ReadingProgress {
        require(count > 0)
        val current = page.coerceIn(0, count - 1)
        return copy(page = current, pageCount = count, readPages = current + 1)
    }
}

interface ReadingStore {
    fun load(): List<ReadingProgress>
    fun save(books: List<ReadingProgress>)
}

fun encodeLibrary(books: List<ReadingProgress>): String = buildJsonArray {
    books.forEach { book -> add(buildJsonObject {
        put("path", book.path); put("title", book.title); put("page", book.page)
        put("inverted", book.inverted); put("pageCount", book.pageCount); put("readPages", book.readPages)
    }) }
}.toString()

fun decodeLibrary(value: String?, legacy: ReadingProgress): List<ReadingProgress> {
    if (value == null) return listOf(legacy).filter { it.path.isNotBlank() }
    return Json.parseToJsonElement(value).jsonArray.map { element ->
        val item = element.jsonObject
        ReadingProgress(path = item.getValue("path").jsonPrimitive.content,
            title = item.getValue("title").jsonPrimitive.content,
            page = (item["page"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0),
            inverted = item["inverted"]?.jsonPrimitive?.booleanOrNull ?: true,
            pageCount = (item["pageCount"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0),
            readPages = (item["readPages"]?.jsonPrimitive?.intOrNull ?: 0).coerceAtLeast(0))
    }.filter { it.path.isNotBlank() }.distinctBy { it.path }
}

class ReadingController(private val store: ReadingStore) {
    var error by mutableStateOf<String?>(null)
    var books by mutableStateOf<List<ReadingProgress>>(emptyList())
        private set
    var active by mutableStateOf<ReadingProgress?>(null)
        private set
    private var loaded = false
    init {
        try { books = store.load(); loaded = true }
        catch (_: Exception) { error = "No se pudo abrir la biblioteca. Reinicia la aplicación para volver a intentarlo." }
    }
    private fun persist(updated: List<ReadingProgress>): Boolean {
        if (!loaded) return false
        return try { store.save(updated); books = updated; error = null; true }
        catch (_: Exception) { error = "No se pudo guardar la lectura. Vuelve a intentarlo."; false }
    }
    fun add(path: String, title: String) {
        if (books.any { it.path == path }) return
        persist(books + ReadingProgress(path, title))
    }
    fun open(path: String) { active = books.firstOrNull { it.path == path } }
    fun remove(path: String): Boolean {
        if (!persist(books.filterNot { it.path == path })) return false
        if (active?.path == path) active = null
        return true
    }
    fun update(book: ReadingProgress) {
        active = book
        persist(books.map { if (it.path == book.path) book else it })
    }
    fun leave() {
        val book = active ?: return
        if (persist(books.map { if (it.path == book.path) book else it })) active = null
    }
}
