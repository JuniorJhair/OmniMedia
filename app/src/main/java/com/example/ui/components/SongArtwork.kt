package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val artworkMemoryCache = LruCache<String, Bitmap>(100)

@Composable
fun SongArtwork(
    uri: String,
    title: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf(artworkMemoryCache.get(uri)) }

    LaunchedEffect(uri) {
        if (bitmap == null && uri.isNotEmpty()) {
            val loaded = withContext(Dispatchers.IO) {
                loadSongArtworkBitmap(context, uri)
            }
            if (loaded != null) {
                artworkMemoryCache.put(uri, loaded)
                bitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            AsyncImage(
                model = bitmap,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Elegant OmniMedia vinyl disk / note fallback
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Transparent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(iconSize * 1.6f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            }
        }
    }
}

private fun loadSongArtworkBitmap(context: Context, uriString: String): Bitmap? {
    return try {
        val parsedUri = Uri.parse(uriString)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uriString.startsWith("content://media/")) {
            try {
                return context.contentResolver.loadThumbnail(parsedUri, Size(256, 256), null)
            } catch (_: Exception) {}
        }

        // Fallback: extract embedded picture via MediaMetadataRetriever
        val mmr = MediaMetadataRetriever()
        try {
            if (uriString.startsWith("content://") || uriString.startsWith("file://")) {
                mmr.setDataSource(context, parsedUri)
            } else {
                mmr.setDataSource(uriString)
            }
            val artBytes = mmr.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                return BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, opts)
            }
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
        null
    } catch (_: Exception) {
        null
    }
}

@Composable
fun MusicWaveformIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 4,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val heights = (0 until barCount).map { i ->
        val duration = 400 + i * 150
        if (isPlaying) {
            val anim by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )
            anim
        } else {
            0.3f
        }
    }

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { frac ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(frac)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}
