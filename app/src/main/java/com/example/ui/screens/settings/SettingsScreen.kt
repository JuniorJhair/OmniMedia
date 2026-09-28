package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.ThemeMode
import com.example.data.local.entity.UserSettingsEntity

@Composable
fun SettingsScreen(
    settings: UserSettingsEntity,
    onUpdateSettings: ((UserSettingsEntity) -> UserSettingsEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Appearance
        item {
            SettingsSectionCard(
                title = "Apariencia",
                icon = Icons.Default.BrightnessMedium
            ) {
                Text(
                    text = "Tema de la aplicación",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ThemeMode.values().forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.SYSTEM -> "Sistema"
                            ThemeMode.LIGHT -> "Claro"
                            ThemeMode.DARK -> "Oscuro"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = settings.themeMode == mode.name,
                                onClick = { onUpdateSettings { it.copy(themeMode = mode.name) } }
                            )
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Playback
        item {
            SettingsSectionCard(
                title = "Reproducción",
                icon = Icons.Default.PlayArrow
            ) {
                SettingsSwitchRow(
                    title = "Continuar automáticamente",
                    subtitle = "Reanudar videos y audios desde la última posición guardada.",
                    checked = settings.autoResume,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(autoResume = checked) } }
                )
                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                SettingsSwitchRow(
                    title = "Picture-in-Picture (PiP)",
                    subtitle = "Continuar viendo videos en miniatura al salir de la app.",
                    checked = settings.pipEnabled,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(pipEnabled = checked) } }
                )
                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                SettingsSwitchRow(
                    title = "Reproducción en segundo plano",
                    subtitle = "Permitir escuchar audios y podcasts con la pantalla apagada.",
                    checked = settings.backgroundAudioEnabled,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(backgroundAudioEnabled = checked) } }
                )
            }
        }

        // Gestures
        item {
            SettingsSectionCard(
                title = "Gestos del reproductor",
                icon = Icons.Default.TouchApp
            ) {
                SettingsSwitchRow(
                    title = "Activar gestos en pantalla",
                    subtitle = "Habilitar control por deslizamiento para volumen, brillo y posición.",
                    checked = settings.gesturesEnabled,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(gesturesEnabled = checked) } }
                )
                if (settings.gesturesEnabled) {
                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                    SettingsSwitchRow(
                        title = "Control de brillo (lado izquierdo)",
                        subtitle = "Deslizar verticalmente para regular el brillo de la pantalla.",
                        checked = settings.gestureBrightness,
                        onCheckedChange = { checked -> onUpdateSettings { it.copy(gestureBrightness = checked) } }
                    )
                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                    SettingsSwitchRow(
                        title = "Control de volumen (lado derecho)",
                        subtitle = "Deslizar verticalmente para regular el volumen de audio.",
                        checked = settings.gestureVolume,
                        onCheckedChange = { checked -> onUpdateSettings { it.copy(gestureVolume = checked) } }
                    )
                    HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                    SettingsSwitchRow(
                        title = "Doble toque para adelantar/retroceder",
                        subtitle = "Tocar dos veces en los lados para saltar 10 segundos.",
                        checked = settings.gestureDoubleTap,
                        onCheckedChange = { checked -> onUpdateSettings { it.copy(gestureDoubleTap = checked) } }
                    )
                }
            }
        }

        // Privacy & Vault
        item {
            SettingsSectionCard(
                title = "Privacidad y Bóveda",
                icon = Icons.Default.Lock
            ) {
                SettingsSwitchRow(
                    title = "Papelera de reciclaje",
                    subtitle = "Permite recuperar archivos eliminados antes del borrado definitivo.",
                    checked = settings.trashEnabled,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(trashEnabled = checked) } }
                )
                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.5f))
                SettingsSwitchRow(
                    title = "Autenticación biométrica",
                    subtitle = "Usar huella o rostro para desbloquear la bóveda si está disponible.",
                    checked = settings.biometricEnabled,
                    onCheckedChange = { checked -> onUpdateSettings { it.copy(biometricEnabled = checked) } }
                )
            }
        }

        // About
        item {
            SettingsSectionCard(
                title = "Acerca de OmniMedia",
                icon = Icons.Default.Info
            ) {
                Text(
                    text = "OmniMedia Player v1.0",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Centro multimedia todo-en-uno para Android desarrollado con Jetpack Compose, Material 3, Room Database y Media3/ExoPlayer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
