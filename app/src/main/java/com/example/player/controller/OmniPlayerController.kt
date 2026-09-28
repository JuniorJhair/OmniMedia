package com.example.player.controller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.MainActivity
import com.example.data.vault.VaultAwareDataSource
import com.example.data.vault.VaultStorageManager
import com.example.player.audio.OmniEqualizerManager
import com.example.player.model.AspectRatioMode
import com.example.player.model.EqualizerState
import com.example.player.model.MediaInfoDetails
import com.example.player.model.PlaylistItem
import com.example.player.model.PlayerTrackInfo
import com.example.player.model.RepeatMode
import com.example.player.model.SleepTimerState
import com.example.player.model.SpeedApplicationScope
import com.example.player.model.resolveEffectiveVideoSpeed
import com.example.player.service.OmniPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class OmniPlayerController private constructor(private val context: Context) : Player.Listener {

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 15_000,
            /* maxBufferMs = */ 50_000,
            /* bufferForPlaybackMs = */ 1_000,
            /* bufferForPlaybackAfterRebufferMs = */ 2_000
        )
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(VaultAwareDataSource.Factory(context)))
        .setLoadControl(loadControl)
        .setAudioAttributes(audioAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .build().apply {
            addListener(this@OmniPlayerController)
        }

    private val sessionActivityPendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val mediaSession: MediaSession = MediaSession.Builder(context, exoPlayer)
        .setSessionActivity(sessionActivityPendingIntent)
        .build()

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var cueDelayJob: Job? = null
    private var sleepTimerJob: Job? = null

    val equalizerManager = OmniEqualizerManager()
    val equalizerState: StateFlow<EqualizerState> = equalizerManager.state

    val queuePersistence = QueuePersistenceManager(context)

    var onPlaybackProgressChanged: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    var onTrackPlaybackStarted: ((PlaylistItem) -> Unit)? = null
    private var lastRecordedPlaybackUri: String? = null

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _globalVideoSpeed = MutableStateFlow(queuePersistence.getGlobalVideoSpeed())
    val globalVideoSpeed: StateFlow<Float> = _globalVideoSpeed.asStateFlow()

    private val _currentVideoSpecificSpeed = MutableStateFlow<Float?>(null)
    val currentVideoSpecificSpeed: StateFlow<Float?> = _currentVideoSpecificSpeed.asStateFlow()

    private var suspendedAudioQueue: PersistedQueueState? = null

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _aspectRatioMode = MutableStateFlow(AspectRatioMode.FIT)
    val aspectRatioMode: StateFlow<AspectRatioMode> = _aspectRatioMode.asStateFlow()

    private val _isAudioOnly = MutableStateFlow(false)
    val isAudioOnly: StateFlow<Boolean> = _isAudioOnly.asStateFlow()

    // Tracks & Subtitles
    private val _availableAudioTracks = MutableStateFlow<List<PlayerTrackInfo>>(emptyList())
    val availableAudioTracks: StateFlow<List<PlayerTrackInfo>> = _availableAudioTracks.asStateFlow()

    private val _availableSubtitles = MutableStateFlow<List<PlayerTrackInfo>>(emptyList())
    val availableSubtitles: StateFlow<List<PlayerTrackInfo>> = _availableSubtitles.asStateFlow()

    private val _subtitlesEnabled = MutableStateFlow(true)
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

    private val _subtitleDelayMs = MutableStateFlow(0L)
    val subtitleDelayMs: StateFlow<Long> = _subtitleDelayMs.asStateFlow()

    private val _activeCues = MutableStateFlow<List<Cue>>(emptyList())
    val activeCues: StateFlow<List<Cue>> = _activeCues.asStateFlow()

    // Playlist / Queue
    private val _playlist = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlist: StateFlow<List<PlaylistItem>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    // Sleep Timer
    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    // Error & Info
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _mediaInfo = MutableStateFlow<MediaInfoDetails?>(null)
    val mediaInfo: StateFlow<MediaInfoDetails?> = _mediaInfo.asStateFlow()

    private val _videoDimensions = MutableStateFlow(com.example.player.model.VideoDimensions())
    val videoDimensions: StateFlow<com.example.player.model.VideoDimensions> = _videoDimensions.asStateFlow()

    private var currentUri: Uri? = null
    private var currentTitle: String = ""

    init {
        startProgressTracker()
        try {
            equalizerManager.attachToAudioSession(exoPlayer.audioSessionId)
        } catch (e: Exception) {
            Log.w("OmniPlayerController", "Equalizer attach init deferred: ${e.message}")
        }
    }

    fun prepareMedia(uri: Uri, title: String, startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        val singleItem = PlaylistItem(
            uri = uri.toString(),
            title = title
        )
        preparePlaylist(listOf(singleItem), startIndex = 0, startPositionMs = startPositionMs, autoPlay = autoPlay)
    }

    fun pauseForTransition() {
        if (_isAudioOnly.value && _playlist.value.isNotEmpty() && suspendedAudioQueue == null) {
            suspendedAudioQueue = PersistedQueueState(
                items = _playlist.value,
                currentIndex = _currentIndex.value,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                shuffleEnabled = _shuffleEnabled.value,
                repeatMode = _repeatMode.value
            )
        }
        exoPlayer.playWhenReady = false
        exoPlayer.pause()
        _isPlaying.value = false
    }

    fun hasSuspendedAudioQueue(): Boolean = suspendedAudioQueue != null

    fun onExitVideoPlayback(): PersistedQueueState? {
        exoPlayer.playWhenReady = false
        exoPlayer.pause()
        _isPlaying.value = false

        val toRestore = suspendedAudioQueue
        suspendedAudioQueue = null
        if (toRestore != null && toRestore.items.isNotEmpty()) {
            _shuffleEnabled.value = toRestore.shuffleEnabled
            exoPlayer.shuffleModeEnabled = toRestore.shuffleEnabled
            _repeatMode.value = toRestore.repeatMode
            exoPlayer.repeatMode = when (toRestore.repeatMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
            preparePlaylist(
                items = toRestore.items,
                startIndex = toRestore.currentIndex,
                startPositionMs = toRestore.positionMs,
                autoPlay = false
            )
            exoPlayer.playWhenReady = false
            exoPlayer.pause()
            _isPlaying.value = false
            return toRestore
        } else {
            exoPlayer.clearMediaItems()
            exoPlayer.stop()
            _playlist.value = emptyList()
            _currentIndex.value = 0
            return null
        }
    }

    fun preparePlaylist(items: List<PlaylistItem>, startIndex: Int = 0, startPositionMs: Long = 0L, autoPlay: Boolean = true) {
        if (items.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, items.size - 1)
        val currentItem = items[safeIndex]
        val isNewAudio = (currentItem.mediaType == com.example.core.model.MediaType.AUDIO)

        if (!isNewAudio) {
            if (_isAudioOnly.value && _playlist.value.isNotEmpty() && suspendedAudioQueue == null) {
                suspendedAudioQueue = PersistedQueueState(
                    items = _playlist.value,
                    currentIndex = _currentIndex.value,
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    shuffleEnabled = _shuffleEnabled.value,
                    repeatMode = _repeatMode.value
                )
            }
            exoPlayer.playWhenReady = false
            exoPlayer.pause()
        } else {
            suspendedAudioQueue = null
        }

        _playlist.value = items
        _currentIndex.value = safeIndex

        currentUri = Uri.parse(currentItem.uri)
        currentTitle = currentItem.title
        _errorMessage.value = null
        _isAudioOnly.value = isNewAudio

        applySpeedForMedia(currentItem.uri, currentItem.mediaType)

        val mediaItems = items.map { item ->
            val meta = MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artist.ifEmpty { "OmniMedia" })
                .setAlbumTitle(item.album)
                .apply {
                    item.artworkUri?.let { setArtworkUri(Uri.parse(it)) }
                }
                .build()

            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaMetadata(meta)
                .apply {
                    if (item.mimeType.isNotEmpty()) {
                        setMimeType(item.mimeType)
                    }
                }
                .build()
        }

        exoPlayer.playWhenReady = autoPlay
        exoPlayer.setMediaItems(mediaItems, safeIndex, startPositionMs)
        exoPlayer.prepare()
        if (autoPlay) {
            startPlaybackService()
        } else {
            exoPlayer.pause()
            _isPlaying.value = false
        }
        val containsVaultItems = items.any { VaultStorageManager.isVaultUri(it.uri) }
        if (!containsVaultItems) {
            queuePersistence.saveQueue(items, safeIndex, startPositionMs, _shuffleEnabled.value, _repeatMode.value)
        }
    }

    fun restoreSavedQueue(autoPlay: Boolean = false): Boolean {
        if (_playlist.value.isNotEmpty()) return false
        val saved = queuePersistence.loadSavedQueue() ?: return false
        if (saved.items.isEmpty()) return false

        _shuffleEnabled.value = saved.shuffleEnabled
        exoPlayer.shuffleModeEnabled = saved.shuffleEnabled

        _repeatMode.value = saved.repeatMode
        exoPlayer.repeatMode = when (saved.repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }

        preparePlaylist(
            items = saved.items,
            startIndex = saved.currentIndex,
            startPositionMs = saved.positionMs,
            autoPlay = autoPlay
        )
        return true
    }

    fun addToQueue(item: PlaylistItem) {
        val updated = _playlist.value + item
        _playlist.value = updated
        val meta = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist.ifEmpty { "OmniMedia" })
            .setAlbumTitle(item.album)
            .apply { item.artworkUri?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(item.uri)
            .setMediaMetadata(meta)
            .apply {
                if (item.mimeType.isNotEmpty()) {
                    setMimeType(item.mimeType)
                }
            }
            .build()
        exoPlayer.addMediaItem(mediaItem)
        queuePersistence.saveQueue(_playlist.value, _currentIndex.value, exoPlayer.currentPosition, _shuffleEnabled.value, _repeatMode.value)
    }

    fun playNext(item: PlaylistItem) {
        val list = _playlist.value.toMutableList()
        val insertIndex = if (list.isEmpty()) 0 else (_currentIndex.value + 1).coerceAtMost(list.size)
        list.add(insertIndex, item)
        _playlist.value = list

        val meta = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist.ifEmpty { "OmniMedia" })
            .setAlbumTitle(item.album)
            .apply { item.artworkUri?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(item.uri)
            .setMediaMetadata(meta)
            .apply {
                if (item.mimeType.isNotEmpty()) {
                    setMimeType(item.mimeType)
                }
            }
            .build()
        exoPlayer.addMediaItem(insertIndex, mediaItem)
        queuePersistence.saveQueue(_playlist.value, _currentIndex.value, exoPlayer.currentPosition, _shuffleEnabled.value, _repeatMode.value)
        if (!exoPlayer.isPlaying && exoPlayer.playbackState == Player.STATE_IDLE) {
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val list = _playlist.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return

        val currentIdx = _currentIndex.value
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _playlist.value = list

        exoPlayer.moveMediaItem(fromIndex, toIndex)

        val newCurrentIndex = when {
            currentIdx == fromIndex -> toIndex
            fromIndex < currentIdx && toIndex >= currentIdx -> currentIdx - 1
            fromIndex > currentIdx && toIndex <= currentIdx -> currentIdx + 1
            else -> currentIdx
        }
        _currentIndex.value = newCurrentIndex
        queuePersistence.saveQueue(_playlist.value, newCurrentIndex, exoPlayer.currentPosition, _shuffleEnabled.value, _repeatMode.value)
    }

    fun removeFromQueue(index: Int) {
        val list = _playlist.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _playlist.value = list
            exoPlayer.removeMediaItem(index)
            if (index == _currentIndex.value) {
                _currentIndex.value = index.coerceAtMost(list.size - 1).coerceAtLeast(0)
            } else if (index < _currentIndex.value) {
                _currentIndex.value = (_currentIndex.value - 1).coerceAtLeast(0)
            }
            queuePersistence.saveQueue(_playlist.value, _currentIndex.value, exoPlayer.currentPosition, _shuffleEnabled.value, _repeatMode.value)
        }
    }

    fun clearQueue(keepCurrent: Boolean = false) {
        if (keepCurrent && _playlist.value.isNotEmpty()) {
            val current = _playlist.value.getOrNull(_currentIndex.value)
            if (current != null) {
                _playlist.value = listOf(current)
                _currentIndex.value = 0
                val count = exoPlayer.mediaItemCount
                for (i in count - 1 downTo 0) {
                    if (i != _currentIndex.value) {
                        exoPlayer.removeMediaItem(i)
                    }
                }
                queuePersistence.saveQueue(_playlist.value, 0, exoPlayer.currentPosition, _shuffleEnabled.value, _repeatMode.value)
                return
            }
        }
        _playlist.value = emptyList()
        _currentIndex.value = 0
        queuePersistence.clearQueue()
        exoPlayer.clearMediaItems()
        exoPlayer.stop()
        _activeCues.value = emptyList()
        try {
            val intent = Intent(context, OmniPlaybackService::class.java)
            context.stopService(intent)
        } catch (_: Exception) {}
    }

    fun skipToQueueIndex(index: Int) {
        if (index in _playlist.value.indices) {
            _currentIndex.value = index
            val item = _playlist.value[index]
            currentUri = Uri.parse(item.uri)
            currentTitle = item.title
            exoPlayer.seekTo(index, 0L)
            exoPlayer.play()
            startPlaybackService()
        }
    }

    fun playNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        } else if (_repeatMode.value == RepeatMode.ALL && _playlist.value.isNotEmpty()) {
            skipToQueueIndex(0)
        }
    }

    fun playPrevious() {
        if (exoPlayer.currentPosition > 3000L) {
            exoPlayer.seekTo(0L)
        } else if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
            exoPlayer.play()
        } else {
            exoPlayer.seekTo(0L)
        }
    }

    fun toggleShuffle() {
        val newState = !_shuffleEnabled.value
        _shuffleEnabled.value = newState
        exoPlayer.shuffleModeEnabled = newState
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _repeatMode.value = nextMode
        exoPlayer.repeatMode = when (nextMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    // Sleep Timer
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerState.value = SleepTimerState()
            return
        }

        val totalSeconds = minutes * 60L
        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            remainingSeconds = totalSeconds,
            stopAtEndOfSong = false,
            initialMinutes = minutes
        )

        sleepTimerJob = scope.launch {
            var currentSec = totalSeconds
            while (isActive && currentSec > 0) {
                delay(1000)
                currentSec -= 1
                _sleepTimerState.value = _sleepTimerState.value.copy(remainingSeconds = currentSec)
            }
            if (currentSec <= 0) {
                pause()
                _sleepTimerState.value = SleepTimerState()
            }
        }
    }

    fun setStopAtEndOfSong(enabled: Boolean) {
        sleepTimerJob?.cancel()
        _sleepTimerState.value = SleepTimerState(
            isActive = enabled,
            remainingSeconds = 0L,
            stopAtEndOfSong = enabled,
            initialMinutes = 0
        )
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerState.value = SleepTimerState()
    }

    private fun startPlaybackService() {
        try {
            val intent = Intent(context, OmniPlaybackService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            Log.w("OmniPlayerController", "Start playback service non-fatal error: ${e.message}")
        }
    }

    fun addExternalSubtitle(
        subtitleUri: Uri,
        mimeType: String = MimeTypes.APPLICATION_SUBRIP,
        label: String = "Externo"
    ) {
        currentUri?.let { mediaUri ->
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                .setMimeType(mimeType)
                .setLanguage("es")
                .setLabel(label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            val currentPos = exoPlayer.currentPosition
            val isPlayingNow = exoPlayer.isPlaying

            val mediaItemWithSub = MediaItem.Builder()
                .setUri(mediaUri)
                .setSubtitleConfigurations(listOf(subtitleConfig))
                .build()

            exoPlayer.setMediaItem(mediaItemWithSub)
            exoPlayer.prepare()
            exoPlayer.seekTo(currentPos)
            setSubtitlesEnabled(true)
            exoPlayer.playWhenReady = isPlayingNow
        }
    }

    fun play() {
        exoPlayer.play()
        startPlaybackService()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        exoPlayer.seekTo(safePos)
        _currentPosition.value = safePos
        queuePersistence.updatePosition(safePos, exoPlayer.currentMediaItemIndex)
    }

    fun seekBy(seconds: Int) {
        val newPos = (exoPlayer.currentPosition + seconds * 1000L).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        seekTo(newPos)
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    fun setVideoSpeed(
        speed: Float,
        scope: SpeedApplicationScope,
        targetUri: String? = currentUri?.toString()
    ) {
        when (scope) {
            SpeedApplicationScope.THIS_VIDEO -> {
                if (!targetUri.isNullOrEmpty()) {
                    queuePersistence.saveVideoSpecificSpeed(targetUri, speed)
                }
                _currentVideoSpecificSpeed.value = speed
                _playbackSpeed.value = speed
                exoPlayer.playbackParameters = PlaybackParameters(speed)
            }
            SpeedApplicationScope.ALL_VIDEOS -> {
                queuePersistence.saveGlobalVideoSpeed(speed)
                _globalVideoSpeed.value = speed
                val specific = if (!targetUri.isNullOrEmpty()) {
                    queuePersistence.getVideoSpecificSpeed(targetUri)
                } else {
                    null
                }
                _currentVideoSpecificSpeed.value = specific
                val effective = resolveEffectiveVideoSpeed(specific, speed)
                _playbackSpeed.value = effective
                exoPlayer.playbackParameters = PlaybackParameters(effective)
            }
        }
    }

    fun clearVideoSpecificSpeed(targetUri: String? = currentUri?.toString()) {
        if (!targetUri.isNullOrEmpty()) {
            queuePersistence.clearVideoSpecificSpeed(targetUri)
        }
        _currentVideoSpecificSpeed.value = null
        val global = queuePersistence.getGlobalVideoSpeed()
        _globalVideoSpeed.value = global
        val effective = resolveEffectiveVideoSpeed(null, global)
        _playbackSpeed.value = effective
        exoPlayer.playbackParameters = PlaybackParameters(effective)
    }

    fun applySpeedForMedia(uri: String, mediaType: com.example.core.model.MediaType) {
        if (mediaType == com.example.core.model.MediaType.VIDEO) {
            val specific = queuePersistence.getVideoSpecificSpeed(uri)
            val global = queuePersistence.getGlobalVideoSpeed()
            _currentVideoSpecificSpeed.value = specific
            _globalVideoSpeed.value = global
            val effective = resolveEffectiveVideoSpeed(specific, global)
            _playbackSpeed.value = effective
            exoPlayer.playbackParameters = PlaybackParameters(effective)
        } else {
            _currentVideoSpecificSpeed.value = null
            _playbackSpeed.value = 1.0f
            exoPlayer.playbackParameters = PlaybackParameters(1.0f)
        }
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        exoPlayer.volume = if (muted) 0f else 1f
    }

    fun toggleMute() {
        setMuted(!_isMuted.value)
    }

    fun setAspectRatioMode(mode: AspectRatioMode) {
        _aspectRatioMode.value = mode
    }

    fun toggleAudioOnly() {
        _isAudioOnly.value = !_isAudioOnly.value
    }

    fun setSubtitleDelay(delayMs: Long) {
        _subtitleDelayMs.value = delayMs
    }

    fun setSubtitlesEnabled(enabled: Boolean) {
        _subtitlesEnabled.value = enabled
        if (!enabled) {
            _activeCues.value = emptyList()
        }
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
            .build()
    }

    fun selectAudioTrack(trackInfo: PlayerTrackInfo) {
        val currentTracks = exoPlayer.currentTracks
        for (trackGroupInfo in currentTracks.groups) {
            if (trackGroupInfo.type == C.TRACK_TYPE_AUDIO) {
                val group = trackGroupInfo.mediaTrackGroup
                for (i in 0 until group.length) {
                    val format = group.getFormat(i)
                    val candidateId = format.id ?: "${format.language ?: "und"}_$i"
                    if (candidateId == trackInfo.id) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .setOverrideForType(TrackSelectionOverride(group, i))
                            .build()
                        return
                    }
                }
            }
        }
    }

    fun selectSubtitleTrack(trackInfo: PlayerTrackInfo?) {
        if (trackInfo == null) {
            setSubtitlesEnabled(false)
            return
        }
        setSubtitlesEnabled(true)
        val currentTracks = exoPlayer.currentTracks
        for (trackGroupInfo in currentTracks.groups) {
            if (trackGroupInfo.type == C.TRACK_TYPE_TEXT) {
                val group = trackGroupInfo.mediaTrackGroup
                for (i in 0 until group.length) {
                    val format = group.getFormat(i)
                    val candidateId = format.id ?: "${format.language ?: "und"}_$i"
                    if (candidateId == trackInfo.id) {
                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(group, i))
                            .build()
                        return
                    }
                }
            }
        }
    }

    fun getResizeMode(): Int {
        return when (_aspectRatioMode.value) {
            AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioMode.FIXED_16_9, AspectRatioMode.FIXED_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                if (exoPlayer.playbackState != Player.STATE_IDLE) {
                    _currentPosition.value = exoPlayer.currentPosition
                    _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                    _bufferedPosition.value = exoPlayer.bufferedPosition
                }
                delay(250)
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val newIndex = exoPlayer.currentMediaItemIndex
        if (newIndex in _playlist.value.indices) {
            _currentIndex.value = newIndex
            val item = _playlist.value[newIndex]
            currentUri = Uri.parse(item.uri)
            currentTitle = item.title
            _isAudioOnly.value = (item.mediaType == com.example.core.model.MediaType.AUDIO)
            applySpeedForMedia(item.uri, item.mediaType)
            if (exoPlayer.isPlaying) {
                checkAndNotifyTrackPlaybackStarted()
            }
        }

        // Handle stop at end of song
        if (_sleepTimerState.value.stopAtEndOfSong && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            pause()
            _sleepTimerState.value = SleepTimerState()
        }
        queuePersistence.updatePosition(0L, newIndex)
    }

    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
        _videoDimensions.value = com.example.player.model.VideoDimensions(
            width = videoSize.width,
            height = videoSize.height,
            unappliedRotationDegrees = videoSize.unappliedRotationDegrees
        )
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        equalizerManager.attachToAudioSession(audioSessionId)
    }

    override fun onCues(cueGroup: CueGroup) {
        if (!_subtitlesEnabled.value) {
            _activeCues.value = emptyList()
            return
        }
        val delayMs = _subtitleDelayMs.value
        cueDelayJob?.cancel()
        if (delayMs <= 0L) {
            _activeCues.value = cueGroup.cues
        } else {
            cueDelayJob = scope.launch {
                delay(delayMs)
                _activeCues.value = cueGroup.cues
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
        if (isPlaying) {
            _duration.value = exoPlayer.duration.coerceAtLeast(0L)
            startPlaybackService()
            checkAndNotifyTrackPlaybackStarted()
        } else {
            val curPos = exoPlayer.currentPosition
            queuePersistence.updatePosition(curPos, exoPlayer.currentMediaItemIndex)
            if (exoPlayer.playbackState == Player.STATE_READY) {
                onPlaybackProgressChanged?.invoke(
                    curPos,
                    exoPlayer.duration.coerceAtLeast(0L)
                )
            }
        }
    }

    private fun checkAndNotifyTrackPlaybackStarted() {
        val idx = _currentIndex.value
        if (idx in _playlist.value.indices) {
            val item = _playlist.value[idx]
            if (item.uri != lastRecordedPlaybackUri) {
                lastRecordedPlaybackUri = item.uri
                onTrackPlaybackStarted?.invoke(item)
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        _playbackState.value = playbackState
        if (playbackState == Player.STATE_READY) {
            _duration.value = exoPlayer.duration.coerceAtLeast(0L)
            updateTracks()
            updateMediaInfo()
        } else if (playbackState == Player.STATE_ENDED) {
            if (_sleepTimerState.value.stopAtEndOfSong) {
                pause()
                _sleepTimerState.value = SleepTimerState()
            }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        updateTracks()
        updateMediaInfo()
    }

    override fun onPlayerError(error: PlaybackException) {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) {
            Log.w("OmniPlayerController", "File inaccessible (${error.message}). Skipping to next track if available.")
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
                exoPlayer.prepare()
                if (exoPlayer.playWhenReady) {
                    exoPlayer.play()
                }
                return
            }
        }

        val userFriendlyMessage = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> "Este formato o códec de video/audio no es compatible con este dispositivo."
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "El archivo multimedia no fue encontrado en el almacenamiento."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Error de conexión de red al intentar reproducir la transmisión."
            else -> "Error de reproducción (${error.errorCodeName}): ${error.localizedMessage ?: "formato no compatible"}"
        }
        _errorMessage.value = userFriendlyMessage
    }

    private fun updateTracks() {
        val audioList = mutableListOf<PlayerTrackInfo>()
        val subtitleList = mutableListOf<PlayerTrackInfo>()

        val currentTracks = exoPlayer.currentTracks
        for (groupInfo in currentTracks.groups) {
            val group = groupInfo.mediaTrackGroup
            for (i in 0 until group.length) {
                val format = group.getFormat(i)
                val id = format.id ?: "${format.language ?: "und"}_$i"
                val lang = format.language ?: "desconocido"
                val label = format.label ?: if (lang != "desconocido") lang.uppercase() else "Pista ${i + 1}"
                val isSelected = groupInfo.isTrackSelected(i)

                val trackInfo = PlayerTrackInfo(
                    id = id,
                    index = i,
                    language = lang,
                    label = label,
                    mimeType = format.sampleMimeType ?: "",
                    isSelected = isSelected
                )

                if (groupInfo.type == C.TRACK_TYPE_AUDIO) {
                    audioList.add(trackInfo)
                } else if (groupInfo.type == C.TRACK_TYPE_TEXT) {
                    subtitleList.add(trackInfo)
                }
            }
        }
        _availableAudioTracks.value = audioList
        _availableSubtitles.value = subtitleList
    }

    private fun updateMediaInfo() {
        val format = exoPlayer.videoFormat
        val audioFormat = exoPlayer.audioFormat
        val width = format?.width ?: 0
        val height = format?.height ?: 0
        val fps = format?.frameRate ?: 0f
        val bitrate = (format?.bitrate ?: audioFormat?.bitrate ?: 0).toLong()
        val mime = format?.sampleMimeType ?: audioFormat?.sampleMimeType ?: "audio/*"
        val sampleRate = audioFormat?.sampleRate ?: 0
        val channelCount = audioFormat?.channelCount ?: 0
        val audioMime = audioFormat?.sampleMimeType ?: ""

        _mediaInfo.value = MediaInfoDetails(
            title = currentTitle,
            uri = currentUri?.toString() ?: "",
            sizeBytes = 0L,
            durationMs = exoPlayer.duration.coerceAtLeast(0L),
            width = width,
            height = height,
            mimeType = mime,
            fps = fps,
            bitrate = bitrate,
            audioTracksCount = _availableAudioTracks.value.size,
            subtitleTracksCount = _availableSubtitles.value.size,
            sampleRate = sampleRate,
            channelCount = channelCount,
            audioMimeType = audioMime
        )
    }

    fun release() {
        progressJob?.cancel()
        cueDelayJob?.cancel()
        sleepTimerJob?.cancel()
        equalizerManager.release()
        exoPlayer.removeListener(this)
        exoPlayer.release()
        mediaSession.release()
        scope.cancel()
        synchronized(OmniPlayerController::class.java) {
            if (sInstance === this) {
                sInstance = null
            }
        }
    }

    companion object {
        @Volatile
        private var sInstance: OmniPlayerController? = null

        fun getInstance(context: Context): OmniPlayerController {
            return sInstance ?: synchronized(this) {
                sInstance ?: OmniPlayerController(context.applicationContext).also { sInstance = it }
            }
        }
    }
}
