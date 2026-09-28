package com.example.player.model

import android.content.pm.ActivityInfo
import com.example.core.model.MediaType

enum class SpeedApplicationScope(val displayName: String) {
    THIS_VIDEO("Este video"),
    ALL_VIDEOS("Todos los videos")
}

enum class SystemRotationMode {
    AUTO_ROTATE_ENABLED,
    AUTO_ROTATE_DISABLED
}

enum class PlayerOrientationPreference(val displayName: String, val shortLabel: String) {
    FOLLOW_SYSTEM("Automático (Seguir sistema)", "Auto"),
    PORTRAIT("Vertical (Portrait)", "Vertical"),
    LANDSCAPE("Horizontal (Landscape)", "Horizontal")
}

fun resolveRequestedOrientation(
    systemRotationMode: SystemRotationMode,
    playerPreference: PlayerOrientationPreference
): Int {
    return when (playerPreference) {
        PlayerOrientationPreference.PORTRAIT ->
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        PlayerOrientationPreference.LANDSCAPE ->
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        PlayerOrientationPreference.FOLLOW_SYSTEM -> {
            when (systemRotationMode) {
                SystemRotationMode.AUTO_ROTATE_ENABLED ->
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR
                SystemRotationMode.AUTO_ROTATE_DISABLED ->
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}

fun nextOrientationPreference(
    current: PlayerOrientationPreference,
    isCurrentlyLandscape: Boolean
): PlayerOrientationPreference {
    return when (current) {
        PlayerOrientationPreference.FOLLOW_SYSTEM -> {
            if (isCurrentlyLandscape) PlayerOrientationPreference.PORTRAIT
            else PlayerOrientationPreference.LANDSCAPE
        }
        PlayerOrientationPreference.LANDSCAPE -> PlayerOrientationPreference.PORTRAIT
        PlayerOrientationPreference.PORTRAIT -> PlayerOrientationPreference.FOLLOW_SYSTEM
    }
}

fun resolveEffectiveVideoSpeed(specificVideoSpeed: Float?, globalVideoSpeed: Float?): Float {
    return specificVideoSpeed ?: globalVideoSpeed ?: 1.0f
}

enum class AspectRatioMode(val displayName: String) {
    FIT("Ajustar"),
    FILL("Rellenar"),
    ZOOM("Zoom"),
    FIXED_16_9("16:9"),
    FIXED_4_3("4:3")
}

enum class RepeatMode(val displayName: String) {
    OFF("Desactivado"),
    ALL("Repetir todo"),
    ONE("Repetir una")
}

data class PlaylistItem(
    val uri: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val mediaType: MediaType = MediaType.VIDEO,
    val mimeType: String = ""
)

data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Long = 0L,
    val stopAtEndOfSong: Boolean = false,
    val initialMinutes: Int = 0
)

data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val currentGainMb: Short,
    val minGainMb: Short,
    val maxGainMb: Short
)

data class EqualizerState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val bands: List<EqualizerBand> = emptyList(),
    val presets: List<String> = emptyList(),
    val currentPresetIndex: Short = -1,
    val statusMessage: String = ""
)

data class PlayerTrackInfo(
    val id: String,
    val index: Int,
    val language: String,
    val label: String,
    val mimeType: String,
    val isSelected: Boolean
)

data class MediaInfoDetails(
    val title: String,
    val uri: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val fps: Float,
    val bitrate: Long,
    val audioTracksCount: Int,
    val subtitleTracksCount: Int,
    val sampleRate: Int = 0,
    val channelCount: Int = 0,
    val audioMimeType: String = "",
    val artist: String = "",
    val album: String = "",
    val genre: String = ""
)

enum class GestureType {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK
}

data class GestureState(
    val type: GestureType = GestureType.NONE,
    val valuePercent: Int = 0,
    val seekSeconds: Long = 0L
)

enum class DoubleTapAction {
    NONE,
    SEEK_BACK,
    SEEK_FORWARD,
    PLAY_PAUSE
}

data class VideoDimensions(
    val width: Int = 0,
    val height: Int = 0,
    val unappliedRotationDegrees: Int = 0
) {
    val effectiveWidth: Int get() = if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) height else width
    val effectiveHeight: Int get() = if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) width else height
    val isVertical: Boolean get() = effectiveHeight > effectiveWidth && effectiveWidth > 0
    val isHorizontal: Boolean get() = effectiveWidth >= effectiveHeight && effectiveHeight > 0
    val aspectRatio: Float get() = if (effectiveHeight > 0) effectiveWidth.toFloat() / effectiveHeight.toFloat() else 16f / 9f
}
