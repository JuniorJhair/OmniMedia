package com.example.ui.screens.music

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.MediaFileEntity
import com.example.ui.components.MusicWaveformIndicator
import com.example.ui.components.SongArtwork
import com.example.ui.components.formatDuration

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRowItem(
    song: MediaFileEntity,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShowTechInfo: () -> Unit,
    onProtectInVault: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit = {},
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val border = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onLongClick?.invoke()
                    else onPlay()
                },
                onLongClick = onLongClick
            )
            .testTag("song_item_${song.id}"),
        shape = RoundedCornerShape(10.dp),
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else if (isCurrentPlaying) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode || isSelected) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
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
            SongArtwork(
                uri = song.uri,
                title = song.title,
                modifier = Modifier.size(48.dp),
                iconSize = 24.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrentPlaying) {
                        MusicWaveformIndicator(isPlaying = isPlaying, modifier = Modifier.padding(end = 6.dp))
                    }
                    Text(
                        text = song.title.ifEmpty { song.displayName },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val artistAlbum = buildString {
                    val art = song.artist.ifEmpty { "Artista desconocido" }
                    append(art)
                    if (song.album.isNotEmpty()) {
                        append(" • ")
                        append(song.album)
                    }
                }
                Text(
                    text = artistAlbum,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = formatDuration(song.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (song.isFavorite) "Quitar de favoritos" else "Marcar como favorito",
                    tint = if (song.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", modifier = Modifier.size(18.dp))
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Reproducir") },
                        onClick = { showMenu = false; onPlay() }
                    )
                    DropdownMenuItem(
                        text = { Text("Reproducir siguiente") },
                        onClick = { showMenu = false; onPlayNext() }
                    )
                    DropdownMenuItem(
                        text = { Text("Añadir a la cola") },
                        onClick = { showMenu = false; onAddToQueue() }
                    )
                    DropdownMenuItem(
                        text = { Text("Añadir a playlist") },
                        onClick = { showMenu = false; onAddToPlaylist() }
                    )
                    DropdownMenuItem(
                        text = { Text("Información técnica") },
                        onClick = { showMenu = false; onShowTechInfo() }
                    )
                    DropdownMenuItem(
                        text = { Text("Compartir") },
                        onClick = { showMenu = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text("Proteger en Bóveda") },
                        onClick = { showMenu = false; onProtectInVault() }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistDetailView(
    artistName: String,
    songs: List<MediaFileEntity>,
    currentPlayingUri: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlaySong: (MediaFileEntity) -> Unit,
    onPlayNext: (MediaFileEntity) -> Unit,
    onAddToQueue: (MediaFileEntity) -> Unit,
    onAddToPlaylist: (MediaFileEntity) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onShowTechInfo: (MediaFileEntity) -> Unit,
    onSelectAlbum: (String) -> Unit
) {
    val albums = remember(songs) {
        songs.map { it.album.ifEmpty { "Álbum desconocido" } }.distinct()
    }
    val totalDurationMs = remember(songs) { songs.sumOf { it.durationMs } }

    Column(modifier = Modifier.fillMaxSize().testTag("artist_detail_view")) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                text = "Artista",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(90.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${songs.size} canciones • ${albums.size} álbumes • ${formatDuration(totalDurationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onPlayAll,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reproducir")
                            }
                            FilledTonalButton(
                                onClick = onShuffleAll,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aleatorio")
                            }
                        }
                    }
                }
            }

            // Albums section
            if (albums.isNotEmpty()) {
                item {
                    Text(
                        text = "Álbumes (${albums.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        albums.take(4).forEach { alb ->
                            OutlinedButton(
                                onClick = { onSelectAlbum(alb) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(alb, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // Songs title
            item {
                Text(
                    text = "Canciones",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Songs list
            items(songs, key = { it.id }) { song ->
                SongRowItem(
                    song = song,
                    isCurrentPlaying = song.uri == currentPlayingUri,
                    isPlaying = isPlaying,
                    onPlay = { onPlaySong(song) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onToggleFavorite = { onToggleFavorite(song.uri, !song.isFavorite) },
                    onShowTechInfo = { onShowTechInfo(song) },
                    onProtectInVault = {},
                    onDelete = {}
                )
            }
        }
    }
}

@Composable
fun AlbumDetailView(
    albumName: String,
    songs: List<MediaFileEntity>,
    currentPlayingUri: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlaySong: (MediaFileEntity) -> Unit,
    onPlayNext: (MediaFileEntity) -> Unit,
    onAddToQueue: (MediaFileEntity) -> Unit,
    onAddToPlaylist: (MediaFileEntity) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onShowTechInfo: (MediaFileEntity) -> Unit
) {
    val artistName = remember(songs) {
        songs.firstOrNull()?.artist?.ifEmpty { "Varios Artistas" } ?: "Artista desconocido"
    }
    val totalDurationMs = remember(songs) { songs.sumOf { it.durationMs } }
    val representativeUri = remember(songs) { songs.firstOrNull()?.uri ?: "" }

    Column(modifier = Modifier.fillMaxSize().testTag("album_detail_view")) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                text = "Álbum",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header card
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SongArtwork(
                            uri = representativeUri,
                            title = albumName,
                            modifier = Modifier.size(130.dp),
                            iconSize = 56.dp,
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${songs.size} pistas • ${formatDuration(totalDurationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onPlayAll,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reproducir")
                            }
                            FilledTonalButton(
                                onClick = onShuffleAll,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aleatorio")
                            }
                        }
                    }
                }
            }

            // Songs title
            item {
                Text(
                    text = "Pistas del álbum",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Track list
            itemsIndexed(songs, key = { index, s -> "${s.id}_$index" }) { index, song ->
                SongRowItem(
                    song = song,
                    isCurrentPlaying = song.uri == currentPlayingUri,
                    isPlaying = isPlaying,
                    onPlay = { onPlaySong(song) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onToggleFavorite = { onToggleFavorite(song.uri, !song.isFavorite) },
                    onShowTechInfo = { onShowTechInfo(song) },
                    onProtectInVault = {},
                    onDelete = {}
                )
            }
        }
    }
}

@Composable
fun GenreDetailView(
    genreName: String,
    songs: List<MediaFileEntity>,
    currentPlayingUri: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlaySong: (MediaFileEntity) -> Unit,
    onPlayNext: (MediaFileEntity) -> Unit,
    onAddToQueue: (MediaFileEntity) -> Unit,
    onAddToPlaylist: (MediaFileEntity) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onShowTechInfo: (MediaFileEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().testTag("genre_detail_view")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                text = "Género: $genreName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = genreName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${songs.size} canciones en este género",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = onPlayAll, shape = RoundedCornerShape(20.dp)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reproducir todo")
                            }
                            FilledTonalButton(onClick = onShuffleAll, shape = RoundedCornerShape(20.dp)) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aleatorio")
                            }
                        }
                    }
                }
            }

            items(songs, key = { it.id }) { song ->
                SongRowItem(
                    song = song,
                    isCurrentPlaying = song.uri == currentPlayingUri,
                    isPlaying = isPlaying,
                    onPlay = { onPlaySong(song) },
                    onPlayNext = { onPlayNext(song) },
                    onAddToQueue = { onAddToQueue(song) },
                    onAddToPlaylist = { onAddToPlaylist(song) },
                    onToggleFavorite = { onToggleFavorite(song.uri, !song.isFavorite) },
                    onShowTechInfo = { onShowTechInfo(song) },
                    onProtectInVault = {},
                    onDelete = {}
                )
            }
        }
    }
}
