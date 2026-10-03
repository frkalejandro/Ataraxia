package com.ataraxia

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ataraxia.data.repository.FocusRepositoryImpl
import com.ataraxia.db.AtaraxiaDatabase
import com.ataraxia.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import java.nio.file.Files
import java.io.File
import java.sql.DriverManager
import kotlin.test.*

class FocusRepositoryTest {
    private val date = LocalDate.parse("2026-10-03")
    private val zone = TimeZone.currentSystemDefault()
    private fun time(hour: Int, minute: Int = 0) = date.atTime(hour, minute).toInstant(zone).toEpochMilliseconds()

    @Test fun sumsCompletedBlocksOnceAndExcludesBreaksPausedAndUnfinishedTimers() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            AtaraxiaDatabase.Schema.create(driver)
            val repo = FocusRepositoryImpl(AtaraxiaDatabase(driver))
            val block = FocusTimer().start(time(10))
            repo.recordElapsedBlock(block, time(10, 24))
            repo.recordElapsedBlock(block.pause(time(10, 10)), time(11))
            repo.recordElapsedBlock(block.reset(), time(11))
            assertEquals(0L, repo.observeTotalSeconds(date).first())
            repo.recordElapsedBlock(block, time(10, 25))
            repo.recordElapsedBlock(block, time(11))
            val rest = block.tick(time(10, 25)).start(time(11))
            repo.recordElapsedBlock(rest, time(11, 5))
            val second = FocusTimer().setMinutes(50).start(time(12))
            repo.recordElapsedBlock(second, time(13))
            assertEquals(75 * 60L, repo.observeTotalSeconds(date).first())
        }
    }

    @Test fun lateRecoveryUsesCompletionDayAndSurvivesDatabaseReopen() = runBlocking {
        val file = Files.createTempFile("atarax-focus-test", ".db").toFile()
        val block = FocusTimer().start(time(23, 30))
        val tomorrow = date.plus(1, DateTimeUnit.DAY)
        val recovered = tomorrow.atTime(10, 0).toInstant(zone).toEpochMilliseconds()
        try {
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = AtaraxiaDatabase.Schema).use { driver ->
                FocusRepositoryImpl(AtaraxiaDatabase(driver)).recordElapsedBlock(block, recovered)
            }
            JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", schema = AtaraxiaDatabase.Schema).use { driver ->
                val repo = FocusRepositoryImpl(AtaraxiaDatabase(driver))
                repo.recordElapsedBlock(block, recovered)
                assertEquals(25 * 60L, repo.observeTotalSeconds(date).first())
                assertEquals(0L, repo.observeTotalSeconds(tomorrow).first())
                val crossingMidnight = FocusTimer().start(time(23, 50))
                repo.recordElapsedBlock(crossingMidnight, recovered)
                assertEquals(25 * 60L, repo.observeTotalSeconds(tomorrow).first())
            }
        } finally { file.delete() }
    }

    @Test fun migrationPreservesExistingJournalAndWorkout() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            AtaraxiaDatabase.Schema.create(driver)
            val db = AtaraxiaDatabase(driver)
            db.journalQueries.insertEntry("entry", "Mi diario", null, "", 1, 1)
            db.workoutQueries.saveExercise("exercise", "Sentadillas", "LEGS")
            driver.execute(null, "DROP TABLE CompletedFocusBlock", 0)
            AtaraxiaDatabase.Schema.migrate(driver, 4, 5)
            assertEquals("Mi diario", db.journalQueries.selectEntryById("entry").executeAsOne().content)
            assertEquals(1, db.workoutQueries.selectExercises().executeAsList().size)
            assertEquals(0L, db.focusQueries.totalForDate(date.toString()).executeAsOne())
        }
    }

    @Test fun actualVersionThreeSnapshotMigratesToCurrentTableSchema() {
        val migrated = Files.createTempFile("atarax-migrated", ".db").toFile()
        val fresh = Files.createTempFile("atarax-fresh", ".db").toFile()
        try {
            File("src/commonMain/sqldelight/migrations/3.db").copyTo(migrated, overwrite = true)
            JdbcSqliteDriver("jdbc:sqlite:${migrated.absolutePath}").use { driver ->
                AtaraxiaDatabase(driver).journalQueries.insertEntry("entry", "Conservar", null, "", 1, 1)
                AtaraxiaDatabase.Schema.migrate(driver, 3, 5)
                assertEquals("Conservar", AtaraxiaDatabase(driver).journalQueries
                    .selectEntryById("entry").executeAsOne().content)
            }
            JdbcSqliteDriver("jdbc:sqlite:${fresh.absolutePath}").use { AtaraxiaDatabase.Schema.create(it) }
            assertEquals(tableSchema(fresh), tableSchema(migrated))
        } finally {
            migrated.delete()
            fresh.delete()
        }
    }

    private fun tableSchema(file: File): Map<String, List<List<String?>>> =
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            val names = connection.createStatement().use { statement ->
                statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
            names.associateWith { table ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA table_info('$table')").use { rows ->
                        buildList { while (rows.next()) add((1..6).map { rows.getString(it) }) }
                    }
                }
            }
        }
}
