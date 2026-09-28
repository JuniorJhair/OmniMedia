package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_files",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["mediaType"]),
        Index(value = ["folderPath"]),
        Index(value = ["isFavorite"]),
        Index(value = ["inTrash"]),
        Index(value = ["mediaType", "inTrash", "dateAdded"]),
        Index(value = ["isFavorite", "inTrash", "dateAdded"]),
        Index(value = ["inTrash", "lastPlayedTimestamp"])
    ]
)
data class MediaFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val displayName: String,
    val artist: String = "",
    val album: String = "",
    val genre: String = "",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val mimeType: String = "",
    val mediaType: String, // VIDEO, AUDIO, IMAGE
    val width: Int = 0,
    val height: Int = 0,
    val folderPath: String = "",
    val folderName: String = "",
    val isFavorite: Boolean = false,
    val inTrash: Boolean = false,
    val trashTimestamp: Long = 0L,
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L
)

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey val mediaUri: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "video_speeds")
data class VideoSpeedEntity(
    @PrimaryKey val mediaUri: String,
    val speed: Float,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isSmart: Boolean = false
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "mediaUri"],
    indices = [Index(value = ["playlistId"]), Index(value = ["mediaUri"])]
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val mediaUri: String,
    val orderIndex: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playback_history",
    indices = [Index(value = ["mediaUri"])]
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaUri: String,
    val title: String,
    val mediaType: String,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "vault_items",
    indices = [
        Index(value = ["vaultFileName"], unique = true),
        Index(value = ["originalUri"])
    ]
)
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalUri: String,
    val originalName: String,
    val vaultFileName: String,
    val mediaType: String, // VIDEO, AUDIO, IMAGE
    val sizeBytes: Long,
    val mimeType: String,
    val originalFolderPath: String,
    val addedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val artist: String = "",
    val album: String = "",
    val encryptedFilePath: String = "",
    val encryptionIv: String = "",
    val status: String = "COMPLETED"
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = "SYSTEM",
    val viewMode: String = "GRID",
    val sortOption: String = "DATE_DESC",
    val defaultPlaybackSpeed: Float = 1.0f,
    val skipForwardSec: Int = 10,
    val skipBackwardSec: Int = 10,
    val autoResume: Boolean = true,
    val gesturesEnabled: Boolean = true,
    val gestureBrightness: Boolean = true,
    val gestureVolume: Boolean = true,
    val gestureSeek: Boolean = true,
    val gestureDoubleTap: Boolean = true,
    val vaultPinHash: String = "",
    val vaultSalt: String = "",
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val trashEnabled: Boolean = true,
    val pipEnabled: Boolean = true,
    val backgroundAudioEnabled: Boolean = true
)
