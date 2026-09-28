package com.example.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.core.model.NavSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OmniTopAppBar(
    currentSection: NavSection,
    onNavigateToSection: (NavSection) -> Unit,
    onSearchClick: () -> Unit,
    onScanClick: () -> Unit,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Text(
                text = currentSection.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        actions = {
            if (actions != null) {
                actions()
            }
            IconButton(
                onClick = onScanClick,
                modifier = Modifier.testTag("action_scan_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Actualizar biblioteca",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.testTag("action_search_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { onNavigateToSection(NavSection.PLAYLISTS) },
                modifier = Modifier.testTag("action_playlists_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistPlay,
                    contentDescription = "Playlists",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.testTag("action_more_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Más opciones",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Escanear dispositivo") },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onScanClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Historial") },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onNavigateToSection(NavSection.HISTORY)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Estadísticas") },
                    leadingIcon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onNavigateToSection(NavSection.STATS)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Configuración") },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onNavigateToSection(NavSection.SETTINGS)
                    }
                )
            }
        }
    )
}
