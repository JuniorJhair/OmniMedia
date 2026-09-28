package com.example.data.vault

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.result.IntentSenderRequest
import com.example.core.model.MediaType
import com.example.data.local.entity.MediaFileEntity
import com.example.data.local.entity.VaultItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

sealed class OriginalDeletionOutcome {
    data object DeletedDirectly : OriginalDeletionOutcome()
    data class RequiresSystemConsent(
        val intentSenderRequest: IntentSenderRequest,
        val pendingUris: List<String>
    ) : OriginalDeletionOutcome()
    data class Failed(val reason: String) : OriginalDeletionOutcome()
}

data class VaultImportOutcome(
    val vaultEntity: VaultItemEntity,
    val encryptedFile: File,
    val originalDeletionOutcome: OriginalDeletionOutcome
)

data class VaultRestoreOutcome(
    val restoredMediaEntity: MediaFileEntity,
    val restoredUri: String
)

/**
 * Manages physical private storage in [Context.getFilesDir]/vault,
 * transactional encryption/import, original MediaStore file deletion,
 * and decrypted restoration to public storage.
 */
object VaultStorageManager {

    private const val VAULT_DIR_NAME = "vault"
    const val VAULT_FILE_EXTENSION = ".vlt"

    fun getVaultDirectory(context: Context): File {
        val dir = File(context.applicationContext.filesDir, VAULT_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val noMedia = File(dir, ".nomedia")
        if (!noMedia.exists()) {
            runCatching { noMedia.createNewFile() }
        }
        return dir
    }

    fun getVaultFile(context: Context, vaultFileName: String): File {
        return File(getVaultDirectory(context), vaultFileName)
    }

    fun getPlaybackUriForVaultItem(context: Context, item: VaultItemEntity): String {
        val file = if (item.encryptedFilePath.isNotEmpty()) {
            File(item.encryptedFilePath)
        } else {
            getVaultFile(context, item.vaultFileName)
        }
        return Uri.fromFile(file).toString()
    }

    fun isVaultUri(uriString: String?): Boolean {
        if (uriString.isNullOrEmpty()) return false
        return uriString.startsWith("vault://") ||
            uriString.contains("/$VAULT_DIR_NAME/") ||
            uriString.endsWith(VAULT_FILE_EXTENSION)
    }

    fun resolveVaultFileFromUri(context: Context, uriString: String): File? {
        if (!isVaultUri(uriString)) return null
        return try {
            val parsed = Uri.parse(uriString)
            if (parsed.scheme == "file" && !parsed.path.isNullOrEmpty()) {
                File(parsed.path!!)
            } else {
                val fileName = uriString.substringAfterLast("/")
                getVaultFile(context, fileName)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Step 1 & 2 of transactional move-to-vault:
     * Streams the original media file into an AES-256-GCM encrypted `.vlt` file inside `filesDir/vault`,
     * verifies cryptographic integrity, and attempts direct deletion of the original file.
     */
    suspend fun encryptMediaToVault(
        context: Context,
        media: MediaFileEntity,
        attemptDirectOriginalDelete: Boolean = true
    ): Result<VaultImportOutcome> = withContext(Dispatchers.IO) {
        val vaultDir = getVaultDirectory(context)
        val uuid = UUID.randomUUID().toString().replace("-", "")
        val vaultFileName = "vault_${System.currentTimeMillis()}_${uuid}$VAULT_FILE_EXTENSION"
        val tempFile = File(vaultDir, "$vaultFileName.tmp")
        val finalFile = File(vaultDir, vaultFileName)

        try {
            val sourceStream = openSourceInputStream(context, media.uri)
                ?: return@withContext Result.failure(IllegalStateException("No se pudo abrir el archivo original: ${media.uri}"))

            val encryptionResult = sourceStream.use { input ->
                VaultCryptoManager.encryptStream(context, input, tempFile)
            }

            if (!tempFile.exists() || !VaultCryptoManager.verifyEncryptedFile(context, tempFile)) {
                tempFile.delete()
                return@withContext Result.failure(IllegalStateException("Falló la verificación de integridad del archivo cifrado."))
            }

            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            val deletionOutcome = if (attemptDirectOriginalDelete) {
                tryDeleteOriginalFile(context, listOf(media.uri))
            } else {
                OriginalDeletionOutcome.Failed("Deferred")
            }

            val status = when (deletionOutcome) {
                is OriginalDeletionOutcome.DeletedDirectly -> "COMPLETED"
                is OriginalDeletionOutcome.RequiresSystemConsent -> "PENDING_ORIGINAL_DELETE"
                is OriginalDeletionOutcome.Failed -> "COMPLETED"
            }

            val actualPlaintextSize = if (encryptionResult.plaintextSizeBytes > 0L) {
                encryptionResult.plaintextSizeBytes
            } else {
                media.sizeBytes
            }

            val vaultEntity = VaultItemEntity(
                originalUri = media.uri,
                originalName = media.displayName.ifEmpty { media.title }.ifEmpty { "Archivo_privado" },
                vaultFileName = vaultFileName,
                mediaType = media.mediaType,
                sizeBytes = actualPlaintextSize,
                mimeType = media.mimeType.ifEmpty { defaultMimeTypeFor(media.mediaType) },
                originalFolderPath = media.folderPath,
                addedAt = System.currentTimeMillis(),
                durationMs = media.durationMs,
                width = media.width,
                height = media.height,
                artist = media.artist,
                album = media.album,
                encryptedFilePath = finalFile.absolutePath,
                encryptionIv = encryptionResult.baseIvHex,
                status = status
            )

            Result.success(
                VaultImportOutcome(
                    vaultEntity = vaultEntity,
                    encryptedFile = finalFile,
                    originalDeletionOutcome = deletionOutcome
                )
            )
        } catch (e: Exception) {
            tempFile.delete()
            finalFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Imports an external Uri (e.g. from SAF document picker) directly into the encrypted Vault.
     */
    suspend fun importExternalUriDirectlyToVault(
        context: Context,
        uri: Uri,
        fallbackMediaType: MediaType? = null
    ): Result<VaultImportOutcome> = withContext(Dispatchers.IO) {
        var displayName = "Vault_${System.currentTimeMillis()}"
        var fileSize = 0L

        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                }
            }
        }

        val mimeType = context.contentResolver.getType(uri) ?: guessMimeTypeFromName(displayName, fallbackMediaType)
        val resolvedType = when {
            mimeType.startsWith("video") -> MediaType.VIDEO
            mimeType.startsWith("audio") -> MediaType.AUDIO
            mimeType.startsWith("image") -> MediaType.IMAGE
            else -> fallbackMediaType ?: MediaType.VIDEO
        }

        var durationMs = 0L
        var width = 0
        var height = 0
        var artist = ""
        var album = ""

        if (resolvedType != MediaType.IMAGE) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                retriever.release()
            }
        }

        val tempMedia = MediaFileEntity(
            uri = uri.toString(),
            title = displayName.substringBeforeLast("."),
            displayName = displayName,
            artist = artist,
            album = album,
            durationMs = durationMs,
            sizeBytes = fileSize,
            mimeType = mimeType,
            mediaType = resolvedType.name,
            width = width,
            height = height,
            folderPath = defaultRelativePathFor(resolvedType.name),
            folderName = "Importados"
        )

        encryptMediaToVault(context, tempMedia, attemptDirectOriginalDelete = true)
    }

    /**
     * Attempts to delete original public files from MediaStore or filesystem.
     * On Android 11+ (API 30+), if ContentResolver.delete throws SecurityException or returns 0
     * for a MediaStore content:// URI, creates a system delete request via MediaStore.createDeleteRequest.
     */
    fun tryDeleteOriginalFile(context: Context, uris: List<String>): OriginalDeletionOutcome {
        val requiresConsentUris = mutableListOf<Uri>()
        val requiresConsentStrings = mutableListOf<String>()

        for (uriStr in uris) {
            try {
                val uri = Uri.parse(uriStr)
                when (uri.scheme) {
                    "file" -> {
                        val f = File(uri.path ?: "")
                        if (f.exists()) {
                            f.delete()
                        }
                    }
                    "content" -> {
                        val deletedRows = try {
                            context.contentResolver.delete(uri, null, null)
                        } catch (_: SecurityException) {
                            0
                        } catch (_: Exception) {
                            0
                        }
                        if (deletedRows <= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (uri.authority == MediaStore.AUTHORITY || uriStr.startsWith("content://media/")) {
                                requiresConsentUris.add(uri)
                                requiresConsentStrings.add(uriStr)
                            }
                        }
                    }
                    else -> {
                        val f = File(uriStr)
                        if (f.exists()) f.delete()
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (requiresConsentUris.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return try {
                val pendingIntent: PendingIntent = MediaStore.createDeleteRequest(
                    context.contentResolver,
                    requiresConsentUris
                )
                OriginalDeletionOutcome.RequiresSystemConsent(
                    intentSenderRequest = IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                    pendingUris = requiresConsentStrings
                )
            } catch (e: Exception) {
                OriginalDeletionOutcome.Failed(e.message ?: "Cannot create delete request")
            }
        }

        return OriginalDeletionOutcome.DeletedDirectly
    }

    /**
     * Transactionally decrypts a Vault item back into public storage (MediaStore on Android 10+,
     * or restored public file fallback) and deletes the encrypted `.vlt` file ONLY after the
     * decrypted destination file is completely written and verified.
     */
    suspend fun restoreFromVault(
        context: Context,
        item: VaultItemEntity
    ): Result<VaultRestoreOutcome> = withContext(Dispatchers.IO) {
        val encryptedFile = if (item.encryptedFilePath.isNotEmpty() && File(item.encryptedFilePath).exists()) {
            File(item.encryptedFilePath)
        } else {
            getVaultFile(context, item.vaultFileName)
        }

        if (!encryptedFile.exists() || !VaultCryptoManager.isEncryptedVaultFile(encryptedFile)) {
            return@withContext Result.failure(IllegalStateException("El archivo cifrado no se encontró o está dañado."))
        }

        try {
            val origParsed = runCatching { Uri.parse(item.originalUri) }.getOrNull()
            val resolvedMime = resolveSpecificMimeType(item.originalName, item.mimeType, item.mediaType)
            val resolvedName = ensureDisplayNameExtension(item.originalName, resolvedMime, item.mediaType)

            // 1. If originalUri was an internal app storage file (e.g. unit test cacheDir/filesDir) whose parent is writable, restore directly there
            if (origParsed?.scheme == "file" && !origParsed.path.isNullOrEmpty()) {
                val targetFile = File(origParsed.path!!)
                val parent = targetFile.parentFile
                val isInInternalAppStorage = parent != null && (
                    targetFile.absolutePath.startsWith(context.cacheDir.absolutePath) ||
                    targetFile.absolutePath.startsWith(context.filesDir.absolutePath)
                )
                if (isInInternalAppStorage && parent.exists() && parent.canWrite()) {
                    val tempRestore = File(parent, "${targetFile.name}.restore_tmp")
                    FileOutputStream(tempRestore).use { fos ->
                        VaultCryptoManager.decryptStream(context, encryptedFile, fos)
                    }
                    if (targetFile.exists()) targetFile.delete()
                    if (!tempRestore.renameTo(targetFile)) {
                        tempRestore.copyTo(targetFile, overwrite = true)
                        tempRestore.delete()
                    }
                    // Only after targetFile is verified, delete encryptedFile
                    if (targetFile.exists() && targetFile.length() > 0L) {
                        encryptedFile.delete()
                    }

                    runCatching {
                        MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(resolvedMime), null)
                    }

                    val restoredEntity = buildRestoredMediaEntity(item, Uri.fromFile(targetFile).toString(), targetFile.length(), resolvedMime, resolvedName)
                    return@withContext Result.success(VaultRestoreOutcome(restoredEntity, restoredEntity.uri))
                }
            }

            // 2. Primary: Restore directly to public MediaStore according to media type:
            //    Image -> MediaStore Images (MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            //    Video -> MediaStore Video (MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            //    Audio -> MediaStore Audio (MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            val mediaStoreUri = tryRestoreToMediaStore(context, item, encryptedFile, resolvedMime, resolvedName)
            if (mediaStoreUri != null) {
                // Confirm restoration succeeded before deleting the .vlt file
                encryptedFile.delete()
                val restoredEntity = buildRestoredMediaEntity(item, mediaStoreUri.toString(), item.sizeBytes, resolvedMime, resolvedName)
                return@withContext Result.success(VaultRestoreOutcome(restoredEntity, restoredEntity.uri))
            }

            // 3. Fallback (e.g. Robolectric JVM or restricted MediaStore provider): restore to public external directory
            val publicDirType = when (item.mediaType) {
                MediaType.IMAGE.name -> Environment.DIRECTORY_PICTURES
                MediaType.AUDIO.name -> Environment.DIRECTORY_MUSIC
                else -> Environment.DIRECTORY_MOVIES
            }
            val publicBase = Environment.getExternalStoragePublicDirectory(publicDirType)
            val restoredDir = File(publicBase, "OmniMedia").apply { mkdirs() }
            val safeName = sanitizeFileName(resolvedName.ifEmpty { "restored_${item.id}" })
            val targetFile = resolveNonCollidingFile(if (restoredDir.exists()) restoredDir else (context.getExternalFilesDir(null) ?: context.filesDir), safeName)
            FileOutputStream(targetFile).use { fos ->
                VaultCryptoManager.decryptStream(context, encryptedFile, fos)
            }
            if (!targetFile.exists() || targetFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("Error al escribir el archivo restaurado."))
            }
            runCatching {
                MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), arrayOf(resolvedMime), null)
            }
            encryptedFile.delete()
            val restoredUriStr = Uri.fromFile(targetFile).toString()
            val restoredEntity = buildRestoredMediaEntity(item, restoredUriStr, targetFile.length(), resolvedMime, resolvedName)
            Result.success(VaultRestoreOutcome(restoredEntity, restoredUriStr))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun tryRestoreToMediaStore(
        context: Context,
        item: VaultItemEntity,
        encryptedFile: File,
        resolvedMime: String,
        resolvedName: String
    ): Uri? {
        return try {
            val resolver = context.contentResolver
            // Primary destination collection strictly according to media type:
            // Image -> MediaStore Images
            // Video -> MediaStore Video
            // Audio -> MediaStore Audio
            val collection = when (item.mediaType) {
                MediaType.IMAGE.name -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaType.AUDIO.name -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val relativePath = resolveRestoreRelativePath(item)
            val nowMs = System.currentTimeMillis()
            val nowSec = nowMs / 1000L

            val cleanTitle = resolvedName.substringBeforeLast(".").ifEmpty { resolvedName }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, resolvedName)
                put(MediaStore.MediaColumns.MIME_TYPE, resolvedMime)
                put(MediaStore.MediaColumns.DATE_ADDED, nowSec)
                // Note: Do NOT set DATE_MODIFIED on insert or update.
                // In Android MediaProvider, DATE_MODIFIED is read-only and causes insert/update to fail or be rejected.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                when (item.mediaType) {
                    MediaType.IMAGE.name -> {
                        put(MediaStore.Images.Media.DATE_TAKEN, nowMs)
                    }
                    MediaType.AUDIO.name -> {
                        put(MediaStore.Audio.Media.TITLE, cleanTitle)
                        if (item.artist.isNotEmpty()) put(MediaStore.Audio.Media.ARTIST, item.artist)
                        if (item.album.isNotEmpty()) put(MediaStore.Audio.Media.ALBUM, item.album)
                    }
                    MediaType.VIDEO.name -> {
                        put(MediaStore.Video.Media.TITLE, cleanTitle)
                    }
                }
            }

            var insertedUri = try {
                resolver.insert(collection, values)
            } catch (_: Exception) {
                null
            }

            // If insert failed due to custom relativePath restriction on Android Q+, retry with default OmniMedia folder
            if (insertedUri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val fallbackPath = defaultRelativePathFor(item.mediaType)
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, fallbackPath)
                insertedUri = try {
                    resolver.insert(collection, values)
                } catch (_: Exception) {
                    null
                }
            }

            // Secondary fallback for API 29+ if EXTERNAL_CONTENT_URI returned null: try VOLUME_EXTERNAL_PRIMARY
            if (insertedUri == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val primaryCollection = when (item.mediaType) {
                    MediaType.IMAGE.name -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    MediaType.AUDIO.name -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                insertedUri = try {
                    resolver.insert(primaryCollection, values)
                } catch (_: Exception) {
                    null
                }
            }

            if (insertedUri == null) return null

            // Stream directly from encryptedFile into MediaStore output stream without temporary public copies
            var writeSuccess = false
            var bytesWritten = 0L
            try {
                val stream = resolver.openOutputStream(insertedUri, "w")
                if (stream == null) {
                    resolver.delete(insertedUri, null, null)
                    return null
                }
                stream.use { out ->
                    bytesWritten = VaultCryptoManager.decryptStream(context, encryptedFile, out)
                    out.flush()
                }
                writeSuccess = bytesWritten > 0L
            } catch (_: Exception) {
                runCatching { resolver.delete(insertedUri, null, null) }
                return null
            }

            if (!writeSuccess) {
                runCatching { resolver.delete(insertedUri, null, null) }
                return null
            }

            // Publish the item by clearing IS_PENDING so other apps (system Gallery, Photos, etc.) can access it immediately.
            // CRITICAL: ONLY put IS_PENDING = 0. Do NOT include DATE_MODIFIED or any other columns.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                try {
                    resolver.update(insertedUri, completeValues, null, null)
                } catch (_: Exception) {
                    // Retry once to ensure IS_PENDING is cleared
                    runCatching { resolver.update(insertedUri, completeValues, null, null) }
                }
            }

            // Notify ContentResolver so apps observing MediaStore update immediately
            runCatching {
                resolver.notifyChange(insertedUri, null)
            }

            // Immediately scan through MediaScannerConnection to ensure Gallery apps refresh their index
            runCatching {
                var actualPath: String? = null
                val proj = arrayOf(MediaStore.MediaColumns.DATA)
                resolver.query(insertedUri, proj, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (idx >= 0) actualPath = cursor.getString(idx)
                    }
                }
                if (actualPath.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val candidate = File(Environment.getExternalStorageDirectory(), "$relativePath$resolvedName")
                    if (candidate.exists()) {
                        actualPath = candidate.absolutePath
                    }
                }
                if (!actualPath.isNullOrEmpty()) {
                    MediaScannerConnection.scanFile(context, arrayOf(actualPath), arrayOf(resolvedMime), null)
                }
            }

            insertedUri
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Permanently deletes the encrypted file from internal private vault storage.
     */
    suspend fun deleteVaultFilePermanently(context: Context, item: VaultItemEntity): Boolean = withContext(Dispatchers.IO) {
        val file = if (item.encryptedFilePath.isNotEmpty()) {
            File(item.encryptedFilePath)
        } else {
            getVaultFile(context, item.vaultFileName)
        }
        if (file.exists()) {
            file.delete()
        } else {
            true
        }
    }

    private fun openSourceInputStream(context: Context, uriString: String): InputStream? {
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path ?: return null
                    val file = File(path)
                    if (file.exists()) FileInputStream(file) else null
                }
                "content" -> context.contentResolver.openInputStream(uri)
                else -> {
                    val file = File(uriString)
                    if (file.exists()) FileInputStream(file) else context.contentResolver.openInputStream(uri)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildRestoredMediaEntity(
        item: VaultItemEntity,
        restoredUri: String,
        actualSize: Long,
        resolvedMime: String,
        resolvedName: String
    ): MediaFileEntity {
        val folderPath = resolveRestoreRelativePath(item).trimEnd('/')
        val folderName = folderPath.substringAfterLast('/', folderPath).ifEmpty { "OmniMedia" }
        val cleanTitle = resolvedName.substringBeforeLast(".").ifEmpty { resolvedName }
        return MediaFileEntity(
            uri = restoredUri,
            title = cleanTitle,
            displayName = resolvedName,
            artist = item.artist,
            album = item.album,
            durationMs = item.durationMs,
            sizeBytes = if (actualSize > 0L) actualSize else item.sizeBytes,
            dateAdded = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
            mimeType = resolvedMime,
            mediaType = item.mediaType,
            width = item.width,
            height = item.height,
            folderPath = folderPath,
            folderName = folderName
        )
    }

    private fun resolveRestoreRelativePath(item: VaultItemEntity): String {
        val raw = item.originalFolderPath.trim()
        val clean = raw.removePrefix("/storage/emulated/0/").trimStart('/').trimEnd('/')
        return when (item.mediaType) {
            MediaType.IMAGE.name -> {
                if (clean.isNotEmpty() && (clean.startsWith("DCIM", ignoreCase = true) || clean.startsWith("Pictures", ignoreCase = true))) {
                    "$clean/"
                } else {
                    "${Environment.DIRECTORY_PICTURES}/OmniMedia/"
                }
            }
            MediaType.AUDIO.name -> {
                if (clean.isNotEmpty() && (clean.startsWith("Music", ignoreCase = true) ||
                        clean.startsWith("Podcasts", ignoreCase = true) ||
                        clean.startsWith("Audiobooks", ignoreCase = true) ||
                        clean.startsWith("Recordings", ignoreCase = true) ||
                        clean.startsWith("Ringtones", ignoreCase = true) ||
                        clean.startsWith("Alarms", ignoreCase = true) ||
                        clean.startsWith("Notifications", ignoreCase = true))) {
                    "$clean/"
                } else {
                    "${Environment.DIRECTORY_MUSIC}/OmniMedia/"
                }
            }
            else -> {
                if (clean.isNotEmpty() && (clean.startsWith("Movies", ignoreCase = true) ||
                        clean.startsWith("DCIM", ignoreCase = true) ||
                        clean.startsWith("Pictures", ignoreCase = true))) {
                    "$clean/"
                } else {
                    "${Environment.DIRECTORY_MOVIES}/OmniMedia/"
                }
            }
        }
    }

    private fun resolveSpecificMimeType(fileName: String, existingMime: String, mediaType: String): String {
        val clean = existingMime.trim().lowercase()
        val ext = fileName.substringAfterLast(".", "").lowercase()

        // 1. If existing MIME matches media type category and is valid
        if (clean.isNotEmpty() && !clean.endsWith("/*") && clean.contains("/")) {
            val normalized = if (clean == "image/jpg") "image/jpeg" else clean
            val isValidForType = when (mediaType) {
                MediaType.IMAGE.name -> normalized.startsWith("image/")
                MediaType.AUDIO.name -> normalized.startsWith("audio/")
                MediaType.VIDEO.name -> normalized.startsWith("video/")
                else -> true
            }
            if (isValidForType) return normalized
        }

        // 2. Resolve from file extension
        if (ext.isNotEmpty()) {
            val fromExt = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (!fromExt.isNullOrEmpty() && !fromExt.endsWith("/*")) {
                val cleanExtMime = if (fromExt.lowercase() == "image/jpg") "image/jpeg" else fromExt.lowercase()
                val isValidForType = when (mediaType) {
                    MediaType.IMAGE.name -> cleanExtMime.startsWith("image/")
                    MediaType.AUDIO.name -> cleanExtMime.startsWith("audio/")
                    MediaType.VIDEO.name -> cleanExtMime.startsWith("video/")
                    else -> true
                }
                if (isValidForType) return cleanExtMime
            }

            val fallbackType = runCatching { MediaType.valueOf(mediaType) }.getOrNull()
            val mappedMime = guessMimeTypeFromName(fileName, fallbackType)
            if (mappedMime.isNotEmpty() && !mappedMime.endsWith("/*")) {
                val isValidForType = when (mediaType) {
                    MediaType.IMAGE.name -> mappedMime.startsWith("image/")
                    MediaType.AUDIO.name -> mappedMime.startsWith("audio/")
                    MediaType.VIDEO.name -> mappedMime.startsWith("video/")
                    else -> true
                }
                if (isValidForType) return mappedMime
            }
        }

        return defaultMimeTypeFor(mediaType)
    }

    private fun ensureDisplayNameExtension(name: String, mimeType: String, mediaType: String): String {
        if (name.contains(".") && name.substringAfterLast(".").isNotBlank()) {
            return name
        }
        val extFromMime = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        val fallbackExt = when (mediaType) {
            MediaType.IMAGE.name -> extFromMime ?: "jpg"
            MediaType.AUDIO.name -> extFromMime ?: "mp3"
            else -> extFromMime ?: "mp4"
        }
        return "$name.$fallbackExt"
    }

    private fun defaultRelativePathFor(mediaType: String): String {
        return when (mediaType) {
            MediaType.AUDIO.name -> "${Environment.DIRECTORY_MUSIC}/OmniMedia/"
            MediaType.IMAGE.name -> "${Environment.DIRECTORY_PICTURES}/OmniMedia/"
            else -> "${Environment.DIRECTORY_MOVIES}/OmniMedia/"
        }
    }

    private fun defaultMimeTypeFor(mediaType: String): String {
        return when (mediaType) {
            MediaType.AUDIO.name -> "audio/mpeg"
            MediaType.IMAGE.name -> "image/jpeg"
            else -> "video/mp4"
        }
    }

    private fun guessMimeTypeFromName(name: String, fallback: MediaType?): String {
        val ext = name.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "mp4", "mkv", "webm", "mov", "avi", "3gp" -> "video/$ext"
            "mp3", "wav", "flac", "ogg", "m4a", "aac" -> "audio/$ext"
            "jpg", "jpeg", "png", "webp", "gif", "heic" -> "image/${if (ext == "jpg") "jpeg" else ext}"
            else -> when (fallback) {
                MediaType.AUDIO -> "audio/mpeg"
                MediaType.IMAGE -> "image/jpeg"
                else -> "video/mp4"
            }
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun resolveNonCollidingFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast(".")
        val ext = fileName.substringAfterLast(".", "")
        var counter = 1
        while (candidate.exists()) {
            val newName = if (ext.isNotEmpty()) "${base}_($counter).$ext" else "${base}_($counter)"
            candidate = File(dir, newName)
            counter++
        }
        return candidate
    }
}
