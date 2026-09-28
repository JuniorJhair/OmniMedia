package com.example.player.gestures

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.model.GestureState
import com.example.player.model.GestureType

@Composable
fun GestureOverlay(
    gestureState: GestureState,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = gestureState.type != GestureType.NONE,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.75f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp).testTag("gesture_overlay")
        ) {
            when (gestureState.type) {
                GestureType.BRIGHTNESS -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Icon(
                            imageVector = if (gestureState.valuePercent > 50) Icons.Default.BrightnessMedium else Icons.Default.BrightnessLow,
                            contentDescription = "Brillo",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { gestureState.valuePercent / 100f },
                            modifier = Modifier.width(100.dp).height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${gestureState.valuePercent}%",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                GestureType.VOLUME -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Icon(
                            imageVector = if (gestureState.valuePercent == 0) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Volumen",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { gestureState.valuePercent / 100f },
                            modifier = Modifier.width(100.dp).height(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${gestureState.valuePercent}%",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                GestureType.SEEK -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        val isForward = gestureState.seekSeconds >= 0
                        Icon(
                            imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isForward) "+${gestureState.seekSeconds}s" else "${gestureState.seekSeconds}s",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                GestureType.NONE -> {}
            }
        }
    }
}
