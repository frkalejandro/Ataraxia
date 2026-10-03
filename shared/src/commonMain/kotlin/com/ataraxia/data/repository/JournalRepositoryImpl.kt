package com.ataraxia.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.JournalEntry
import com.ataraxia.domain.repository.JournalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

class JournalRepositoryImpl(private val db: AtaraxiaDatabase) : JournalRepository {

    private val q get() = db.journalQueries

    override fun observeAllEntries(): Flow<List<JournalEntry>> =
        q.selectAllEntries().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getEntryById(id: String): JournalEntry? =
        withContext(Dispatchers.Default) {
            q.selectEntryById(id).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun saveEntry(entry: JournalEntry) = withContext(Dispatchers.Default) {
        val existing = q.selectEntryById(entry.id).executeAsOneOrNull()
        if (existing == null) {
            q.insertEntry(
                id        = entry.id,
                content   = entry.content,
                mood      = entry.mood,
                tags      = entry.tags.joinToString(","),
                createdAt = entry.createdAt.toEpochMilliseconds(),
                updatedAt = entry.updatedAt.toEpochMilliseconds(),
            )
        } else {
            q.updateEntry(
                content   = entry.content,
                mood      = entry.mood,
                tags      = entry.tags.joinToString(","),
                updatedAt = entry.updatedAt.toEpochMilliseconds(),
                id        = entry.id,
            )
        }
    }

    override suspend fun deleteEntry(id: String) = withContext(Dispatchers.Default) {
        q.deleteEntry(id)
    }

    private fun com.ataraxia.db.JournalEntry.toDomain() = JournalEntry(
        id        = id,
        content   = content,
        mood      = mood,
        tags      = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() },
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    )
}
