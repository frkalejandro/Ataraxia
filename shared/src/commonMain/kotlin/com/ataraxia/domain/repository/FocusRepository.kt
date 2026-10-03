package com.ataraxia.domain.repository

import com.ataraxia.domain.model.FocusTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface FocusRepository {
    fun observeTotalSeconds(date: LocalDate): Flow<Long>

    /** Small, synchronous insert: commit before advancing/persisting the timer's next phase. */
    fun recordElapsedBlock(timer: FocusTimer, nowMillis: Long)
}
