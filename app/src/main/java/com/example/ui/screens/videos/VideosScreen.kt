package com.example.ui.screens.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.model.SortOption
import com.example.core.model.ViewMode
import com.example.data.local.entity.MediaFileEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FolderGrid
import com.example.ui.components.FolderHeaderBar
import com.example.ui.components.MediaItemCard

enum class DurationFilter(val label: String) {
    ALL("Todos"),
    SHORT("< 5 min"),
    MEDIUM("5-30 min"),
    LONG("> 30 min")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(
    videos: List<MediaFileEntity>,
    currentViewMode: ViewMode,
    currentSortOption: SortOption,
    selectedFolder: String?,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onViewModeChange: (ViewMode) -> Unit,
    onSortOptionChange: (SortOption) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onPlayVideo: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onLongClickSelect: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onProtectInVault: (MediaFileEntity) -> Unit,
    onDeleteVideo: (String) -> Unit,
    onPickVideoFile: () -> Unit,
    onShareVideo: (MediaFileEntity) -> Unit = {},
    onShowVideoInfo: (MediaFileEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Todos", "Carpetas", "Favoritos", "Recientes")
    var showSortMenu by remember { mutableStateOf(false) }
    var durationFilter by remember { mutableStateOf(DurationFilter.ALL) }
    var showDurationMenu by remember { mutableStateOf(false) }

    // If a folder was selected, show folder items
    val sortedVideos = remember(videos, selectedFolder, selectedTab, durationFilter, currentSortOption) {
        val tabFiltered = when {
            selectedFolder != null -> videos.filter { it.folderName == selectedFolder }
            selectedTab == 2 -> videos.filter { it.isFavorite }
            selectedTab == 3 -> videos.sortedByDescending { it.lastPlayedTimestamp }
            else -> videos
        }

        val durationFiltered = when (durationFilter) {
            DurationFilter.ALL -> tabFiltered
            DurationFilter.SHORT -> tabFiltered.filter { it.durationMs in 1..299_999 }
            DurationFilter.MEDIUM -> tabFiltered.filter { it.durationMs in 300_000..1_799_999 }
            DurationFilter.LONG -> tabFiltered.filter { it.durationMs >= 1_800_000 }
        }

        when (currentSortOption) {
            SortOption.NAME_ASC -> durationFiltered.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> durationFiltered.sortedByDescending { it.title.lowercase() }
            SortOption.DATE_DESC -> durationFiltered.sortedByDescending { it.dateAdded }
            SortOption.DATE_ASC -> durationFiltered.sortedBy { it.dateAdded }
            SortOption.SIZE_DESC -> durationFiltered.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> durationFiltered.sortedBy { it.sizeBytes }
            SortOption.DURATION_DESC -> durationFiltered.sortedByDescending { it.durationMs }
            SortOption.DURATION_ASC -> durationFiltered.sortedBy { it.durationMs }
            else -> durationFiltered.sortedByDescending { it.dateAdded }
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag("videos_screen")) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedFolder != null) {
                FolderHeaderBar(
                    folderName = selectedFolder,
                    itemCount = sortedVideos.size,
                    onBack = { onSelectFolder(null) }
                )
            } else {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }

            // If Carpetas tab is selected and no specific folder opened, show FolderGrid
            if (selectedTab == 1 && selectedFolder == null) {
                FolderGrid(
                    items = videos,
                    onSelectFolder = { onSelectFolder(it) }
                )
            } else {
                // Toolbar: Count, Duration Filter, ViewMode & Sort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${sortedVideos.size} videos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Duration Filter
                        Box {
                            IconButton(onClick = { showDurationMenu = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filtro duración")
                            }
                            DropdownMenu(
                                expanded = showDurationMenu,
                                onDismissRequest = { showDurationMenu = false }
                            ) {
                                DurationFilter.values().forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.label) },
                                        onClick = {
                                            durationFilter = filter
                                            showDurationMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Sort Menu
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Ordenar")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOption.values().forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.displayName) },
                                        onClick = {
                                            onSortOptionChange(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Cycle view mode
                        IconButton(
                            onClick = {
                                val nextMode = when (currentViewMode) {
                                ViewMode.GRID -> ViewMode.COMPACT_GRID
                                ViewMode.COMPACT_GRID -> ViewMode.LIST
                                ViewMode.LIST -> ViewMode.COMPACT_LIST
                                ViewMode.COMPACT_LIST -> ViewMode.GRID
                            }
                                onViewModeChange(nextMode)
                            }
                        ) {
                            val icon = when (currentViewMode) {
                                ViewMode.GRID -> Icons.Default.GridView
                                ViewMode.COMPACT_GRID -> Icons.Default.ViewCompact
                                ViewMode.LIST -> Icons.Default.ViewList
                                ViewMode.COMPACT_LIST -> Icons.Default.ViewAgenda
                            }
                            Icon(icon, contentDescription = "Cambiar vista")
                        }
                    }
                }

                if (sortedVideos.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.Movie,
                        title = if (durationFilter != DurationFilter.ALL) "Sin coincidencias de filtro" else "No hay videos disponibles",
                        subtitle = if (durationFilter != DurationFilter.ALL) "Prueba seleccionando 'Todos' en el filtro de duración."
                        else "Concede permisos o añade videos para comenzar a disfrutar de tu biblioteca.",
                        actionLabel = if (durationFilter != DurationFilter.ALL) "Ver todos" else "+ Añadir video",
                        onActionClick = {
                            if (durationFilter != DurationFilter.ALL) durationFilter = DurationFilter.ALL
                            else onPickVideoFile()
                        }
                    )
                } else {
                    when (currentViewMode) {
                        ViewMode.GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 160.dp),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(sortedVideos, key = { it.id }) { item ->
                                    val isSelected = selectedUris.contains(item.uri)
                                    MediaItemCard(
                                        item = item,
                                        viewMode = ViewMode.GRID,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) onToggleSelect(item.uri)
                                            else onPlayVideo(item.uri)
                                        },
                                        onLongClick = { onLongClickSelect(item.uri) },
                                        onToggleFavorite = { onToggleFavorite(item.uri, !item.isFavorite) },
                                        onAddToPlaylist = { onAddToPlaylist(item.uri) },
                                        onProtectInVault = { onProtectInVault(item) },
                                        onDelete = { onDeleteVideo(item.uri) },
                                        onShare = { onShareVideo(item) },
                                        onShowInfo = { onShowVideoInfo(item) }
                                    )
                                }
                            }
                        }
                        ViewMode.COMPACT_GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 110.dp),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(sortedVideos, key = { it.id }) { item ->
                                    val isSelected = selectedUris.contains(item.uri)
                                    MediaItemCard(
                                        item = item,
                                        viewMode = ViewMode.COMPACT_GRID,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) onToggleSelect(item.uri)
                                            else onPlayVideo(item.uri)
                                        },
                                        onLongClick = { onLongClickSelect(item.uri) },
                                        onToggleFavorite = { onToggleFavorite(item.uri, !item.isFavorite) },
                                        onAddToPlaylist = { onAddToPlaylist(item.uri) },
                                        onProtectInVault = { onProtectInVault(item) },
                                        onDelete = { onDeleteVideo(item.uri) },
                                        onShare = { onShareVideo(item) },
                                        onShowInfo = { onShowVideoInfo(item) }
                                    )
                                }
                            }
                        }
                        ViewMode.LIST, ViewMode.COMPACT_LIST -> {
                            LazyColumn(
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(sortedVideos, key = { it.id }) { item ->
                                    val isSelected = selectedUris.contains(item.uri)
                                    MediaItemCard(
                                        item = item,
                                        viewMode = currentViewMode,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        onClick = {
                                            if (isSelectionMode) onToggleSelect(item.uri)
                                            else onPlayVideo(item.uri)
                                        },
                                        onLongClick = { onLongClickSelect(item.uri) },
                                        onToggleFavorite = { onToggleFavorite(item.uri, !item.isFavorite) },
                                        onAddToPlaylist = { onAddToPlaylist(item.uri) },
                                        onProtectInVault = { onProtectInVault(item) },
                                        onDelete = { onDeleteVideo(item.uri) },
                                        onShare = { onShareVideo(item) },
                                        onShowInfo = { onShowVideoInfo(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isSelectionMode) {
            FloatingActionButton(
                onClick = onPickVideoFile,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_video_fab"),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir video")
            }
        }
    }
}
