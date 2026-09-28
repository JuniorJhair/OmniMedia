package com.example.data.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.core.model.MediaType
import com.example.data.local.entity.MediaFileEntity
import com.example.ui.components.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaScanner {

    suspend fun getMediaStoreSummary(
        context: Context,
        mediaType: MediaType
    ): Map<Long, Long> = withContext(Dispatchers.IO) {
        if (!PermissionUtils.hasPermissionFor(context, mediaType)) {
            return@withContext emptyMap()
        }
        val collection = when (mediaType) {
            MediaType.VIDEO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            MediaType.AUDIO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            MediaType.IMAGE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val summary = mutableMapOf<Long, Long>()
        try {
            context.contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val modIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                if (idIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        val mod = if (modIdx >= 0) cursor.getLong(modIdx) * 1000L else 0L
                        summary[id] = mod
                    }
                }
            }
        } catch (_: Exception) {}
        summary
    }

    suspend fun scanVideos(context: Context, targetIds: Set<Long>? = null): List<MediaFileEntity> = withContext(Dispatchers.IO) {
        if (!PermissionUtils.hasPermissionFor(context, MediaType.VIDEO)) {
            return@withContext emptyList()
        }
        if (targetIds != null && targetIds.isEmpty()) {
            return@withContext emptyList()
        }
        val videos = mutableListOf<MediaFileEntity>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            } else {
                add(MediaStore.Video.Media.DATA)
            }
        }.toTypedArray()

        val idChunks = targetIds?.chunked(500) ?: listOf(null)
        for (chunk in idChunks) {
            val selection = chunk?.let { "${MediaStore.Video.Media._ID} IN (${it.joinToString(",")})" }
            queryMediaStore(context.contentResolver, collection, projection, selection) { cursor ->
                try {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val displayName = cursor.getStringOrEmpty(MediaStore.Video.Media.DISPLAY_NAME)
                    val title = cursor.getStringOrEmpty(MediaStore.Video.Media.TITLE).ifEmpty { displayName.substringBeforeLast(".") }
                    val duration = cursor.getLongOrZero(MediaStore.Video.Media.DURATION)
                    val size = cursor.getLongOrZero(MediaStore.Video.Media.SIZE)
                    val dateAdded = cursor.getLongOrZero(MediaStore.Video.Media.DATE_ADDED) * 1000L
                    val dateModified = cursor.getLongOrZero(MediaStore.Video.Media.DATE_MODIFIED) * 1000L
                    val mimeType = cursor.getStringOrEmpty(MediaStore.Video.Media.MIME_TYPE).ifEmpty { "video/*" }
                    val width = cursor.getIntOrZero(MediaStore.Video.Media.WIDTH)
                    val height = cursor.getIntOrZero(MediaStore.Video.Media.HEIGHT)

                    val (folderPath, folderName) = resolveFolder(cursor)

                    videos.add(
                        MediaFileEntity(
                            uri = contentUri.toString(),
                            title = title,
                            displayName = displayName.ifEmpty { title },
                            durationMs = duration,
                            sizeBytes = size,
                            dateAdded = if (dateAdded > 0) dateAdded else System.currentTimeMillis(),
                            dateModified = dateModified,
                            mimeType = mimeType,
                            mediaType = MediaType.VIDEO.name,
                            width = width,
                            height = height,
                            folderPath = folderPath,
                            folderName = folderName
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        videos
    }

    suspend fun scanAudio(context: Context, targetIds: Set<Long>? = null): List<MediaFileEntity> = withContext(Dispatchers.IO) {
        if (!PermissionUtils.hasPermissionFor(context, MediaType.AUDIO)) {
            return@withContext emptyList()
        }
        if (targetIds != null && targetIds.isEmpty()) {
            return@withContext emptyList()
        }
        val audios = mutableListOf<MediaFileEntity>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.MIME_TYPE
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                add(MediaStore.Audio.Media.DATA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.GENRE)
            }
        }.toTypedArray()

        val idChunks = targetIds?.chunked(500) ?: listOf(null)
        for (chunk in idChunks) {
            val selection = chunk?.let { "${MediaStore.Audio.Media._ID} IN (${it.joinToString(",")})" }
            queryMediaStore(context.contentResolver, collection, projection, selection) { cursor ->
                try {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                    val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val displayName = cursor.getStringOrEmpty(MediaStore.Audio.Media.DISPLAY_NAME)
                    val title = cursor.getStringOrEmpty(MediaStore.Audio.Media.TITLE).ifEmpty { displayName.substringBeforeLast(".") }
                    val artist = cursor.getStringOrEmpty(MediaStore.Audio.Media.ARTIST)
                    val album = cursor.getStringOrEmpty(MediaStore.Audio.Media.ALBUM)
                    val genre = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        cursor.getStringOrEmpty(MediaStore.Audio.Media.GENRE)
                    } else ""
                    val duration = cursor.getLongOrZero(MediaStore.Audio.Media.DURATION)
                    val size = cursor.getLongOrZero(MediaStore.Audio.Media.SIZE)
                    val dateAdded = cursor.getLongOrZero(MediaStore.Audio.Media.DATE_ADDED) * 1000L
                    val dateModified = cursor.getLongOrZero(MediaStore.Audio.Media.DATE_MODIFIED) * 1000L
                    val mimeType = cursor.getStringOrEmpty(MediaStore.Audio.Media.MIME_TYPE).ifEmpty { "audio/*" }

                    val (folderPath, folderName) = resolveFolder(cursor)

                    audios.add(
                        MediaFileEntity(
                            uri = contentUri.toString(),
                            title = title,
                            displayName = displayName.ifEmpty { title },
                            artist = if (artist == "<unknown>") "" else artist,
                            album = if (album == "<unknown>") "" else album,
                            genre = if (genre == "<unknown>") "" else genre,
                            durationMs = duration,
                            sizeBytes = size,
                            dateAdded = if (dateAdded > 0) dateAdded else System.currentTimeMillis(),
                            dateModified = dateModified,
                            mimeType = mimeType,
                            mediaType = MediaType.AUDIO.name,
                            folderPath = folderPath,
                            folderName = folderName
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        audios
    }

    suspend fun scanImages(context: Context, targetIds: Set<Long>? = null): List<MediaFileEntity> = withContext(Dispatchers.IO) {
        if (!PermissionUtils.hasPermissionFor(context, MediaType.IMAGE)) {
            return@withContext emptyList()
        }
        if (targetIds != null && targetIds.isEmpty()) {
            return@withContext emptyList()
        }
        val images = mutableListOf<MediaFileEntity>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.TITLE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                add(MediaStore.Images.Media.DATA)
            }
        }.toTypedArray()

        val idChunks = targetIds?.chunked(500) ?: listOf(null)
        for (chunk in idChunks) {
            val selection = chunk?.let { "${MediaStore.Images.Media._ID} IN (${it.joinToString(",")})" }
            queryMediaStore(context.contentResolver, collection, projection, selection) { cursor ->
                try {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val displayName = cursor.getStringOrEmpty(MediaStore.Images.Media.DISPLAY_NAME)
                    val title = cursor.getStringOrEmpty(MediaStore.Images.Media.TITLE).ifEmpty { displayName.substringBeforeLast(".") }
                    val size = cursor.getLongOrZero(MediaStore.Images.Media.SIZE)
                    val dateAdded = cursor.getLongOrZero(MediaStore.Images.Media.DATE_ADDED) * 1000L
                    val dateModified = cursor.getLongOrZero(MediaStore.Images.Media.DATE_MODIFIED) * 1000L
                    val mimeType = cursor.getStringOrEmpty(MediaStore.Images.Media.MIME_TYPE).ifEmpty { "image/*" }
                    val width = cursor.getIntOrZero(MediaStore.Images.Media.WIDTH)
                    val height = cursor.getIntOrZero(MediaStore.Images.Media.HEIGHT)

                    val (folderPath, folderName) = resolveFolder(cursor)

                    images.add(
                        MediaFileEntity(
                            uri = contentUri.toString(),
                            title = title,
                            displayName = displayName.ifEmpty { title },
                            sizeBytes = size,
                            dateAdded = if (dateAdded > 0) dateAdded else System.currentTimeMillis(),
                            dateModified = dateModified,
                            mimeType = mimeType,
                            mediaType = MediaType.IMAGE.name,
                            width = width,
                            height = height,
                            folderPath = folderPath,
                            folderName = folderName
                        )
                    )
                } catch (_: Exception) {}
            }
        }
        images
    }

    private inline fun queryMediaStore(
        resolver: ContentResolver,
        collection: Uri,
        projection: Array<String>,
        selection: String? = null,
        onEach: (Cursor) -> Unit
    ) {
        try {
            resolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    onEach(cursor)
                }
            }
        } catch (_: Exception) {}
    }

    private fun resolveFolder(cursor: Cursor): Pair<String, String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            if (relativePathIndex >= 0) {
                val relPath = cursor.getString(relativePathIndex) ?: ""
                val cleanPath = relPath.trimEnd('/')
                val folderName = cleanPath.substringAfterLast('/', cleanPath).ifEmpty { "Almacenamiento" }
                Pair(cleanPath, folderName)
            } else {
                Pair("", "Almacenamiento")
            }
        } else {
            val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
            if (dataIndex >= 0) {
                val fullPath = cursor.getString(dataIndex) ?: ""
                val parent = File(fullPath).parentFile
                val folderName = parent?.name ?: "Almacenamiento"
                Pair(parent?.absolutePath ?: "", folderName)
            } else {
                Pair("", "Almacenamiento")
            }
        }
    }

    private fun Cursor.getStringOrEmpty(column: String): String {
        val index = getColumnIndex(column)
        return if (index >= 0) getString(index) ?: "" else ""
    }

    private fun Cursor.getLongOrZero(column: String): Long {
        val index = getColumnIndex(column)
        return if (index >= 0) getLong(index) else 0L
    }

    private fun Cursor.getIntOrZero(column: String): Int {
        val index = getColumnIndex(column)
        return if (index >= 0) getInt(index) else 0
    }
}
