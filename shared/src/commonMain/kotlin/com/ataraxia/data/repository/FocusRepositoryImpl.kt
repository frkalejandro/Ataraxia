package com.ataraxia.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToList
import com.ataraxia.domain.model.FocusRecord
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.FocusPhase
import com.ataraxia.domain.model.FocusTimer
import com.ataraxia.domain.repository.FocusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.*

class FocusRepositoryImpl(private val db: AtaraxiaDatabase) : FocusRepository {
    override fun observeBlocks() = db.focusQueries.selectBlocks().asFlow().mapToList(Dispatchers.Default).map { rows ->
        rows.map { FocusRecord(it.id, LocalDate.parse(it.date), it.durationSeconds, it.completedAt) }
    }
    override suspend fun addBlock(record: FocusRecord) = withContext(Dispatchers.Default) {
        require(!record.id.startsWith("focus-"))
        db.focusQueries.recordBlock(record.id, record.date.toString(), record.durationSeconds, record.completedAt)
    }
    override suspend fun updateBlock(record: FocusRecord) = withContext(Dispatchers.Default) {
        db.focusQueries.updateBlock(record.date.toString(), record.durationSeconds, record.completedAt, record.id)
    }
    override suspend fun deleteBlock(id: String) = withContext(Dispatchers.Default) {
        // Keep the completed timer's identity so a delayed alarm cannot recreate a deleted record.
        // Zero-duration rows are excluded from history and contribute nothing to the daily total.
        db.focusQueries.deleteBlock(id)
    }
    override fun observeTotalSeconds(date: LocalDate) = db.focusQueries.totalForDate(date.toString())
        .asFlow().mapToOne(Dispatchers.Default)

    override fun recordElapsedBlock(timer: FocusTimer, nowMillis: Long) {
        val end = timer.deadlineMillis ?: return
        if (timer.phase != FocusPhase.FOCUS || nowMillis < end) return
        // Use the actual completion day, even if Android delivered the alarm late.
        val date = Instant.fromEpochMilliseconds(end).toLocalDateTime(TimeZone.currentSystemDefault()).date
        // The same deadline may be recovered by both the receiver and the UI after a restart.
        db.focusQueries.recordBlock("focus-$end", date.toString(), timer.durationSeconds.toLong(), end)
    }
}
