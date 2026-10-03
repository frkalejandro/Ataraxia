package com.ataraxia.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.*
import com.ataraxia.ui.agenda.AgendaScreen
import com.ataraxia.ui.dashboard.DashboardScreen
import com.ataraxia.ui.habits.HabitsScreen
import com.ataraxia.ui.journal.JournalScreen
import com.ataraxia.ui.sleep.SleepScreen
import com.ataraxia.ui.state.StateScreen
import com.ataraxia.ui.focus.*
import com.ataraxia.ui.exercise.ExerciseScreen

@Composable
fun AtaraxiaApp(agendaRequest: Int = 0) {
    val focus = rememberFocusController()
    CompositionLocalProvider(LocalFocusController provides focus) {
    MaterialTheme(colorScheme = ataraxiaColorScheme()) {
        TabNavigator(DashboardTab) { navigator ->
            LaunchedEffect(agendaRequest) { if (agendaRequest > 0) navigator.current = AgendaTab }
            val snackbar = remember { SnackbarHostState() }
            LaunchedEffect(focus.timer.awaitingNext) {
                if (focus.timer.awaitingNext) {
                    val result = snackbar.showSnackbar(
                        message = if (focus.timer.phase == com.ataraxia.domain.model.FocusPhase.FOCUS)
                            "Descanso terminado. Puedes comenzar otro bloque." else "¡Sesión completada! Es momento de descansar.",
                        actionLabel = "Ver enfoque", withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) navigator.current = FocusTab
                }
            }
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    BoxWithConstraints {
                    val navigationWidth = maxOf(maxWidth, 704.dp)
                    NavigationBar(modifier = androidx.compose.ui.Modifier.horizontalScroll(rememberScrollState()), tonalElevation = 0.dp) {
                      Row(androidx.compose.ui.Modifier.width(navigationWidth)) {
                        listOf(DashboardTab, HabitsTab, ExerciseTab, SleepTab, AgendaTab, FocusTab, JournalTab, StateTab).forEach { tab ->
                            val selected = navigator.current == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick  = { navigator.current = tab },
                                icon     = {
                                    Icon(
                                        if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.options.title,
                                    )
                                },
                                label = {
                                    Text(
                                        tab.options.title,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor   = Color(0xFF6C63FF),
                                    selectedTextColor   = Color(0xFF6C63FF),
                                    indicatorColor      = Color(0xFF6C63FF).copy(alpha = 0.12f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                      }
                    }
                    }
                }
            ) { padding ->
                Box(androidx.compose.ui.Modifier.padding(padding)) {
                    CurrentTab()
                }
            }
        }
    }
    }
}

object FocusTab : AtaraxiaTab {
    override val icon = Icons.Outlined.Timer
    override val selectedIcon = Icons.Filled.Timer
    override val options
        @Composable get() = TabOptions(index = 6u, title = "Enfoque")
    @Composable
    override fun Content() = FocusScreen()
}

object ExerciseTab : AtaraxiaTab {
    override val icon = Icons.Outlined.FitnessCenter
    override val selectedIcon = Icons.Filled.FitnessCenter
    override val options
        @Composable get() = TabOptions(index = 7u, title = "Ejercicio")
    @Composable
    override fun Content() = ExerciseScreen().Content()
}

private interface AtaraxiaTab : Tab {
    val icon: ImageVector
    val selectedIcon: ImageVector
}

object DashboardTab : AtaraxiaTab {
    override val icon         = Icons.Outlined.Home
    override val selectedIcon = Icons.Filled.Home
    override val options
        @Composable get() = TabOptions(index = 0u, title = "Inicio")
    @Composable
    override fun Content() = DashboardScreen().Content()
}

object HabitsTab : AtaraxiaTab {
    override val icon         = Icons.Outlined.AutoAwesome
    override val selectedIcon = Icons.Filled.AutoAwesome
    override val options
        @Composable get() = TabOptions(index = 1u, title = "Hábitos")
    @Composable
    override fun Content() = HabitsScreen().Content()
}

object SleepTab : AtaraxiaTab {
    override val icon         = Icons.Outlined.Bedtime
    override val selectedIcon = Icons.Filled.Bedtime
    override val options
        @Composable get() = TabOptions(index = 2u, title = "Sueño")
    @Composable
    override fun Content() = SleepScreen().Content()
}

object AgendaTab : AtaraxiaTab {
    override val icon         = Icons.Outlined.CalendarMonth
    override val selectedIcon = Icons.Filled.CalendarMonth
    override val options
        @Composable get() = TabOptions(index = 3u, title = "Agenda")
    @Composable
    override fun Content() = AgendaScreen().Content()
}

object JournalTab : AtaraxiaTab {
    override val icon         = Icons.Outlined.Book
    override val selectedIcon = Icons.Filled.Book
    override val options
        @Composable get() = TabOptions(index = 4u, title = "Diario")
    @Composable
    override fun Content() = JournalScreen().Content()
}

object StateTab : AtaraxiaTab {
    override val icon         = Icons.Outlined.MonitorHeart
    override val selectedIcon = Icons.Filled.MonitorHeart
    override val options
        @Composable get() = TabOptions(index = 5u, title = "Estado")
    @Composable
    override fun Content() = StateScreen().Content()
}

@Composable
private fun ataraxiaColorScheme(): ColorScheme {
    val isDark = isSystemInDarkTheme()
    return if (isDark) darkColorScheme(
        primary          = Color(0xFF8B83FF),
        onPrimary        = Color(0xFF1A1A2E),
        primaryContainer = Color(0xFF3D3580),
        surface          = Color(0xFF1E1E2E),
        background       = Color(0xFF16161F),
        surfaceVariant   = Color(0xFF2A2A3E),
        onSurface        = Color(0xFFE8E8F0),
        onBackground     = Color(0xFFE8E8F0),
        onSurfaceVariant = Color(0xFF9E9EB8),
    ) else lightColorScheme(
        primary          = Color(0xFF6C63FF),
        onPrimary        = Color.White,
        primaryContainer = Color(0xFFE8E6FF),
        surface          = Color(0xFFFFFFFF),
        background       = Color(0xFFF5F5FA),
        surfaceVariant   = Color(0xFFEEEEF8),
        onSurface        = Color(0xFF1A1A2E),
        onBackground     = Color(0xFF1A1A2E),
        onSurfaceVariant = Color(0xFF6E6E8A),
    )
}
