package com.ataraxia.domain.repository

import com.ataraxia.domain.model.FocusTimer
import com.ataraxia.domain.model.FocusRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface FocusRepository {
    fun observeBlocks(): Flow<List<FocusRecord>>
    suspend fun addBlock(record: FocusRecord)
    suspend fun updateBlock(record: FocusRecord)
    suspend fun deleteBlock(id: String)
    fun observeTotalSeconds(date: LocalDate): Flow<Long>

    /** Small, synchronous insert: commit before advancing/persisting the timer's next phase. */
    fun recordElapsedBlock(timer: FocusTimer, nowMillis: Long)
}
