package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.MediaFileEntity
import com.example.data.local.entity.PlaybackProgressEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistItemEntity
import com.example.data.local.entity.UserSettingsEntity
import com.example.data.local.entity.VaultItemEntity
import com.example.data.local.entity.VideoSpeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_files WHERE inTrash = 0 ORDER BY dateAdded DESC")
    fun getAllMedia(): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE mediaType = :mediaType AND inTrash = 0 ORDER BY dateAdded DESC")
    fun getMediaByType(mediaType: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE isFavorite = 1 AND inTrash = 0 ORDER BY dateAdded DESC")
    fun getFavorites(): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE inTrash = 0 ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    fun getRecentlyPlayed(limit: Int = 20): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE inTrash = 0 ORDER BY dateAdded DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int = 20): Flow<List<MediaFileEntity>>

    @Query("SELECT DISTINCT folderName FROM media_files WHERE mediaType = :mediaType AND inTrash = 0 ORDER BY folderName ASC")
    fun getFoldersByType(mediaType: String): Flow<List<String>>

    @Query("SELECT * FROM media_files WHERE folderName = :folderName AND inTrash = 0 ORDER BY title ASC")
    fun getMediaByFolder(folderName: String): Flow<List<MediaFileEntity>>

    @Query("SELECT * FROM media_files WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE inTrash = 0 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' OR folderName LIKE '%' || :query || '%')")
    fun searchMedia(query: String): Flow<List<MediaFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaFileEntity): Long

    @Update
    suspend fun update(item: MediaFileEntity)

    @Query("UPDATE media_files SET isFavorite = :isFavorite WHERE uri = :uri")
    suspend fun setFavorite(uri: String, isFavorite: Boolean)

    @Query("UPDATE media_files SET inTrash = :inTrash, trashTimestamp = :timestamp WHERE uri = :uri")
    suspend fun setTrash(uri: String, inTrash: Boolean, timestamp: Long)

    @Query("UPDATE media_files SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE uri = :uri")
    suspend fun recordPlay(uri: String, timestamp: Long)

    @Query("DELETE FROM media_files WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM media_files WHERE uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    @Query("UPDATE media_files SET isFavorite = :isFavorite WHERE uri IN (:uris)")
    suspend fun setFavorites(uris: List<String>, isFavorite: Boolean)

    @Query("SELECT * FROM media_files WHERE mediaType = :mediaType AND inTrash = 0")
    suspend fun getMediaListByType(mediaType: String): List<MediaFileEntity>

    @Query("SELECT originalUri FROM vault_items")
    suspend fun getVaultedOriginalUris(): List<String>

    @Transaction
    suspend fun syncMediaStore(scannedItems: List<MediaFileEntity>, mediaType: String) {
        val vaultedUris = getVaultedOriginalUris().toSet()
        val filteredScanned = scannedItems.filter { it.uri !in vaultedUris }

        val existing = getMediaListByType(mediaType)
        val existingMap = existing.associateBy { it.uri }
        val scannedUris = filteredScanned.map { it.uri }.toSet()

        val vaultedStillInPublic = existing.filter { it.uri in vaultedUris }.map { it.uri }
        if (vaultedStillInPublic.isNotEmpty()) {
            deleteByUris(vaultedStillInPublic)
        }

        val toDeleteUris = existing
            .filter { it.uri.startsWith("content://media/") && it.uri !in scannedUris && !it.inTrash }
            .map { it.uri }
        if (toDeleteUris.isNotEmpty()) {
            deleteByUris(toDeleteUris)
        }

        val itemsToSave = filteredScanned.map { scanned ->
            val prev = existingMap[scanned.uri]
            if (prev != null) {
                scanned.copy(
                    id = prev.id,
                    isFavorite = prev.isFavorite,
                    playCount = prev.playCount,
                    lastPlayedTimestamp = prev.lastPlayedTimestamp,
                    inTrash = prev.inTrash,
                    trashTimestamp = prev.trashTimestamp
                )
            } else {
                scanned
            }
        }
        if (itemsToSave.isNotEmpty()) {
            insertAll(itemsToSave)
        }
    }

    @Transaction
    suspend fun syncIncremental(
        newOrModifiedItems: List<MediaFileEntity>,
        deletedUris: List<String>,
        mediaType: String
    ) {
        val vaultedUris = getVaultedOriginalUris().toSet()

        if (deletedUris.isNotEmpty()) {
            deleteByUris(deletedUris)
        }

        val existing = getMediaListByType(mediaType)
        val vaultedStillInPublic = existing.filter { it.uri in vaultedUris }.map { it.uri }
        if (vaultedStillInPublic.isNotEmpty()) {
            deleteByUris(vaultedStillInPublic)
        }

        val existingMap = existing.associateBy { it.uri }
        val filteredNewOrModified = newOrModifiedItems.filter { it.uri !in vaultedUris }

        val itemsToSave = filteredNewOrModified.map { item ->
            val prev = existingMap[item.uri]
            if (prev != null) {
                item.copy(
                    id = prev.id,
                    isFavorite = prev.isFavorite,
                    playCount = prev.playCount,
                    lastPlayedTimestamp = prev.lastPlayedTimestamp,
                    inTrash = prev.inTrash,
                    trashTimestamp = prev.trashTimestamp
                )
            } else {
                item
            }
        }

        if (itemsToSave.isNotEmpty()) {
            insertAll(itemsToSave)
        }
    }

    @Query("SELECT COUNT(*) FROM media_files WHERE mediaType = :mediaType AND inTrash = 0")
    fun getCountByType(mediaType: String): Flow<Int>

    @Query("SELECT SUM(sizeBytes) FROM media_files WHERE inTrash = 0")
    fun getTotalSizeBytes(): Flow<Long?>
}

@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE mediaUri = :mediaUri LIMIT 1")
    suspend fun getProgress(mediaUri: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE mediaUri = :mediaUri LIMIT 1")
    fun getProgressFlow(mediaUri: String): Flow<PlaybackProgressEntity?>

    @Query("SELECT * FROM playback_progress WHERE completed = 0 AND positionMs > 5000 ORDER BY lastUpdated DESC LIMIT :limit")
    fun getUnfinished(limit: Int = 15): Flow<List<PlaybackProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE mediaUri = :mediaUri")
    suspend fun deleteProgress(mediaUri: String)

    @Query("DELETE FROM playback_progress")
    suspend fun clearAll()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getItemsForPlaylist(playlistId: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items ORDER BY playlistId ASC, orderIndex ASC")
    fun getAllPlaylistItems(): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaUri = :mediaUri")
    suspend fun removeItemFromPlaylist(playlistId: Long, mediaUri: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    fun getItemCount(playlistId: Long): Flow<Int>

    @Query("""
        SELECT m.* FROM media_files m
        INNER JOIN playlist_items p ON m.uri = p.mediaUri
        WHERE p.playlistId = :playlistId AND m.inTrash = 0
        ORDER BY p.orderIndex ASC
    """)
    fun getMediaForPlaylist(playlistId: Long): Flow<List<MediaFileEntity>>

    @Query("UPDATE playlists SET name = :name, description = :description WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String, description: String = "")

    @Query("UPDATE playlist_items SET orderIndex = :newIndex WHERE playlistId = :playlistId AND mediaUri = :mediaUri")
    suspend fun updateItemOrder(playlistId: Long, mediaUri: String, newIndex: Int)

    @Query("DELETE FROM playlist_items WHERE mediaUri = :mediaUri")
    suspend fun removeMediaFromAllPlaylists(mediaUri: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun getHistory(limit: Int = 50): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity): Long

    @Query("DELETE FROM playback_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM playback_history WHERE mediaUri = :mediaUri")
    suspend fun deleteByMediaUri(mediaUri: String)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY addedAt DESC")
    fun getAllVaultItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE mediaType = :mediaType ORDER BY addedAt DESC")
    fun getVaultItemsByType(mediaType: String): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE vaultFileName = :vaultFileName LIMIT 1")
    suspend fun getByVaultFileName(vaultFileName: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultItemEntity): Long

    @Update
    suspend fun update(item: VaultItemEntity)

    @Query("UPDATE vault_items SET status = :status WHERE originalUri IN (:originalUris)")
    suspend fun updateStatusByOriginalUris(originalUris: List<String>, status: String)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM vault_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM vault_items")
    fun getVaultCount(): Flow<Int>

    @Query("SELECT SUM(sizeBytes) FROM vault_items")
    fun getVaultTotalSizeBytes(): Flow<Long?>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): UserSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: UserSettingsEntity)
}

@Dao
interface VideoSpeedDao {
    @Query("SELECT speed FROM video_speeds WHERE mediaUri = :mediaUri LIMIT 1")
    suspend fun getSpeedForVideo(mediaUri: String): Float?

    @Query("SELECT * FROM video_speeds")
    fun getAllVideoSpeedsFlow(): Flow<List<VideoSpeedEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVideoSpeed(entity: VideoSpeedEntity)

    @Query("DELETE FROM video_speeds WHERE mediaUri = :mediaUri")
    suspend fun clearVideoSpeed(mediaUri: String)

    @Query("DELETE FROM video_speeds")
    suspend fun clearAll()
}
