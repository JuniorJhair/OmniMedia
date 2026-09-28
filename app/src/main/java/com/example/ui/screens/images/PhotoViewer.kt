package com.example.ui.screens.images

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.data.local.entity.MediaFileEntity
import com.example.player.model.MediaInfoDetails
import com.example.ui.components.TechnicalInfoDialog
import com.example.ui.components.formatFileSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PhotoViewer(
    initialImage: MediaFileEntity,
    images: List<MediaFileEntity>,
    onClose: () -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onDelete: (MediaFileEntity) -> Unit,
    onShare: (String) -> Unit,
    onProtectInVault: (MediaFileEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageList = remember(images, initialImage) {
        if (images.any { it.uri == initialImage.uri }) images else listOf(initialImage)
    }

    var currentIndex by remember {
        mutableIntStateOf(imageList.indexOfFirst { it.uri == initialImage.uri }.coerceAtLeast(0))
    }

    val currentImage = imageList.getOrNull(currentIndex) ?: initialImage

    // Zoom & Pan state
    var scale by remember(currentIndex) { mutableFloatStateOf(1f) }
    var offset by remember(currentIndex) { mutableStateOf(Offset.Zero) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var showTechInfoDialog by remember { mutableStateOf(false) }

    // Intercept hardware/system Back
    BackHandler {
        if (scale > 1.05f) {
            scale = 1f
            offset = Offset.Zero
        } else {
            onClose()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("photo_viewer_screen")
    ) {
        // Image Canvas with Zoom & Pan Gestures
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(currentIndex) {
                    detectTapGestures(
                        onTap = {
                            areControlsVisible = !areControlsVisible
                        },
                        onDoubleTap = { tapOffset ->
                            if (scale > 1.2f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                                // Center the zoom slightly toward tap
                                offset = Offset(
                                    x = (size.width / 2f - tapOffset.x) * 1.5f,
                                    y = (size.height / 2f - tapOffset.y) * 1.5f
                                )
                            }
                        }
                    )
                }
                .pointerInput(currentIndex) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale

                        if (newScale > 1f) {
                            // Boundary calculation
                            val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                            val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                            val newOffsetX = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                            val newOffsetY = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            offset = Offset(newOffsetX, newOffsetY)
                        } else {
                            offset = Offset.Zero
                            // Allow simple drag to change photos when scale is 1f
                            if (pan.x > 40f && currentIndex > 0) {
                                currentIndex--
                            } else if (pan.x < -40f && currentIndex < imageList.size - 1) {
                                currentIndex++
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = currentImage.uri,
                contentDescription = currentImage.title.ifEmpty { currentImage.displayName },
                contentScale = ContentScale.Fit,
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No se pudo cargar la imagen",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }

        // Top Controls Overlay
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("photo_viewer_back")) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentImage.title.ifEmpty { currentImage.displayName },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (imageList.size > 1) {
                            Text(
                                text = "${currentIndex + 1} de ${imageList.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    IconButton(onClick = { onToggleFavorite(currentImage.uri, !currentImage.isFavorite) }) {
                        Icon(
                            imageVector = if (currentImage.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorito",
                            tint = if (currentImage.isFavorite) MaterialTheme.colorScheme.error else Color.White
                        )
                    }

                    IconButton(onClick = { onShare(currentImage.uri) }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Compartir",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { showTechInfoDialog = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Información",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { onProtectInVault(currentImage) }) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Proteger en Bóveda",
                            tint = Color.White
                        )
                    }

                    IconButton(onClick = { onDelete(currentImage) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Bottom Metadata Overlay
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val formattedDate = remember(currentImage.dateAdded) {
                        try {
                            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                            sdf.format(Date(currentImage.dateAdded))
                        } catch (_: Exception) {
                            ""
                        }
                    }

                    val resText = if (currentImage.width > 0 && currentImage.height > 0) {
                        "${currentImage.width} × ${currentImage.height}"
                    } else {
                        currentImage.mimeType
                    }

                    Text(
                        text = resText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Text(
                        text = "${formatFileSize(currentImage.sizeBytes)}${if (formattedDate.isNotEmpty()) " • $formattedDate" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }
        }

        // Technical Information Dialog
        if (showTechInfoDialog) {
            TechnicalInfoDialog(
                mediaInfo = MediaInfoDetails(
                    title = currentImage.title.ifEmpty { currentImage.displayName },
                    uri = currentImage.uri,
                    sizeBytes = currentImage.sizeBytes,
                    durationMs = 0L,
                    width = currentImage.width,
                    height = currentImage.height,
                    mimeType = currentImage.mimeType,
                    fps = 0f,
                    bitrate = 0L,
                    audioTracksCount = 0,
                    subtitleTracksCount = 0,
                    sampleRate = 0,
                    channelCount = 0,
                    audioMimeType = ""
                ),
                onDismiss = { showTechInfoDialog = false }
            )
        }
    }
}
