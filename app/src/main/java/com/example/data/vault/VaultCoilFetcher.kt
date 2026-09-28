package com.example.data.vault

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.buffer
import okio.source
import java.io.File

/**
 * Custom Coil [Fetcher] that decrypts OMNIVAULT_V1 `.vlt` image and video files
 * in memory on the fly for thumbnails and fullscreen photo viewing.
 */
class VaultCoilFetcher(
    private val context: Context,
    private val encryptedFile: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val decryptedStream = VaultCryptoManager.openDecryptedInputStream(context, encryptedFile)
        val bufferedSource = decryptedStream.source().buffer()
        return SourceResult(
            source = ImageSource(source = bufferedSource, context = options.context),
            mimeType = null,
            dataSource = DataSource.DISK
        )
    }

    class UriFactory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uriStr = data.toString()
            if (!VaultStorageManager.isVaultUri(uriStr)) return null
            val file = VaultStorageManager.resolveVaultFileFromUri(context, uriStr) ?: return null
            if (!VaultCryptoManager.isEncryptedVaultFile(file)) return null
            return VaultCoilFetcher(context.applicationContext, file, options)
        }
    }

    class FileFactory(private val context: Context) : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (!VaultCryptoManager.isEncryptedVaultFile(data)) return null
            return VaultCoilFetcher(context.applicationContext, data, options)
        }
    }
}
