package com.example

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import com.example.data.vault.VaultSecurityManager
import com.example.data.vault.VaultStorageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ScaffoldDefaults
import com.example.data.local.entity.MediaFileEntity
import com.example.player.model.MediaInfoDetails
import com.example.ui.components.TechnicalInfoDialog
import com.example.ui.screens.images.PhotoViewerScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.MediaType
import com.example.core.model.NavSection
import com.example.core.model.SortOption
import com.example.core.model.ThemeMode
import com.example.core.model.ViewMode
import com.example.player.controller.OmniPlayerController
import com.example.player.model.PlaylistItem
import com.example.player.ui.PlayerScreen
import com.example.ui.MainViewModel
import com.example.ui.components.MiniPlayer
import com.example.ui.components.OmniTopAppBar
import com.example.ui.components.PermissionUtils
import com.example.ui.components.ScanProgressBar
import com.example.ui.components.SelectionActionBar
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.images.ImagesScreen
import com.example.ui.screens.music.MusicScreen
import com.example.ui.screens.playlists.PlaylistsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.stats.StatsScreen
import com.example.ui.screens.vault.VaultScreen
import com.example.ui.screens.videos.VideosScreen
import com.example.ui.theme.OmniMediaTheme

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if opened via VIEW intent (e.g. from File Manager)
        intent?.data?.let { uri ->
            val type = intent.type ?: ""
            val mediaType = when {
                type.startsWith("video") -> MediaType.VIDEO
                type.startsWith("audio") -> MediaType.AUDIO
                else -> MediaType.IMAGE
            }
            viewModel.importMediaUri(this, uri, mediaType)
            if (mediaType != MediaType.IMAGE) {
                viewModel.openPlayback(uri.toString())
            }
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val themeMode = runCatching { ThemeMode.valueOf(settings.themeMode) }.getOrDefault(ThemeMode.SYSTEM)

            OmniMediaTheme(themeMode = themeMode) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.example.ui.components.PermissionUtils.hasMediaPermissions(this)) {
            viewModel.performIncrementalSync(this)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isInPictureInPictureMode) {
            viewModel.lockVault()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.setIsInPipMode(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val active = viewModel.activePlayback.value
        val isExpanded = viewModel.isPlayerExpanded.value
        if (active != null && isExpanded && active.mediaType == MediaType.VIDEO) {
            val controller = OmniPlayerController.getInstance(this)
            if (controller.isPlaying.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }
}

data class BottomNavItem(
    val section: NavSection,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

fun MediaFileEntity.toMediaInfoDetails(): MediaInfoDetails {
    return MediaInfoDetails(
        title = displayName.ifEmpty { title },
        uri = uri,
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        width = width,
        height = height,
        mimeType = mimeType,
        fps = 0f,
        bitrate = 0L,
        audioTracksCount = if (mediaType == MediaType.AUDIO.name) 1 else 0,
        subtitleTracksCount = 0,
        artist = artist,
        album = album,
        genre = genre
    )
}

@Composable
fun MainAppContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentSection by viewModel.currentSection.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val selectedUris by viewModel.selectedUris.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()

    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val audioTracks by viewModel.audioTracks.collectAsStateWithLifecycle()
    val images by viewModel.images.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val unfinished by viewModel.unfinishedProgress.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()
    val totalSizeBytes by viewModel.totalSizeBytes.collectAsStateWithLifecycle()
    val vaultSizeBytes by viewModel.vaultSizeBytes.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsStateWithLifecycle()
    val activePlayback by viewModel.activePlayback.collectAsStateWithLifecycle()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
    val isInPipMode by viewModel.isInPipMode.collectAsStateWithLifecycle()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var singleItemToDeleteUri by remember { mutableStateOf<String?>(null) }
    var pendingDeleteUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingVaultOriginalDeleteUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var activePhotoIndex by remember { mutableStateOf<Int?>(null) }
    var activePhotoList by remember { mutableStateOf<List<MediaFileEntity>>(emptyList()) }
    var infoItemToShow by remember { mutableStateOf<MediaFileEntity?>(null) }

    val deleteSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onSystemDeleteConfirmed(pendingDeleteUris)
            Toast.makeText(context, "Elemento(s) eliminado(s)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Eliminación cancelada", Toast.LENGTH_SHORT).show()
        }
        pendingDeleteUris = emptyList()
    }

    val vaultOriginalDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onVaultOriginalDeleteConfirmed(pendingVaultOriginalDeleteUris)
            Toast.makeText(context, "Archivo original eliminado de la galería pública", Toast.LENGTH_SHORT).show()
        }
        pendingVaultOriginalDeleteUris = emptyList()
    }

    val vaultDirectImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUrisDirectlyToVault(
                context = context,
                uris = uris,
                onRequiresOriginalDeleteConsent = { req, pendingUris ->
                    pendingVaultOriginalDeleteUris = pendingUris
                    vaultOriginalDeleteLauncher.launch(req)
                },
                onComplete = { count, err ->
                    if (count > 0) {
                        Toast.makeText(context, "$count archivo(s) cifrado(s) en la Bóveda", Toast.LENGTH_SHORT).show()
                    } else if (err != null) {
                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // Close any open Vault photo when Vault locks
    LaunchedEffect(isVaultUnlocked) {
        if (!isVaultUnlocked) {
            val isViewingVaultPhoto = activePhotoList.any { VaultStorageManager.isVaultUri(it.uri) }
            if (isViewingVaultPhoto) {
                activePhotoIndex = null
                activePhotoList = emptyList()
            }
        }
    }

    // Apply FLAG_SECURE while viewing unlocked Vault or playing/viewing Vault media
    val isViewingVaultContent = (currentSection == NavSection.VAULT && isVaultUnlocked) ||
        VaultStorageManager.isVaultUri(activePlayback?.uri) ||
        (activePhotoIndex != null && activePhotoList.any { VaultStorageManager.isVaultUri(it.uri) })

    LaunchedEffect(isViewingVaultContent) {
        val window = (context as? Activity)?.window
        if (window != null) {
            if (isViewingVaultContent) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    var isSearching by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }
    var hasPermissions by remember { mutableStateOf(PermissionUtils.hasMediaPermissions(context)) }

    // Permission request launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        hasPermissions = granted
        if (granted) {
            viewModel.scanDeviceMedia(context)
        }
    }

    // Startup check: request permissions if needed on first launch, or sync if already granted
    LaunchedEffect(Unit) {
        if (!PermissionUtils.hasMediaPermissions(context)) {
            requestPermissionLauncher.launch(PermissionUtils.getRequiredMediaPermissions())
        } else {
            viewModel.performIncrementalSync(context)
        }
    }

    // Auto-sync when permissions become available
    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            if (videos.isEmpty() && audioTracks.isEmpty() && images.isEmpty()) {
                viewModel.scanDeviceMedia(context)
            } else {
                viewModel.performIncrementalSync(context)
            }
        }
    }

    // SAF File pickers
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.importMediaUri(context, uri, MediaType.VIDEO)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.importMediaUri(context, uri, MediaType.AUDIO)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.importMediaUri(context, uri, MediaType.IMAGE)
        }
    }

    val playerController = remember { OmniPlayerController.getInstance(context) }
    val isAudioOnly by playerController.isAudioOnly.collectAsStateWithLifecycle()
    val isVideoPlayback = activePlayback != null && (activePlayback?.mediaType == MediaType.VIDEO || (!isAudioOnly && activePlayback?.mediaType != MediaType.AUDIO))
    val isAudioPlayback = activePlayback != null && (activePlayback?.mediaType == MediaType.AUDIO || isAudioOnly)
    val isFullscreenVideo = isVideoPlayback && (isPlayerExpanded || isInPipMode)
    val isExpandedAudio = isAudioPlayback && isPlayerExpanded && !isInPipMode
    val isFullscreenPhoto = activePhotoIndex != null && activePhotoList.isNotEmpty()
    val shouldHideTopBar = isFullscreenVideo || isExpandedAudio || isFullscreenPhoto
    val shouldHideBottomBar = isFullscreenVideo || isFullscreenPhoto

    LaunchedEffect(isFullscreenVideo) {
        if (!isFullscreenVideo) {
            (context as? android.app.Activity)?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // BackHandler hierarchy: PhotoViewer -> Fullscreen Video -> Expanded Audio -> Selection -> Folder -> Search -> Return to HOME
    BackHandler(enabled = isFullscreenPhoto || isFullscreenVideo || isExpandedAudio || isSelectionMode || selectedFolder != null || isSearching || currentSection != NavSection.HOME) {
        when {
            isFullscreenPhoto -> activePhotoIndex = null
            isFullscreenVideo -> {
                val pos = playerController.currentPosition.value
                val dur = playerController.duration.value
                viewModel.minimizePlayer(pos, dur)
            }
            isExpandedAudio -> {
                val pos = playerController.currentPosition.value
                val dur = playerController.duration.value
                viewModel.minimizePlayer(pos, dur)
            }
            isSelectionMode -> viewModel.clearSelection()
            selectedFolder != null -> viewModel.selectFolder(null)
            isSearching -> {
                isSearching = false
                searchInput = ""
                viewModel.setSearchQuery("")
            }
            else -> viewModel.navigateTo(NavSection.HOME)
        }
    }

    val navItems = listOf(
        BottomNavItem(NavSection.HOME, Icons.Filled.Home, Icons.Outlined.Home, "Inicio"),
        BottomNavItem(NavSection.VIDEOS, Icons.Filled.Movie, Icons.Outlined.Movie, "Videos"),
        BottomNavItem(NavSection.MUSIC, Icons.Filled.Audiotrack, Icons.Outlined.Audiotrack, "Música"),
        BottomNavItem(NavSection.IMAGES, Icons.Filled.Image, Icons.Outlined.Image, "Fotos"),
        BottomNavItem(NavSection.VAULT, Icons.Filled.Lock, Icons.Outlined.Lock, "Bóveda")
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!shouldHideTopBar) {
                    if (isSelectionMode) {
                        SelectionActionBar(
                            selectedCount = selectedUris.size,
                            showAddToPlaylist = currentSection != NavSection.IMAGES,
                            onSelectAll = {
                                val currentUris = when (currentSection) {
                                    NavSection.VIDEOS -> videos.map { it.uri }
                                    NavSection.MUSIC -> audioTracks.map { it.uri }
                                    NavSection.IMAGES -> images.map { it.uri }
                                    else -> emptyList()
                                }
                                viewModel.selectAll(currentUris)
                            },
                            onClearSelection = { viewModel.clearSelection() },
                            onAddToPlaylist = {
                                if (playlists.isNotEmpty()) {
                                    viewModel.addSelectedToPlaylist(playlists.first().id)
                                    Toast.makeText(context, "Añadidos a ${playlists.first().name}", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.createPlaylist("Mi lista")
                                    Toast.makeText(context, "Playlist creada y elementos añadidos", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onToggleFavorite = {
                                viewModel.toggleFavoritesSelected(true)
                                Toast.makeText(context, "Marcados como favoritos", Toast.LENGTH_SHORT).show()
                            },
                            onProtectInVault = {
                                viewModel.protectSelectedInVault(
                                    onRequiresOriginalDeleteConsent = { req, uris ->
                                        pendingVaultOriginalDeleteUris = uris
                                        vaultOriginalDeleteLauncher.launch(req)
                                    },
                                    onComplete = { count, err ->
                                        if (count > 0) {
                                            Toast.makeText(context, "$count archivo(s) cifrado(s) en la Bóveda", Toast.LENGTH_SHORT).show()
                                        } else if (err != null) {
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            },
                            onShare = {
                                val urisToShare = selectedUris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                                if (urisToShare.isNotEmpty()) {
                                    val shareIntent = if (urisToShare.size == 1) {
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "*/*"
                                            putExtra(Intent.EXTRA_STREAM, urisToShare.first())
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    } else {
                                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                            type = "*/*"
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(urisToShare))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Compartir multimedia"))
                                }
                            },
                            onDelete = { showDeleteConfirmDialog = true },
                            modifier = Modifier.statusBarsPadding()
                        )
                    } else {
                        Column {
                            if (isSearching) {
                                OutlinedTextField(
                                    value = searchInput,
                                    onValueChange = {
                                        searchInput = it
                                        viewModel.setSearchQuery(it)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .testTag("search_text_field"),
                                    placeholder = { Text("Buscar por nombre, artista, carpeta...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            isSearching = false
                                            searchInput = ""
                                            viewModel.setSearchQuery("")
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Cerrar búsqueda")
                                        }
                                    },
                                    singleLine = true
                                )
                            } else {
                                OmniTopAppBar(
                                    currentSection = currentSection,
                                    onNavigateToSection = { viewModel.navigateTo(it) },
                                    onSearchClick = { isSearching = true },
                                    onScanClick = {
                                        if (PermissionUtils.hasMediaPermissions(context)) {
                                            viewModel.scanDeviceMedia(context)
                                            Toast.makeText(context, "Actualizando biblioteca...", Toast.LENGTH_SHORT).show()
                                        } else {
                                            requestPermissionLauncher.launch(PermissionUtils.getRequiredMediaPermissions())
                                        }
                                    }
                                )
                            }
                            ScanProgressBar(scanState = scanState)
                        }
                    }
                }
            },
            bottomBar = {
                if (!shouldHideBottomBar && !isSelectionMode) {
                    Column {
                        if (activePlayback != null && !isPlayerExpanded && !isInPipMode) {
                            val controller = OmniPlayerController.getInstance(context)
                            val isPlaying by controller.isPlaying.collectAsStateWithLifecycle()
                            val currentPos by controller.currentPosition.collectAsStateWithLifecycle()
                            val dur by controller.duration.collectAsStateWithLifecycle()
                            val playlist by controller.playlist.collectAsStateWithLifecycle()
                            val currentIndex by controller.currentIndex.collectAsStateWithLifecycle()
                            val currentItem = playlist.getOrNull(currentIndex)

                            MiniPlayer(
                                title = currentItem?.title ?: activePlayback!!.title,
                                artist = currentItem?.artist ?: activePlayback!!.artist,
                                artworkUri = currentItem?.artworkUri ?: activePlayback!!.artworkUri,
                                isPlaying = isPlaying,
                                currentPosMs = currentPos,
                                durationMs = dur,
                                onExpand = { viewModel.expandPlayer() },
                                onPlayPause = { controller.togglePlayPause() },
                                onPrevious = { controller.playPrevious() },
                                onNext = { controller.playNext() },
                                onClose = { viewModel.stopAndClosePlayback() }
                            )
                        }

                        if (!isInPipMode) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.testTag("bottom_nav_bar")
                            ) {
                                navItems.forEach { item ->
                                    val isSelected = currentSection == item.section
                                    NavigationBarItem(
                                        selected = isSelected,
                                        onClick = { viewModel.navigateTo(item.section) },
                                        icon = {
                                            Icon(
                                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        },
                                        label = { Text(item.label) },
                                        modifier = Modifier.testTag("nav_item_${item.section.name.lowercase()}")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isExpandedAudio) {
                val playlist by playerController.playlist.collectAsStateWithLifecycle()
                val currentIndex by playerController.currentIndex.collectAsStateWithLifecycle()
                val currentItem = playlist.getOrNull(currentIndex)
                    ?.takeIf { it.mediaType == MediaType.AUDIO || isAudioOnly }

                PlayerScreen(
                    modifier = Modifier.fillMaxSize(),
                    mediaUri = currentItem?.uri ?: activePlayback!!.uri,
                    mediaTitle = currentItem?.title ?: activePlayback!!.title,
                    savedProgressMs = activePlayback!!.initialPositionMs,
                    doubleTapSeconds = settings.skipForwardSec,
                    isInPipMode = false,
                    isFavorite = favorites.any { it.uri == (currentItem?.uri ?: activePlayback!!.uri) },
                    onToggleFavorite = { isFav ->
                        val uri = currentItem?.uri ?: activePlayback!!.uri
                        viewModel.toggleFavorite(uri, isFav)
                    },
                    onSaveQueueAsPlaylist = { name -> viewModel.saveQueueAsPlaylist(name) },
                    onBackPressed = { currentPos, duration ->
                        viewModel.minimizePlayer(currentPos, duration)
                    },
                    onProgressUpdate = { currentPos, duration ->
                        viewModel.updatePlaybackProgress(currentPos, duration)
                    }
                )
            } else {
                val query = searchInput.trim()
            val filteredVideos = remember(videos, query) {
                if (query.isEmpty()) videos else videos.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.folderName.contains(query, ignoreCase = true)
                }
            }
            val filteredAudios = remember(audioTracks, query) {
                if (query.isEmpty()) audioTracks else audioTracks.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.artist.contains(query, ignoreCase = true) ||
                    it.album.contains(query, ignoreCase = true) ||
                    it.genre.contains(query, ignoreCase = true) ||
                    it.folderName.contains(query, ignoreCase = true)
                }
            }
            val filteredImages = remember(images, query) {
                if (query.isEmpty()) images else images.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.folderName.contains(query, ignoreCase = true)
                }
            }

            when (currentSection) {
                NavSection.HOME -> {
                    HomeScreen(
                        videoCount = videos.size,
                        audioCount = audioTracks.size,
                        imageCount = images.size,
                        hasPermissions = hasPermissions,
                        unfinishedList = unfinished,
                        recentlyPlayed = recentlyPlayed,
                        favorites = favorites,
                        playlists = playlists,
                        onNavigateToSection = { viewModel.navigateTo(it) },
                        onPlayMedia = { uri -> viewModel.openPlayback(uri) },
                        onViewPhoto = { item ->
                            val idx = images.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
                            activePhotoIndex = idx
                            activePhotoList = if (images.isNotEmpty()) images else listOf(item)
                        },
                        onPermissionsGranted = {
                            hasPermissions = true
                            viewModel.scanDeviceMedia(context)
                        },
                        onScanDevice = {
                            if (PermissionUtils.hasMediaPermissions(context)) {
                                viewModel.scanDeviceMedia(context)
                            } else {
                                requestPermissionLauncher.launch(PermissionUtils.getRequiredMediaPermissions())
                            }
                        }
                    )
                }
                NavSection.VIDEOS -> {
                    val viewMode = runCatching { ViewMode.valueOf(settings.viewMode) }.getOrDefault(ViewMode.GRID)
                    val sortOption = runCatching { SortOption.valueOf(settings.sortOption) }.getOrDefault(SortOption.DATE_DESC)

                    VideosScreen(
                        videos = filteredVideos,
                        currentViewMode = viewMode,
                        currentSortOption = sortOption,
                        selectedFolder = selectedFolder,
                        selectedUris = selectedUris,
                        isSelectionMode = isSelectionMode,
                        onViewModeChange = { newMode -> viewModel.updateSettings { it.copy(viewMode = newMode.name) } },
                        onSortOptionChange = { newSort -> viewModel.updateSettings { it.copy(sortOption = newSort.name) } },
                        onSelectFolder = { folder -> viewModel.selectFolder(folder) },
                        onPlayVideo = { uri ->
                            val videoPlaylist = filteredVideos.map {
                                PlaylistItem(
                                    uri = it.uri,
                                    title = it.title,
                                    artworkUri = it.uri,
                                    durationMs = it.durationMs,
                                    mediaType = MediaType.VIDEO
                                )
                            }
                            viewModel.openPlayback(uri, videoPlaylist)
                        },
                        onToggleSelect = { uri -> viewModel.toggleSelection(uri) },
                        onLongClickSelect = { uri -> viewModel.toggleSelection(uri) },
                        onToggleFavorite = { uri, isFav -> viewModel.toggleFavorite(uri, isFav) },
                        onAddToPlaylist = { uri ->
                            if (playlists.isNotEmpty()) {
                                viewModel.addToPlaylist(playlists.first().id, uri)
                                Toast.makeText(context, "Añadido a ${playlists.first().name}", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.createPlaylist("Mi lista")
                                Toast.makeText(context, "Playlist creada y añadido", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onProtectInVault = { item ->
                            viewModel.protectInVault(
                                item = item,
                                onRequiresOriginalDeleteConsent = { req, uris ->
                                    pendingVaultOriginalDeleteUris = uris
                                    vaultOriginalDeleteLauncher.launch(req)
                                },
                                onComplete = { count, err ->
                                    if (count > 0) {
                                        Toast.makeText(context, "Video cifrado y trasladado a la Bóveda", Toast.LENGTH_SHORT).show()
                                    } else if (err != null) {
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                        onDeleteVideo = { uri -> singleItemToDeleteUri = uri },
                        onPickVideoFile = { videoPickerLauncher.launch(arrayOf("video/*")) },
                        onShareVideo = { video ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = video.mimeType.ifEmpty { "video/*" }
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(video.uri))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir video"))
                        },
                        onShowVideoInfo = { video -> infoItemToShow = video }
                    )
                }
                NavSection.MUSIC -> {
                    val sortOption = runCatching { SortOption.valueOf(settings.sortOption) }.getOrDefault(SortOption.DATE_DESC)
                    val controller = OmniPlayerController.getInstance(context)
                    val isPlayingNow by controller.isPlaying.collectAsStateWithLifecycle()

                    MusicScreen(
                        songs = filteredAudios,
                        playlists = playlists,
                        currentPlayingUri = activePlayback?.uri,
                        isPlaying = isPlayingNow,
                        currentSortOption = sortOption,
                        selectedFolder = selectedFolder,
                        selectedUris = selectedUris,
                        isSelectionMode = isSelectionMode,
                        onSortOptionChange = { newSort -> viewModel.updateSettings { it.copy(sortOption = newSort.name) } },
                        onSelectFolder = { folder -> viewModel.selectFolder(folder) },
                        onPlaySong = { song, list ->
                            val musicPlaylist = list.map {
                                PlaylistItem(
                                    uri = it.uri,
                                    title = it.title.ifEmpty { it.displayName },
                                    artist = it.artist,
                                    album = it.album,
                                    artworkUri = it.uri,
                                    durationMs = it.durationMs,
                                    mediaType = MediaType.AUDIO
                                )
                            }
                            viewModel.openPlayback(song.uri, musicPlaylist)
                        },
                        onPlayNext = { song -> viewModel.playNextInQueue(song) },
                        onAddToQueue = { song ->
                            viewModel.addToQueue(song)
                            Toast.makeText(context, "Añadida a la cola", Toast.LENGTH_SHORT).show()
                        },
                        onAddToPlaylist = { playlistId, uri ->
                            viewModel.addToPlaylist(playlistId, uri)
                            Toast.makeText(context, "Añadida a la playlist", Toast.LENGTH_SHORT).show()
                        },
                        onCreatePlaylistAndAdd = { name, desc, uri ->
                            viewModel.createPlaylistAndAdd(name, desc, uri)
                            Toast.makeText(context, "Playlist creada y canción añadida", Toast.LENGTH_SHORT).show()
                        },
                        onCreatePlaylist = { name, desc ->
                            viewModel.createPlaylist(name, desc)
                            Toast.makeText(context, "Playlist creada", Toast.LENGTH_SHORT).show()
                        },
                        onRenamePlaylist = { id, name, desc ->
                            viewModel.renamePlaylist(id, name, desc)
                            Toast.makeText(context, "Playlist renombrada", Toast.LENGTH_SHORT).show()
                        },
                        onDeletePlaylist = { id ->
                            viewModel.deletePlaylist(id)
                            Toast.makeText(context, "Playlist eliminada", Toast.LENGTH_SHORT).show()
                        },
                        onRemoveFromPlaylist = { playlistId, uri ->
                            viewModel.removeMediaFromPlaylist(playlistId, uri)
                            Toast.makeText(context, "Canción quitada de la playlist", Toast.LENGTH_SHORT).show()
                        },
                        onReorderPlaylistItem = { playlistId, from, to ->
                            viewModel.reorderPlaylistItem(playlistId, from, to)
                        },
                        onAddMultipleToPlaylist = { playlistId, uris ->
                            viewModel.addMultipleToPlaylist(playlistId, uris)
                            Toast.makeText(context, "Canciones añadidas a la playlist", Toast.LENGTH_SHORT).show()
                        },
                        onGetPlaylistSongs = { playlistId ->
                            viewModel.getPlaylistSongs(playlistId)
                        },
                        onToggleSelect = { uri -> viewModel.toggleSelection(uri) },
                        onLongClickSelect = { uri -> viewModel.toggleSelection(uri) },
                        onToggleFavorite = { uri, isFav -> viewModel.toggleFavorite(uri, isFav) },
                        onProtectInVault = { item ->
                            viewModel.protectInVault(
                                item = item,
                                onRequiresOriginalDeleteConsent = { req, uris ->
                                    pendingVaultOriginalDeleteUris = uris
                                    vaultOriginalDeleteLauncher.launch(req)
                                },
                                onComplete = { count, err ->
                                    if (count > 0) {
                                        Toast.makeText(context, "Audio cifrado y trasladado a la Bóveda", Toast.LENGTH_SHORT).show()
                                    } else if (err != null) {
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                        onDeleteSong = { uri -> singleItemToDeleteUri = uri },
                        onPickAudioFile = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                        onShareSong = { song ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = song.mimeType.ifEmpty { "audio/*" }
                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(Uri.parse(song.uri)))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir canción"))
                        }
                    )
                }
                NavSection.IMAGES -> {
                    val sortOption = runCatching { SortOption.valueOf(settings.sortOption) }.getOrDefault(SortOption.DATE_DESC)

                    ImagesScreen(
                        images = filteredImages,
                        currentSortOption = sortOption,
                        selectedFolder = selectedFolder,
                        selectedUris = selectedUris,
                        isSelectionMode = isSelectionMode,
                        onSortOptionChange = { newSort -> viewModel.updateSettings { it.copy(sortOption = newSort.name) } },
                        onSelectFolder = { folder -> viewModel.selectFolder(folder) },
                        onViewImage = { index, list ->
                            activePhotoIndex = index
                            activePhotoList = list
                        },
                        onToggleSelect = { uri -> viewModel.toggleSelection(uri) },
                        onLongClickSelect = { uri -> viewModel.toggleSelection(uri) },
                        onToggleFavorite = { uri, isFav -> viewModel.toggleFavorite(uri, isFav) },
                        onProtectInVault = { item ->
                            viewModel.protectInVault(
                                item = item,
                                onRequiresOriginalDeleteConsent = { req, uris ->
                                    pendingVaultOriginalDeleteUris = uris
                                    vaultOriginalDeleteLauncher.launch(req)
                                },
                                onComplete = { count, err ->
                                    if (count > 0) {
                                        Toast.makeText(context, "Foto cifrada y trasladada a la Bóveda", Toast.LENGTH_SHORT).show()
                                    } else if (err != null) {
                                        Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                        onDeleteImage = { uri -> singleItemToDeleteUri = uri },
                        onPickImageFile = { imagePickerLauncher.launch(arrayOf("image/*")) },
                        onShareImage = { image ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = image.mimeType.ifEmpty { "image/*" }
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(image.uri))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir foto"))
                        },
                        onShowImageInfo = { image -> infoItemToShow = image }
                    )
                }
                NavSection.PLAYLISTS -> {
                    PlaylistsScreen(
                        playlists = playlists,
                        allAvailableSongs = audioTracks,
                        currentPlayingUri = activePlayback?.uri,
                        onGetPlaylistSongs = { id -> viewModel.getPlaylistSongs(id) },
                        onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                        onRenamePlaylist = { id, name, desc -> viewModel.renamePlaylist(id, name, desc) },
                        onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                        onPlaySong = { song, list ->
                            val musicPlaylist = list.map {
                                PlaylistItem(
                                    uri = it.uri,
                                    title = it.title.ifEmpty { it.displayName },
                                    artist = it.artist,
                                    album = it.album,
                                    artworkUri = it.uri,
                                    durationMs = it.durationMs,
                                    mediaType = MediaType.AUDIO
                                )
                            }
                            viewModel.openPlayback(song.uri, musicPlaylist)
                        },
                        onRemoveFromPlaylist = { playlistId, uri ->
                            viewModel.removeMediaFromPlaylist(playlistId, uri)
                            Toast.makeText(context, "Canción quitada de la playlist", Toast.LENGTH_SHORT).show()
                        },
                        onReorderItem = { playlistId, from, to ->
                            viewModel.reorderPlaylistItem(playlistId, from, to)
                        },
                        onAddSongsToPlaylist = { playlistId, uris ->
                            viewModel.addMultipleToPlaylist(playlistId, uris)
                        }
                    )
                }
                NavSection.VAULT -> {
                    val canUseBio = remember(context) { VaultSecurityManager.canUseBiometrics(context) }
                    VaultScreen(
                        isUnlocked = isVaultUnlocked,
                        hasPinConfigured = settings.vaultPinHash.isNotEmpty(),
                        vaultItems = vaultItems,
                        biometricEnabled = settings.biometricEnabled,
                        canUseBiometrics = canUseBio,
                        onRequestBiometricUnlock = {
                            (context as? FragmentActivity)?.let { fragAct ->
                                VaultSecurityManager.authenticateWithBiometrics(
                                    activity = fragAct,
                                    onSuccess = { viewModel.unlockVaultWithBiometrics() },
                                    onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                                )
                            }
                        },
                        onUnlockWithPin = { pin -> viewModel.unlockVaultWithPin(pin) },
                        onVerifyPinDetailed = { pin -> viewModel.verifyVaultPinDetailed(pin) },
                        onConfigurePin = { pin -> viewModel.setupVaultPin(pin) },
                        onChangePin = { curPin, newPin, cb -> viewModel.changeVaultPin(curPin, newPin, cb) },
                        onLockVault = { viewModel.lockVault() },
                        onOpenVaultItem = { item, currentList ->
                            if (item.mediaType == MediaType.IMAGE.name) {
                                val vaultPhotos = currentList
                                    .filter { it.mediaType == MediaType.IMAGE.name }
                                    .ifEmpty { listOf(item) }
                                    .map { viewModel.vaultItemToMediaFileEntity(it) }
                                val targetUri = VaultStorageManager.getPlaybackUriForVaultItem(context, item)
                                val idx = vaultPhotos.indexOfFirst { it.uri == targetUri }.coerceAtLeast(0)
                                activePhotoList = vaultPhotos
                                activePhotoIndex = idx
                            } else {
                                viewModel.openVaultPlayback(item, currentList)
                            }
                        },
                        onRestoreItem = { item ->
                            viewModel.restoreVaultItem(item) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRestoreMultipleItems = { items ->
                            viewModel.restoreVaultItems(items) { count, err ->
                                if (count > 0) {
                                    Toast.makeText(context, "$count archivo(s) restaurado(s)", Toast.LENGTH_SHORT).show()
                                } else if (err != null) {
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDeleteItem = { id ->
                            viewModel.deleteVaultItemPermanently(id) { deleted ->
                                if (deleted) {
                                    Toast.makeText(context, "Archivo eliminado permanentemente de la Bóveda", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDeleteMultipleItems = { ids ->
                            viewModel.deleteVaultItemsPermanently(ids) { count ->
                                Toast.makeText(context, "$count archivo(s) eliminado(s) de la Bóveda", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onImportToVault = {
                            vaultDirectImportLauncher.launch(arrayOf("video/*", "audio/*", "image/*"))
                        }
                    )
                }
                NavSection.HISTORY -> {
                    HistoryScreen(
                        historyList = historyList,
                        onPlayItem = { uri -> viewModel.openPlayback(uri) },
                        onDeleteItem = { id -> viewModel.deleteHistoryItem(id) },
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }
                NavSection.STATS -> {
                    StatsScreen(
                        videoCount = videos.size,
                        audioCount = audioTracks.size,
                        imageCount = images.size,
                        totalSizeBytes = totalSizeBytes,
                        vaultItemCount = vaultItems.size,
                        vaultSizeBytes = vaultSizeBytes
                    )
                }
                NavSection.SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        onUpdateSettings = { transform -> viewModel.updateSettings(transform) }
                    )
                }
            }
        }
    }
    }

    // High-priority Fullscreen Video Player Overlay (outside Scaffold for true immersive fullscreen)
    if (isFullscreenVideo && activePlayback != null) {
        val controller = OmniPlayerController.getInstance(context)
        val playlist by controller.playlist.collectAsStateWithLifecycle()
        val currentIndex by controller.currentIndex.collectAsStateWithLifecycle()
        val currentItem = playlist.getOrNull(currentIndex)
            ?.takeIf { it.mediaType == MediaType.VIDEO }

        PlayerScreen(
            modifier = Modifier.fillMaxSize(),
            mediaUri = currentItem?.uri ?: activePlayback!!.uri,
            mediaTitle = currentItem?.title ?: activePlayback!!.title,
            savedProgressMs = activePlayback!!.initialPositionMs,
            doubleTapSeconds = settings.skipForwardSec,
            isInPipMode = isInPipMode,
            isFavorite = favorites.any { it.uri == (currentItem?.uri ?: activePlayback!!.uri) },
            onToggleFavorite = { isFav ->
                val uri = currentItem?.uri ?: activePlayback!!.uri
                viewModel.toggleFavorite(uri, isFav)
            },
            onSaveQueueAsPlaylist = { name -> viewModel.saveQueueAsPlaylist(name) },
            onBackPressed = { currentPos, duration ->
                viewModel.minimizePlayer(currentPos, duration)
            },
            onProgressUpdate = { currentPos, duration ->
                viewModel.updatePlaybackProgress(currentPos, duration)
            },
            onVideoSpeedChanged = { speed, scope, mediaUri ->
                viewModel.saveVideoSpeedPreference(mediaUri, speed, scope)
            }
        )
    }

    // High-priority Fullscreen Photo Viewer Overlay
    if (activePhotoIndex != null && activePhotoList.isNotEmpty()) {
        PhotoViewerScreen(
            modifier = Modifier.fillMaxSize(),
            photos = activePhotoList,
            initialIndex = activePhotoIndex!!,
            onClose = { activePhotoIndex = null },
            onToggleFavorite = { uri, isFav -> viewModel.toggleFavorite(uri, isFav) },
            onDeletePhoto = { uri ->
                if (VaultStorageManager.isVaultUri(uri)) {
                    val targetItem = vaultItems.firstOrNull {
                        VaultStorageManager.getPlaybackUriForVaultItem(context, it) == uri
                    }
                    if (targetItem != null) {
                        viewModel.deleteVaultItemPermanently(targetItem.id)
                    }
                    val updatedList = activePhotoList.filterNot { it.uri == uri }
                    activePhotoList = updatedList
                    if (updatedList.isEmpty()) {
                        activePhotoIndex = null
                    }
                } else {
                    viewModel.deleteMediaListPermanently(context, listOf(uri)) { req, uris ->
                        pendingDeleteUris = uris
                        deleteSenderLauncher.launch(req)
                    }
                }
            },
            onSharePhoto = { photo ->
                if (VaultStorageManager.isVaultUri(photo.uri)) {
                    Toast.makeText(
                        context,
                        "Los archivos de la Bóveda están cifrados. Restaura la foto primero para compartir.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = photo.mimeType.ifEmpty { "image/*" }
                        putExtra(Intent.EXTRA_STREAM, Uri.parse(photo.uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Compartir foto"))
                }
            }
        )
    }

    // Technical Info Dialog (triggered from Video/Audio/Photo menus)
    if (infoItemToShow != null) {
        TechnicalInfoDialog(
            mediaInfo = infoItemToShow!!.toMediaInfoDetails(),
            onDismiss = { infoItemToShow = null }
        )
    }

    // Single item delete confirmation dialog
    if (singleItemToDeleteUri != null) {
        AlertDialog(
            onDismissRequest = { singleItemToDeleteUri = null },
            title = { Text("¿Eliminar archivo?") },
            text = { Text("¿Deseas eliminar este archivo? Esta acción moverá el archivo a la papelera o lo eliminará permanentemente de tu dispositivo.") },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = singleItemToDeleteUri!!
                        singleItemToDeleteUri = null
                        viewModel.deleteMediaListPermanently(context, listOf(uri)) { req, uris ->
                            pendingDeleteUris = uris
                            deleteSenderLauncher.launch(req)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { singleItemToDeleteUri = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Multi-delete confirmation dialog
    if (showDeleteConfirmDialog) {
        val count = selectedUris.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(if (count == 1) "¿Eliminar archivo seleccionado?" else "¿Eliminar $count archivos seleccionados?") },
            text = { Text("¿Deseas eliminar permanentemente los archivos seleccionados de tu biblioteca?") },
            confirmButton = {
                Button(
                    onClick = {
                        val uris = selectedUris.toList()
                        showDeleteConfirmDialog = false
                        viewModel.deleteMediaListPermanently(context, uris) { req, urisList ->
                            pendingDeleteUris = urisList
                            deleteSenderLauncher.launch(req)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
}
