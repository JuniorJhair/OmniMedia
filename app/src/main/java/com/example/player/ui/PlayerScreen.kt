package com.example.player.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.core.model.MediaType
import com.example.player.controller.OmniPlayerController
import com.example.player.gestures.GestureOverlay
import com.example.player.model.AspectRatioMode
import com.example.player.model.GestureState
import com.example.player.model.GestureType
import com.example.player.model.PlayerOrientationPreference
import com.example.player.model.RepeatMode
import com.example.player.model.SpeedApplicationScope
import com.example.player.model.SystemRotationMode
import com.example.player.model.nextOrientationPreference
import com.example.player.model.resolveRequestedOrientation
import com.example.ui.components.MusicWaveformIndicator
import com.example.ui.components.QueueBottomSheet
import com.example.ui.components.SongArtwork
import com.example.ui.components.TechnicalInfoDialog
import com.example.ui.components.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    mediaUri: String,
    mediaTitle: String,
    savedProgressMs: Long = 0L,
    doubleTapSeconds: Int = 10,
    isInPipMode: Boolean = false,
    onBackPressed: (currentPosMs: Long, durationMs: Long) -> Unit,
    onProgressUpdate: ((positionMs: Long, durationMs: Long) -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: ((isFavorite: Boolean) -> Unit)? = null,
    onSaveQueueAsPlaylist: ((name: String) -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onVideoSpeedChanged: ((speed: Float, scope: SpeedApplicationScope, mediaUri: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    val controller = remember { OmniPlayerController.getInstance(context) }

    val isPlaying by controller.isPlaying.collectAsStateWithLifecycle()
    val playbackState by controller.playbackState.collectAsStateWithLifecycle()
    val currentPosition by controller.currentPosition.collectAsStateWithLifecycle()
    val duration by controller.duration.collectAsStateWithLifecycle()
    val bufferedPosition by controller.bufferedPosition.collectAsStateWithLifecycle()
    val speed by controller.playbackSpeed.collectAsStateWithLifecycle()
    val globalVideoSpeed by controller.globalVideoSpeed.collectAsStateWithLifecycle()
    val currentVideoSpecificSpeed by controller.currentVideoSpecificSpeed.collectAsStateWithLifecycle()
    val isMuted by controller.isMuted.collectAsStateWithLifecycle()
    val aspectRatioMode by controller.aspectRatioMode.collectAsStateWithLifecycle()
    val isAudioOnly by controller.isAudioOnly.collectAsStateWithLifecycle()
    val audioTracks by controller.availableAudioTracks.collectAsStateWithLifecycle()
    val subtitles by controller.availableSubtitles.collectAsStateWithLifecycle()
    val subtitlesEnabled by controller.subtitlesEnabled.collectAsStateWithLifecycle()
    val subtitleDelayMs by controller.subtitleDelayMs.collectAsStateWithLifecycle()
    val activeCues by controller.activeCues.collectAsStateWithLifecycle()
    val mediaInfo by controller.mediaInfo.collectAsStateWithLifecycle()
    val errorMessage by controller.errorMessage.collectAsStateWithLifecycle()

    val playlist by controller.playlist.collectAsStateWithLifecycle()
    val currentIndex by controller.currentIndex.collectAsStateWithLifecycle()
    val shuffleEnabled by controller.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by controller.repeatMode.collectAsStateWithLifecycle()
    val sleepTimerState by controller.sleepTimerState.collectAsStateWithLifecycle()
    val equalizerState by controller.equalizerState.collectAsStateWithLifecycle()
    val videoDimensions by controller.videoDimensions.collectAsStateWithLifecycle()

    var areControlsVisible by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }

    val currentItem = playlist.getOrNull(currentIndex)
    var hasPromptedResumeForUri by remember(mediaUri) { mutableStateOf(false) }
    var showResumeDialog by remember(mediaUri) {
        val shouldPrompt = savedProgressMs > 3000L && !isAudioOnly && currentItem?.mediaType != MediaType.AUDIO
        if (shouldPrompt) hasPromptedResumeForUri = true
        mutableStateOf(shouldPrompt)
    }

    LaunchedEffect(mediaUri, savedProgressMs) {
        if (!hasPromptedResumeForUri && savedProgressMs > 3000L && !isAudioOnly && currentItem?.mediaType != MediaType.AUDIO) {
            hasPromptedResumeForUri = true
            controller.pause()
            showResumeDialog = true
        }
    }

    LaunchedEffect(showResumeDialog, isPlaying) {
        if (showResumeDialog && isPlaying) {
            controller.pause()
        }
    }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showMediaInfoDialog by remember { mutableStateOf(false) }
    var showAspectDialog by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showOrientationDialog by remember { mutableStateOf(false) }

    var systemRotationMode by remember {
        mutableStateOf(readSystemRotationMode(context))
    }
    var playerOrientationPref by remember(mediaUri) {
        mutableStateOf(PlayerOrientationPreference.FOLLOW_SYSTEM)
    }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemRotationMode = readSystemRotationMode(context)
            }
        }
        try {
            resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                false,
                observer
            )
        } catch (_: Exception) {}
        onDispose {
            try {
                resolver.unregisterContentObserver(observer)
            } catch (_: Exception) {}
        }
    }

    var gestureState by remember { mutableStateOf(GestureState()) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()

    // Notify progress update on pause
    LaunchedEffect(controller) {
        controller.onPlaybackProgressChanged = { pos, dur ->
            onProgressUpdate?.invoke(pos, dur)
        }
    }

    // SAF Subtitle Picker
    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            val uriStr = uri.toString().lowercase()
            val mimeType = when {
                uriStr.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                uriStr.endsWith(".ssa") || uriStr.endsWith(".ass") -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            controller.addExternalSubtitle(uri, mimeType = mimeType)
        }
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(areControlsVisible, isPlaying, isControlsLocked) {
        if (areControlsVisible && isPlaying && !isControlsLocked) {
            delay(4000)
            areControlsVisible = false
        }
    }

    // Handle Back Press
    BackHandler {
        if (isControlsLocked) {
            isControlsLocked = false
        } else {
            onBackPressed(currentPosition, duration)
        }
    }

    // Apply orientation mode and restore window brightness and orientation on exit
    DisposableEffect(activity, isAudioOnly, currentItem?.mediaType, systemRotationMode, playerOrientationPref) {
        val isAudioItem = currentItem?.mediaType == MediaType.AUDIO
        if (!isAudioOnly && !isAudioItem) {
            activity?.requestedOrientation = resolveRequestedOrientation(
                systemRotationMode = systemRotationMode,
                playerPreference = playerOrientationPref
            )
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.window?.attributes?.let { lp ->
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                activity.window.attributes = lp
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Dynamic fullscreen system bars management for video mode
    DisposableEffect(activity, isAudioOnly, isInPipMode) {
        if (activity != null && !isAudioOnly && !isInPipMode) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (activity != null) {
                val window = activity.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-adjust orientation based on video aspect ratio (vertical vs horizontal)
    LaunchedEffect(videoDimensions, isAudioOnly) {
        if (!isAudioOnly && activity != null) {
            val format = controller.exoPlayer.videoFormat
            val width = if (videoDimensions.width > 0) videoDimensions.width else (format?.width ?: 0)
            val height = if (videoDimensions.height > 0) videoDimensions.height else (format?.height ?: 0)
            val rotation = if (videoDimensions.width > 0) videoDimensions.unappliedRotationDegrees else (format?.rotationDegrees ?: 0)
            if (width > 0 && height > 0) {
                val isVertical = (height > width && rotation % 180 == 0) || (width > height && rotation % 180 != 0)
                if (isVertical) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }
    }

    // Start playback if playlist is empty or new media
    LaunchedEffect(mediaUri) {
        val currentLoadedUri = controller.playlist.value.getOrNull(controller.currentIndex.value)?.uri
        if (currentLoadedUri != mediaUri) {
            val uri = Uri.parse(mediaUri)
            controller.prepareMedia(uri, mediaTitle, if (savedProgressMs > 3000L) 0L else savedProgressMs)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Video View or Audio-only view
        if (!isAudioOnly) {
            val videoModifier = if (isInPipMode) {
                Modifier.fillMaxSize()
            } else {
                when (aspectRatioMode) {
                    AspectRatioMode.FIXED_16_9 -> Modifier.aspectRatio(16f / 9f)
                    AspectRatioMode.FIXED_4_3 -> Modifier.aspectRatio(4f / 3f)
                    else -> Modifier.fillMaxSize()
                }
            }

            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = controller.exoPlayer
                        useController = false
                        subtitleView?.visibility = android.view.View.GONE
                        resizeMode = controller.getResizeMode()
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = controller.getResizeMode()
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = videoModifier
            )
        } else {
            if (!isInPipMode) {
                val currentItem = playlist.getOrNull(currentIndex)
                AudioPlayerContent(
                    mediaUri = currentItem?.uri ?: mediaUri,
                    mediaTitle = currentItem?.title ?: mediaTitle,
                    artist = currentItem?.artist ?: "",
                    album = currentItem?.album ?: "",
                    artworkUri = currentItem?.artworkUri ?: mediaUri,
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    bufferedPosition = bufferedPosition,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    isFavorite = isFavorite,
                    queueSize = playlist.size,
                    isEqualizerEnabled = equalizerState.isEnabled,
                    isSleepTimerActive = sleepTimerState.isActive,
                    onBackPressed = { onBackPressed(currentPosition, duration) },
                    onPlayPause = { controller.togglePlayPause() },
                    onSkipNext = { controller.playNext() },
                    onSkipPrevious = { controller.playPrevious() },
                    onSeek = { controller.seekTo(it) },
                    onToggleShuffle = { controller.toggleShuffle() },
                    onCycleRepeat = { controller.cycleRepeatMode() },
                    onToggleFavorite = { onToggleFavorite?.invoke(!isFavorite) },
                    onOpenQueue = { showQueueDialog = true },
                    onOpenEqualizer = { showEqualizerDialog = true },
                    onOpenSleepTimer = { showSleepTimerDialog = true },
                    onOpenTechInfo = { showMediaInfoDialog = true },
                    onAddToPlaylist = { onAddToPlaylist?.invoke() },
                    onSwitchToVideo = { controller.toggleAudioOnly() }
                )
            }
        }

        // Subtitles Overlay (hidden in PiP mode)
        if (!isInPipMode && subtitlesEnabled && activeCues.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (areControlsVisible && !isControlsLocked) 130.dp else 40.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                activeCues.forEach { cue ->
                    cue.text?.let { cueText ->
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = cueText.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Gesture Detection Overlay on Video Area (hidden in PiP mode or Audio-only mode)
        if (!isInPipMode && !isAudioOnly) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isControlsLocked) {
                        if (isControlsLocked) {
                            detectTapGestures(onTap = { areControlsVisible = !areControlsVisible })
                        } else {
                            detectTapGestures(
                                onTap = {
                                    areControlsVisible = !areControlsVisible
                                },
                                onDoubleTap = { offset ->
                                    val screenWidth = size.width
                                    when {
                                        offset.x < screenWidth * 0.35f -> {
                                            controller.seekBy(-doubleTapSeconds)
                                            gestureState = GestureState(GestureType.SEEK, seekSeconds = -doubleTapSeconds.toLong())
                                            scope.launch {
                                                delay(700)
                                                gestureState = GestureState()
                                            }
                                        }
                                        offset.x > screenWidth * 0.65f -> {
                                            controller.seekBy(doubleTapSeconds)
                                            gestureState = GestureState(GestureType.SEEK, seekSeconds = doubleTapSeconds.toLong())
                                            scope.launch {
                                                delay(700)
                                                gestureState = GestureState()
                                            }
                                        }
                                        else -> {
                                            controller.togglePlayPause()
                                        }
                                    }
                                }
                            )
                        }
                    }
                    .pointerInput(isControlsLocked) {
                        if (!isControlsLocked) {
                            var totalDragX = 0f
                            var totalDragY = 0f
                            var isHorizontalSeek = false
                            var initialDragX = 0f
                            var startVolume = 0
                            var startBrightness = 0.5f

                            detectDragGestures(
                                onDragStart = { offset ->
                                    totalDragX = 0f
                                    totalDragY = 0f
                                    isHorizontalSeek = false
                                    initialDragX = offset.x
                                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val currentBr = activity?.window?.attributes?.screenBrightness ?: -1f
                                    startBrightness = if (currentBr in 0.01f..1f) currentBr else 0.5f
                                },
                                onDragEnd = {
                                    if (isHorizontalSeek && gestureState.type == GestureType.SEEK) {
                                        controller.seekBy(gestureState.seekSeconds.toInt())
                                    }
                                    scope.launch {
                                        delay(400)
                                        gestureState = GestureState()
                                    }
                                },
                                onDragCancel = {
                                    gestureState = GestureState()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragX += dragAmount.x
                                    totalDragY += dragAmount.y

                                    if (!isHorizontalSeek && abs(totalDragX) > abs(totalDragY) * 1.5f && abs(totalDragX) > 40f) {
                                        isHorizontalSeek = true
                                    }

                                    if (isHorizontalSeek) {
                                        val seekDeltaSec = (totalDragX / 15f).toLong()
                                        gestureState = GestureState(type = GestureType.SEEK, seekSeconds = seekDeltaSec)
                                    } else {
                                        val isLeftSide = initialDragX < size.width / 2f
                                        val dragFraction = -totalDragY / 500f

                                        if (isLeftSide) {
                                            val window = activity?.window
                                            val layoutParams = window?.attributes
                                            val newBrightness = (startBrightness + dragFraction).coerceIn(0.01f, 1f)
                                            layoutParams?.screenBrightness = newBrightness
                                            window?.attributes = layoutParams

                                            gestureState = GestureState(
                                                type = GestureType.BRIGHTNESS,
                                                valuePercent = (newBrightness * 100).toInt()
                                            )
                                        } else {
                                            val newVolFloat = startVolume + (dragFraction * maxVolume)
                                            val newVol = newVolFloat.roundToInt().coerceIn(0, maxVolume)
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)

                                            gestureState = GestureState(
                                                type = GestureType.VOLUME,
                                                valuePercent = ((newVol.toFloat() / maxVolume) * 100).toInt()
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
            )

            // Center HUD for Gestures
            GestureOverlay(
                gestureState = gestureState,
                modifier = Modifier.align(Alignment.Center)
            )

            // Buffering Indicator
            if (playbackState == Player.STATE_BUFFERING) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Center).size(52.dp)
                )
            }

            // Lock button when controls are locked
            if (isControlsLocked) {
                Surface(
                    onClick = { isControlsLocked = false },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(16.dp)
                        .size(48.dp)
                        .testTag("unlock_player_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Desbloquear pantalla",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Full Controls Overlay
            AnimatedVisibility(
                visible = areControlsVisible && !isControlsLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.38f))
                ) {
                    // Top Control Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onBackPressed(currentPosition, duration) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                        }
                        Text(
                            text = mediaTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                        )

                        // PiP button
                        IconButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val format = controller.exoPlayer.videoFormat
                                val rational = if (format != null && format.width > 0 && format.height > 0) {
                                    val aspect = format.width.toFloat() / format.height.toFloat()
                                    if (aspect in 0.41841f..2.39f) {
                                        Rational(format.width, format.height)
                                    } else if (aspect < 0.41841f) {
                                        Rational(100, 239)
                                    } else {
                                        Rational(239, 100)
                                    }
                                } else {
                                    Rational(16, 9)
                                }
                                val params = PictureInPictureParams.Builder()
                                    .setAspectRatio(rational)
                                    .build()
                                activity?.enterPictureInPictureMode(params)
                            }
                        }) {
                            Icon(Icons.Default.PictureInPicture, contentDescription = "Picture-in-Picture", tint = Color.White)
                        }

                        // Queue button
                        IconButton(onClick = { showQueueDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Cola", tint = Color.White)
                        }

                        // Equalizer button
                        IconButton(onClick = { showEqualizerDialog = true }) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = "Ecualizador",
                                tint = if (equalizerState.isEnabled) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        // Sleep Timer button
                        IconButton(onClick = { showSleepTimerDialog = true }) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = "Temporizador",
                                tint = if (sleepTimerState.isActive) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        // Audio tracks & Subtitles
                        IconButton(onClick = { showAudioDialog = true }) {
                            Icon(Icons.Default.Audiotrack, contentDescription = "Pistas de audio", tint = Color.White)
                        }
                        IconButton(onClick = { showSubtitleDialog = true }) {
                            Icon(
                                Icons.Default.Subtitles,
                                contentDescription = "Subtítulos",
                                tint = if (subtitlesEnabled) Color.White else Color.White.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(onClick = { showAspectDialog = true }) {
                            Icon(Icons.Default.Crop, contentDescription = "Relación de aspecto", tint = Color.White)
                        }
                        IconButton(onClick = { showMediaInfoDialog = true }) {
                            Icon(Icons.Default.Info, contentDescription = "Información", tint = Color.White)
                        }
                        IconButton(onClick = { isControlsLocked = true }) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Bloquear controles", tint = Color.White)
                        }
                    }

                    // Center Play/Pause & Queue Navigation
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { controller.playPrevious() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Canción anterior",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        IconButton(
                            onClick = { controller.seekBy(-doubleTapSeconds) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Retroceder $doubleTapSeconds s",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Surface(
                            onClick = { controller.togglePlayPause() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(68.dp).testTag("play_pause_center_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { controller.seekBy(doubleTapSeconds) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Adelantar $doubleTapSeconds s",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = { controller.playNext() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Siguiente canción",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    // Bottom Control Bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                )
                            )
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Timeline Scrubber
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayPos = if (isDraggingSlider) sliderDragPosition.toLong() else currentPosition
                            Text(
                                text = formatDuration(displayPos),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Slider(
                                value = if (isDraggingSlider) sliderDragPosition else currentPosition.toFloat(),
                                onValueChange = {
                                    isDraggingSlider = true
                                    sliderDragPosition = it
                                },
                                onValueChangeFinished = {
                                    isDraggingSlider = false
                                    controller.seekTo(sliderDragPosition.toLong())
                                },
                                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("timeline_slider"),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                            Text(
                                text = formatDuration(duration),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        // Bottom Action Icons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { controller.toggleMute() }) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Silenciar / Sonido",
                                        tint = Color.White
                                    )
                                }
                                IconButton(onClick = { controller.toggleShuffle() }) {
                                    Icon(
                                        Icons.Default.Shuffle,
                                        contentDescription = if (shuffleEnabled) "Aleatorio activado" else "Aleatorio desactivado",
                                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(onClick = { controller.cycleRepeatMode() }) {
                                    Icon(
                                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                        contentDescription = "Modo repetición: ${repeatMode.displayName}",
                                        tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                    )
                                }
                                IconButton(onClick = { showSpeedDialog = true }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Speed, contentDescription = "Velocidad", tint = Color.White, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "${speed}x", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { controller.toggleAudioOnly() }) {
                                    Icon(
                                        Icons.Default.Audiotrack,
                                        contentDescription = "Modo solo audio",
                                        tint = if (isAudioOnly) MaterialTheme.colorScheme.primary else Color.White
                                    )
                                }
                                Surface(
                                    onClick = { showOrientationDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .testTag("orientation_mode_chip")
                                ) {
                                    Text(
                                        text = playerOrientationPref.shortLabel,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val isCurrentlyLandscape =
                                            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                                                playerOrientationPref == PlayerOrientationPreference.LANDSCAPE
                                        val nextPref = nextOrientationPreference(
                                            current = playerOrientationPref,
                                            isCurrentlyLandscape = isCurrentlyLandscape
                                        )
                                        playerOrientationPref = nextPref
                                        activity?.requestedOrientation = resolveRequestedOrientation(
                                            systemRotationMode = systemRotationMode,
                                            playerPreference = nextPref
                                        )
                                    },
                                    modifier = Modifier.testTag("rotate_screen_btn")
                                ) {
                                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotar pantalla", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error message banner if playback fails
        if (!isInPipMode && errorMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error de reproducción",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Button(onClick = { onBackPressed(currentPosition, duration) }) {
                        Text("Volver")
                    }
                }
            }
        }

        // Dialogs (suppressed when in PiP)
        if (!isInPipMode) {
            if (showResumeDialog) {
                ResumePlaybackDialog(
                    savedPositionMs = savedProgressMs,
                    onContinue = {
                        showResumeDialog = false
                        controller.seekTo(savedProgressMs)
                        controller.play()
                    },
                    onStartOver = {
                        showResumeDialog = false
                        controller.seekTo(0L)
                        controller.play()
                    },
                    onCancel = {
                        showResumeDialog = false
                        controller.pause()
                        onBackPressed(0L, duration)
                    }
                )
            }

            if (showSpeedDialog) {
                SpeedSelectorDialog(
                    currentSpeed = speed,
                    specificVideoSpeed = currentVideoSpecificSpeed,
                    globalVideoSpeed = globalVideoSpeed,
                    initialScope = SpeedApplicationScope.THIS_VIDEO,
                    onSelectSpeed = { controller.setSpeed(it) },
                    onSelectSpeedWithScope = { selectedSpeed, scope ->
                        controller.setVideoSpeed(selectedSpeed, scope, mediaUri)
                        onVideoSpeedChanged?.invoke(selectedSpeed, scope, mediaUri)
                    },
                    onClearSpecificSpeed = {
                        controller.clearVideoSpecificSpeed(mediaUri)
                    },
                    onDismiss = { showSpeedDialog = false }
                )
            }

            if (showOrientationDialog) {
                OrientationSelectorDialog(
                    currentPreference = playerOrientationPref,
                    systemRotationMode = systemRotationMode,
                    onSelectPreference = { newPref ->
                        playerOrientationPref = newPref
                        activity?.requestedOrientation = resolveRequestedOrientation(
                            systemRotationMode = systemRotationMode,
                            playerPreference = newPref
                        )
                    },
                    onDismiss = { showOrientationDialog = false }
                )
            }

            if (showAudioDialog) {
                AudioTrackDialog(
                    audioTracks = audioTracks,
                    onSelectTrack = { controller.selectAudioTrack(it) },
                    onDismiss = { showAudioDialog = false }
                )
            }

            if (showSubtitleDialog) {
                SubtitleDialog(
                    subtitlesEnabled = subtitlesEnabled,
                    onToggleSubtitles = { controller.setSubtitlesEnabled(it) },
                    subtitles = subtitles,
                    onSelectSubtitle = { controller.selectSubtitleTrack(it) },
                    onLoadExternalSubtitle = {
                        subtitlePickerLauncher.launch(arrayOf("text/*", "application/*"))
                    },
                    subtitleDelayMs = subtitleDelayMs,
                    onAdjustDelay = { controller.setSubtitleDelay(it) },
                    onDismiss = { showSubtitleDialog = false }
                )
            }

            if (showMediaInfoDialog) {
                TechnicalInfoDialog(
                    mediaInfo = mediaInfo,
                    onDismiss = { showMediaInfoDialog = false }
                )
            }

            if (showAspectDialog) {
                AspectRatioDialog(
                    currentMode = aspectRatioMode,
                    onSelectMode = { controller.setAspectRatioMode(it) },
                    onDismiss = { showAspectDialog = false }
                )
            }

            if (showQueueDialog) {
                QueueBottomSheet(
                    playlist = playlist,
                    currentIndex = currentIndex,
                    isPlaying = isPlaying,
                    onSkipToIndex = { controller.skipToQueueIndex(it) },
                    onMoveItem = { from, to -> controller.moveQueueItem(from, to) },
                    onRemoveItem = { controller.removeFromQueue(it) },
                    onClearQueue = { controller.clearQueue(it) },
                    onSaveAsPlaylist = { onSaveQueueAsPlaylist?.invoke(it) },
                    onDismiss = { showQueueDialog = false }
                )
            }

            if (showSleepTimerDialog) {
                SleepTimerDialog(
                    sleepTimerState = sleepTimerState,
                    onSetTimerMinutes = { controller.setSleepTimer(it) },
                    onToggleStopAtEndOfSong = { controller.setStopAtEndOfSong(it) },
                    onCancelTimer = { controller.cancelSleepTimer() },
                    onDismiss = { showSleepTimerDialog = false }
                )
            }

            if (showEqualizerDialog) {
                EqualizerDialog(
                    state = equalizerState,
                    onToggleEnabled = { controller.equalizerManager.setEnabled(it) },
                    onBandGainChange = { bIndex, gainMb -> controller.equalizerManager.setBandGain(bIndex, gainMb) },
                    onSelectPreset = { pIndex -> controller.equalizerManager.setPreset(pIndex) },
                    onDismiss = { showEqualizerDialog = false }
                )
            }
        }
    }
}

@Composable
private fun AudioPlayerContent(
    mediaUri: String,
    mediaTitle: String,
    artist: String,
    album: String,
    artworkUri: String?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    isFavorite: Boolean,
    queueSize: Int,
    isEqualizerEnabled: Boolean,
    isSleepTimerActive: Boolean,
    onBackPressed: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenTechInfo: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSwitchToVideo: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF141218),
                        Color(0xFF1D1B20),
                        Color(0xFF2B2830)
                    )
                )
            )
            .testTag("audio_player_screen")
    ) {
        val availableHeight = maxHeight
        // Calculate adaptive artwork size so all controls (TopBar, Artwork, Title/Artist, Timeline,
        // Main Playback Controls, and Secondary Actions: Timer/Equalizer/Info) fit on one screen without scrolling.
        val artworkSize = (availableHeight - 310.dp).coerceIn(88.dp, 172.dp)
        val needsFallbackScroll = availableHeight < 380.dp
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (needsFallbackScroll) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Bar (Compact)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackPressed,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Minimizar",
                        tint = Color.White
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "REPRODUCIENDO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = mediaTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onOpenQueue,
                    modifier = Modifier.size(48.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Cola de reproducción",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 2. Adaptive Album Artwork
            Box(
                modifier = Modifier
                    .size(artworkSize)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .testTag("audio_player_artwork"),
                contentAlignment = Alignment.Center
            ) {
                SongArtwork(
                    uri = artworkUri ?: mediaUri,
                    title = mediaTitle,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = (artworkSize * 0.4f).coerceIn(36.dp, 68.dp),
                    shape = RoundedCornerShape(20.dp)
                )
            }

            // 3. Title, Artist/Album & Inline Favorite / Playlist Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(48.dp).testTag("audio_player_favorite_btn")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        MusicWaveformIndicator(isPlaying = isPlaying)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = mediaTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    val sub = buildString {
                        val art = artist.ifEmpty { "Artista desconocido" }
                        append(art)
                        if (album.isNotEmpty()) {
                            append(" • ")
                            append(album)
                        }
                    }
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.size(48.dp).testTag("audio_player_add_playlist_btn")
                ) {
                    Icon(
                        Icons.Default.PlaylistAdd,
                        contentDescription = "Añadir a playlist",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 4. Priority Timeline / Seekbar
            Column(modifier = Modifier.fillMaxWidth()) {
                val currentSec = if (isDragging) dragPosition else currentPosition.toFloat()
                val totalSec = duration.coerceAtLeast(1L).toFloat()

                Slider(
                    value = currentSec.coerceIn(0f, totalSec),
                    onValueChange = {
                        isDragging = true
                        dragPosition = it
                    },
                    onValueChangeFinished = {
                        onSeek(dragPosition.toLong())
                        isDragging = false
                    },
                    valueRange = 0f..totalSec,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("audio_player_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(if (isDragging) dragPosition.toLong() else currentPosition),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            // 5. Priority Main Playback Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(48.dp).testTag("audio_player_shuffle_btn")
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(48.dp).testTag("audio_player_prev_btn")
                ) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .clickable { onPlayPause() }
                        .testTag("audio_player_play_pause"),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(48.dp).testTag("audio_player_next_btn")
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Siguiente",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier.size(48.dp).testTag("audio_player_repeat_btn")
                ) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repetir: ${repeatMode.displayName}",
                        tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 6. Visible Compact Secondary Actions Row (Temporizador, Ecualizador, Información técnica, Modo video)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("audio_player_secondary_actions")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenSleepTimer,
                        modifier = Modifier.size(48.dp).testTag("audio_player_timer_btn")
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = "Temporizador de apagado",
                            tint = if (isSleepTimerActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f)
                        )
                    }

                    IconButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier.size(48.dp).testTag("audio_player_equalizer_btn")
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = "Ecualizador",
                            tint = if (isEqualizerEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f)
                        )
                    }

                    IconButton(
                        onClick = onOpenTechInfo,
                        modifier = Modifier.size(48.dp).testTag("audio_player_info_btn")
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Información técnica",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    IconButton(
                        onClick = onSwitchToVideo,
                        modifier = Modifier.size(48.dp).testTag("audio_player_video_mode_btn")
                    ) {
                        Icon(
                            Icons.Default.Crop,
                            contentDescription = "Modo video",
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

private fun readSystemRotationMode(context: Context): SystemRotationMode {
    val autoRotateEnabled = try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            1
        ) == 1
    } catch (_: Exception) {
        true
    }
    return if (autoRotateEnabled) {
        SystemRotationMode.AUTO_ROTATE_ENABLED
    } else {
        SystemRotationMode.AUTO_ROTATE_DISABLED
    }
}
