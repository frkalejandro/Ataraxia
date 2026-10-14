package com.ataraxia.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

/** El contenedor de pestañas ya reserva el espacio de la barra de estado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionTopAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}
