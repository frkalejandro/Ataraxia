package com.ataraxia.data.db

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ataraxia.db.AtaraxiaDatabase
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Crea el driver Android y repara de forma idempotente la tabla de Estado.
 *
 * Hubo dos bases distintas publicadas con user_version = 2:
 *  1. la versión antigua, sin DailyStateEntry;
 *  2. una instalación limpia de la primera versión de Estado, con DailyStateEntry.
 *
 * Por eso no podemos asumir que "versión 2" implica un único esquema. El callback
 * comprueba físicamente la tabla al actualizar y cada vez que se abre la base.
 */
actual class DatabaseDriverFactory : KoinComponent {
    private val app: Application by inject()

    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = AtaraxiaDatabase.Schema,
        context = app,
        name = DATABASE_NAME,
        callback = object : AndroidSqliteDriver.Callback(AtaraxiaDatabase.Schema) {

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int,
            ) {
                // El único cambio estructural 2 -> 3 es Estado. No delegamos esa
                // transición al migrador generado porque existen dos esquemas v2.
                if (oldVersion <= 2 && newVersion >= 3) {
                    ensureDailyStateSchema(db)
                    // Continuar desde v3 para aplicar también las migraciones nuevas.
                    if (newVersion > 3) super.onUpgrade(db, 3, newVersion)
                    return
                }

                super.onUpgrade(db, oldVersion, newVersion)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)

                // También repara instalaciones que hayan alcanzado user_version = 3
                // durante una prueba anterior, pero hayan quedado sin la tabla.
                ensureDailyStateSchema(db)
            }
        },
    )

    private fun ensureDailyStateSchema(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_DAILY_STATE_TABLE)
        db.execSQL(CREATE_DAILY_STATE_DATE_INDEX)
    }

    private companion object {
        const val DATABASE_NAME = "ataraxia.db"

        val CREATE_DAILY_STATE_TABLE = """
            CREATE TABLE IF NOT EXISTS DailyStateEntry (
                id                   TEXT    NOT NULL PRIMARY KEY,
                date                 TEXT    NOT NULL UNIQUE,
                energy               INTEGER NOT NULL,
                mood                 INTEGER NOT NULL,
                stress               INTEGER NOT NULL,
                trained              INTEGER NOT NULL DEFAULT 0,
                breakfast            TEXT    NOT NULL DEFAULT '',
                lunch                TEXT    NOT NULL DEFAULT '',
                tea                  TEXT    NOT NULL DEFAULT '',
                dinner               TEXT    NOT NULL DEFAULT '',
                otherFood            TEXT    NOT NULL DEFAULT '',
                waterGlasses         INTEGER NOT NULL DEFAULT 0,
                musclePain           INTEGER NOT NULL,
                fatigue              INTEGER NOT NULL,
                libido               INTEGER,
                morningErection      TEXT    NOT NULL DEFAULT 'NOT_ANSWERED',
                deepStudyMinutes     INTEGER NOT NULL DEFAULT 0,
                concentration        INTEGER NOT NULL,
                createdAt            INTEGER NOT NULL,
                updatedAt            INTEGER NOT NULL
            )
        """.trimIndent()

        val CREATE_DAILY_STATE_DATE_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_daily_state_date
            ON DailyStateEntry(date)
        """.trimIndent()
    }
}
