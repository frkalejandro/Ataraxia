package com.ataraxia.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.ataraxia.notifications.TimerNotificationScheduler
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@Composable
fun TimerNotificationHint() {
    val scheduler: TimerNotificationScheduler = koinInject()
    if (scheduler.supported) {
        var exactAllowed by remember { mutableStateOf(scheduler.exactAlarmsAllowed) }
        LaunchedEffect(scheduler) {
            while (true) {
                exactAllowed = scheduler.exactAlarmsAllowed
                delay(1000)
            }
        }
        Text("Verás la cuenta regresiva en las notificaciones y recibirás un aviso con sonido al terminar. Permite las notificaciones y el sonido en los ajustes de Android.",
            style = MaterialTheme.typography.bodySmall)
        if (!exactAllowed) {
            Text("Para recibir el aviso a tiempo con la app en segundo plano, permite las alarmas exactas.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = scheduler::requestExactAlarmPermission) { Text("Permitir alarmas exactas") }
        }
    }
}
