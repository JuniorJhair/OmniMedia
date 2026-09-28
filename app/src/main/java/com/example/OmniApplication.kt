package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.local.database.AppDatabase
import com.example.data.repository.HistoryRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.PlaybackProgressRepository
import com.example.data.repository.PlaylistRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.VaultRepository
import com.example.data.vault.VaultCoilFetcher

class OmniApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .components {
                add(VaultCoilFetcher.UriFactory(this@OmniApplication))
                add(VaultCoilFetcher.FileFactory(this@OmniApplication))
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    lateinit var database: AppDatabase
        private set

    lateinit var mediaRepository: MediaRepository
        private set

    lateinit var playbackProgressRepository: PlaybackProgressRepository
        private set

    lateinit var playlistRepository: PlaylistRepository
        private set

    lateinit var historyRepository: HistoryRepository
        private set

    lateinit var vaultRepository: VaultRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        mediaRepository = MediaRepository(database.mediaDao())
        playbackProgressRepository = PlaybackProgressRepository(database.playbackProgressDao())
        playlistRepository = PlaylistRepository(database.playlistDao())
        historyRepository = HistoryRepository(database.historyDao())
        vaultRepository = VaultRepository(database.vaultDao())
        settingsRepository = SettingsRepository(database.settingsDao())
    }

    companion object {
        lateinit var instance: OmniApplication
            private set
    }
}
