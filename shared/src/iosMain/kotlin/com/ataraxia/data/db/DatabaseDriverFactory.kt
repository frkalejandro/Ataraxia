package com.ataraxia.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.ataraxia.db.AtaraxiaDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(AtaraxiaDatabase.Schema, "ataraxia.db")
}
