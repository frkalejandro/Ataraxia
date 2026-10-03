package com.ataraxia.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.ataraxia.di.sharedModules
import com.ataraxia.notifications.AndroidHabitNotificationScheduler
import com.ataraxia.notifications.AndroidAgendaNotificationScheduler
import com.ataraxia.ui.AtaraxiaApp
import com.ataraxia.ui.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

class AtaraxiaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@AtaraxiaApplication)
            modules(
                module { single { this@AtaraxiaApplication as Application } },
                *sharedModules.toTypedArray(),
                viewModelModule,
            )
        }

        // Activa los recordatorios diarios de hábitos al iniciar la aplicación.
        AndroidHabitNotificationScheduler(this).scheduleDailyReminders()
        AndroidAgendaNotificationScheduler(this).scheduleDailyReminders()
    }
}

class MainActivity : ComponentActivity() {
    private var agendaRequest by mutableStateOf(0)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* El usuario decide si permite o no las notificaciones. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        if (intent.getBooleanExtra("open_agenda", false)) agendaRequest++

        setContent {
            AtaraxiaApp(agendaRequest = agendaRequest)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_agenda", false)) agendaRequest++
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
