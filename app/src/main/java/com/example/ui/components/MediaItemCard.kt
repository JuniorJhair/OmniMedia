package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.core.model.MediaType
import com.example.core.model.ViewMode
import com.example.data.local.entity.MediaFileEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaItemCard(
    item: MediaFileEntity,
    viewMode: ViewMode,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onProtectInVault: () -> Unit,
    onDelete: () -> Unit,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onShowInfo: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val border = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else null

    when (viewMode) {
        ViewMode.GRID -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .testTag("media_card_${item.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = border,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    ) {
                        MediaThumbnail(item = item)
                        if (item.durationMs > 0) {
                            DurationBadge(
                                durationMs = item.durationMs,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                            )
                        }
                        SelectionBadge(isSelected = isSelected, modifier = Modifier.align(Alignment.TopStart))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title.ifEmpty { item.displayName },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatFileSize(item.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                MediaOptionsMenu(
                    expanded = showMenu,
                    isFavorite = item.isFavorite,
                    showAddToPlaylist = item.mediaType != MediaType.IMAGE.name,
                    onDismiss = { showMenu = false },
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onProtectInVault = onProtectInVault,
                    onDelete = onDelete,
                    onShare = onShare,
                    onShowInfo = onShowInfo
                )
            }
        }
        ViewMode.COMPACT_GRID -> {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .testTag("media_card_${item.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = border,
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                ) {
                    MediaThumbnail(item = item)
                    if (item.durationMs > 0) {
                        DurationBadge(
                            durationMs = item.durationMs,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        )
                    }
                    SelectionBadge(isSelected = isSelected, modifier = Modifier.align(Alignment.TopStart))

                    // 3-dots menu icon for compact grid
                    Box(modifier = Modifier.align(Alignment.TopEnd)) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier
                                .padding(4.dp)
                                .size(28.dp)
                        ) {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Opciones",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                MediaOptionsMenu(
                    expanded = showMenu,
                    isFavorite = item.isFavorite,
                    showAddToPlaylist = item.mediaType != MediaType.IMAGE.name,
                    onDismiss = { showMenu = false },
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist,
                    onProtectInVault = onProtectInVault,
                    onDelete = onDelete,
                    onShare = onShare,
                    onShowInfo = onShowInfo
                )
            }
        }
        ViewMode.LIST, ViewMode.COMPACT_LIST -> {
            val isCompact = viewMode == ViewMode.COMPACT_LIST
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .testTag("media_card_${item.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = border,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (isCompact) 6.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 48.dp else 64.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        MediaThumbnail(item = item)
                        SelectionBadge(isSelected = isSelected, modifier = Modifier.align(Alignment.TopStart))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title.ifEmpty { item.displayName },
                            style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (item.durationMs > 0) {
                                Text(
                                    text = formatDuration(item.durationMs) + " • ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatFileSize(item.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.folderName.isNotEmpty()) {
                                Text(
                                    text = " • " + item.folderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opciones", modifier = Modifier.size(20.dp))
                        }
                        MediaOptionsMenu(
                            expanded = showMenu,
                            isFavorite = item.isFavorite,
                            showAddToPlaylist = item.mediaType != MediaType.IMAGE.name,
                            onDismiss = { showMenu = false },
                            onToggleFavorite = onToggleFavorite,
                            onAddToPlaylist = onAddToPlaylist,
                            onProtectInVault = onProtectInVault,
                            onDelete = onDelete,
                            onShare = onShare,
                            onShowInfo = onShowInfo
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionBadge(isSelected: Boolean, modifier: Modifier = Modifier) {
    if (isSelected) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier
                .padding(6.dp)
                .size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Seleccionado",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaThumbnail(item: MediaFileEntity) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val fallbackIcon = when (item.mediaType) {
            MediaType.VIDEO.name -> Icons.Default.Movie
            MediaType.AUDIO.name -> Icons.Default.Audiotrack
            else -> Icons.Default.Image
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        AsyncImage(
            model = item.uri,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun DurationBadge(durationMs: Long, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.75f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MediaOptionsMenu(
    expanded: Boolean,
    isFavorite: Boolean,
    showAddToPlaylist: Boolean = true,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onProtectInVault: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit = {},
    onShowInfo: () -> Unit = {}
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("Compartir") },
            onClick = {
                onDismiss()
                onShare()
            }
        )
        DropdownMenuItem(
            text = { Text("Información") },
            onClick = {
                onDismiss()
                onShowInfo()
            }
        )
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos") },
            onClick = {
                onDismiss()
                onToggleFavorite()
            }
        )
        if (showAddToPlaylist) {
            DropdownMenuItem(
                text = { Text("Añadir a playlist") },
                onClick = {
                    onDismiss()
                    onAddToPlaylist()
                }
            )
        }
        DropdownMenuItem(
            text = { Text("Proteger en Bóveda") },
            onClick = {
                onDismiss()
                onProtectInVault()
            }
        )
        DropdownMenuItem(
            text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
            onClick = {
                onDismiss()
                onDelete()
            }
        )
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format("%.1f %s", value, units[digitGroups.coerceAtMost(units.size - 1)])
}
