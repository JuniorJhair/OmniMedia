package com.example.ui.screens.images

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.example.data.local.entity.MediaFileEntity
import com.example.ui.components.formatFileSize
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun PhotoViewerScreen(
    photos: List<MediaFileEntity>,
    initialIndex: Int,
    onClose: () -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onDeletePhoto: (String) -> Unit,
    onSharePhoto: (MediaFileEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val context = LocalContext.current
    val activity = context as? Activity

    val safeIndex = initialIndex.coerceIn(0, photos.size - 1)
    val pagerState = rememberPagerState(initialPage = safeIndex, pageCount = { photos.size })

    var areControlsVisible by remember { mutableStateOf(true) }
    var isSlideshowActive by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val currentPhoto = photos.getOrNull(pagerState.currentPage) ?: photos.first()

    // Dynamic fullscreen system bars management
    DisposableEffect(activity, areControlsVisible) {
        if (activity != null) {
            val window = activity.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (areControlsVisible) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (activity != null) {
                val window = activity.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-advance slideshow without cancelling mid-scroll when currentPage changes
    LaunchedEffect(isSlideshowActive, photos.size) {
        while (isSlideshowActive && photos.size > 1) {
            delay(3500)
            if (!pagerState.isScrollInProgress) {
                val nextPage = (pagerState.settledPage + 1) % photos.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    // Ensure the pager never remains stuck at a fractional offset between two photos
    LaunchedEffect(pagerState.isScrollInProgress, isSlideshowActive) {
        if (!pagerState.isScrollInProgress && pagerState.currentPageOffsetFraction != 0f) {
            pagerState.scrollToPage(pagerState.currentPage)
        }
    }

    BackHandler {
        onClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .testTag("photo_viewer_screen")
    ) {
        // Horizontal Pager for Photos (full-bleed single image per viewport)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .testTag("photo_pager"),
            userScrollEnabled = true,
            pageSpacing = 0.dp,
            contentPadding = PaddingValues(0.dp),
            key = { index -> photos.getOrNull(index)?.uri ?: index.toString() },
            beyondViewportPageCount = 0
        ) { page ->
            val photo = photos[page]
            ZoomablePhotoItem(
                photo = photo,
                isCurrentPage = page == pagerState.currentPage,
                onTap = { areControlsVisible = !areControlsVisible }
            )
        }

        // Top Control Bar Overlay
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = currentPhoto.title.ifEmpty { currentPhoto.displayName },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val dateFormatted = remember(currentPhoto.dateAdded) {
                        try {
                            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                            sdf.format(Date(currentPhoto.dateAdded))
                        } catch (_: Exception) {
                            ""
                        }
                    }
                    if (dateFormatted.isNotEmpty()) {
                        Text(
                            text = dateFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Page count pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 8.dp).testTag("photo_page_indicator")
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Bottom Control Bar Overlay
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Slideshow Button
                IconButton(
                    onClick = { isSlideshowActive = !isSlideshowActive },
                    modifier = Modifier.size(48.dp).testTag("photo_slideshow_btn")
                ) {
                    Icon(
                        imageVector = if (isSlideshowActive) Icons.Default.Pause else Icons.Default.Slideshow,
                        contentDescription = if (isSlideshowActive) "Pausar presentación" else "Iniciar presentación",
                        tint = if (isSlideshowActive) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                // Favorite Button
                IconButton(
                    onClick = { onToggleFavorite(currentPhoto.uri, !currentPhoto.isFavorite) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (currentPhoto.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (currentPhoto.isFavorite) "Quitar de favoritos" else "Marcar como favorito",
                        tint = if (currentPhoto.isFavorite) MaterialTheme.colorScheme.primary else Color.White
                    )
                }

                // Share Button
                IconButton(
                    onClick = { onSharePhoto(currentPhoto) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Compartir foto",
                        tint = Color.White
                    )
                }

                // Info Dialog Button
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Detalles técnicos",
                        tint = Color.White
                    )
                }

                // Delete Button
                IconButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar foto",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Slideshow active floating pill
        if (isSlideshowActive) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Slideshow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Presentación en curso",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    // Technical Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Información de la imagen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Nombre", currentPhoto.displayName.ifEmpty { currentPhoto.title })
                    if (currentPhoto.width > 0 && currentPhoto.height > 0) {
                        InfoRow("Resolución", "${currentPhoto.width} × ${currentPhoto.height}")
                    }
                    InfoRow("Tamaño", formatFileSize(currentPhoto.sizeBytes))
                    InfoRow("Tipo MIME", currentPhoto.mimeType.ifEmpty { "image/jpeg" })
                    if (currentPhoto.folderName.isNotEmpty()) {
                        InfoRow("Carpeta", currentPhoto.folderName)
                    }
                    val dateStr = remember(currentPhoto.dateAdded) {
                        try {
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(currentPhoto.dateAdded))
                        } catch (_: Exception) { "" }
                    }
                    if (dateStr.isNotEmpty()) {
                        InfoRow("Fecha", dateStr)
                    }
                    InfoRow("Ruta / URI", currentPhoto.uri)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Eliminar foto") },
            text = { Text("¿Deseas eliminar permanentemente esta fotografía de tu dispositivo? Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        val uriToDelete = currentPhoto.uri
                        showDeleteConfirmDialog = false
                        onDeletePhoto(uriToDelete)
                        if (photos.size <= 1) {
                            onClose()
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

@Composable
private fun ZoomablePhotoItem(
    photo: MediaFileEntity,
    isCurrentPage: Boolean,
    onTap: () -> Unit
) {
    var scale by remember(photo.uri) { mutableFloatStateOf(1f) }
    var offset by remember(photo.uri) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && (scale != 1f || offset != Offset.Zero)) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    BackHandler(enabled = isCurrentPage && scale > 1.05f) {
        scale = 1f
        offset = Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(photo.uri) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            val maxOffsetX = (size.width * (scale - 1f)) / 2f
                            val maxOffsetY = (size.height * (scale - 1f)) / 2f
                            offset = Offset(
                                x = ((size.width / 2f - tapOffset.x) * 1.2f).coerceIn(-maxOffsetX, maxOffsetX),
                                y = ((size.height / 2f - tapOffset.y) * 1.2f).coerceIn(-maxOffsetY, maxOffsetY)
                            )
                        }
                    }
                )
            }
            .pointerInput(photo.uri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                scale = newScale
                                if (newScale > 1.02f) {
                                    val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                    val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                    offset = Offset(
                                        x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                } else {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            }
                        } else if (pressedCount == 1 && scale > 1.02f) {
                            val panChange = event.calculatePan()
                            if (panChange != Offset.Zero) {
                                val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                val atLeftEdgeSwipingRight = panChange.x > 0f && offset.x >= maxOffsetX - 2f
                                val atRightEdgeSwipingLeft = panChange.x < 0f && offset.x <= -maxOffsetX + 2f
                                val isHorizontalDominant = abs(panChange.x) > abs(panChange.y)

                                if (isHorizontalDominant && (atLeftEdgeSwipingRight || atRightEdgeSwipingLeft)) {
                                    // Do not consume: allow parent HorizontalPager to swipe to adjacent image
                                } else {
                                    offset = Offset(
                                        x = (offset.x + panChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                                        y = (offset.y + panChange.y).coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) {
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.title.ifEmpty { photo.displayName },
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
