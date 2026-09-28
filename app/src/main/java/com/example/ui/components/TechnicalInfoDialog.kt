package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.player.model.MediaInfoDetails

@Composable
fun TechnicalInfoDialog(
    mediaInfo: MediaInfoDetails?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Información Técnica")
            }
        },
        text = {
            if (mediaInfo == null) {
                Text("No hay detalles técnicos disponibles para esta pista.")
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .testTag("tech_info_content"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoRow("Título", mediaInfo.title)
                    if (mediaInfo.artist.isNotEmpty()) {
                        InfoRow("Artista", mediaInfo.artist)
                    }
                    if (mediaInfo.album.isNotEmpty()) {
                        InfoRow("Álbum", mediaInfo.album)
                    }
                    if (mediaInfo.genre.isNotEmpty()) {
                        InfoRow("Género", mediaInfo.genre)
                    }
                    InfoRow("URI / Ubicación", mediaInfo.uri)
                    if (mediaInfo.durationMs > 0) {
                        InfoRow("Duración", formatDuration(mediaInfo.durationMs))
                    }
                    if (mediaInfo.sizeBytes > 0) {
                        InfoRow("Tamaño de archivo", formatFileSize(mediaInfo.sizeBytes))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Parámetros de Audio",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (mediaInfo.audioMimeType.isNotEmpty()) {
                        InfoRow("Códec / Formato", mediaInfo.audioMimeType)
                    } else if (mediaInfo.mimeType.isNotEmpty()) {
                        InfoRow("Tipo MIME", mediaInfo.mimeType)
                    }

                    if (mediaInfo.sampleRate > 0) {
                        InfoRow("Frecuencia de muestreo", "${mediaInfo.sampleRate} Hz")
                    }

                    if (mediaInfo.channelCount > 0) {
                        val channelsText = when (mediaInfo.channelCount) {
                            1 -> "Mono (1 canal)"
                            2 -> "Estéreo (2 canales)"
                            6 -> "5.1 Surround (6 canales)"
                            else -> "${mediaInfo.channelCount} canales"
                        }
                        InfoRow("Canales", channelsText)
                    }

                    if (mediaInfo.bitrate > 0) {
                        InfoRow("Tasa de bits (Bitrate)", "${mediaInfo.bitrate / 1000} kbps")
                    }

                    if (mediaInfo.width > 0 && mediaInfo.height > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Parámetros de Video",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        InfoRow("Resolución", "${mediaInfo.width} x ${mediaInfo.height}")
                        if (mediaInfo.fps > 0) {
                            InfoRow("Cuadros por segundo", "%.2f fps".format(mediaInfo.fps))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
