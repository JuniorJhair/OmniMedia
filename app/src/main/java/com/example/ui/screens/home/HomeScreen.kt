package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.MediaType
import com.example.core.model.NavSection
import com.example.data.local.entity.MediaFileEntity
import com.example.data.local.entity.PlaybackProgressEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PermissionPromptCard
import com.example.ui.components.formatDuration
import com.example.ui.theme.AudioAccent
import com.example.ui.theme.ImageAccent
import com.example.ui.theme.VaultAccent
import com.example.ui.theme.VideoAccent

@Composable
fun HomeScreen(
    videoCount: Int,
    audioCount: Int,
    imageCount: Int,
    hasPermissions: Boolean,
    unfinishedList: List<PlaybackProgressEntity>,
    recentlyPlayed: List<MediaFileEntity>,
    favorites: List<MediaFileEntity>,
    playlists: List<PlaylistEntity>,
    onNavigateToSection: (NavSection) -> Unit,
    onPlayMedia: (String) -> Unit,
    onViewPhoto: (MediaFileEntity) -> Unit = {},
    onPermissionsGranted: () -> Unit,
    onScanDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Permission Prompt Banner if needed
        if (!hasPermissions) {
            item {
                PermissionPromptCard(
                    onPermissionsGranted = onPermissionsGranted
                )
            }
        }

        // Quick Category Stats Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CategoryPill(
                    title = "Videos",
                    count = videoCount,
                    icon = Icons.Default.Movie,
                    color = VideoAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToSection(NavSection.VIDEOS) }
                )
                CategoryPill(
                    title = "Música",
                    count = audioCount,
                    icon = Icons.Default.Audiotrack,
                    color = AudioAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToSection(NavSection.MUSIC) }
                )
                CategoryPill(
                    title = "Fotos",
                    count = imageCount,
                    icon = Icons.Default.Image,
                    color = ImageAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToSection(NavSection.IMAGES) }
                )
                CategoryPill(
                    title = "Bóveda",
                    count = 0,
                    icon = Icons.Default.Lock,
                    color = VaultAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToSection(NavSection.VAULT) }
                )
            }
        }

        // Continue watching
        if (unfinishedList.isNotEmpty()) {
            item {
                SectionHeader(title = "Continuar viendo")
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(unfinishedList, key = { it.mediaUri }) { item ->
                        ContinueWatchingCard(
                            progress = item,
                            onClick = { onPlayMedia(item.mediaUri) }
                        )
                    }
                }
            }
        }

        // Favorites
        if (favorites.isNotEmpty()) {
            item {
                SectionHeader(title = "Favoritos")
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(favorites, key = { it.id }) { item ->
                        MiniMediaCard(
                            item = item,
                            onClick = {
                                if (item.mediaType == MediaType.IMAGE.name) {
                                    onViewPhoto(item)
                                } else {
                                    onPlayMedia(item.uri)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Recently Played
        if (recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader(title = "Reproducidos recientemente")
            }
            items(recentlyPlayed.take(8), key = { it.id }) { item ->
                RecentRowItem(
                    item = item,
                    onClick = {
                        if (item.mediaType == MediaType.IMAGE.name) {
                            onViewPhoto(item)
                        } else {
                            onPlayMedia(item.uri)
                        }
                    }
                )
            }
        }

        // Empty state when library is fresh
        if (videoCount == 0 && audioCount == 0 && imageCount == 0 && unfinishedList.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Default.PlayCircle,
                    title = "Centro multimedia listo",
                    subtitle = "Descubre tus videos, canciones y fotos locales con escaneo automático o selección manual.",
                    actionLabel = "Escanear dispositivo ahora",
                    onActionClick = onScanDevice
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun CategoryPill(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = color.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = if (title == "Bóveda") "PIN" else count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    progress: PlaybackProgressEntity,
    onClick: () -> Unit
) {
    val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainer
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = progress.mediaUri.substringAfterLast("/"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatDuration(progress.positionMs)} / ${formatDuration(progress.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MiniMediaCard(
    item: MediaFileEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when (item.mediaType) {
                        MediaType.AUDIO.name -> Icons.Default.Audiotrack
                        MediaType.IMAGE.name -> Icons.Default.Image
                        else -> Icons.Default.Movie
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.title.ifEmpty { item.displayName },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentRowItem(
    item: MediaFileEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (item.mediaType) {
                            MediaType.AUDIO.name -> Icons.Default.Audiotrack
                            MediaType.IMAGE.name -> Icons.Default.Image
                            else -> Icons.Default.Movie
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifEmpty { item.displayName },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.durationMs > 0) formatDuration(item.durationMs) else item.folderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
