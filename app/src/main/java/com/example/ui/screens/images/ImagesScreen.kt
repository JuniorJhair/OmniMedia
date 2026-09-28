package com.example.ui.screens.images

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesScreen(
    images: List<MediaFileEntity>,
    currentSortOption: SortOption,
    selectedFolder: String?,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSortOptionChange: (SortOption) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onViewImage: (Int, List<MediaFileEntity>) -> Unit,
    onToggleSelect: (String) -> Unit,
    onLongClickSelect: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onProtectInVault: (MediaFileEntity) -> Unit,
    onDeleteImage: (String) -> Unit,
    onPickImageFile: () -> Unit,
    onShareImage: (MediaFileEntity) -> Unit = {},
    onShowImageInfo: (MediaFileEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Todas", "Carpetas", "Favoritas")
    var showSortMenu by remember { mutableStateOf(false) }

    val sortedImages = remember(images, selectedFolder, selectedTab, currentSortOption) {
        val tabFiltered = when {
            selectedFolder != null -> images.filter { it.folderName == selectedFolder }
            selectedTab == 2 -> images.filter { it.isFavorite }
            else -> images
        }

        when (currentSortOption) {
            SortOption.NAME_ASC -> tabFiltered.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> tabFiltered.sortedByDescending { it.title.lowercase() }
            SortOption.DATE_DESC -> tabFiltered.sortedByDescending { it.dateAdded }
            SortOption.DATE_ASC -> tabFiltered.sortedBy { it.dateAdded }
            else -> tabFiltered
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag("images_screen")) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedFolder != null) {
                FolderHeaderBar(
                    folderName = selectedFolder,
                    itemCount = sortedImages.size,
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

            if (selectedTab == 1 && selectedFolder == null) {
                FolderGrid(
                    items = images,
                    onSelectFolder = { onSelectFolder(it) }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${sortedImages.size} imágenes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Ordenar")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            listOf(
                                SortOption.DATE_DESC,
                                SortOption.DATE_ASC,
                                SortOption.NAME_ASC,
                                SortOption.NAME_DESC
                            ).forEach { option ->
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
                }

                if (sortedImages.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.Image,
                        title = "No hay imágenes disponibles",
                        subtitle = "Añade fotografías para verlas con zoom suave, presentación y protección privada.",
                        actionLabel = "+ Añadir imágenes",
                        onActionClick = onPickImageFile
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        contentPadding = PaddingValues(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(sortedImages, key = { it.id }) { item ->
                            val isSelected = selectedUris.contains(item.uri)
                            MediaItemCard(
                                item = item,
                                viewMode = ViewMode.COMPACT_GRID,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) onToggleSelect(item.uri)
                                    else {
                                        val idx = sortedImages.indexOf(item).coerceAtLeast(0)
                                        onViewImage(idx, sortedImages)
                                    }
                                },
                                onLongClick = { onLongClickSelect(item.uri) },
                                onToggleFavorite = { onToggleFavorite(item.uri, !item.isFavorite) },
                                onAddToPlaylist = {},
                                onProtectInVault = { onProtectInVault(item) },
                                onDelete = { onDeleteImage(item.uri) },
                                onShare = { onShareImage(item) },
                                onShowInfo = { onShowImageInfo(item) }
                            )
                        }
                    }
                }
            }
        }

        if (!isSelectionMode) {
            FloatingActionButton(
                onClick = onPickImageFile,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("add_image_fab"),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir imágenes")
            }
        }
    }
}
