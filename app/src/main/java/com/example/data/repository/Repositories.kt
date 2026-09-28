package com.example.data.repository

import com.example.core.model.MediaType
import com.example.data.local.dao.HistoryDao
import com.example.data.local.dao.MediaDao
import com.example.data.local.dao.PlaybackProgressDao
import com.example.data.local.dao.PlaylistDao
import com.example.data.local.dao.SettingsDao
import com.example.data.local.dao.VaultDao
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.MediaFileEntity
import com.example.data.local.entity.PlaybackProgressEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistItemEntity
import com.example.data.local.entity.UserSettingsEntity
import com.example.data.local.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MediaRepository(private val mediaDao: MediaDao) {
    val allMedia: Flow<List<MediaFileEntity>> = mediaDao.getAllMedia()
    val favorites: Flow<List<MediaFileEntity>> = mediaDao.getFavorites()
    val recentlyPlayed: Flow<List<MediaFileEntity>> = mediaDao.getRecentlyPlayed(20)
    val recentlyAdded: Flow<List<MediaFileEntity>> = mediaDao.getRecentlyAdded(20)

    fun getMediaByType(type: MediaType): Flow<List<MediaFileEntity>> =
        mediaDao.getMediaByType(type.name)

    fun getFoldersByType(type: MediaType): Flow<List<String>> =
        mediaDao.getFoldersByType(type.name)

    fun getMediaByFolder(folderName: String): Flow<List<MediaFileEntity>> =
        mediaDao.getMediaByFolder(folderName)

    fun search(query: String): Flow<List<MediaFileEntity>> =
        mediaDao.searchMedia(query)

    suspend fun getByUri(uri: String): MediaFileEntity? =
        mediaDao.getByUri(uri)

    suspend fun insertAll(items: List<MediaFileEntity>) =
        mediaDao.insertAll(items)

    suspend fun insert(item: MediaFileEntity): Long =
        mediaDao.insert(item)

    suspend fun setFavorite(uri: String, isFavorite: Boolean) =
        mediaDao.setFavorite(uri, isFavorite)

    suspend fun setFavorites(uris: List<String>, isFavorite: Boolean) =
        mediaDao.setFavorites(uris, isFavorite)

    suspend fun moveToTrash(uri: String) =
        mediaDao.setTrash(uri, inTrash = true, timestamp = System.currentTimeMillis())

    suspend fun restoreFromTrash(uri: String) =
        mediaDao.setTrash(uri, inTrash = false, timestamp = 0L)

    suspend fun deletePermanently(uri: String) =
        mediaDao.deleteByUri(uri)

    suspend fun deleteMultiplePermanently(uris: List<String>) =
        mediaDao.deleteByUris(uris)

    suspend fun getMediaListByType(type: MediaType): List<MediaFileEntity> =
        mediaDao.getMediaListByType(type.name)

    suspend fun syncMediaStore(scannedItems: List<MediaFileEntity>, type: MediaType) =
        mediaDao.syncMediaStore(scannedItems, type.name)

    suspend fun syncIncremental(newOrModifiedItems: List<MediaFileEntity>, deletedUris: List<String>, type: MediaType) =
        mediaDao.syncIncremental(newOrModifiedItems, deletedUris, type.name)

    suspend fun recordPlay(uri: String) =
        mediaDao.recordPlay(uri, System.currentTimeMillis())

    fun getCountByType(type: MediaType): Flow<Int> =
        mediaDao.getCountByType(type.name)

    fun getTotalSizeBytes(): Flow<Long> =
        mediaDao.getTotalSizeBytes().map { it ?: 0L }
}

class PlaybackProgressRepository(private val playbackProgressDao: PlaybackProgressDao) {
    val unfinished: Flow<List<PlaybackProgressEntity>> = playbackProgressDao.getUnfinished(15)

    suspend fun getProgress(mediaUri: String): PlaybackProgressEntity? =
        playbackProgressDao.getProgress(mediaUri)

    fun getProgressFlow(mediaUri: String): Flow<PlaybackProgressEntity?> =
        playbackProgressDao.getProgressFlow(mediaUri)

    suspend fun saveProgress(mediaUri: String, positionMs: Long, durationMs: Long) {
        val completed = durationMs > 0 && (positionMs.toFloat() / durationMs.toFloat()) >= 0.95f
        playbackProgressDao.saveProgress(
            PlaybackProgressEntity(
                mediaUri = mediaUri,
                positionMs = positionMs,
                durationMs = durationMs,
                completed = completed,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteProgress(mediaUri: String) =
        playbackProgressDao.deleteProgress(mediaUri)

    suspend fun clearAll() =
        playbackProgressDao.clearAll()
}

class PlaylistRepository(private val playlistDao: PlaylistDao) {
    val allPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
    val allPlaylistItems: Flow<List<PlaylistItemEntity>> = playlistDao.getAllPlaylistItems()

    suspend fun createPlaylist(name: String, description: String = ""): Long =
        playlistDao.insertPlaylist(PlaylistEntity(name = name, description = description))

    suspend fun updatePlaylist(playlist: PlaylistEntity) =
        playlistDao.updatePlaylist(playlist)

    suspend fun deletePlaylist(id: Long) {
        playlistDao.clearPlaylistItems(id)
        playlistDao.deletePlaylist(id)
    }

    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        playlistDao.getItemsForPlaylist(playlistId)

    suspend fun addItemToPlaylist(playlistId: Long, mediaUri: String, orderIndex: Int) =
        playlistDao.insertPlaylistItem(
            PlaylistItemEntity(playlistId = playlistId, mediaUri = mediaUri, orderIndex = orderIndex)
        )

    suspend fun removeItemFromPlaylist(playlistId: Long, mediaUri: String) =
        playlistDao.removeItemFromPlaylist(playlistId, mediaUri)

    fun getItemCount(playlistId: Long): Flow<Int> =
        playlistDao.getItemCount(playlistId)

    fun getMediaForPlaylist(playlistId: Long): Flow<List<MediaFileEntity>> =
        playlistDao.getMediaForPlaylist(playlistId)

    suspend fun renamePlaylist(id: Long, newName: String, description: String = "") =
        playlistDao.renamePlaylist(id, newName, description)

    suspend fun reorderPlaylistItem(playlistId: Long, mediaUri: String, newIndex: Int) =
        playlistDao.updateItemOrder(playlistId, mediaUri, newIndex)

    suspend fun removeMediaFromAllPlaylists(mediaUri: String) =
        playlistDao.removeMediaFromAllPlaylists(mediaUri)
}

class HistoryRepository(private val historyDao: HistoryDao) {
    val history: Flow<List<HistoryEntity>> = historyDao.getHistory(50)

    suspend fun recordHistory(mediaUri: String, title: String, mediaType: MediaType, durationMs: Long, positionMs: Long) =
        historyDao.insert(
            HistoryEntity(
                mediaUri = mediaUri,
                title = title,
                mediaType = mediaType.name,
                durationMs = durationMs,
                positionMs = positionMs,
                playedAt = System.currentTimeMillis()
            )
        )

    suspend fun deleteHistoryItem(id: Long) =
        historyDao.deleteById(id)

    suspend fun deleteHistoryByMediaUri(mediaUri: String) =
        historyDao.deleteByMediaUri(mediaUri)

    suspend fun clearHistory() =
        historyDao.clearHistory()
}

class VaultRepository(private val vaultDao: VaultDao) {
    val allVaultItems: Flow<List<VaultItemEntity>> = vaultDao.getAllVaultItems()
    val vaultCount: Flow<Int> = vaultDao.getVaultCount()
    val vaultTotalSizeBytes: Flow<Long> = vaultDao.getVaultTotalSizeBytes().map { it ?: 0L }

    fun getVaultItemsByType(type: MediaType): Flow<List<VaultItemEntity>> =
        vaultDao.getVaultItemsByType(type.name)

    suspend fun getById(id: Long): VaultItemEntity? =
        vaultDao.getById(id)

    suspend fun getByVaultFileName(vaultFileName: String): VaultItemEntity? =
        vaultDao.getByVaultFileName(vaultFileName)

    suspend fun insertVaultItem(item: VaultItemEntity): Long =
        vaultDao.insert(item)

    suspend fun updateVaultItem(item: VaultItemEntity) =
        vaultDao.update(item)

    suspend fun updateStatusByOriginalUris(originalUris: List<String>, status: String) =
        vaultDao.updateStatusByOriginalUris(originalUris, status)

    suspend fun deleteVaultItem(id: Long) =
        vaultDao.deleteById(id)

    suspend fun deleteVaultItems(ids: List<Long>) =
        vaultDao.deleteByIds(ids)
}

class SettingsRepository(private val settingsDao: SettingsDao) {
    val settingsFlow: Flow<UserSettingsEntity> = settingsDao.getSettingsFlow().map {
        it ?: UserSettingsEntity()
    }

    suspend fun getSettings(): UserSettingsEntity {
        return settingsDao.getSettings() ?: UserSettingsEntity().also {
            settingsDao.saveSettings(it)
        }
    }

    suspend fun updateSettings(transform: (UserSettingsEntity) -> UserSettingsEntity) {
        val current = getSettings()
        val updated = transform(current)
        settingsDao.saveSettings(updated)
    }
}
