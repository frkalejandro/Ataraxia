package com.ataraxia.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ataraxia.db.AtaraxiaDatabase
import java.io.File
import java.util.Properties

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbDir  = File(System.getProperty("user.home"), ".ataraxia")
        dbDir.mkdirs()
        val dbFile = File(dbDir, "ataraxia.db")
        val exists = dbFile.exists()

        val driver = JdbcSqliteDriver(
            url        = "jdbc:sqlite:${dbFile.absolutePath}",
            properties = Properties().apply { put("foreign_keys", "true") },
            schema     = AtaraxiaDatabase.Schema,
            migrateEmptySchema = !exists,
        )
        return driver
    }
}
