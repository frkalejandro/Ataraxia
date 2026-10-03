package com.ataraxia.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.FocusPhase
import com.ataraxia.domain.model.FocusTimer
import com.ataraxia.domain.repository.FocusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.*

class FocusRepositoryImpl(private val db: AtaraxiaDatabase) : FocusRepository {
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
