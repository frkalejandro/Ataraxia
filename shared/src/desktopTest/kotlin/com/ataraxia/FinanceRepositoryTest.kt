package com.ataraxia

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ataraxia.data.repository.FinanceRepositoryImpl
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class FinanceRepositoryTest {
    @Test fun desktopFactoryCreatesAllTablesOnFirstLaunch() {
        val home = Files.createTempDirectory("atarax-finance-home").toFile()
        val previousHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", home.absolutePath)
            com.ataraxia.data.db.DatabaseDriverFactory().createDriver().use { driver ->
                val db = AtaraxiaDatabase(driver)
                assertTrue(db.financeQueries.selectEntries().executeAsList().isEmpty())
                db.journalQueries.insertEntry("entry", "Primera apertura", null, "", 1, 1)
            }
            com.ataraxia.data.db.DatabaseDriverFactory().createDriver().use { driver ->
                assertEquals("Primera apertura", AtaraxiaDatabase(driver).journalQueries.selectEntryById("entry").executeAsOne().content)
            }
        } finally {
            System.setProperty("user.home", previousHome)
            home.resolve(".ataraxia/ataraxia.db").delete()
            home.resolve(".ataraxia").delete()
            home.delete()
        }
    }

    @Test fun persistsNamedCategorizedEntriesAndOpeningBalanceAcrossReopen() = runBlocking {
        val file = Files.createTempFile("atarax-finance-test", ".db").toFile()
        try {
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = AtaraxiaDatabase.Schema).use { driver ->
                val repo = FinanceRepositoryImpl(AtaraxiaDatabase(driver))
                repo.add(FinanceEntry("initial", "Mi saldo", FinanceCategory.INITIAL, 500_000, 1))
                repo.add(FinanceEntry("gift", "Cumpleaños", FinanceCategory.GIFT, 50_000, 2))
                repo.add(FinanceEntry("food", "Almuerzo", FinanceCategory.FOOD, 8_000, 3))
                assertFails { repo.add(FinanceEntry("initial-again", "Duplicado", FinanceCategory.INITIAL, 500_000, 4)) }
            }
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = AtaraxiaDatabase.Schema).use { driver ->
                val entries = FinanceRepositoryImpl(AtaraxiaDatabase(driver)).observeEntries().first()
                assertEquals(listOf("Almuerzo", "Cumpleaños", "Mi saldo"), entries.map { it.name })
                assertEquals(FinanceCategory.FOOD, entries.first().category)
                assertEquals(542_000L, FinanceSummary(entries).available)
            }
        } finally { file.delete() }
    }

    @Test fun migrationFromFiveKeepsExistingDataAndStartsWithAnEmptyLedger() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            AtaraxiaDatabase.Schema.create(driver)
            val db = AtaraxiaDatabase(driver)
            db.journalQueries.insertEntry("entry", "Conservar diario", null, "", 1, 1)
            driver.execute(null, "DROP TABLE FinanceEntry", 0)
            AtaraxiaDatabase.Schema.migrate(driver, 5, 6)
            assertEquals("Conservar diario", db.journalQueries.selectEntryById("entry").executeAsOne().content)
            assertTrue(db.financeQueries.selectEntries().executeAsList().isEmpty())
        }
    }
}
