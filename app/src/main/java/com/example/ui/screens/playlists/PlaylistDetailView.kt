package com.example.ui.screens.playlists

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.MediaFileEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.ui.components.SongArtwork
import com.example.ui.components.formatDuration

@Composable
fun PlaylistDetailView(
    playlist: PlaylistEntity,
    songs: List<MediaFileEntity>,
    allAvailableSongs: List<MediaFileEntity>,
    currentPlayingUri: String?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlaySong: (MediaFileEntity) -> Unit,
    onRemoveSong: (mediaUri: String) -> Unit,
    onReorderSong: (fromIndex: Int, toIndex: Int) -> Unit,
    onRenamePlaylist: (newName: String, newDesc: String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onAddSongs: (List<String>) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(playlist.name) }
    var editDesc by remember { mutableStateOf(playlist.description) }

    val totalDurationMs = remember(songs) { songs.sumOf { it.durationMs } }

    Column(modifier = Modifier.fillMaxSize().testTag("playlist_detail_view")) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row {
                IconButton(onClick = {
                    editName = playlist.name
                    editDesc = playlist.description
                    showRenameDialog = true
                }) {
                    Icon(Icons.Default.Edit, contentDescription = "Renombrar")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
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
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PlaylistPlay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (playlist.description.isNotEmpty()) {
                            Text(
                                text = playlist.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${songs.size} canciones • ${formatDuration(totalDurationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onPlayAll,
                                enabled = songs.isNotEmpty(),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reproducir")
                            }
                            FilledTonalButton(
                                onClick = onShuffleAll,
                                enabled = songs.isNotEmpty(),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Aleatorio")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { showAddSongsDialog = true },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Añadir canciones")
                        }
                    }
                }
            }

            if (songs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Esta playlist está vacía. Añade canciones pulsando el botón superior.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(songs, key = { index, item -> "${item.uri}_$index" }) { index, song ->
                    val isCurrent = song.uri == currentPlayingUri

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPlaySong(song) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.width(28.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            SongArtwork(
                                uri = song.uri,
                                title = song.title,
                                modifier = Modifier.size(42.dp),
                                iconSize = 20.dp
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title.ifEmpty { song.displayName },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist.ifEmpty { "Artista desconocido" },
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
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            if (index > 0) {
                                IconButton(
                                    onClick = { onReorderSong(index, index - 1) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Mover arriba", modifier = Modifier.size(16.dp))
                                }
                            }
                            if (index < songs.size - 1) {
                                IconButton(
                                    onClick = { onReorderSong(index, index + 1) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Mover abajo", modifier = Modifier.size(16.dp))
                                }
                            }

                            IconButton(
                                onClick = { onRemoveSong(song.uri) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.RemoveCircleOutline,
                                    contentDescription = "Quitar de la lista",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("rename_playlist_input")
                    )
                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Descripción") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            onRenamePlaylist(editName.trim(), editDesc.trim())
                            showRenameDialog = false
                        }
                    },
                    enabled = editName.isNotBlank()
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar Playlist") },
            text = { Text("¿Deseas eliminar la playlist '${playlist.name}'? Los archivos de música originales no se eliminarán.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePlaylist()
                        showDeleteDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Picker dialog to add songs
    if (showAddSongsDialog) {
        val existingUris = remember(songs) { songs.map { it.uri }.toSet() }
        val candidateSongs = remember(allAvailableSongs, existingUris) {
            allAvailableSongs.filter { it.uri !in existingUris }
        }
        var selectedToAdd by remember { mutableStateOf(setOf<String>()) }

        AlertDialog(
            onDismissRequest = { showAddSongsDialog = false },
            title = { Text("Añadir canciones a la playlist") },
            text = {
                if (candidateSongs.isEmpty()) {
                    Text("Todas las canciones disponibles ya están en esta playlist.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(candidateSongs, key = { it.id }) { song ->
                            val isSelected = song.uri in selectedToAdd
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedToAdd = if (isSelected) {
                                            selectedToAdd - song.uri
                                        } else {
                                            selectedToAdd + song.uri
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedToAdd = if (checked) selectedToAdd + song.uri else selectedToAdd - song.uri
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title.ifEmpty { song.displayName },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist.ifEmpty { "Artista desconocido" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (candidateSongs.isNotEmpty()) {
                    Button(
                        onClick = {
                            if (selectedToAdd.isNotEmpty()) {
                                onAddSongs(selectedToAdd.toList())
                                showAddSongsDialog = false
                            }
                        },
                        enabled = selectedToAdd.isNotEmpty()
                    ) {
                        Text("Añadir (${selectedToAdd.size})")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSongsDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
