package com.example.ui.screens.playlists

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.components.EmptyStateView

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistEntity>,
    allAvailableSongs: List<MediaFileEntity>,
    currentPlayingUri: String?,
    onGetPlaylistSongs: (Long) -> List<MediaFileEntity>,
    onCreatePlaylist: (String, String) -> Unit,
    onRenamePlaylist: (Long, String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onPlaySong: (MediaFileEntity, List<MediaFileEntity>) -> Unit,
    onRemoveFromPlaylist: (playlistId: Long, uri: String) -> Unit,
    onReorderItem: (playlistId: Long, fromIndex: Int, toIndex: Int) -> Unit,
    onAddSongsToPlaylist: (playlistId: Long, uris: List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var newPlaylistDesc by remember { mutableStateOf("") }

    BackHandler(enabled = selectedPlaylistId != null) {
        selectedPlaylistId = null
    }

    if (selectedPlaylistId != null) {
        val currentPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
        if (currentPlaylist != null) {
            val plSongs = onGetPlaylistSongs(currentPlaylist.id)
            PlaylistDetailView(
                playlist = currentPlaylist,
                songs = plSongs,
                allAvailableSongs = allAvailableSongs,
                currentPlayingUri = currentPlayingUri,
                onBack = { selectedPlaylistId = null },
                onPlayAll = {
                    if (plSongs.isNotEmpty()) {
                        onPlaySong(plSongs.first(), plSongs)
                    }
                },
                onShuffleAll = {
                    if (plSongs.isNotEmpty()) {
                        val shuffled = plSongs.shuffled()
                        onPlaySong(shuffled.first(), shuffled)
                    }
                },
                onPlaySong = { s -> onPlaySong(s, plSongs) },
                onRemoveSong = { uri -> onRemoveFromPlaylist(currentPlaylist.id, uri) },
                onReorderSong = { from, to -> onReorderItem(currentPlaylist.id, from, to) },
                onRenamePlaylist = { newName, newDesc -> onRenamePlaylist(currentPlaylist.id, newName, newDesc) },
                onDeletePlaylist = {
                    onDeletePlaylist(currentPlaylist.id)
                    selectedPlaylistId = null
                },
                onAddSongs = { uris -> onAddSongsToPlaylist(currentPlaylist.id, uris) }
            )
            return
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag("playlists_screen")) {
        if (playlists.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.PlaylistPlay,
                title = "No tienes playlists todavía",
                subtitle = "Crea listas personalizadas para organizar tus videos y canciones favoritas.",
                actionLabel = "+ Crear playlist",
                onActionClick = { showCreateDialog = true }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedPlaylistId = playlist.id },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (playlist.description.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = playlist.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onDeletePlaylist(playlist.id) }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Eliminar playlist",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("create_playlist_fab"),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Crear playlist")
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCreateDialog = false
                    newPlaylistName = ""
                    newPlaylistDesc = ""
                },
                title = { Text("Nueva Playlist") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            label = { Text("Nombre de la playlist") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("playlist_name_input")
                        )
                        OutlinedTextField(
                            value = newPlaylistDesc,
                            onValueChange = { newPlaylistDesc = it },
                            label = { Text("Descripción (opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                onCreatePlaylist(newPlaylistName.trim(), newPlaylistDesc.trim())
                                showCreateDialog = false
                                newPlaylistName = ""
                                newPlaylistDesc = ""
                            }
                        },
                        enabled = newPlaylistName.isNotBlank()
                    ) {
                        Text("Crear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCreateDialog = false
                        newPlaylistName = ""
                        newPlaylistDesc = ""
                    }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
