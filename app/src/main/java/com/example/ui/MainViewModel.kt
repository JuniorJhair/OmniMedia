package com.example.ui

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.OmniApplication
import com.example.core.model.MediaType
import com.example.core.model.NavSection
import com.example.core.model.SortOption
import com.example.core.model.ViewMode
import com.example.data.local.entity.MediaFileEntity
import com.example.data.local.entity.PlaybackProgressEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.UserSettingsEntity
import com.example.data.local.entity.VaultItemEntity
import com.example.data.scanner.MediaScanner
import com.example.data.vault.OriginalDeletionOutcome
import com.example.data.vault.PinVerificationResult
import com.example.data.vault.VaultSecurityManager
import com.example.data.vault.VaultStorageManager
import com.example.player.controller.OmniPlayerController
import com.example.player.model.PlaylistItem
import com.example.ui.components.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class ActivePlayback(
    val uri: String,
    val title: String,
    val initialPositionMs: Long = 0L,
    val mediaType: MediaType = MediaType.VIDEO,
    val artist: String = "",
    val artworkUri: String? = null
)

data class ScanState(
    val isScanning: Boolean = false,
    val currentStep: String = "",
    val totalFound: Int = 0,
    val lastScanTimestamp: Long = 0L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as OmniApplication
    private val mediaRepo = app.mediaRepository
    private val progressRepo = app.playbackProgressRepository
    private val playlistRepo = app.playlistRepository
    private val vaultRepo = app.vaultRepository
    private val settingsRepo = app.settingsRepository
    private val historyRepo = app.historyRepository

    private val _currentSection = MutableStateFlow(NavSection.HOME)
    val currentSection: StateFlow<NavSection> = _currentSection.asStateFlow()

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _activePlayback = MutableStateFlow<ActivePlayback?>(null)
    val activePlayback: StateFlow<ActivePlayback?> = _activePlayback.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    // Multi-selection state
    private val _selectedUris = MutableStateFlow<Set<String>>(emptySet())
    val selectedUris: StateFlow<Set<String>> = _selectedUris.asStateFlow()

    private var mediaContentObserver: ContentObserver? = null
    private var syncDebounceJob: Job? = null

    init {
        setupMediaStoreObserver()
    }

    private fun setupMediaStoreObserver() {
        val handler = Handler(Looper.getMainLooper())
        mediaContentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                syncDebounceJob?.cancel()
                syncDebounceJob = viewModelScope.launch {
                    delay(1200L)
                    performIncrementalSync(app)
                }
            }
        }
        try {
            val resolver = app.contentResolver
            mediaContentObserver?.let { observer ->
                resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
                resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
                resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            }
        } catch (_: Exception) {}
    }

    val isSelectionMode: StateFlow<Boolean> = _selectedUris
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Folder selection filter (when user taps a specific folder)
    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    val settings: StateFlow<UserSettingsEntity> = settingsRepo.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettingsEntity()
    )

    val allMedia: StateFlow<List<MediaFileEntity>> = mediaRepo.allMedia.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val videos: StateFlow<List<MediaFileEntity>> = mediaRepo.getMediaByType(MediaType.VIDEO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val audioTracks: StateFlow<List<MediaFileEntity>> = mediaRepo.getMediaByType(MediaType.AUDIO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val images: StateFlow<List<MediaFileEntity>> = mediaRepo.getMediaByType(MediaType.IMAGE).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val favorites: StateFlow<List<MediaFileEntity>> = mediaRepo.favorites.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentlyPlayed: StateFlow<List<MediaFileEntity>> = mediaRepo.recentlyPlayed.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val unfinishedProgress: StateFlow<List<PlaybackProgressEntity>> = progressRepo.unfinished
        .map { list -> list.filter { !VaultStorageManager.isVaultUri(it.mediaUri) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepo.allPlaylists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val vaultItems: StateFlow<List<VaultItemEntity>> = vaultRepo.allVaultItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val totalSizeBytes: StateFlow<Long> = mediaRepo.getTotalSizeBytes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val vaultSizeBytes: StateFlow<Long> = vaultRepo.vaultTotalSizeBytes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val historyList: StateFlow<List<com.example.data.local.entity.HistoryEntity>> = historyRepo.history.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playlistSongsMap: StateFlow<Map<Long, List<MediaFileEntity>>> = combine(
        playlistRepo.allPlaylistItems,
        mediaRepo.allMedia
    ) { items, allMediaList ->
        val mediaMap = allMediaList.associateBy { it.uri }
        items.groupBy { it.playlistId }.mapValues { (_, pItems) ->
            pItems.sortedBy { it.orderIndex }.mapNotNull { mediaMap[it.mediaUri] }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    init {
        val controller = OmniPlayerController.getInstance(app)
        controller.onTrackPlaybackStarted = { item ->
            viewModelScope.launch {
                _activePlayback.value = ActivePlayback(
                    uri = item.uri,
                    title = item.title,
                    initialPositionMs = 0L,
                    mediaType = item.mediaType,
                    artist = item.artist,
                    artworkUri = item.artworkUri ?: item.uri
                )
                if (!VaultStorageManager.isVaultUri(item.uri)) {
                    historyRepo.recordHistory(
                        mediaUri = item.uri,
                        title = item.title,
                        mediaType = item.mediaType,
                        durationMs = item.durationMs,
                        positionMs = 0L
                    )
                    mediaRepo.recordPlay(item.uri)
                }
            }
        }

        viewModelScope.launch {
            val savedState = controller.queuePersistence.loadSavedQueue()
            if (savedState != null && savedState.items.isNotEmpty() && _activePlayback.value == null) {
                val currentItem = savedState.items.getOrNull(savedState.currentIndex) ?: savedState.items.first()
                if (currentItem.mediaType == MediaType.AUDIO) {
                    val restored = controller.restoreSavedQueue(autoPlay = false)
                    if (restored && _activePlayback.value == null) {
                        _activePlayback.value = ActivePlayback(
                            uri = currentItem.uri,
                            title = currentItem.title,
                            initialPositionMs = savedState.positionMs,
                            mediaType = currentItem.mediaType,
                            artist = currentItem.artist,
                            artworkUri = currentItem.artworkUri ?: currentItem.uri
                        )
                    }
                }
            }
        }
    }

    fun getPlaylistSongs(playlistId: Long): List<MediaFileEntity> =
        playlistSongsMap.value[playlistId] ?: emptyList()

    fun navigateTo(section: NavSection) {
        _currentSection.value = section
        _selectedFolder.value = null
        clearSelection()
        if (_activePlayback.value != null && _isPlayerExpanded.value) {
            _isPlayerExpanded.value = false
        }
    }

    fun selectFolder(folderName: String?) {
        _selectedFolder.value = folderName
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSearching(searching: Boolean) {
        _isSearching.value = searching
        if (!searching) {
            _searchQuery.value = ""
        }
    }

    // MediaStore Real Device Scanner
    fun scanDeviceMedia(context: Context) {
        if (_scanState.value.isScanning) return
        viewModelScope.launch {
            _scanState.value = ScanState(isScanning = true, currentStep = "Iniciando escaneo...")
            var count = 0
            withContext(Dispatchers.IO) {
                try {
                    _scanState.value = _scanState.value.copy(currentStep = "Escaneando videos...")
                    val videos = MediaScanner.scanVideos(context)
                    count += videos.size
                    mediaRepo.syncMediaStore(videos, MediaType.VIDEO)

                    _scanState.value = _scanState.value.copy(currentStep = "Escaneando música...", totalFound = count)
                    val audios = MediaScanner.scanAudio(context)
                    count += audios.size
                    mediaRepo.syncMediaStore(audios, MediaType.AUDIO)

                    _scanState.value = _scanState.value.copy(currentStep = "Escaneando fotos...", totalFound = count)
                    val images = MediaScanner.scanImages(context)
                    count += images.size
                    mediaRepo.syncMediaStore(images, MediaType.IMAGE)
                } catch (_: Exception) {}
            }
            _scanState.value = ScanState(
                isScanning = false,
                currentStep = "Escaneo completado",
                totalFound = count,
                lastScanTimestamp = System.currentTimeMillis()
            )
        }
    }

    // Real-time automatic incremental synchronization
    fun performIncrementalSync(context: Context) {
        if (_scanState.value.isScanning) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val types = listOf(MediaType.VIDEO, MediaType.AUDIO, MediaType.IMAGE)
                    var anyChanges = false

                    for (type in types) {
                        if (!PermissionUtils.hasPermissionFor(context, type)) continue

                        val summary = MediaScanner.getMediaStoreSummary(context, type)
                        val existing = mediaRepo.getMediaListByType(type)
                        val existingMediaMap = mutableMapOf<Long, MediaFileEntity>()

                        for (item in existing) {
                            val id = runCatching {
                                if (item.uri.startsWith("content://media/")) {
                                    ContentUris.parseId(Uri.parse(item.uri))
                                } else null
                            }.getOrNull()

                            if (id != null) {
                                existingMediaMap[id] = item
                            }
                        }

                        // 1. Detect deleted items (in Room with content://media/ but no longer in MediaStore)
                        val deletedUris = mutableListOf<String>()
                        for ((id, item) in existingMediaMap) {
                            if (!summary.containsKey(id)) {
                                deletedUris.add(item.uri)
                            }
                        }

                        // 2. Detect new or modified items
                        val idsToFetch = mutableSetOf<Long>()
                        for ((id, modTime) in summary) {
                            val prev = existingMediaMap[id]
                            if (prev == null) {
                                idsToFetch.add(id)
                            } else if (modTime > 0L && modTime > prev.dateModified) {
                                idsToFetch.add(id)
                            }
                        }

                        if (deletedUris.isNotEmpty() || idsToFetch.isNotEmpty()) {
                            anyChanges = true
                            val newOrModified = when (type) {
                                MediaType.VIDEO -> MediaScanner.scanVideos(context, idsToFetch)
                                MediaType.AUDIO -> MediaScanner.scanAudio(context, idsToFetch)
                                MediaType.IMAGE -> MediaScanner.scanImages(context, idsToFetch)
                            }
                            mediaRepo.syncIncremental(newOrModified, deletedUris, type)
                        }
                    }

                    if (anyChanges) {
                        _scanState.value = _scanState.value.copy(
                            lastScanTimestamp = System.currentTimeMillis()
                        )
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Multi-Selection Logic
    fun toggleSelection(uri: String) {
        val current = _selectedUris.value.toMutableSet()
        if (current.contains(uri)) {
            current.remove(uri)
        } else {
            current.add(uri)
        }
        _selectedUris.value = current
    }

    fun selectAll(uris: List<String>) {
        _selectedUris.value = uris.toSet()
    }

    fun clearSelection() {
        _selectedUris.value = emptySet()
    }

    fun deleteSelected() {
        val uris = _selectedUris.value.toList()
        if (uris.isEmpty()) return
        viewModelScope.launch {
            mediaRepo.deleteMultiplePermanently(uris)
            clearSelection()
        }
    }

    fun deleteMultiplePermanently(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            mediaRepo.deleteMultiplePermanently(uris)
            _selectedUris.value = _selectedUris.value - uris.toSet()
        }
    }

    fun toggleFavoritesSelected(isFavorite: Boolean) {
        val uris = _selectedUris.value.toList()
        if (uris.isEmpty()) return
        viewModelScope.launch {
            mediaRepo.setFavorites(uris, isFavorite)
            clearSelection()
        }
    }

    fun addSelectedToPlaylist(playlistId: Long) {
        val uris = _selectedUris.value.toList()
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uris.forEachIndexed { index, uri ->
                playlistRepo.addItemToPlaylist(playlistId, uri, index)
            }
            clearSelection()
        }
    }

    fun protectSelectedInVault(
        onRequiresOriginalDeleteConsent: ((androidx.activity.result.IntentSenderRequest, List<String>) -> Unit)? = null,
        onComplete: ((Int, String?) -> Unit)? = null
    ) {
        val uris = _selectedUris.value.toSet()
        if (uris.isEmpty()) return
        val items = allMedia.value.filter { it.uri in uris }
        clearSelection()
        protectMediaListInVault(
            items = items,
            onRequiresOriginalDeleteConsent = onRequiresOriginalDeleteConsent,
            onComplete = onComplete
        )
    }

    fun updateSettings(transform: (UserSettingsEntity) -> UserSettingsEntity) {
        viewModelScope.launch {
            val before = settingsRepo.getSettings()
            settingsRepo.updateSettings(transform)
            val after = settingsRepo.getSettings()
            if (before.defaultPlaybackSpeed != after.defaultPlaybackSpeed) {
                OmniPlayerController.getInstance(app).setVideoSpeed(
                    speed = after.defaultPlaybackSpeed,
                    scope = com.example.player.model.SpeedApplicationScope.ALL_VIDEOS
                )
            }
        }
    }

    fun toggleFavorite(uri: String, isFavorite: Boolean) {
        viewModelScope.launch {
            mediaRepo.setFavorite(uri, isFavorite)
        }
    }

    fun deleteMedia(uri: String) {
        viewModelScope.launch {
            mediaRepo.deletePermanently(uri)
            progressRepo.deleteProgress(uri)
            _selectedUris.value = _selectedUris.value - setOf(uri)
            val current = _activePlayback.value
            if (current != null && current.uri == uri) {
                stopAndClosePlayback()
            }
        }
    }

    fun deleteMediaListPermanently(
        context: Context,
        uris: List<String>,
        onRequiresConsent: (androidx.activity.result.IntentSenderRequest, List<String>) -> Unit
    ) {
        if (uris.isEmpty()) return
        val plan = com.example.data.scanner.MediaDeletionHelper.planDeletion(context, uris)
        when (plan) {
            is com.example.data.scanner.DeletionPlan.RequiresIntentSender -> {
                onRequiresConsent(plan.intentSenderRequest, plan.uris)
            }
            is com.example.data.scanner.DeletionPlan.Direct -> {
                viewModelScope.launch {
                    com.example.data.scanner.MediaDeletionHelper.executeDirectDelete(context.contentResolver, plan.uris)
                    mediaRepo.deleteMultiplePermanently(plan.uris)
                    plan.uris.forEach { progressRepo.deleteProgress(it) }
                    _selectedUris.value = _selectedUris.value - plan.uris.toSet()

                    val current = _activePlayback.value
                    if (current != null && current.uri in plan.uris) {
                        stopAndClosePlayback()
                    }
                }
            }
        }
    }

    fun onSystemDeleteConfirmed(uris: List<String>) {
        viewModelScope.launch {
            mediaRepo.deleteMultiplePermanently(uris)
            uris.forEach { progressRepo.deleteProgress(it) }
            _selectedUris.value = _selectedUris.value - uris.toSet()

            val current = _activePlayback.value
            if (current != null && current.uri in uris) {
                stopAndClosePlayback()
            }
        }
    }

    fun createPlaylist(name: String, description: String = "") {
        viewModelScope.launch {
            playlistRepo.createPlaylist(name, description)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            playlistRepo.deletePlaylist(id)
        }
    }

    fun addToPlaylist(playlistId: Long, mediaUri: String) {
        viewModelScope.launch {
            playlistRepo.addItemToPlaylist(playlistId, mediaUri, 0)
        }
    }

    fun renamePlaylist(id: Long, newName: String, description: String = "") {
        viewModelScope.launch {
            playlistRepo.renamePlaylist(id, newName, description)
        }
    }

    fun removeMediaFromPlaylist(playlistId: Long, mediaUri: String) {
        viewModelScope.launch {
            playlistRepo.removeItemFromPlaylist(playlistId, mediaUri)
        }
    }

    fun reorderPlaylistItem(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val items = playlistRepo.getItemsForPlaylist(playlistId).first()
            if (fromIndex in items.indices && toIndex in items.indices && fromIndex != toIndex) {
                val list = items.toMutableList()
                val moved = list.removeAt(fromIndex)
                list.add(toIndex, moved)
                list.forEachIndexed { idx, item ->
                    playlistRepo.reorderPlaylistItem(playlistId, item.mediaUri, idx)
                }
            }
        }
    }

    fun addMultipleToPlaylist(playlistId: Long, uris: List<String>) {
        viewModelScope.launch {
            uris.forEachIndexed { index, uri ->
                playlistRepo.addItemToPlaylist(playlistId, uri, index)
            }
        }
    }

    fun createPlaylistAndAdd(name: String, description: String = "", mediaUri: String) {
        viewModelScope.launch {
            val playlistId = playlistRepo.createPlaylist(name, description)
            playlistRepo.addItemToPlaylist(playlistId, mediaUri, 0)
        }
    }

    fun getMediaForPlaylist(playlistId: Long) = playlistRepo.getMediaForPlaylist(playlistId)

    // Queue Management
    fun addToQueue(song: MediaFileEntity) {
        val controller = OmniPlayerController.getInstance(app)
        val item = PlaylistItem(
            uri = song.uri,
            title = song.title.ifEmpty { song.displayName },
            artist = song.artist,
            album = song.album,
            artworkUri = song.uri,
            durationMs = song.durationMs,
            mediaType = runCatching { MediaType.valueOf(song.mediaType) }.getOrDefault(MediaType.AUDIO)
        )
        controller.addToQueue(item)
    }

    fun playNextInQueue(song: MediaFileEntity) {
        val controller = OmniPlayerController.getInstance(app)
        val item = PlaylistItem(
            uri = song.uri,
            title = song.title.ifEmpty { song.displayName },
            artist = song.artist,
            album = song.album,
            artworkUri = song.uri,
            durationMs = song.durationMs,
            mediaType = runCatching { MediaType.valueOf(song.mediaType) }.getOrDefault(MediaType.AUDIO)
        )
        controller.playNext(item)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = OmniPlayerController.getInstance(app)
        controller.moveQueueItem(fromIndex, toIndex)
    }

    fun removeFromQueue(index: Int) {
        val controller = OmniPlayerController.getInstance(app)
        controller.removeFromQueue(index)
    }

    fun clearQueue(keepCurrent: Boolean = false) {
        val controller = OmniPlayerController.getInstance(app)
        controller.clearQueue(keepCurrent)
        if (!keepCurrent) {
            _activePlayback.value = null
            _isPlayerExpanded.value = false
        }
    }

    fun saveQueueAsPlaylist(name: String, description: String = "") {
        val controller = OmniPlayerController.getInstance(app)
        val items = controller.playlist.value
        if (items.isEmpty()) return
        viewModelScope.launch {
            val playlistId = playlistRepo.createPlaylist(name, description)
            items.forEachIndexed { index, item ->
                playlistRepo.addItemToPlaylist(playlistId, item.uri, index)
            }
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            historyRepo.deleteHistoryItem(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepo.clearHistory()
        }
    }

    // Playback Lifecycle
    fun openPlayback(uri: String, playlist: List<PlaylistItem>? = null) {
        val controller = OmniPlayerController.getInstance(app)
        controller.pauseForTransition()

        viewModelScope.launch {
            val media = mediaRepo.getByUri(uri)
            val title = media?.displayName?.ifEmpty { media.title }
                ?: uri.substringAfterLast("/").substringBefore("?")
            val artist = media?.artist ?: ""
            val album = media?.album ?: ""
            val resolvedMediaType = playlist?.firstOrNull { it.uri == uri }?.mediaType
                ?: runCatching { MediaType.valueOf(media?.mediaType ?: "") }.getOrDefault(MediaType.VIDEO)
            val progress = progressRepo.getProgress(uri)
            val savedPos = if (progress != null && !progress.completed && progress.positionMs > 0L) {
                progress.positionMs
            } else {
                0L
            }

            val isVaultMedia = VaultStorageManager.isVaultUri(uri)
            if (!isVaultMedia) {
                mediaRepo.recordPlay(uri)
            }

            val fullPlaylist = if (!playlist.isNullOrEmpty()) {
                playlist
            } else {
                listOf(
                    PlaylistItem(
                        uri = uri,
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUri = uri,
                        durationMs = media?.durationMs ?: 0L,
                        mediaType = resolvedMediaType
                    )
                )
            }

            val startIndex = fullPlaylist.indexOfFirst { it.uri == uri }.coerceAtLeast(0)
            val currentPlaylistItem = fullPlaylist.getOrNull(startIndex)
            val finalMediaType = currentPlaylistItem?.mediaType ?: resolvedMediaType

            val shouldAutoPlay = !(finalMediaType == MediaType.VIDEO && savedPos > 3000L)
            controller.preparePlaylist(
                items = fullPlaylist,
                startIndex = startIndex,
                startPositionMs = savedPos,
                autoPlay = shouldAutoPlay
            )

            _activePlayback.value = ActivePlayback(
                uri = uri,
                title = currentPlaylistItem?.title ?: title,
                initialPositionMs = savedPos,
                mediaType = finalMediaType,
                artist = currentPlaylistItem?.artist ?: artist,
                artworkUri = currentPlaylistItem?.artworkUri ?: uri
            )
            _isPlayerExpanded.value = true
        }
    }

    fun openVaultPlayback(item: VaultItemEntity, vaultList: List<VaultItemEntity> = listOf(item)) {
        val mediaType = runCatching { MediaType.valueOf(item.mediaType) }.getOrDefault(MediaType.VIDEO)
        val targetUri = VaultStorageManager.getPlaybackUriForVaultItem(app, item)
        val sameTypeList = vaultList.filter { it.mediaType == item.mediaType }.ifEmpty { listOf(item) }
        val playlist = sameTypeList.map { v ->
            val vUri = VaultStorageManager.getPlaybackUriForVaultItem(app, v)
            PlaylistItem(
                uri = vUri,
                title = v.originalName.substringBeforeLast(".").ifEmpty { v.originalName },
                artist = v.artist.ifEmpty { "Bóveda Privada" },
                album = v.album,
                artworkUri = vUri,
                durationMs = v.durationMs,
                mediaType = mediaType,
                mimeType = v.mimeType
            )
        }
        openPlayback(targetUri, playlist)
    }

    fun vaultItemToMediaFileEntity(item: VaultItemEntity): MediaFileEntity {
        val playbackUri = VaultStorageManager.getPlaybackUriForVaultItem(app, item)
        return MediaFileEntity(
            id = item.id,
            uri = playbackUri,
            title = item.originalName.substringBeforeLast(".").ifEmpty { item.originalName },
            displayName = item.originalName,
            artist = item.artist,
            album = item.album,
            durationMs = item.durationMs,
            sizeBytes = item.sizeBytes,
            dateAdded = item.addedAt,
            dateModified = item.addedAt,
            mimeType = item.mimeType,
            mediaType = item.mediaType,
            width = item.width,
            height = item.height,
            folderPath = item.originalFolderPath,
            folderName = "Bóveda Privada"
        )
    }

    fun minimizePlayer(currentPosMs: Long, durationMs: Long) {
        val current = _activePlayback.value
        val controller = OmniPlayerController.getInstance(app)
        if (current != null && durationMs > 0) {
            viewModelScope.launch {
                progressRepo.saveProgress(current.uri, currentPosMs, durationMs)
            }
        }

        val isVideoMode = current != null && current.mediaType == MediaType.VIDEO && !controller.isAudioOnly.value
        if (isVideoMode) {
            val restoredAudio = controller.onExitVideoPlayback()
            if (restoredAudio != null && restoredAudio.items.isNotEmpty()) {
                val audioItem = restoredAudio.items.getOrNull(restoredAudio.currentIndex) ?: restoredAudio.items.first()
                _activePlayback.value = ActivePlayback(
                    uri = audioItem.uri,
                    title = audioItem.title,
                    initialPositionMs = restoredAudio.positionMs,
                    mediaType = MediaType.AUDIO,
                    artist = audioItem.artist,
                    artworkUri = audioItem.artworkUri ?: audioItem.uri
                )
                _isPlayerExpanded.value = false
            } else {
                _activePlayback.value = null
                _isPlayerExpanded.value = false
            }
        } else {
            _isPlayerExpanded.value = false
        }
    }

    fun expandPlayer() {
        _isPlayerExpanded.value = true
    }

    fun stopAndClosePlayback() {
        val current = _activePlayback.value
        val controller = OmniPlayerController.getInstance(app)
        val pos = controller.currentPosition.value
        val dur = controller.duration.value
        if (current != null && dur > 0) {
            viewModelScope.launch {
                progressRepo.saveProgress(current.uri, pos, dur)
            }
        }
        controller.pause()
        controller.clearQueue()
        _activePlayback.value = null
        _isPlayerExpanded.value = false
    }

    fun setIsInPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun closePlayback(currentPosMs: Long, durationMs: Long) {
        minimizePlayer(currentPosMs, durationMs)
    }

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long) {
        val current = _activePlayback.value
        if (current != null && durationMs > 0) {
            viewModelScope.launch {
                progressRepo.saveProgress(current.uri, positionMs, durationMs)
            }
        }
    }

    fun saveVideoSpeedPreference(mediaUri: String, speed: Float, scope: com.example.player.model.SpeedApplicationScope) {
        val controller = OmniPlayerController.getInstance(app)
        controller.setVideoSpeed(speed, scope, mediaUri)
        viewModelScope.launch(Dispatchers.IO) {
            when (scope) {
                com.example.player.model.SpeedApplicationScope.THIS_VIDEO -> {
                    app.database.videoSpeedDao().saveVideoSpeed(
                        com.example.data.local.entity.VideoSpeedEntity(
                            mediaUri = mediaUri,
                            speed = speed
                        )
                    )
                }
                com.example.player.model.SpeedApplicationScope.ALL_VIDEOS -> {
                    settingsRepo.updateSettings { it.copy(defaultPlaybackSpeed = speed) }
                }
            }
        }
    }

    // Vault Security (PBKDF2 + Salt + Lockout Cooldown + Biometrics)
    fun verifyVaultPinDetailed(pin: String): PinVerificationResult {
        val currentSettings = settings.value
        val result = VaultSecurityManager.verifyPin(
            inputPin = pin,
            storedHash = currentSettings.vaultPinHash,
            storedSalt = currentSettings.vaultSalt
        )
        if (result.isSuccess) {
            _isVaultUnlocked.value = true
            result.upgradedCredentials?.let { upgraded ->
                viewModelScope.launch {
                    settingsRepo.updateSettings {
                        it.copy(vaultPinHash = upgraded.hashBase64, vaultSalt = upgraded.saltBase64)
                    }
                }
            }
        }
        return result
    }

    fun unlockVaultWithPin(pin: String): Boolean {
        return verifyVaultPinDetailed(pin).isSuccess
    }

    fun unlockVaultWithBiometrics() {
        VaultSecurityManager.resetLockoutState()
        _isVaultUnlocked.value = true
    }

    fun setupVaultPin(pin: String) {
        val credentials = VaultSecurityManager.createPinCredentials(pin)
        VaultSecurityManager.resetLockoutState()
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(
                    vaultPinHash = credentials.hashBase64,
                    vaultSalt = credentials.saltBase64
                )
            }
            _isVaultUnlocked.value = true
        }
    }

    fun changeVaultPin(currentPin: String, newPin: String, onResult: (Boolean, String) -> Unit) {
        val currentSettings = settings.value
        val verifyResult = VaultSecurityManager.verifyPin(
            inputPin = currentPin,
            storedHash = currentSettings.vaultPinHash,
            storedSalt = currentSettings.vaultSalt
        )
        if (!verifyResult.isSuccess) {
            val msg = if (verifyResult.isLockedOut) {
                "Demasiados intentos. Espera ${verifyResult.remainingLockoutSeconds}s."
            } else {
                "El PIN actual es incorrecto."
            }
            onResult(false, msg)
            return
        }
        val newCredentials = VaultSecurityManager.createPinCredentials(newPin)
        viewModelScope.launch {
            settingsRepo.updateSettings {
                it.copy(
                    vaultPinHash = newCredentials.hashBase64,
                    vaultSalt = newCredentials.saltBase64
                )
            }
            onResult(true, "PIN de la Bóveda actualizado correctamente.")
        }
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
        val current = _activePlayback.value
        if (current != null && VaultStorageManager.isVaultUri(current.uri)) {
            stopAndClosePlayback()
        }
    }

    fun protectInVault(
        item: MediaFileEntity,
        onRequiresOriginalDeleteConsent: ((androidx.activity.result.IntentSenderRequest, List<String>) -> Unit)? = null,
        onComplete: ((Int, String?) -> Unit)? = null
    ) {
        protectMediaListInVault(
            items = listOf(item),
            onRequiresOriginalDeleteConsent = onRequiresOriginalDeleteConsent,
            onComplete = onComplete
        )
    }

    fun protectMediaListInVault(
        items: List<MediaFileEntity>,
        onRequiresOriginalDeleteConsent: ((androidx.activity.result.IntentSenderRequest, List<String>) -> Unit)? = null,
        onComplete: ((Int, String?) -> Unit)? = null
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val active = _activePlayback.value
            val itemUris = items.map { it.uri }.toSet()
            if (active != null && active.uri in itemUris) {
                stopAndClosePlayback()
            }

            var successCount = 0
            var errorReason: String? = null
            val urisRequiringConsent = mutableListOf<String>()

            withContext(Dispatchers.IO) {
                for (item in items) {
                    val result = VaultStorageManager.encryptMediaToVault(
                        context = app,
                        media = item,
                        attemptDirectOriginalDelete = true
                    )
                    result.onSuccess { outcome ->
                        vaultRepo.insertVaultItem(outcome.vaultEntity)
                        mediaRepo.deletePermanently(item.uri)
                        progressRepo.deleteProgress(item.uri)
                        historyRepo.deleteHistoryByMediaUri(item.uri)
                        playlistRepo.removeMediaFromAllPlaylists(item.uri)
                        successCount++

                        if (outcome.originalDeletionOutcome is OriginalDeletionOutcome.RequiresSystemConsent) {
                            urisRequiringConsent.addAll(outcome.originalDeletionOutcome.pendingUris)
                        }
                    }.onFailure { err ->
                        errorReason = err.localizedMessage ?: "No se pudo cifrar el archivo."
                    }
                }
            }

            if (urisRequiringConsent.isNotEmpty() && onRequiresOriginalDeleteConsent != null) {
                val batchDelete = VaultStorageManager.tryDeleteOriginalFile(app, urisRequiringConsent.distinct())
                if (batchDelete is OriginalDeletionOutcome.RequiresSystemConsent) {
                    onRequiresOriginalDeleteConsent(batchDelete.intentSenderRequest, batchDelete.pendingUris)
                }
            }

            onComplete?.invoke(successCount, errorReason)
        }
    }

    fun importUrisDirectlyToVault(
        context: Context,
        uris: List<Uri>,
        onRequiresOriginalDeleteConsent: ((androidx.activity.result.IntentSenderRequest, List<String>) -> Unit)? = null,
        onComplete: ((Int, String?) -> Unit)? = null
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            var errorReason: String? = null
            val urisRequiringConsent = mutableListOf<String>()

            withContext(Dispatchers.IO) {
                for (uri in uris) {
                    val uriStr = uri.toString()
                    val result = VaultStorageManager.importExternalUriDirectlyToVault(context, uri)
                    result.onSuccess { outcome ->
                        vaultRepo.insertVaultItem(outcome.vaultEntity)
                        mediaRepo.deletePermanently(uriStr)
                        progressRepo.deleteProgress(uriStr)
                        historyRepo.deleteHistoryByMediaUri(uriStr)
                        playlistRepo.removeMediaFromAllPlaylists(uriStr)
                        successCount++

                        if (outcome.originalDeletionOutcome is OriginalDeletionOutcome.RequiresSystemConsent) {
                            urisRequiringConsent.addAll(outcome.originalDeletionOutcome.pendingUris)
                        }
                    }.onFailure { err ->
                        errorReason = err.localizedMessage ?: "Error al importar archivo a la Bóveda."
                    }
                }
            }

            if (urisRequiringConsent.isNotEmpty() && onRequiresOriginalDeleteConsent != null) {
                val batchDelete = VaultStorageManager.tryDeleteOriginalFile(context, urisRequiringConsent.distinct())
                if (batchDelete is OriginalDeletionOutcome.RequiresSystemConsent) {
                    onRequiresOriginalDeleteConsent(batchDelete.intentSenderRequest, batchDelete.pendingUris)
                }
            }

            onComplete?.invoke(successCount, errorReason)
        }
    }

    fun onVaultOriginalDeleteConfirmed(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            vaultRepo.updateStatusByOriginalUris(uris, "COMPLETED")
            mediaRepo.deleteMultiplePermanently(uris)
            uris.forEach { uri ->
                progressRepo.deleteProgress(uri)
                historyRepo.deleteHistoryByMediaUri(uri)
                playlistRepo.removeMediaFromAllPlaylists(uri)
            }
        }
    }

    fun restoreVaultItem(
        item: VaultItemEntity,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        restoreVaultItems(listOf(item)) { count, err ->
            if (count > 0) {
                onComplete?.invoke(true, "Archivo restaurado a la biblioteca pública")
            } else {
                onComplete?.invoke(false, err ?: "No se pudo restaurar el archivo")
            }
        }
    }

    fun restoreVaultItems(
        items: List<VaultItemEntity>,
        onComplete: ((Int, String?) -> Unit)? = null
    ) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val active = _activePlayback.value
            val vaultPlaybackUris = items.map { VaultStorageManager.getPlaybackUriForVaultItem(app, it) }.toSet()
            if (active != null && active.uri in vaultPlaybackUris) {
                stopAndClosePlayback()
            }

            var restoredCount = 0
            var lastError: String? = null

            withContext(Dispatchers.IO) {
                for (item in items) {
                    val result = VaultStorageManager.restoreFromVault(app, item)
                    result.onSuccess { outcome ->
                        mediaRepo.insert(outcome.restoredMediaEntity)
                        vaultRepo.deleteVaultItem(item.id)
                        progressRepo.deleteProgress(VaultStorageManager.getPlaybackUriForVaultItem(app, item))
                        restoredCount++
                    }.onFailure { err ->
                        lastError = err.localizedMessage ?: "Error al descifrar y restaurar archivo."
                    }
                }
            }
            onComplete?.invoke(restoredCount, lastError)
        }
    }

    fun deleteVaultItemPermanently(
        id: Long,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        deleteVaultItemsPermanently(listOf(id)) { count ->
            onComplete?.invoke(count > 0)
        }
    }

    fun deleteVaultItemsPermanently(
        ids: List<Long>,
        onComplete: ((Int) -> Unit)? = null
    ) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            var deletedCount = 0
            withContext(Dispatchers.IO) {
                for (id in ids) {
                    val item = vaultRepo.getById(id) ?: continue
                    val playbackUri = VaultStorageManager.getPlaybackUriForVaultItem(app, item)
                    withContext(Dispatchers.Main) {
                        val active = _activePlayback.value
                        if (active != null && active.uri == playbackUri) {
                            stopAndClosePlayback()
                        }
                    }
                    VaultStorageManager.deleteVaultFilePermanently(app, item)
                    progressRepo.deleteProgress(playbackUri)
                    vaultRepo.deleteVaultItem(id)
                    deletedCount++
                }
            }
            onComplete?.invoke(deletedCount)
        }
    }

    // SAF Import: Read real media file metadata and insert into database
    fun importMediaUri(context: Context, uri: Uri, fallbackMediaType: MediaType) {
        viewModelScope.launch(Dispatchers.IO) {
            var fileName = "Archivo_${System.currentTimeMillis()}"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex >= 0) fileSize = cursor.getLong(sizeIndex)
                }
            }

            var durationMs = 0L
            var width = 0
            var height = 0
            var artist = ""
            var album = ""
            val mimeType = context.contentResolver.getType(uri) ?: when (fallbackMediaType) {
                MediaType.VIDEO -> "video/*"
                MediaType.AUDIO -> "audio/*"
                MediaType.IMAGE -> "image/*"
            }

            if (fallbackMediaType != MediaType.IMAGE) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
                    width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    retriever.release()
                } catch (_: Exception) {}
            }

            val entity = MediaFileEntity(
                uri = uri.toString(),
                title = fileName.substringBeforeLast("."),
                displayName = fileName,
                artist = artist,
                album = album,
                durationMs = durationMs,
                sizeBytes = fileSize,
                dateAdded = System.currentTimeMillis(),
                mimeType = mimeType,
                mediaType = fallbackMediaType.name,
                width = width,
                height = height,
                folderName = "Importados"
            )
            mediaRepo.insert(entity)
        }
    }

    override fun onCleared() {
        super.onCleared()
        mediaContentObserver?.let { observer ->
            runCatching { app.contentResolver.unregisterContentObserver(observer) }
        }
    }
}
