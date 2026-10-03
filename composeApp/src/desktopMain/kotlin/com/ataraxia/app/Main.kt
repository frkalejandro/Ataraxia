package com.ataraxia.app

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.ataraxia.data.db.DatabaseDriverFactory
import com.ataraxia.di.sharedModules
import com.ataraxia.ui.AtaraxiaApp
import com.ataraxia.ui.viewModelModule
import org.koin.core.context.startKoin
import org.koin.dsl.module

fun main() = application {
    startKoin {
        modules(
            module { single { DatabaseDriverFactory() } },
            *sharedModules.toTypedArray(),
            viewModelModule,
        )
    }
    Window(
        onCloseRequest = ::exitApplication,
        title          = "Ataraxia",
        state          = rememberWindowState(width = 420.dp, height = 820.dp),
    ) {
        AtaraxiaApp()
    }
}
