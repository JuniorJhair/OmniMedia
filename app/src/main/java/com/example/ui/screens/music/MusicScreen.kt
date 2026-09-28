package com.example.ui.screens.music

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.core.model.SortOption
import com.example.core.model.ViewMode
import com.example.data.local.entity.MediaFileEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.player.model.MediaInfoDetails
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.EmptyStateView
import com.example.ui.components.FolderGrid
import com.example.ui.components.FolderHeaderBar
import com.example.ui.components.SongArtwork
import com.example.ui.components.TechnicalInfoDialog
import com.example.ui.components.formatDuration
import com.example.ui.screens.playlists.PlaylistDetailView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    songs: List<MediaFileEntity>,
    playlists: List<PlaylistEntity>,
    currentPlayingUri: String?,
    isPlaying: Boolean,
    currentSortOption: SortOption,
    selectedFolder: String?,
    selectedUris: Set<String>,
    isSelectionMode: Boolean,
    onSortOptionChange: (SortOption) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onPlaySong: (MediaFileEntity, List<MediaFileEntity>) -> Unit,
    onPlayNext: (MediaFileEntity) -> Unit,
    onAddToQueue: (MediaFileEntity) -> Unit,
    onAddToPlaylist: (playlistId: Long, uri: String) -> Unit,
    onCreatePlaylistAndAdd: (name: String, desc: String, uri: String) -> Unit,
    onCreatePlaylist: (name: String, desc: String) -> Unit,
    onRenamePlaylist: (id: Long, name: String, desc: String) -> Unit,
    onDeletePlaylist: (id: Long) -> Unit,
    onRemoveFromPlaylist: (playlistId: Long, uri: String) -> Unit,
    onReorderPlaylistItem: (playlistId: Long, fromIndex: Int, toIndex: Int) -> Unit,
    onAddMultipleToPlaylist: (playlistId: Long, uris: List<String>) -> Unit,
    onGetPlaylistSongs: (playlistId: Long) -> List<MediaFileEntity>,
    onToggleSelect: (String) -> Unit,
    onLongClickSelect: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onProtectInVault: (MediaFileEntity) -> Unit,
    onDeleteSong: (String) -> Unit,
    onPickAudioFile: () -> Unit,
    onShareSong: (MediaFileEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Canciones", "Artistas", "Álbumes", "Géneros", "Playlists", "Favoritos", "Carpetas")

    var isGridView by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var searchFilter by remember { mutableStateOf("") }

    // Navigation sub-views within MusicScreen
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }

    // Dialogs
    var songForPlaylist by remember { mutableStateOf<MediaFileEntity?>(null) }
    var techInfoSong by remember { mutableStateOf<MediaFileEntity?>(null) }

    // BackHandler for sub-views
    BackHandler(enabled = selectedArtist != null || selectedAlbum != null || selectedGenre != null || selectedPlaylistId != null || selectedFolder != null) {
        when {
            selectedArtist != null -> selectedArtist = null
            selectedAlbum != null -> selectedAlbum = null
            selectedGenre != null -> selectedGenre = null
            selectedPlaylistId != null -> selectedPlaylistId = null
            selectedFolder != null -> onSelectFolder(null)
        }
    }

    // Detail Sub-views
    if (selectedArtist != null) {
        val artistSongs = songs.filter { (it.artist.ifEmpty { "Artista desconocido" }) == selectedArtist }
        ArtistDetailView(
            artistName = selectedArtist!!,
            songs = artistSongs,
            currentPlayingUri = currentPlayingUri,
            isPlaying = isPlaying,
            onBack = { selectedArtist = null },
            onPlayAll = {
                if (artistSongs.isNotEmpty()) {
                    onPlaySong(artistSongs.first(), artistSongs)
                }
            },
            onShuffleAll = {
                if (artistSongs.isNotEmpty()) {
                    val shuffled = artistSongs.shuffled()
                    onPlaySong(shuffled.first(), shuffled)
                }
            },
            onPlaySong = { s -> onPlaySong(s, artistSongs) },
            onPlayNext = { s -> onPlayNext(s) },
            onAddToQueue = { s -> onAddToQueue(s) },
            onAddToPlaylist = { s -> songForPlaylist = s },
            onToggleFavorite = onToggleFavorite,
            onShowTechInfo = { s -> techInfoSong = s },
            onSelectAlbum = { alb ->
                selectedArtist = null
                selectedAlbum = alb
            }
        )
        return
    }

    if (selectedAlbum != null) {
        val albumSongs = songs.filter { (it.album.ifEmpty { "Álbum desconocido" }) == selectedAlbum }
        AlbumDetailView(
            albumName = selectedAlbum!!,
            songs = albumSongs,
            currentPlayingUri = currentPlayingUri,
            isPlaying = isPlaying,
            onBack = { selectedAlbum = null },
            onPlayAll = {
                if (albumSongs.isNotEmpty()) {
                    onPlaySong(albumSongs.first(), albumSongs)
                }
            },
            onShuffleAll = {
                if (albumSongs.isNotEmpty()) {
                    val shuffled = albumSongs.shuffled()
                    onPlaySong(shuffled.first(), shuffled)
                }
            },
            onPlaySong = { s -> onPlaySong(s, albumSongs) },
            onPlayNext = { s -> onPlayNext(s) },
            onAddToQueue = { s -> onAddToQueue(s) },
            onAddToPlaylist = { s -> songForPlaylist = s },
            onToggleFavorite = onToggleFavorite,
            onShowTechInfo = { s -> techInfoSong = s }
        )
        return
    }

    if (selectedGenre != null) {
        val genreSongs = songs.filter { it.genre.equals(selectedGenre, ignoreCase = true) }
        GenreDetailView(
            genreName = selectedGenre!!,
            songs = genreSongs,
            currentPlayingUri = currentPlayingUri,
            isPlaying = isPlaying,
            onBack = { selectedGenre = null },
            onPlayAll = {
                if (genreSongs.isNotEmpty()) {
                    onPlaySong(genreSongs.first(), genreSongs)
                }
            },
            onShuffleAll = {
                if (genreSongs.isNotEmpty()) {
                    val shuffled = genreSongs.shuffled()
                    onPlaySong(shuffled.first(), shuffled)
                }
            },
            onPlaySong = { s -> onPlaySong(s, genreSongs) },
            onPlayNext = { s -> onPlayNext(s) },
            onAddToQueue = { s -> onAddToQueue(s) },
            onAddToPlaylist = { s -> songForPlaylist = s },
            onToggleFavorite = onToggleFavorite,
            onShowTechInfo = { s -> techInfoSong = s }
        )
        return
    }

    if (selectedPlaylistId != null) {
        val currentPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
        if (currentPlaylist != null) {
            val plSongs = onGetPlaylistSongs(currentPlaylist.id)
            PlaylistDetailView(
                playlist = currentPlaylist,
                songs = plSongs,
                allAvailableSongs = songs,
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
                onReorderSong = { from, to -> onReorderPlaylistItem(currentPlaylist.id, from, to) },
                onRenamePlaylist = { newName, newDesc -> onRenamePlaylist(currentPlaylist.id, newName, newDesc) },
                onDeletePlaylist = {
                    onDeletePlaylist(currentPlaylist.id)
                    selectedPlaylistId = null
                },
                onAddSongs = { uris -> onAddMultipleToPlaylist(currentPlaylist.id, uris) }
            )
            return
        }
    }

    // Main Music Screen
    Box(modifier = modifier.fillMaxSize().testTag("music_screen")) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedFolder != null) {
                FolderHeaderBar(
                    folderName = selectedFolder,
                    itemCount = songs.count { it.folderName == selectedFolder },
                    onBack = { onSelectFolder(null) }
                )
            } else {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    edgePadding = 16.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> { // Canciones
                    SongsTabContent(
                        songs = songs,
                        currentSortOption = currentSortOption,
                        isGridView = isGridView,
                        currentPlayingUri = currentPlayingUri,
                        isPlaying = isPlaying,
                        isSelectionMode = isSelectionMode,
                        selectedUris = selectedUris,
                        onToggleSelect = onToggleSelect,
                        onLongClickSelect = onLongClickSelect,
                        onSortChange = onSortOptionChange,
                        onToggleGridView = { isGridView = !isGridView },
                        onPlaySong = { song, list -> onPlaySong(song, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = { s -> songForPlaylist = s },
                        onToggleFavorite = onToggleFavorite,
                        onShowTechInfo = { s -> techInfoSong = s },
                        onProtectInVault = onProtectInVault,
                        onDeleteSong = onDeleteSong,
                        onPickAudioFile = onPickAudioFile,
                        onShareSong = onShareSong
                    )
                }
                1 -> { // Artistas
                    ArtistsTabContent(
                        songs = songs,
                        onSelectArtist = { selectedArtist = it }
                    )
                }
                2 -> { // Álbumes
                    AlbumsTabContent(
                        songs = songs,
                        onSelectAlbum = { selectedAlbum = it }
                    )
                }
                3 -> { // Géneros
                    GenresTabContent(
                        songs = songs,
                        onSelectGenre = { selectedGenre = it }
                    )
                }
                4 -> { // Playlists
                    PlaylistsTabContent(
                        playlists = playlists,
                        onSelectPlaylist = { selectedPlaylistId = it },
                        onCreatePlaylist = onCreatePlaylist
                    )
                }
                5 -> { // Favoritos
                    val favoriteSongs = songs.filter { it.isFavorite }
                    if (favoriteSongs.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.Audiotrack,
                            title = "No tienes canciones favoritas",
                            subtitle = "Toca el corazón en cualquier canción para guardarla aquí.",
                            actionLabel = "+ Explorar canciones",
                            onActionClick = { selectedTab = 0 }
                        )
                    } else {
                        SongsTabContent(
                            songs = favoriteSongs,
                            currentSortOption = currentSortOption,
                            isGridView = isGridView,
                            currentPlayingUri = currentPlayingUri,
                            isPlaying = isPlaying,
                            isSelectionMode = isSelectionMode,
                            selectedUris = selectedUris,
                            onToggleSelect = onToggleSelect,
                            onLongClickSelect = onLongClickSelect,
                            onSortChange = onSortOptionChange,
                            onToggleGridView = { isGridView = !isGridView },
                            onPlaySong = { song, list -> onPlaySong(song, list) },
                            onPlayNext = onPlayNext,
                            onAddToQueue = onAddToQueue,
                            onAddToPlaylist = { s -> songForPlaylist = s },
                            onToggleFavorite = onToggleFavorite,
                            onShowTechInfo = { s -> techInfoSong = s },
                            onProtectInVault = onProtectInVault,
                            onDeleteSong = onDeleteSong,
                            onPickAudioFile = onPickAudioFile,
                            onShareSong = onShareSong
                        )
                    }
                }
                6 -> { // Carpetas
                    FolderGrid(
                        items = songs,
                        onSelectFolder = { onSelectFolder(it) }
                    )
                }
            }
        }
    }

    // Dialog for adding song to playlist
    if (songForPlaylist != null) {
        val targetSong = songForPlaylist!!
        AddToPlaylistDialog(
            playlists = playlists,
            onSelectPlaylist = { plId ->
                onAddToPlaylist(plId, targetSong.uri)
                songForPlaylist = null
            },
            onCreateAndAdd = { name, desc ->
                onCreatePlaylistAndAdd(name, desc, targetSong.uri)
                songForPlaylist = null
            },
            onDismiss = { songForPlaylist = null }
        )
    }

    // Technical information dialog
    if (techInfoSong != null) {
        val s = techInfoSong!!
        val context = androidx.compose.ui.platform.LocalContext.current
        val details = remember(s.uri) {
            var extractedBitrate = 0L
            var extractedSampleRate = 0
            val mmr = android.media.MediaMetadataRetriever()
            try {
                val parsed = android.net.Uri.parse(s.uri)
                if (s.uri.startsWith("content://") || s.uri.startsWith("file://")) {
                    mmr.setDataSource(context, parsed)
                } else {
                    mmr.setDataSource(s.uri)
                }
                val brStr = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                extractedBitrate = brStr?.toLongOrNull() ?: 0L
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val srStr = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    extractedSampleRate = srStr?.toIntOrNull() ?: 0
                }
            } catch (_: Exception) {
            } finally {
                try { mmr.release() } catch (_: Exception) {}
            }

            MediaInfoDetails(
                title = s.title.ifEmpty { s.displayName },
                uri = s.uri,
                sizeBytes = s.sizeBytes,
                durationMs = s.durationMs,
                width = s.width,
                height = s.height,
                mimeType = s.mimeType,
                fps = 0f,
                bitrate = extractedBitrate,
                audioTracksCount = 1,
                subtitleTracksCount = 0,
                sampleRate = extractedSampleRate,
                channelCount = if (extractedSampleRate > 0) 2 else 0,
                audioMimeType = s.mimeType,
                artist = s.artist,
                album = s.album,
                genre = s.genre
            )
        }

        TechnicalInfoDialog(
            mediaInfo = details,
            onDismiss = { techInfoSong = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongsTabContent(
    songs: List<MediaFileEntity>,
    currentSortOption: SortOption,
    isGridView: Boolean,
    currentPlayingUri: String?,
    isPlaying: Boolean,
    isSelectionMode: Boolean = false,
    selectedUris: Set<String> = emptySet(),
    onToggleSelect: (String) -> Unit = {},
    onLongClickSelect: (String) -> Unit = {},
    onSortChange: (SortOption) -> Unit,
    onToggleGridView: () -> Unit,
    onPlaySong: (MediaFileEntity, List<MediaFileEntity>) -> Unit,
    onPlayNext: (MediaFileEntity) -> Unit,
    onAddToQueue: (MediaFileEntity) -> Unit,
    onAddToPlaylist: (MediaFileEntity) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onShowTechInfo: (MediaFileEntity) -> Unit,
    onProtectInVault: (MediaFileEntity) -> Unit,
    onDeleteSong: (String) -> Unit,
    onPickAudioFile: () -> Unit,
    onShareSong: (MediaFileEntity) -> Unit = {}
) {
    var showSortMenu by remember { mutableStateOf(false) }

    val sortedSongs = remember(songs, currentSortOption) {
        when (currentSortOption) {
            SortOption.NAME_ASC -> songs.sortedBy { it.title.lowercase() }
            SortOption.NAME_DESC -> songs.sortedByDescending { it.title.lowercase() }
            SortOption.ARTIST_ASC -> songs.sortedBy { it.artist.lowercase() }
            SortOption.ARTIST_DESC -> songs.sortedByDescending { it.artist.lowercase() }
            SortOption.ALBUM_ASC -> songs.sortedBy { it.album.lowercase() }
            SortOption.ALBUM_DESC -> songs.sortedByDescending { it.album.lowercase() }
            SortOption.DATE_DESC -> songs.sortedByDescending { it.dateAdded }
            SortOption.DATE_ASC -> songs.sortedBy { it.dateAdded }
            SortOption.DURATION_DESC -> songs.sortedByDescending { it.durationMs }
            SortOption.DURATION_ASC -> songs.sortedBy { it.durationMs }
            SortOption.SIZE_DESC -> songs.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> songs.sortedBy { it.sizeBytes }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${sortedSongs.size} canciones",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleGridView) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Cambiar vista"
                    )
                }

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
                                    onSortChange(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (sortedSongs.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Audiotrack,
                title = "No hay canciones disponibles",
                subtitle = "Añade música a tu dispositivo para reproducirla en segundo plano con ecualizador y colas avanzadas.",
                actionLabel = "+ Añadir música",
                onActionClick = onPickAudioFile
            )
        } else if (!isGridView) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedSongs, key = { it.id }) { song ->
                    val isSelected = selectedUris.contains(song.uri)
                    SongRowItem(
                        song = song,
                        isCurrentPlaying = song.uri == currentPlayingUri,
                        isPlaying = isPlaying,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onPlay = {
                            if (isSelectionMode) onToggleSelect(song.uri)
                            else onPlaySong(song, sortedSongs)
                        },
                        onLongClick = { onLongClickSelect(song.uri) },
                        onPlayNext = { onPlayNext(song) },
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onToggleFavorite = { onToggleFavorite(song.uri, !song.isFavorite) },
                        onShowTechInfo = { onShowTechInfo(song) },
                        onProtectInVault = { onProtectInVault(song) },
                        onDelete = { onDeleteSong(song.uri) },
                        onShare = { onShareSong(song) }
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedSongs, key = { it.id }) { song ->
                    val isSelected = selectedUris.contains(song.uri)
                    val border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) onToggleSelect(song.uri)
                                    else onPlaySong(song, sortedSongs)
                                },
                                onLongClick = { onLongClickSelect(song.uri) }
                            ),
                        shape = RoundedCornerShape(12.dp),
                        border = border,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box {
                            Column {
                                SongArtwork(
                                    uri = song.uri,
                                    title = song.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    iconSize = 40.dp
                                )
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = song.title.ifEmpty { song.displayName },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
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
                            if (isSelectionMode || isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(24.dp)
                                        .align(Alignment.TopStart)
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(
    songs: List<MediaFileEntity>,
    onSelectArtist: (String) -> Unit
) {
    val artistGroups = remember(songs) {
        songs.groupBy { it.artist.ifEmpty { "Artista desconocido" } }
            .toList()
            .sortedByDescending { it.second.size }
    }

    if (artistGroups.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.Person,
            title = "No hay artistas detectados",
            subtitle = "Los artistas se organizan automáticamente a partir de los metadatos de tus archivos de audio."
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(artistGroups, key = { it.first }) { (artist, artistSongs) ->
                val albumsCount = artistSongs.map { it.album.ifEmpty { "Álbum desconocido" } }.distinct().size

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectArtist(artist) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${artistSongs.size} canciones • $albumsCount álbumes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsTabContent(
    songs: List<MediaFileEntity>,
    onSelectAlbum: (String) -> Unit
) {
    val albumGroups = remember(songs) {
        songs.groupBy { it.album.ifEmpty { "Álbum desconocido" } }
            .toList()
            .sortedByDescending { it.second.size }
    }

    if (albumGroups.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.Album,
            title = "No hay álbumes detectados",
            subtitle = "Los álbumes se agrupan automáticamente a partir de las canciones de tu biblioteca."
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(albumGroups, key = { it.first }) { (albumName, albumSongs) ->
                val artist = albumSongs.firstOrNull()?.artist?.ifEmpty { "Artista desconocido" } ?: "Artista desconocido"
                val representativeUri = albumSongs.firstOrNull()?.uri ?: ""

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectAlbum(albumName) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column {
                        SongArtwork(
                            uri = representativeUri,
                            title = albumName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            iconSize = 44.dp,
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = albumName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${albumSongs.size} canciones",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenresTabContent(
    songs: List<MediaFileEntity>,
    onSelectGenre: (String) -> Unit
) {
    val genreGroups = remember(songs) {
        songs.filter { it.genre.isNotBlank() }
            .groupBy { it.genre }
            .toList()
            .sortedByDescending { it.second.size }
    }

    if (genreGroups.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.Category,
            title = "No hay etiquetas de género registradas",
            subtitle = "Cuando tus canciones tengan metadatos de género en MediaStore se agruparán automáticamente aquí."
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(genreGroups, key = { it.first }) { (genre, genreSongs) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectGenre(genre) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Category,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${genreSongs.size} pistas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsTabContent(
    playlists: List<PlaylistEntity>,
    onSelectPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String, String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.PlaylistPlay,
                title = "No tienes playlists de música",
                subtitle = "Crea listas personalizadas para organizar tus canciones favoritas.",
                actionLabel = "+ Crear playlist",
                onActionClick = { showCreateDialog = true }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(playlists, key = { it.id }) { pl ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectPlaylist(pl.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (pl.description.isNotEmpty()) {
                                    Text(
                                        text = pl.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
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
                .testTag("fab_create_music_playlist"),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Crear playlist")
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCreateDialog = false
                    newName = ""
                    newDesc = ""
                },
                title = { Text("Nueva Playlist") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            label = { Text("Nombre") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("playlist_new_name_field")
                        )
                        OutlinedTextField(
                            value = newDesc,
                            onValueChange = { newDesc = it },
                            label = { Text("Descripción (opcional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreatePlaylist(newName.trim(), newDesc.trim())
                                showCreateDialog = false
                                newName = ""
                                newDesc = ""
                            }
                        },
                        enabled = newName.isNotBlank()
                    ) {
                        Text("Crear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCreateDialog = false
                        newName = ""
                        newDesc = ""
                    }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
