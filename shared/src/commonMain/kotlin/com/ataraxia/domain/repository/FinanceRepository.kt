package com.ataraxia.domain.repository

import com.ataraxia.domain.model.FinanceEntry
import kotlinx.coroutines.flow.Flow

interface FinanceRepository {
    fun observeEntries(): Flow<List<FinanceEntry>>
    suspend fun add(entry: FinanceEntry)
    suspend fun update(entry: FinanceEntry)
    suspend fun delete(id: String)
}
