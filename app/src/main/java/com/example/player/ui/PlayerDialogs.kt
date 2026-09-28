package com.example.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.player.model.AspectRatioMode
import com.example.player.model.EqualizerState
import com.example.player.model.MediaInfoDetails
import com.example.player.model.PlayerOrientationPreference
import com.example.player.model.PlaylistItem
import com.example.player.model.PlayerTrackInfo
import com.example.player.model.SleepTimerState
import com.example.player.model.SpeedApplicationScope
import com.example.player.model.SystemRotationMode
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize

@Composable
fun ResumePlaybackDialog(
    savedPositionMs: Long,
    onContinue: () -> Unit,
    onStartOver: () -> Unit,
    onCancel: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("¿Seguir viendo el video?") },
        text = {
            Text("Se encontró progreso guardado en ${formatDuration(savedPositionMs)}. ¿Deseas seguir viendo desde ese punto o empezar de nuevo?")
        },
        confirmButton = {
            Button(
                onClick = onContinue,
                modifier = Modifier.testTag("resume_continue_btn")
            ) {
                Text("Seguir viendo (${formatDuration(savedPositionMs)})")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("resume_cancel_btn")
                ) {
                    Text("Cancelar")
                }
                OutlinedButton(
                    onClick = onStartOver,
                    modifier = Modifier.testTag("resume_restart_btn")
                ) {
                    Text("Empezar de nuevo")
                }
            }
        }
    )
}

@Composable
fun SpeedSelectorDialog(
    currentSpeed: Float,
    specificVideoSpeed: Float? = null,
    globalVideoSpeed: Float = 1.0f,
    initialScope: SpeedApplicationScope = SpeedApplicationScope.THIS_VIDEO,
    onSelectSpeedWithScope: (Float, SpeedApplicationScope) -> Unit = { _, _ -> },
    onSelectSpeed: ((Float) -> Unit)? = null,
    onClearSpecificSpeed: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
    val initialHadSpecific = remember { specificVideoSpeed != null }
    var selectedSpeed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }
    var selectedScope by remember(initialScope) { mutableStateOf(initialScope) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Velocidad de reproducción")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                    items(speeds) { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSpeed = speed
                                    onSelectSpeed?.invoke(speed)
                                    onSelectSpeedWithScope(speed, selectedScope)
                                }
                                .padding(vertical = 4.dp)
                                .testTag("speed_option_${speed}x"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = speed == selectedSpeed,
                                onClick = {
                                    selectedSpeed = speed
                                    onSelectSpeed?.invoke(speed)
                                    onSelectSpeedWithScope(speed, selectedScope)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (speed == 1.0f) "1x (Normal)" else "${speed}x",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (speed == selectedSpeed) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Aplicar a:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedScope = SpeedApplicationScope.THIS_VIDEO
                            onSelectSpeedWithScope(selectedSpeed, SpeedApplicationScope.THIS_VIDEO)
                        }
                        .padding(vertical = 2.dp)
                        .testTag("speed_scope_this_video"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedScope == SpeedApplicationScope.THIS_VIDEO,
                        onClick = {
                            selectedScope = SpeedApplicationScope.THIS_VIDEO
                            onSelectSpeedWithScope(selectedSpeed, SpeedApplicationScope.THIS_VIDEO)
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Este video",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedScope == SpeedApplicationScope.THIS_VIDEO) FontWeight.Bold else FontWeight.Normal
                        )
                        if (specificVideoSpeed != null) {
                            Text(
                                text = "Específica actual: ${specificVideoSpeed}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedScope = SpeedApplicationScope.ALL_VIDEOS
                            if (!initialHadSpecific) {
                                onClearSpecificSpeed?.invoke()
                            }
                            onSelectSpeedWithScope(selectedSpeed, SpeedApplicationScope.ALL_VIDEOS)
                        }
                        .padding(vertical = 2.dp)
                        .testTag("speed_scope_all_videos"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedScope == SpeedApplicationScope.ALL_VIDEOS,
                        onClick = {
                            selectedScope = SpeedApplicationScope.ALL_VIDEOS
                            if (!initialHadSpecific) {
                                onClearSpecificSpeed?.invoke()
                            }
                            onSelectSpeedWithScope(selectedSpeed, SpeedApplicationScope.ALL_VIDEOS)
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Todos los videos",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selectedScope == SpeedApplicationScope.ALL_VIDEOS) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = "Velocidad global: ${globalVideoSpeed}x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (specificVideoSpeed != null && onClearSpecificSpeed != null) {
                    TextButton(
                        onClick = {
                            onClearSpecificSpeed()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("clear_specific_speed_btn")
                    ) {
                        Text("Quitar velocidad específica (usar global ${globalVideoSpeed}x)")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSelectSpeed?.invoke(selectedSpeed)
                    onSelectSpeedWithScope(selectedSpeed, selectedScope)
                    onDismiss()
                },
                modifier = Modifier.testTag("speed_apply_btn")
            ) {
                Text("Listo")
            }
        }
    )
}

@Composable
fun OrientationSelectorDialog(
    currentPreference: PlayerOrientationPreference,
    systemRotationMode: SystemRotationMode,
    onSelectPreference: (PlayerOrientationPreference) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ScreenRotation, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Orientación del reproductor")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                val sysText = if (systemRotationMode == SystemRotationMode.AUTO_ROTATE_ENABLED) {
                    "Rotación automática del sistema: ACTIVADA"
                } else {
                    "Rotación automática del sistema: DESACTIVADA"
                }
                Text(
                    text = sysText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))

                PlayerOrientationPreference.values().forEach { pref ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectPreference(pref)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp)
                            .testTag("orientation_option_${pref.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = pref == currentPreference,
                            onClick = {
                                onSelectPreference(pref)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pref.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (pref == currentPreference) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun AudioTrackDialog(
    audioTracks: List<PlayerTrackInfo>,
    onSelectTrack: (PlayerTrackInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pistas de audio") },
        text = {
            if (audioTracks.isEmpty()) {
                Text("No hay pistas de audio adicionales disponibles.")
            } else {
                LazyColumn {
                    items(audioTracks) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTrack(track)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = track.isSelected,
                                onClick = {
                                    onSelectTrack(track)
                                    onDismiss()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = track.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = "${track.language} • ${track.mimeType.substringAfterLast("/")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun SubtitleDialog(
    subtitlesEnabled: Boolean,
    onToggleSubtitles: (Boolean) -> Unit,
    subtitles: List<PlayerTrackInfo>,
    onSelectSubtitle: (PlayerTrackInfo?) -> Unit,
    onLoadExternalSubtitle: () -> Unit,
    subtitleDelayMs: Long,
    onAdjustDelay: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Subtitles, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Subtítulos")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Activar subtítulos", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Switch(checked = subtitlesEnabled, onCheckedChange = onToggleSubtitles)
                }

                HorizontalDivider()

                if (subtitlesEnabled) {
                    Text("Pistas disponibles:", style = MaterialTheme.typography.labelMedium)
                    if (subtitles.isEmpty()) {
                        Text(
                            text = "No se encontraron subtítulos internos en este archivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                            items(subtitles) { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectSubtitle(track) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = track.isSelected,
                                        onClick = { onSelectSubtitle(track) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = track.label, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sincronización: ${subtitleDelayMs}ms", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { onAdjustDelay(subtitleDelayMs - 250) }) { Text("-0.25s") }
                            TextButton(onClick = { onAdjustDelay(0) }) { Text("0") }
                            TextButton(onClick = { onAdjustDelay(subtitleDelayMs + 250) }) { Text("+0.25s") }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            onLoadExternalSubtitle()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cargar subtítulo externo (SRT, VTT...)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun MediaInfoDialog(
    info: MediaInfoDetails?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Información multimedia")
            }
        },
        text = {
            if (info != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Nombre", info.title)
                    InfoRow("Duración", formatDuration(info.durationMs))
                    if (info.width > 0 && info.height > 0) {
                        InfoRow("Resolución", "${info.width} x ${info.height}")
                    }
                    if (info.fps > 0) {
                        InfoRow("FPS", String.format("%.2f", info.fps))
                    }
                    if (info.bitrate > 0) {
                        InfoRow("Tasa de bits", "${info.bitrate / 1000} kbps")
                    }
                    InfoRow("MIME / Formato", info.mimeType)
                    InfoRow("Pistas de audio", "${info.audioTracksCount}")
                    InfoRow("Pistas de subtítulos", "${info.subtitleTracksCount}")
                    InfoRow("URI", info.uri)
                }
            } else {
                Text("Cargando información del medio...")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun AspectRatioDialog(
    currentMode: AspectRatioMode,
    onSelectMode: (AspectRatioMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Relación de aspecto") },
        text = {
            Column {
                AspectRatioMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectMode(mode)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = {
                                onSelectMode(mode)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
fun QueueDialog(
    playlist: List<PlaylistItem>,
    currentIndex: Int,
    onSkipToIndex: (Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cola de reproducción (${playlist.size})")
                if (playlist.isNotEmpty()) {
                    TextButton(onClick = onClearQueue) {
                        Text("Limpiar", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            if (playlist.isEmpty()) {
                Text(
                    text = "La cola de reproducción está vacía.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    items(playlist.size) { index ->
                        val item = playlist[index]
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSkipToIndex(index)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isCurrent) Icons.Default.PlayArrow else Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.artist.isNotEmpty()) {
                                    Text(
                                        text = item.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            IconButton(onClick = { onRemoveItem(index) }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Eliminar de la cola",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        if (index < playlist.size - 1) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun SleepTimerDialog(
    sleepTimerState: SleepTimerState,
    onSetTimerMinutes: (Int) -> Unit,
    onToggleStopAtEndOfSong: (Boolean) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val presetMinutes = listOf(5, 10, 15, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Temporizador de apagado")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (sleepTimerState.isActive) {
                    if (sleepTimerState.stopAtEndOfSong) {
                        Text(
                            text = "Se detendrá automáticamente al terminar la canción actual.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    } else {
                        val mins = sleepTimerState.remainingSeconds / 60
                        val secs = sleepTimerState.remainingSeconds % 60
                        val timeStr = String.format("%02d:%02d", mins, secs)
                        Text(
                            text = "Tiempo restante: $timeStr",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    Button(
                        onClick = {
                            onCancelTimer()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("cancel_sleep_timer_btn")
                    ) {
                        Text("Cancelar temporizador")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "Seleccionar duración:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column {
                    presetMinutes.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            row.forEach { mins ->
                                OutlinedButton(
                                    onClick = {
                                        onSetTimerMinutes(mins)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("${mins}m")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newStopAtEnd = !sleepTimerState.stopAtEndOfSong
                            onToggleStopAtEndOfSong(newStopAtEnd)
                            if (newStopAtEnd) onDismiss()
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Al terminar esta canción",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Pausa la reproducción al finalizar el audio",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = sleepTimerState.stopAtEndOfSong,
                        onCheckedChange = {
                            onToggleStopAtEndOfSong(it)
                            if (it) onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
fun EqualizerDialog(
    state: EqualizerState,
    onToggleEnabled: (Boolean) -> Unit,
    onBandGainChange: (bandIndex: Short, gainMb: Short) -> Unit,
    onSelectPreset: (presetIndex: Short) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ecualizador de audio")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!state.isAvailable) {
                    Text(
                        text = state.statusMessage.ifEmpty { "El ecualizador de audio por hardware no está disponible en este dispositivo." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.isEnabled) "Ecualizador activado" else "Ecualizador desactivado",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = state.isEnabled,
                            onCheckedChange = onToggleEnabled
                        )
                    }

                    if (state.presets.isNotEmpty()) {
                        Text("Perfiles de sonido:", style = MaterialTheme.typography.labelSmall)
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                            items(state.presets.size) { pIndex ->
                                val isSelected = state.currentPresetIndex == pIndex.toShort()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = state.isEnabled) {
                                            onSelectPreset(pIndex.toShort())
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelectPreset(pIndex.toShort()) },
                                        enabled = state.isEnabled
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = state.presets[pIndex],
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text("Bandas de frecuencia:", style = MaterialTheme.typography.labelSmall)
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        items(state.bands.size) { bIndex ->
                            val band = state.bands[bIndex]
                            val freqLabel = if (band.centerFreqHz >= 1000) "${band.centerFreqHz / 1000} kHz" else "${band.centerFreqHz} Hz"
                            val gainDb = band.currentGainMb / 100
                            val minDb = band.minGainMb / 100
                            val maxDb = band.maxGainMb / 100

                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(freqLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    Text("${if (gainDb > 0) "+$gainDb" else "$gainDb"} dB", style = MaterialTheme.typography.bodySmall)
                                }
                                Slider(
                                    value = band.currentGainMb.toFloat(),
                                    onValueChange = { newGain ->
                                        onBandGainChange(band.index, newGain.toInt().toShort())
                                    },
                                    valueRange = band.minGainMb.toFloat()..band.maxGainMb.toFloat(),
                                    enabled = state.isEnabled,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Listo") }
        }
    )
}

