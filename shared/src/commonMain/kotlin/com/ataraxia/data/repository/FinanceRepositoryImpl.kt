package com.ataraxia.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.*
import com.ataraxia.domain.repository.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FinanceRepositoryImpl(private val db: AtaraxiaDatabase) : FinanceRepository {
    override suspend fun update(entry: FinanceEntry) = withContext(Dispatchers.Default) {
        db.transaction {
            val existing = db.financeQueries.selectEntry(entry.id).executeAsOneOrNull()
                ?: error("El movimiento ya no existe.")
            require(FinanceCategory.valueOf(existing.category).kind == entry.kind)
            db.financeQueries.updateEntry(entry.name.trim(), entry.category.name, entry.amount, entry.id)
        }
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.Default) {
        db.financeQueries.deleteEntry(id)
    }

    override fun observeEntries() = db.financeQueries.selectEntries().asFlow()
        .mapToList(Dispatchers.Default).map { rows ->
            rows.map { FinanceEntry(it.id, it.name, FinanceCategory.valueOf(it.category), it.amount, it.createdAt) }
        }

    override suspend fun add(entry: FinanceEntry) = withContext(Dispatchers.Default) {
        // A fixed primary key prevents adding the opening balance twice, including concurrent saves.
        val id = if (entry.kind == FinanceKind.INITIAL) "initial-balance" else entry.id
        require(entry.kind == FinanceKind.INITIAL || id != "initial-balance")
        db.financeQueries.insertEntry(id, entry.name.trim(), entry.category.name, entry.amount, entry.createdAt)
    }
}
