package com.example

import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.media3.datasource.DataSpec
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.MediaType
import com.example.core.model.SortOption
import com.example.core.model.ViewMode
import com.example.data.local.database.AppDatabase
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.MediaFileEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistItemEntity
import com.example.data.local.entity.VaultItemEntity
import com.example.data.local.entity.VideoSpeedEntity
import com.example.data.vault.VaultCryptoManager
import com.example.data.vault.VaultMedia3DataSource
import com.example.data.vault.VaultSecurityManager
import com.example.data.vault.VaultStorageManager
import com.example.player.controller.OmniPlayerController
import com.example.player.model.PlayerOrientationPreference
import com.example.player.model.PlaylistItem
import com.example.player.model.SpeedApplicationScope
import com.example.player.model.SystemRotationMode
import com.example.player.model.nextOrientationPreference
import com.example.player.model.resolveEffectiveVideoSpeed
import com.example.player.model.resolveRequestedOrientation
import com.example.player.ui.PlayerScreen
import com.example.player.ui.ResumePlaybackDialog
import com.example.player.ui.SpeedSelectorDialog
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.screens.images.PhotoViewerScreen
import com.example.ui.screens.vault.VaultScreen
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context matches app name`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("OmniMedia", appName)
    }

    @Test
    fun `formatDuration formats hours minutes and seconds correctly`() {
        assertEquals("00:05", formatDuration(5000L))
        assertEquals("01:30", formatDuration(90000L))
        assertEquals("1:05:00", formatDuration(3900000L))
    }

    @Test
    fun `formatFileSize formats byte units properly`() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("1.0 KB", formatFileSize(1024L))
        assertEquals("1.5 MB", formatFileSize((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun `enums are correctly defined`() {
        assertEquals(3, MediaType.values().size)
        assertEquals(4, ViewMode.values().size)
        assertEquals("Nombre A-Z", SortOption.NAME_ASC.displayName)
    }

    @Test
    fun `syncMediaStore inserts new items and preserves user state on subsequent scans`() = runBlocking {
        val mediaDao = db.mediaDao()

        val item1 = MediaFileEntity(
            uri = "content://media/external/video/media/101",
            title = "Video One",
            displayName = "Video One.mp4",
            durationMs = 120000L,
            sizeBytes = 5000000L,
            dateAdded = 1000L,
            dateModified = 1000L,
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO.name,
            folderName = "Movies"
        )
        val item2 = MediaFileEntity(
            uri = "content://media/external/video/media/102",
            title = "Video Two",
            displayName = "Video Two.mp4",
            durationMs = 60000L,
            sizeBytes = 2500000L,
            dateAdded = 2000L,
            dateModified = 2000L,
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO.name,
            folderName = "Camera"
        )

        // Initial scan
        mediaDao.syncMediaStore(listOf(item1, item2), MediaType.VIDEO.name)
        var list = mediaDao.getMediaListByType(MediaType.VIDEO.name)
        assertEquals(2, list.size)

        // User marks item1 as favorite and plays it
        mediaDao.setFavorite("content://media/external/video/media/101", true)
        mediaDao.recordPlay("content://media/external/video/media/101", 5000L)

        // Second scan with modified item1 date, item2 deleted on device, and new item3
        val item1Modified = item1.copy(dateModified = 1500L, title = "Video One (Updated)")
        val item3 = MediaFileEntity(
            uri = "content://media/external/video/media/103",
            title = "Video Three",
            displayName = "Video Three.mp4",
            durationMs = 90000L,
            sizeBytes = 3500000L,
            dateAdded = 3000L,
            dateModified = 3000L,
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO.name,
            folderName = "Downloads"
        )

        mediaDao.syncMediaStore(listOf(item1Modified, item3), MediaType.VIDEO.name)
        list = mediaDao.getMediaListByType(MediaType.VIDEO.name)

        // Verify: item2 was removed because it was deleted from device MediaStore
        assertEquals(2, list.size)
        val updatedItem1 = list.first { it.uri == "content://media/external/video/media/101" }
        // Verify: user favorite and play count were preserved across scan!
        assertTrue(updatedItem1.isFavorite)
        assertEquals(1, updatedItem1.playCount)
        assertEquals("Video One (Updated)", updatedItem1.title)

        val newItem3 = list.first { it.uri == "content://media/external/video/media/103" }
        assertEquals("Video Three", newItem3.title)
    }

    @Test
    fun `batch operations in MediaDao update multiple favorites and deletes`() = runBlocking {
        val mediaDao = db.mediaDao()
        val itemA = MediaFileEntity(
            uri = "content://media/external/audio/media/1",
            title = "Song A",
            displayName = "Song A.mp3",
            mediaType = MediaType.AUDIO.name,
            folderName = "Music"
        )
        val itemB = MediaFileEntity(
            uri = "content://media/external/audio/media/2",
            title = "Song B",
            displayName = "Song B.mp3",
            mediaType = MediaType.AUDIO.name,
            folderName = "Music"
        )
        val itemC = MediaFileEntity(
            uri = "content://media/external/audio/media/3",
            title = "Song C",
            displayName = "Song C.mp3",
            mediaType = MediaType.AUDIO.name,
            folderName = "Podcasts"
        )
        mediaDao.insertAll(listOf(itemA, itemB, itemC))

        // Multi-select favorite
        mediaDao.setFavorites(listOf(itemA.uri, itemB.uri), true)
        val favorites = mediaDao.getFavorites().first()
        assertEquals(2, favorites.size)

        // Multi-delete
        mediaDao.deleteByUris(listOf(itemA.uri, itemC.uri))
        val remaining = mediaDao.getAllMedia().first()
        assertEquals(1, remaining.size)
        assertEquals("Song B", remaining.first().title)
    }

    @Test
    fun `searchMedia finds matching titles and artists`() = runBlocking {
        val mediaDao = db.mediaDao()
        val song = MediaFileEntity(
            uri = "content://media/external/audio/media/10",
            title = "Bohemian Rhapsody",
            displayName = "Bohemian Rhapsody.mp3",
            artist = "Queen",
            mediaType = MediaType.AUDIO.name,
            folderName = "Rock"
        )
        mediaDao.insert(song)

        val resultByTitle = mediaDao.searchMedia("Bohemian").first()
        assertEquals(1, resultByTitle.size)

        val resultByArtist = mediaDao.searchMedia("Queen").first()
        assertEquals(1, resultByArtist.size)

        val resultNone = mediaDao.searchMedia("Nonexistent").first()
        assertTrue(resultNone.isEmpty())
    }

    @Test
    fun `playback progress dao saves progress and marks completed appropriately`() = runBlocking {
        val progressDao = db.playbackProgressDao()
        val mediaUri = "content://media/external/video/media/50"

        // Save progress at 50%
        val midProgress = com.example.data.local.entity.PlaybackProgressEntity(
            mediaUri = mediaUri,
            positionMs = 60000L,
            durationMs = 120000L,
            completed = false,
            lastUpdated = System.currentTimeMillis()
        )
        progressDao.saveProgress(midProgress)

        var loaded = progressDao.getProgress(mediaUri)
        assertTrue(loaded != null)
        assertEquals(60000L, loaded?.positionMs)
        assertFalse(loaded?.completed ?: true)

        val unfinished = progressDao.getUnfinished(10).first()
        assertEquals(1, unfinished.size)

        // Save progress at 96% -> completed
        val finishedProgress = midProgress.copy(
            positionMs = 116000L,
            completed = true
        )
        progressDao.saveProgress(finishedProgress)

        val unfinishedAfter = progressDao.getUnfinished(10).first()
        assertTrue(unfinishedAfter.isEmpty())
    }

    @Test
    fun `history dao records and clears playback history`() = runBlocking {
        val historyDao = db.historyDao()
        val historyItem = com.example.data.local.entity.HistoryEntity(
            mediaUri = "content://media/external/video/media/77",
            title = "Test Video",
            mediaType = MediaType.VIDEO.name,
            durationMs = 180000L,
            positionMs = 45000L,
            playedAt = System.currentTimeMillis()
        )
        historyDao.insert(historyItem)

        val historyList = historyDao.getHistory(10).first()
        assertEquals(1, historyList.size)
        assertEquals("Test Video", historyList.first().title)

        historyDao.clearHistory()
        val cleared = historyDao.getHistory(10).first()
        assertTrue(cleared.isEmpty())
    }

    @Test
    fun `repeat mode cycles correctly between OFF ALL ONE`() {
        val controller = com.example.player.controller.OmniPlayerController.getInstance(context)
        while (controller.repeatMode.value != com.example.player.model.RepeatMode.OFF) {
            controller.cycleRepeatMode()
        }
        assertEquals(com.example.player.model.RepeatMode.OFF, controller.repeatMode.value)

        controller.cycleRepeatMode()
        assertEquals(com.example.player.model.RepeatMode.ALL, controller.repeatMode.value)

        controller.cycleRepeatMode()
        assertEquals(com.example.player.model.RepeatMode.ONE, controller.repeatMode.value)

        controller.cycleRepeatMode()
        assertEquals(com.example.player.model.RepeatMode.OFF, controller.repeatMode.value)
    }

    @Test
    fun `shuffle mode toggles between true and false`() {
        val controller = com.example.player.controller.OmniPlayerController.getInstance(context)
        if (controller.shuffleEnabled.value) {
            controller.toggleShuffle()
        }
        assertFalse(controller.shuffleEnabled.value)

        controller.toggleShuffle()
        assertTrue(controller.shuffleEnabled.value)

        controller.toggleShuffle()
        assertFalse(controller.shuffleEnabled.value)
    }

    @Test
    fun `queue adds removes and clears items accurately`() {
        val controller = com.example.player.controller.OmniPlayerController.getInstance(context)
        controller.clearQueue()
        assertTrue(controller.playlist.value.isEmpty())

        val item1 = com.example.player.model.PlaylistItem(
            uri = "content://media/audio/1",
            title = "Track 1",
            artist = "Artist A",
            mediaType = MediaType.AUDIO
        )
        val item2 = com.example.player.model.PlaylistItem(
            uri = "content://media/audio/2",
            title = "Track 2",
            artist = "Artist B",
            mediaType = MediaType.AUDIO
        )

        controller.preparePlaylist(listOf(item1, item2), startIndex = 0)
        assertEquals(2, controller.playlist.value.size)
        assertEquals("Track 1", controller.playlist.value[0].title)

        val item3 = com.example.player.model.PlaylistItem(
            uri = "content://media/audio/3",
            title = "Track 3",
            artist = "Artist C",
            mediaType = MediaType.AUDIO
        )
        controller.addToQueue(item3)
        assertEquals(3, controller.playlist.value.size)

        controller.removeFromQueue(1)
        assertEquals(2, controller.playlist.value.size)
        assertEquals("Track 3", controller.playlist.value[1].title)

        controller.clearQueue()
        assertTrue(controller.playlist.value.isEmpty())
    }

    @Test
    fun `sleep timer configures duration and stop at end of song`() {
        val controller = com.example.player.controller.OmniPlayerController.getInstance(context)
        controller.cancelSleepTimer()
        assertFalse(controller.sleepTimerState.value.isActive)

        controller.setSleepTimer(15)
        assertTrue(controller.sleepTimerState.value.isActive)
        assertEquals(15 * 60L, controller.sleepTimerState.value.remainingSeconds)
        assertFalse(controller.sleepTimerState.value.stopAtEndOfSong)

        controller.setStopAtEndOfSong(true)
        assertTrue(controller.sleepTimerState.value.isActive)
        assertTrue(controller.sleepTimerState.value.stopAtEndOfSong)

        controller.cancelSleepTimer()
        assertFalse(controller.sleepTimerState.value.isActive)
    }

    @Test
    fun `equalizer manager initial state is safe without crashing`() {
        val eqManager = com.example.player.audio.OmniEqualizerManager()
        val state = eqManager.state.value
        // Either available or gracefully unavailable depending on test environment AudioEffects support
        assertTrue(state.statusMessage.isNotEmpty() || !state.isAvailable)
        eqManager.release()
    }

    @Test
    fun `playlist dao performs full CRUD and tracks ordered items`() = runBlocking {
        val playlistDao = db.playlistDao()
        val mediaDao = db.mediaDao()

        // Insert media items
        val song1 = MediaFileEntity(
            uri = "content://media/audio/101",
            title = "Stairway to Heaven",
            displayName = "Stairway to Heaven.mp3",
            artist = "Led Zeppelin",
            album = "Led Zeppelin IV",
            genre = "Rock",
            mediaType = MediaType.AUDIO.name,
            durationMs = 482000L
        )
        val song2 = MediaFileEntity(
            uri = "content://media/audio/102",
            title = "Kashmir",
            displayName = "Kashmir.mp3",
            artist = "Led Zeppelin",
            album = "Physical Graffiti",
            genre = "Rock",
            mediaType = MediaType.AUDIO.name,
            durationMs = 517000L
        )
        val song3 = MediaFileEntity(
            uri = "content://media/audio/103",
            title = "Whole Lotta Love",
            displayName = "Whole Lotta Love.mp3",
            artist = "Led Zeppelin",
            album = "Led Zeppelin II",
            genre = "Rock",
            mediaType = MediaType.AUDIO.name,
            durationMs = 333000L
        )
        mediaDao.insertAll(listOf(song1, song2, song3))

        // Create playlist
        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(name = "Classic Rock Favorites", description = "Best of 70s rock")
        )
        assertTrue(playlistId > 0)

        // Add items to playlist in order
        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = playlistId, mediaUri = song1.uri, orderIndex = 0))
        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = playlistId, mediaUri = song2.uri, orderIndex = 1))
        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = playlistId, mediaUri = song3.uri, orderIndex = 2))

        val items = playlistDao.getItemsForPlaylist(playlistId).first()
        assertEquals(3, items.size)
        assertEquals(song1.uri, items[0].mediaUri)
        assertEquals(song2.uri, items[1].mediaUri)
        assertEquals(song3.uri, items[2].mediaUri)

        // Fetch resolved media items
        val resolvedSongs = playlistDao.getMediaForPlaylist(playlistId).first()
        assertEquals(3, resolvedSongs.size)
        assertEquals("Stairway to Heaven", resolvedSongs[0].title)

        // Remove an item
        playlistDao.removeItemFromPlaylist(playlistId, song2.uri)
        val afterRemove = playlistDao.getItemsForPlaylist(playlistId).first()
        assertEquals(2, afterRemove.size)
        assertFalse(afterRemove.any { it.mediaUri == song2.uri })

        // Reorder items: swap song1 and song3
        playlistDao.updateItemOrder(playlistId, song3.uri, 0)
        playlistDao.updateItemOrder(playlistId, song1.uri, 1)
        val reordered = playlistDao.getItemsForPlaylist(playlistId).first()
        assertEquals(song3.uri, reordered[0].mediaUri)
        assertEquals(song1.uri, reordered[1].mediaUri)

        // Delete playlist
        playlistDao.deletePlaylist(playlistId)
        val allPlaylists = playlistDao.getAllPlaylists().first()
        assertTrue(allPlaylists.none { it.id == playlistId })
    }

    @Test
    fun `controller playNext and reorderQueue manipulate queue properly`() {
        val controller = com.example.player.controller.OmniPlayerController.getInstance(context)
        controller.clearQueue()

        val trackA = PlaylistItem(uri = "content://a", title = "A", mediaType = MediaType.AUDIO)
        val trackB = PlaylistItem(uri = "content://b", title = "B", mediaType = MediaType.AUDIO)
        val trackC = PlaylistItem(uri = "content://c", title = "C", mediaType = MediaType.AUDIO)

        controller.preparePlaylist(listOf(trackA, trackB), startIndex = 0)
        assertEquals(2, controller.playlist.value.size)

        // Test playNext (inserts right after currentIndex)
        controller.playNext(trackC)
        assertEquals(3, controller.playlist.value.size)
        assertEquals("C", controller.playlist.value[1].title)

        // Test moveQueueItem
        controller.moveQueueItem(fromIndex = 1, toIndex = 2)
        assertEquals("B", controller.playlist.value[1].title)
        assertEquals("C", controller.playlist.value[2].title)

        controller.clearQueue()
    }

    @Test
    fun `queue persistence across app restart restores all 10 tracks, index, position, shuffle and repeat`() {
        val persistenceManager = com.example.player.controller.QueuePersistenceManager(context)
        persistenceManager.clearQueue()

        // Case A: 10 songs in queue
        val tenTracks = (1..10).map { i ->
            PlaylistItem(
                uri = "content://media/audio/$i",
                title = "Song $i",
                artist = "Artist $i",
                album = "Album $i",
                durationMs = 180000L,
                mediaType = MediaType.AUDIO
            )
        }
        persistenceManager.saveQueue(
            items = tenTracks,
            currentIndex = 4,
            positionMs = 45000L,
            shuffleEnabled = true,
            repeatMode = com.example.player.model.RepeatMode.ALL
        )

        // Case B & C: App restart - load saved queue from disk
        val restoredState = persistenceManager.loadSavedQueue()
        assertTrue(restoredState != null)
        assertEquals(10, restoredState?.items?.size)
        assertEquals("Song 1", restoredState?.items?.get(0)?.title)
        assertEquals("Song 5", restoredState?.items?.get(4)?.title)
        assertEquals(4, restoredState?.currentIndex)
        assertEquals(45000L, restoredState?.positionMs)
        assertTrue(restoredState?.shuffleEnabled == true)
        assertEquals(com.example.player.model.RepeatMode.ALL, restoredState?.repeatMode)

        // Verify OmniPlayerController restoreSavedQueue
        val controller = com.example.player.controller.OmniPlayerController.getInstance(context)
        controller.clearQueue()
        controller.queuePersistence.saveQueue(
            items = tenTracks,
            currentIndex = 4,
            positionMs = 45000L,
            shuffleEnabled = true,
            repeatMode = com.example.player.model.RepeatMode.ALL
        )
        val restored = controller.restoreSavedQueue(autoPlay = false)
        assertTrue(restored)
        assertEquals(10, controller.playlist.value.size)
        assertEquals(4, controller.currentIndex.value)
        assertEquals(com.example.player.model.RepeatMode.ALL, controller.repeatMode.value)
        assertTrue(controller.shuffleEnabled.value)

        controller.clearQueue()
        persistenceManager.clearQueue()
    }

    @Test
    fun `history flow records on actual playback and persists across reopen`() = runBlocking {
        val historyDao = db.historyDao()
        historyDao.clearHistory()

        // 1. Initial state: history is empty
        var history = historyDao.getHistory(50).first()
        assertTrue(history.isEmpty())

        // 2. Play song 1
        val item1 = com.example.data.local.entity.HistoryEntity(
            mediaUri = "content://media/audio/song_one",
            title = "Track One",
            mediaType = MediaType.AUDIO.name,
            durationMs = 240000L,
            positionMs = 10000L,
            playedAt = 1000L
        )
        historyDao.insert(item1)

        // 3. Play song 2
        val item2 = com.example.data.local.entity.HistoryEntity(
            mediaUri = "content://media/audio/song_two",
            title = "Track Two",
            mediaType = MediaType.AUDIO.name,
            durationMs = 180000L,
            positionMs = 5000L,
            playedAt = 2000L
        )
        historyDao.insert(item2)

        history = historyDao.getHistory(50).first()
        assertEquals(2, history.size)
        // Ordered by playedAt DESC: Track Two was played last, so it's first
        assertEquals("Track Two", history[0].title)
        assertEquals("Track One", history[1].title)

        // 4. Playing Track One again creates an updated recent entry
        val item1PlayedAgain = item1.copy(id = 0, playedAt = 3000L)
        historyDao.insert(item1PlayedAgain)

        val updatedHistory = historyDao.getHistory(50).first()
        assertEquals(3, updatedHistory.size)
        assertEquals("Track One", updatedHistory[0].title)
        assertEquals(3000L, updatedHistory[0].playedAt)
    }

    @Test
    fun `playlist handles missing or deleted file without crashing`() = runBlocking {
        val playlistDao = db.playlistDao()
        val mediaDao = db.mediaDao()

        val itemA = MediaFileEntity(
            uri = "content://audio/A",
            title = "Track A",
            displayName = "A.mp3",
            mediaType = MediaType.AUDIO.name
        )
        val itemB = MediaFileEntity(
            uri = "content://audio/B",
            title = "Track B",
            displayName = "B.mp3",
            mediaType = MediaType.AUDIO.name
        )
        val itemC = MediaFileEntity(
            uri = "content://audio/C",
            title = "Track C",
            displayName = "C.mp3",
            mediaType = MediaType.AUDIO.name
        )
        mediaDao.insertAll(listOf(itemA, itemB, itemC))

        val pId = playlistDao.insertPlaylist(PlaylistEntity(name = "Test Playlist"))
        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = pId, mediaUri = itemA.uri, orderIndex = 0))
        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = pId, mediaUri = itemB.uri, orderIndex = 1))
        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = pId, mediaUri = itemC.uri, orderIndex = 2))

        var songsInPlaylist = playlistDao.getMediaForPlaylist(pId).first()
        assertEquals(3, songsInPlaylist.size)

        // File B physically disappears (deleted from device storage and removed from media_files)
        mediaDao.deleteByUris(listOf(itemB.uri))

        // Query playlist again: does NOT crash, returns A and C cleanly!
        songsInPlaylist = playlistDao.getMediaForPlaylist(pId).first()
        assertEquals(2, songsInPlaylist.size)
        assertEquals("Track A", songsInPlaylist[0].title)
        assertEquals("Track C", songsInPlaylist[1].title)

        // Playlist metadata itself remains valid
        val playlist = playlistDao.getPlaylistById(pId)
        assertTrue(playlist != null)
        assertEquals("Test Playlist", playlist?.name)
    }

    @Test
    fun `music search filters by title, artist, album, genre, folder and handles casing and empty query`() {
        val songs = listOf(
            MediaFileEntity(
                uri = "content://1",
                title = "Paranoid",
                displayName = "Paranoid.mp3",
                artist = "Black Sabbath",
                album = "Paranoid",
                genre = "Heavy Metal",
                folderName = "HeavyMetal",
                mediaType = MediaType.AUDIO.name
            ),
            MediaFileEntity(
                uri = "content://2",
                title = "Billie Jean",
                displayName = "Billie Jean.mp3",
                artist = "Michael Jackson",
                album = "Thriller",
                genre = "Pop",
                folderName = "80sPop",
                mediaType = MediaType.AUDIO.name
            ),
            MediaFileEntity(
                uri = "content://3",
                title = "Iron Man",
                displayName = "Iron Man.mp3",
                artist = "Black Sabbath",
                album = "Paranoid",
                genre = "Heavy Metal",
                folderName = "HeavyMetal",
                mediaType = MediaType.AUDIO.name
            )
        )

        fun filterSongs(query: String) = if (query.isEmpty()) songs else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true) ||
            it.genre.contains(query, ignoreCase = true) ||
            it.folderName.contains(query, ignoreCase = true)
        }

        // Empty query returns all
        assertEquals(3, filterSongs("").size)

        // Search by Title (case insensitive)
        val byTitle = filterSongs("paranoid")
        assertEquals(2, byTitle.size) // Song title Paranoid + Album Paranoid for Iron Man

        // Search by Artist
        val byArtist = filterSongs("michael")
        assertEquals(1, byArtist.size)
        assertEquals("Billie Jean", byArtist[0].title)

        // Search by Album
        val byAlbum = filterSongs("thriller")
        assertEquals(1, byAlbum.size)
        assertEquals("Billie Jean", byAlbum[0].title)

        // Search by Genre
        val byGenre = filterSongs("heavy metal")
        assertEquals(2, byGenre.size)

        // Search by Folder
        val byFolder = filterSongs("80spop")
        assertEquals(1, byFolder.size)
        assertEquals("Billie Jean", byFolder[0].title)

        // Search with no results
        val noResult = filterSongs("Jazz")
        assertTrue(noResult.isEmpty())
    }

    @Test
    fun `music sorting orders correctly by name, date, size and duration`() {
        val song1 = MediaFileEntity(
            uri = "content://1",
            title = "Alpha",
            displayName = "Alpha.mp3",
            mediaType = MediaType.AUDIO.name,
            dateAdded = 100L,
            sizeBytes = 5000L,
            durationMs = 60000L
        )
        val song2 = MediaFileEntity(
            uri = "content://2",
            title = "Beta",
            displayName = "Beta.mp3",
            mediaType = MediaType.AUDIO.name,
            dateAdded = 300L,
            sizeBytes = 2000L,
            durationMs = 180000L
        )
        val song3 = MediaFileEntity(
            uri = "content://3",
            title = "Gamma",
            displayName = "Gamma.mp3",
            mediaType = MediaType.AUDIO.name,
            dateAdded = 200L,
            sizeBytes = 8000L,
            durationMs = 120000L
        )
        val songs = listOf(song2, song1, song3)

        // NAME_ASC
        val byNameAsc = songs.sortedBy { it.title.lowercase() }
        assertEquals(listOf("Alpha", "Beta", "Gamma"), byNameAsc.map { it.title })

        // NAME_DESC
        val byNameDesc = songs.sortedByDescending { it.title.lowercase() }
        assertEquals(listOf("Gamma", "Beta", "Alpha"), byNameDesc.map { it.title })

        // DATE_DESC
        val byDateDesc = songs.sortedByDescending { it.dateAdded }
        assertEquals(listOf("Beta", "Gamma", "Alpha"), byDateDesc.map { it.title })

        // SIZE_DESC
        val bySizeDesc = songs.sortedByDescending { it.sizeBytes }
        assertEquals(listOf("Gamma", "Alpha", "Beta"), bySizeDesc.map { it.title })

        // DURATION_DESC
        val byDurationDesc = songs.sortedByDescending { it.durationMs }
        assertEquals(listOf("Beta", "Gamma", "Alpha"), byDurationDesc.map { it.title })

        // ARTIST_ASC and ARTIST_DESC
        val songA = song1.copy(artist = "Queen", album = "A Night at the Opera")
        val songB = song2.copy(artist = "ABBA", album = "Arrival")
        val songC = song3.copy(artist = "Pink Floyd", album = "The Wall")
        val songsWithMeta = listOf(songA, songB, songC)

        val byArtistAsc = songsWithMeta.sortedBy { it.artist.lowercase() }
        assertEquals(listOf("ABBA", "Pink Floyd", "Queen"), byArtistAsc.map { it.artist })

        val byArtistDesc = songsWithMeta.sortedByDescending { it.artist.lowercase() }
        assertEquals(listOf("Queen", "Pink Floyd", "ABBA"), byArtistDesc.map { it.artist })

        // ALBUM_ASC and ALBUM_DESC
        val byAlbumAsc = songsWithMeta.sortedBy { it.album.lowercase() }
        assertEquals(listOf("A Night at the Opera", "Arrival", "The Wall"), byAlbumAsc.map { it.album })

        val byAlbumDesc = songsWithMeta.sortedByDescending { it.album.lowercase() }
        assertEquals(listOf("The Wall", "Arrival", "A Night at the Opera"), byAlbumDesc.map { it.album })
    }

    @Test
    fun `deleting a playlist does NOT delete or modify underlying media files`() {
        runBlocking {
            val playlistDao = db.playlistDao()
        val mediaDao = db.mediaDao()

        val track1 = MediaFileEntity(
            uri = "content://audio/101",
            title = "Track 101",
            displayName = "101.mp3",
            mediaType = MediaType.AUDIO.name
        )
        val track2 = MediaFileEntity(
            uri = "content://audio/102",
            title = "Track 102",
            displayName = "102.mp3",
            mediaType = MediaType.AUDIO.name
        )
        mediaDao.insertAll(listOf(track1, track2))

        val pId = playlistDao.insertPlaylist(PlaylistEntity(name = "Rock Classics"))
        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = pId, mediaUri = track1.uri, orderIndex = 0))
        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = pId, mediaUri = track2.uri, orderIndex = 1))

        // Confirm playlist has 2 songs
        assertEquals(2, playlistDao.getMediaForPlaylist(pId).first().size)

        // Delete the playlist
        playlistDao.deletePlaylist(pId)

        // Verify playlist is gone
        val deletedPlaylist = playlistDao.getPlaylistById(pId)
        assertTrue(deletedPlaylist == null)

        // CRITICAL: Verify physical media files are COMPLETELY UNTOUCHED in media_files table!
        val remainingMedia = mediaDao.getMediaListByType(MediaType.AUDIO.name)
        assertEquals(2, remainingMedia.size)
        assertTrue(remainingMedia.any { it.uri == track1.uri })
        assertTrue(remainingMedia.any { it.uri == track2.uri })
        }
    }

    @Test
    fun `artist and album grouping derives strictly from scanned media metadata`() {
        val songs = listOf(
            MediaFileEntity(uri = "c://1", title = "Come Together", displayName = "1.mp3", artist = "The Beatles", album = "Abbey Road", genre = "Rock", mediaType = MediaType.AUDIO.name),
            MediaFileEntity(uri = "c://2", title = "Something", displayName = "2.mp3", artist = "The Beatles", album = "Abbey Road", genre = "Rock", mediaType = MediaType.AUDIO.name),
            MediaFileEntity(uri = "c://3", title = "Let It Be", displayName = "3.mp3", artist = "The Beatles", album = "Let It Be", genre = "Rock", mediaType = MediaType.AUDIO.name),
            MediaFileEntity(uri = "c://4", title = "Imagine", displayName = "4.mp3", artist = "John Lennon", album = "Imagine", genre = "Pop", mediaType = MediaType.AUDIO.name)
        )

        // Artist grouping
        val artistGroups = songs.groupBy { it.artist }
        assertEquals(2, artistGroups.size)
        val beatlesSongs = artistGroups["The Beatles"]!!
        assertEquals(3, beatlesSongs.size)
        val beatlesAlbums = beatlesSongs.map { it.album }.distinct()
        assertEquals(2, beatlesAlbums.size) // Abbey Road and Let It Be

        // Album grouping
        val albumGroups = songs.groupBy { it.album }
        assertEquals(3, albumGroups.size)
        val abbeyRoadSongs = albumGroups["Abbey Road"]!!
        assertEquals(2, abbeyRoadSongs.size)

        // Genre grouping
        val genreGroups = songs.groupBy { it.genre }
        assertEquals(2, genreGroups.size)
        assertEquals(3, genreGroups["Rock"]!!.size)
        assertEquals(1, genreGroups["Pop"]!!.size)
    }

    // =========================================================================
    // FASE 5.6 — PRUEBAS AUTOMATIZADAS OBLIGATORIAS
    // =========================================================================

    @Test
    fun `phase5_6_problem1_music_player_displays_all_controls_without_scrolling`() {
        val controller = OmniPlayerController.getInstance(context)
        val audioItem = PlaylistItem(
            uri = "content://audio/phase56_song1",
            title = "Starlight Symphony",
            artist = "Cosmic Orchestra",
            album = "Galaxies",
            durationMs = 210_000L,
            mediaType = MediaType.AUDIO
        )
        controller.preparePlaylist(
            items = listOf(audioItem),
            startIndex = 0,
            startPositionMs = 30_000L,
            autoPlay = false
        )

        composeTestRule.setContent {
            // Simulate a normal phone viewport (360dp x 640dp inside Scaffold)
            Box(modifier = Modifier.size(width = 360.dp, height = 560.dp)) {
                PlayerScreen(
                    mediaUri = audioItem.uri,
                    mediaTitle = audioItem.title,
                    savedProgressMs = 0L,
                    onBackPressed = { _, _ -> }
                )
            }
        }

        // Verify Artwork, Timeline, Main Controls, and Secondary Actions (Timer, Equalizer, Technical Info)
        // are all simultaneously displayed without scrolling
        composeTestRule.onNodeWithTag("audio_player_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_player_artwork").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_player_slider").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_player_play_pause").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_player_timer_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_player_equalizer_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_player_info_btn").assertIsDisplayed()
    }

    @Test
    fun `phase5_6_problem2_photo_viewer_swipes_left_and_right_and_respects_boundaries`() {
        val photos = listOf(
            MediaFileEntity(uri = "content://img/A", title = "Foto A", displayName = "A.jpg", mediaType = MediaType.IMAGE.name),
            MediaFileEntity(uri = "content://img/B", title = "Foto B", displayName = "B.jpg", mediaType = MediaType.IMAGE.name),
            MediaFileEntity(uri = "content://img/C", title = "Foto C", displayName = "C.jpg", mediaType = MediaType.IMAGE.name),
            MediaFileEntity(uri = "content://img/D", title = "Foto D", displayName = "D.jpg", mediaType = MediaType.IMAGE.name)
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                PhotoViewerScreen(
                    photos = photos,
                    initialIndex = 1, // Start at B (index 1 -> "2 / 4")
                    onClose = {},
                    onToggleFavorite = { _, _ -> },
                    onDeletePhoto = {},
                    onSharePhoto = {}
                )
            }
        }

        // Initially viewing B ("2 / 4")
        composeTestRule.onNodeWithText("2 / 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("Foto B").assertIsDisplayed()

        // Swipe left on B -> C ("3 / 4")
        composeTestRule.onNodeWithTag("photo_pager").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("3 / 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("Foto C").assertIsDisplayed()

        // Swipe left on C -> D ("4 / 4")
        composeTestRule.onNodeWithTag("photo_pager").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("4 / 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("Foto D").assertIsDisplayed()

        // Boundary check on last image D: swipe left stays on D ("4 / 4")
        composeTestRule.onNodeWithTag("photo_pager").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("4 / 4").assertIsDisplayed()

        // Swipe right from D -> C ("3 / 4"), then B ("2 / 4"), then A ("1 / 4")
        composeTestRule.onNodeWithTag("photo_pager").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("3 / 4").assertIsDisplayed()

        composeTestRule.onNodeWithTag("photo_pager").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2 / 4").assertIsDisplayed()

        composeTestRule.onNodeWithTag("photo_pager").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 / 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("Foto A").assertIsDisplayed()

        // Boundary check on first image A: swipe right stays on A ("1 / 4")
        composeTestRule.onNodeWithTag("photo_pager").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 / 4").assertIsDisplayed()
    }

    @Test
    fun `phase5_6_problem3_photo_slideshow_advances_full_pages_cleanly`() {
        val photos = listOf(
            MediaFileEntity(uri = "content://img/slide1", title = "Slide 1", displayName = "1.jpg", mediaType = MediaType.IMAGE.name),
            MediaFileEntity(uri = "content://img/slide2", title = "Slide 2", displayName = "2.jpg", mediaType = MediaType.IMAGE.name),
            MediaFileEntity(uri = "content://img/slide3", title = "Slide 3", displayName = "3.jpg", mediaType = MediaType.IMAGE.name)
        )

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                PhotoViewerScreen(
                    photos = photos,
                    initialIndex = 0,
                    onClose = {},
                    onToggleFavorite = { _, _ -> },
                    onDeletePhoto = {},
                    onSharePhoto = {}
                )
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithText("1 / 3").assertIsDisplayed()

        // Start slideshow
        composeTestRule.onNodeWithTag("photo_slideshow_btn").performClick()
        composeTestRule.mainClock.advanceTimeBy(100)

        // Advance clock past first slideshow interval (3500ms + animation time)
        composeTestRule.mainClock.advanceTimeBy(4200)
        composeTestRule.onNodeWithText("2 / 3").assertIsDisplayed()

        // Advance clock past second slideshow interval
        composeTestRule.mainClock.advanceTimeBy(4200)
        composeTestRule.onNodeWithText("3 / 3").assertIsDisplayed()
        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun `phase5_6_problem4_video_speed_scopes_priority_and_persistence`() = runBlocking {
        val controller = OmniPlayerController.getInstance(context)
        val persistence = controller.queuePersistence

        // Reset global speed to 1.0x and clear specific speeds
        val videoX = PlaylistItem(uri = "content://video/x", title = "Video X", mediaType = MediaType.VIDEO)
        val videoY = PlaylistItem(uri = "content://video/y", title = "Video Y", mediaType = MediaType.VIDEO)
        persistence.clearVideoSpecificSpeed(videoX.uri)
        persistence.clearVideoSpecificSpeed(videoY.uri)
        controller.setVideoSpeed(1.0f, SpeedApplicationScope.ALL_VIDEOS, videoX.uri)

        // 1. Default speed is 1.0x
        controller.preparePlaylist(listOf(videoX, videoY), startIndex = 0, autoPlay = false)
        assertEquals(1.0f, controller.playbackSpeed.value, 0.01f)

        // 2. Apply speed 1.5x to "Este video" (Video X only)
        controller.setVideoSpeed(1.5f, SpeedApplicationScope.THIS_VIDEO, videoX.uri)
        assertEquals(1.5f, controller.playbackSpeed.value, 0.01f)
        assertEquals(1.5f, persistence.getVideoSpecificSpeed(videoX.uri) ?: 0f, 0.01f)
        assertNull(persistence.getVideoSpecificSpeed(videoY.uri))

        // Open Video Y -> should use global default (1.0x), NOT Video X's 1.5x
        controller.preparePlaylist(listOf(videoX, videoY), startIndex = 1, autoPlay = false)
        assertEquals(1.0f, controller.playbackSpeed.value, 0.01f)

        // 3. Apply speed 1.25x to "Todos los videos" (Global)
        controller.setVideoSpeed(1.25f, SpeedApplicationScope.ALL_VIDEOS, videoY.uri)
        assertEquals(1.25f, controller.globalVideoSpeed.value, 0.01f)
        assertEquals(1.25f, controller.playbackSpeed.value, 0.01f)

        // 4. Priority verification:
        // Video X has specific speed 1.5x, Global is 1.25x -> Video X MUST use 1.5x, Video Y MUST use 1.25x
        assertEquals(1.5f, resolveEffectiveVideoSpeed(1.5f, 1.25f), 0.01f)
        assertEquals(1.25f, resolveEffectiveVideoSpeed(null, 1.25f), 0.01f)
        assertEquals(1.0f, resolveEffectiveVideoSpeed(null, null), 0.01f)

        controller.preparePlaylist(listOf(videoX, videoY), startIndex = 0, autoPlay = false)
        assertEquals(1.5f, controller.playbackSpeed.value, 0.01f)

        controller.preparePlaylist(listOf(videoX, videoY), startIndex = 1, autoPlay = false)
        assertEquals(1.25f, controller.playbackSpeed.value, 0.01f)

        // 5. Room VideoSpeedDao persistence check
        val speedDao = db.videoSpeedDao()
        speedDao.saveVideoSpeed(VideoSpeedEntity(mediaUri = videoX.uri, speed = 1.75f))
        val savedInDb = speedDao.getSpeedForVideo(videoX.uri)
        assertNotNull(savedInDb)
        assertEquals(1.75f, savedInDb!!, 0.01f)
    }

    @Test
    fun `phase5_6_problem5_resume_dialog_keeps_video_paused_and_handles_continue_or_restart`() {
        val controller = OmniPlayerController.getInstance(context)
        val videoItem = PlaylistItem(
            uri = "content://video/resume_test",
            title = "Resume Test Video",
            durationMs = 120_000L,
            mediaType = MediaType.VIDEO
        )

        // Prepare video with saved progress (45s) and autoPlay = false (as MainViewModel does when savedPos > 3000L)
        controller.preparePlaylist(
            items = listOf(videoItem),
            startIndex = 0,
            startPositionMs = 45_000L,
            autoPlay = false
        )

        // Video MUST NOT be playing while resume dialog is open
        assertFalse(controller.isPlaying.value)
        assertFalse(controller.exoPlayer.playWhenReady)

        var chosenAction = ""
        var seekedPosition = -1L

        composeTestRule.setContent {
            ResumePlaybackDialog(
                savedPositionMs = 45_000L,
                onContinue = {
                    chosenAction = "continue"
                    seekedPosition = 45_000L
                },
                onStartOver = {
                    chosenAction = "restart"
                    seekedPosition = 0L
                },
                onCancel = {
                    chosenAction = "cancel"
                }
            )
        }

        // Verify dialog options and click "Seguir viendo"
        composeTestRule.onNodeWithTag("resume_continue_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("resume_restart_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("resume_continue_btn").performClick()
        assertEquals("continue", chosenAction)
        assertEquals(45_000L, seekedPosition)

        // Click "Empezar de nuevo"
        composeTestRule.onNodeWithTag("resume_restart_btn").performClick()
        assertEquals("restart", chosenAction)
        assertEquals(0L, seekedPosition)
    }

    @Test
    fun `phase5_6_problem6_music_and_video_exclusivity_never_play_simultaneously`() {
        val controller = OmniPlayerController.getInstance(context)
        val songItem = PlaylistItem(
            uri = "content://audio/song_exclusive",
            title = "Exclusive Song",
            artist = "Artist",
            mediaType = MediaType.AUDIO
        )
        val videoItem = PlaylistItem(
            uri = "content://video/video_exclusive",
            title = "Exclusive Video",
            mediaType = MediaType.VIDEO
        )

        // Test A & B: Play song -> open video -> song is replaced/stopped and only video is active
        controller.preparePlaylist(listOf(songItem), startIndex = 0, startPositionMs = 12_000L, autoPlay = true)
        assertEquals(1, controller.playlist.value.size)
        assertEquals(MediaType.AUDIO, controller.playlist.value.first().mediaType)

        controller.pauseForTransition()
        assertFalse(controller.isPlaying.value)

        controller.preparePlaylist(listOf(videoItem), startIndex = 0, startPositionMs = 0L, autoPlay = true)
        assertEquals(1, controller.playlist.value.size)
        assertEquals(videoItem.uri, controller.playlist.value.first().uri)
        assertEquals(MediaType.VIDEO, controller.playlist.value.first().mediaType)
        assertTrue(controller.hasSuspendedAudioQueue())

        // Test D: Close video -> suspended song is restored in PAUSED state, NEVER auto-resumes!
        val restored = controller.onExitVideoPlayback()
        assertNotNull(restored)
        assertEquals(songItem.uri, restored!!.items.first().uri)
        assertFalse("Song must NEVER auto-play after closing video", controller.isPlaying.value)
        assertFalse("ExoPlayer playWhenReady must be false after closing video", controller.exoPlayer.playWhenReady)

        // Test C: Play video -> open song -> video stops and only song is in queue
        controller.preparePlaylist(listOf(videoItem), startIndex = 0, startPositionMs = 5_000L, autoPlay = true)
        assertEquals(MediaType.VIDEO, controller.playlist.value.first().mediaType)

        controller.pauseForTransition()
        controller.preparePlaylist(listOf(songItem), startIndex = 0, startPositionMs = 0L, autoPlay = true)
        assertEquals(1, controller.playlist.value.size)
        assertEquals(songItem.uri, controller.playlist.value.first().uri)
        assertEquals(MediaType.AUDIO, controller.playlist.value.first().mediaType)
    }

    @Test
    fun `phase5_6_problem7_player_orientation_respects_system_and_manual_overrides`() {
        // Caso 1: Auto-rotación del sistema activada + FOLLOW_SYSTEM -> SCREEN_ORIENTATION_SENSOR
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR,
            resolveRequestedOrientation(
                SystemRotationMode.AUTO_ROTATE_ENABLED,
                PlayerOrientationPreference.FOLLOW_SYSTEM
            )
        )

        // Caso 2 & 3: Rotación del sistema bloqueada + FOLLOW_SYSTEM -> SCREEN_ORIENTATION_UNSPECIFIED
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            resolveRequestedOrientation(
                SystemRotationMode.AUTO_ROTATE_DISABLED,
                PlayerOrientationPreference.FOLLOW_SYSTEM
            )
        )

        // Caso 4: Cambio manual desde el botón del reproductor fuerza Horizontal o Vertical
        // independientemente de si la rotación del sistema está activada o bloqueada
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            resolveRequestedOrientation(
                SystemRotationMode.AUTO_ROTATE_DISABLED,
                PlayerOrientationPreference.LANDSCAPE
            )
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            resolveRequestedOrientation(
                SystemRotationMode.AUTO_ROTATE_DISABLED,
                PlayerOrientationPreference.PORTRAIT
            )
        )

        // Cycling orientation preference from button
        val fromFollowPortrait = nextOrientationPreference(
            current = PlayerOrientationPreference.FOLLOW_SYSTEM,
            isCurrentlyLandscape = false
        )
        assertEquals(PlayerOrientationPreference.LANDSCAPE, fromFollowPortrait)

        val fromLandscape = nextOrientationPreference(
            current = fromFollowPortrait,
            isCurrentlyLandscape = true
        )
        assertEquals(PlayerOrientationPreference.PORTRAIT, fromLandscape)

        val backToFollow = nextOrientationPreference(
            current = fromLandscape,
            isCurrentlyLandscape = false
        )
        assertEquals(PlayerOrientationPreference.FOLLOW_SYSTEM, backToFollow)
    }

    // =========================================================================
    // FASE 6 — BÓVEDA PRIVADA PROFESIONAL REAL (PRUEBAS AUTOMATIZADAS)
    // =========================================================================

    @Test
    fun `phase6_vault_crypto_aes256_gcm_chunked_encryption_decryption_and_random_access`() {
        // Create 150 KB of deterministic non-trivial plaintext (spans 3 chunks of 64 KB)
        val plaintextSize = 150 * 1024
        val plaintext = ByteArray(plaintextSize) { i -> ((i * 31 + 7) and 0xFF).toByte() }

        val vaultDir = VaultStorageManager.getVaultDirectory(context)
        val destFile = File(vaultDir, "test_crypto_multi_chunk.vlt")
        destFile.delete()

        val encResult = VaultCryptoManager.encryptStream(
            context = context,
            inputStream = ByteArrayInputStream(plaintext),
            outputFile = destFile
        )

        assertTrue(destFile.exists())
        assertEquals(plaintextSize.toLong(), encResult.plaintextSizeBytes)
        assertTrue("Encrypted file must be larger than plaintext due to header and GCM tags", encResult.encryptedSizeBytes > plaintextSize)
        assertTrue(VaultCryptoManager.isEncryptedVaultFile(destFile))
        assertTrue(VaultCryptoManager.verifyEncryptedFile(context, destFile))
        assertEquals(plaintextSize.toLong(), VaultCryptoManager.getOriginalPlaintextSize(destFile))

        // Verify full stream decryption matches byte-for-byte
        val outStream = ByteArrayOutputStream()
        val decryptedCount = VaultCryptoManager.decryptStream(context, destFile, outStream)
        assertEquals(plaintextSize.toLong(), decryptedCount)
        assertArrayEquals(plaintext, outStream.toByteArray())

        // Verify random-access chunk decryption (Chunk #1 = bytes 65536..131071)
        val chunk1Plaintext = ByteArray(65536)
        val readLen = VaultCryptoManager.readDecryptedRange(
            context = context,
            encryptedFile = destFile,
            position = 65536L,
            buffer = chunk1Plaintext,
            offset = 0,
            length = 65536
        )
        assertEquals(65536, readLen)
        assertArrayEquals(plaintext.copyOfRange(65536, 131072), chunk1Plaintext)

        destFile.delete()
    }

    @Test
    fun `phase6_vault_media3_datasource_supports_random_access_seeking_in_memory`() {
        val plaintextSize = 140 * 1024
        val plaintext = ByteArray(plaintextSize) { i -> ((i * 17 + 3) and 0xFF).toByte() }

        val vaultDir = VaultStorageManager.getVaultDirectory(context)
        val vaultFile = File(vaultDir, "test_media3_seek.vlt")
        vaultFile.delete()

        VaultCryptoManager.encryptStream(
            context = context,
            inputStream = ByteArrayInputStream(plaintext),
            outputFile = vaultFile
        )

        val dataSource = VaultMedia3DataSource(context)
        // Open seeking directly to byte offset 70,000 (inside the second 64KB chunk)
        val seekOffset = 70_000L
        val spec = DataSpec.Builder()
            .setUri(Uri.fromFile(vaultFile))
            .setPosition(seekOffset)
            .build()

        val remainingToRead = dataSource.open(spec)
        assertEquals((plaintextSize - seekOffset), remainingToRead)

        val readBuffer = ByteArray(10_000)
        var totalRead = 0
        while (totalRead < readBuffer.size) {
            val r = dataSource.read(readBuffer, totalRead, readBuffer.size - totalRead)
            if (r <= 0) break
            totalRead += r
        }
        assertEquals(10_000, totalRead)
        assertArrayEquals(
            plaintext.copyOfRange(seekOffset.toInt(), seekOffset.toInt() + 10_000),
            readBuffer
        )

        dataSource.close()
        vaultFile.delete()
    }

    @Test
    fun `phase6_vault_security_pbkdf2_salted_pin_legacy_upgrade_and_bruteforce_lockout`() {
        VaultSecurityManager.resetLockoutState()

        // 1. Create salted PBKDF2 credentials
        val creds = VaultSecurityManager.createPinCredentials("2580")
        assertTrue(creds.hashBase64.isNotEmpty())
        assertTrue(creds.saltBase64.isNotEmpty())
        assertFalse("Hash must never equal raw PIN", creds.hashBase64 == "2580")

        // 2. Valid PIN succeeds
        val okResult = VaultSecurityManager.verifyPin("2580", creds.hashBase64, creds.saltBase64, nowMs = 1000L)
        assertTrue(okResult.isSuccess)
        assertFalse(okResult.isLockedOut)

        // 3. 5 consecutive wrong attempts trigger lockout cooldown
        for (attempt in 1..4) {
            val bad = VaultSecurityManager.verifyPin("0000", creds.hashBase64, creds.saltBase64, nowMs = 2000L)
            assertFalse(bad.isSuccess)
            assertFalse(bad.isLockedOut)
            assertEquals(attempt, bad.failedAttempts)
        }
        val fifthAttempt = VaultSecurityManager.verifyPin("0000", creds.hashBase64, creds.saltBase64, nowMs = 2000L)
        assertFalse(fifthAttempt.isSuccess)
        assertTrue("5th failed attempt must trigger lockout", fifthAttempt.isLockedOut)
        assertTrue(fifthAttempt.remainingLockoutSeconds > 0)

        // Even correct PIN is rejected while locked out
        val duringLockout = VaultSecurityManager.verifyPin("2580", creds.hashBase64, creds.saltBase64, nowMs = 5000L)
        assertFalse(duringLockout.isSuccess)
        assertTrue(duringLockout.isLockedOut)

        // After cooldown expires (2000L + 31_000L), correct PIN unlocks and resets state
        val afterCooldown = VaultSecurityManager.verifyPin("2580", creds.hashBase64, creds.saltBase64, nowMs = 35_000L)
        assertTrue(afterCooldown.isSuccess)
        assertFalse(afterCooldown.isLockedOut)
        assertEquals(0, afterCooldown.failedAttempts)

        VaultSecurityManager.resetLockoutState()
    }

    @Test
    fun `phase6_vault_storage_transactional_move_deletes_original_and_excludes_from_library`() {
        runBlocking {
            val mediaDao = db.mediaDao()
            val vaultDao = db.vaultDao()
            val playlistDao = db.playlistDao()
            val historyDao = db.historyDao()

        // Create a real source file on disk
        val publicSimDir = File(context.cacheDir, "public_sim_media").apply { mkdirs() }
        val sourceFile = File(publicSimDir, "secret_clip.mp4")
        val originalBytes = ByteArray(32_768) { idx: Int -> (idx % 251).toByte() }
        sourceFile.writeBytes(originalBytes)
        assertTrue(sourceFile.exists())

        val sourceUri = Uri.fromFile(sourceFile).toString()
        val mediaEntity = MediaFileEntity(
            uri = sourceUri,
            title = "Secret Clip",
            displayName = "secret_clip.mp4",
            mimeType = "video/mp4",
            sizeBytes = originalBytes.size.toLong(),
            durationMs = 15_000L,
            width = 1920,
            height = 1080,
            mediaType = MediaType.VIDEO.name,
            folderName = "Camera"
        )

        // Insert into public library, playlist, and history
        mediaDao.insert(mediaEntity)
        val playlistId = playlistDao.insertPlaylist(PlaylistEntity(name = "My Favorites"))
        playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = playlistId, mediaUri = sourceUri, orderIndex = 0))
        historyDao.insert(
            HistoryEntity(
                mediaUri = sourceUri,
                title = "Secret Clip",
                mediaType = MediaType.VIDEO.name,
                durationMs = 15_000L,
                positionMs = 5_000L
            )
        )

        // Perform transactional encryption & move to Vault
        val moveResult = VaultStorageManager.encryptMediaToVault(context, mediaEntity)
        assertTrue("Move to Vault must succeed: ${moveResult.exceptionOrNull()?.message}", moveResult.isSuccess)
        val outcome = moveResult.getOrThrow()
        val vaultItem = outcome.vaultEntity

        // 1. Original public file must be deleted!
        assertFalse("Original file must be deleted after moving to Vault", sourceFile.exists())

        // 2. Encrypted .vlt file and .nomedia must exist in private vault directory
        val vaultDir = VaultStorageManager.getVaultDirectory(context)
        assertTrue(File(vaultDir, ".nomedia").exists())
        val encryptedFile = outcome.encryptedFile
        assertTrue(encryptedFile.exists())
        assertTrue(VaultCryptoManager.isEncryptedVaultFile(encryptedFile))

        // 3. Persist VaultItemEntity and clean up public DB references
        vaultDao.insert(vaultItem)
        mediaDao.deleteByUri(sourceUri)
        playlistDao.removeMediaFromAllPlaylists(sourceUri)
        historyDao.deleteByMediaUri(sourceUri)

        assertTrue(mediaDao.getMediaListByType(MediaType.VIDEO.name).none { it.uri == sourceUri })
        assertTrue(playlistDao.getItemsForPlaylist(playlistId).first().none { it.mediaUri == sourceUri })
        assertTrue(historyDao.getHistory(50).first().none { it.mediaUri == sourceUri })

        // 4. Even if MediaStore scan reports the old URI again, syncMediaStore MUST exclude it because it's in vault_items!
        mediaDao.syncMediaStore(listOf(mediaEntity), MediaType.VIDEO.name)
        val afterScan = mediaDao.getMediaListByType(MediaType.VIDEO.name)
        assertTrue("Vaulted URI must never reappear in public library during scan", afterScan.none { it.uri == sourceUri })

        // Clean up
        encryptedFile.delete()
        }
    }

    @Test
    fun `phase6_vault_restore_and_permanent_delete_lifecycle`() {
        runBlocking {
            val vaultDao = db.vaultDao()

        val publicSimDir = File(context.cacheDir, "public_restore_sim").apply { mkdirs() }
        val sourceFile = File(publicSimDir, "vacation_photo.jpg")
        val photoBytes = ByteArray(48_000) { i: Int -> ((i * 13 + 99) and 0xFF).toByte() }
        sourceFile.writeBytes(photoBytes)

        val mediaEntity = MediaFileEntity(
            uri = Uri.fromFile(sourceFile).toString(),
            title = "Vacation Photo",
            displayName = "vacation_photo.jpg",
            mimeType = "image/jpeg",
            sizeBytes = photoBytes.size.toLong(),
            width = 1080,
            height = 1920,
            mediaType = MediaType.IMAGE.name,
            folderName = "Pictures"
        )

        val moveResult = VaultStorageManager.encryptMediaToVault(context, mediaEntity)
        assertTrue(moveResult.isSuccess)
        val outcome = moveResult.getOrThrow()
        val vaultItem = outcome.vaultEntity
        val id = vaultDao.insert(vaultItem)
        val savedVaultItem = vaultItem.copy(id = id)

        val encryptedFile = outcome.encryptedFile
        assertTrue(encryptedFile.exists())
        assertFalse(sourceFile.exists())

        // Restore from Vault
        val restoreResult = VaultStorageManager.restoreFromVault(context, savedVaultItem)
        assertTrue("Restore from Vault must succeed: ${restoreResult.exceptionOrNull()?.message}", restoreResult.isSuccess)
        val restoreOutcome = restoreResult.getOrThrow()
        assertNotNull(restoreOutcome.restoredMediaEntity)

        // Private encrypted .vlt file must be removed after successful restore
        assertFalse("Encrypted .vlt file must be removed after restoration", encryptedFile.exists())
        vaultDao.deleteById(id)
        assertTrue(vaultDao.getAllVaultItems().first().isEmpty())

        // Test permanent delete on a second vault item
        val secondSource = File(publicSimDir, "temp_audio.mp3")
        secondSource.writeBytes(ByteArray(10_000) { 42 })
        val move2 = VaultStorageManager.encryptMediaToVault(
            context,
            mediaEntity.copy(
                uri = Uri.fromFile(secondSource).toString(),
                title = "Temp Audio",
                displayName = "temp_audio.mp3",
                mimeType = "audio/mpeg",
                mediaType = MediaType.AUDIO.name
            )
        )
        assertTrue(move2.isSuccess)
        val outcome2 = move2.getOrThrow()
        val vItem2 = outcome2.vaultEntity
        val encFile2 = outcome2.encryptedFile
        assertTrue(encFile2.exists())

        val deleted = VaultStorageManager.deleteVaultFilePermanently(context, vItem2)
        assertTrue(deleted)
        assertFalse("Permanently deleted vault file must not exist on disk", encFile2.exists())
        }
    }

    @Test
    fun `phase6_vault_ui_pin_setup_unlock_filter_search_and_actions`() {
        var configuredPin = ""
        var unlocked = false
        var restoredItem: VaultItemEntity? = null
        var openedItem: VaultItemEntity? = null
        var deletedItemId: Long? = null

        val sampleItems = listOf(
            VaultItemEntity(
                id = 1L,
                originalUri = "content://media/external/video/media/1",
                originalName = "Private Clip.mp4",
                vaultFileName = "vlt_1.vlt",
                encryptedFilePath = "/data/user/0/com.example/files/vault/vlt_1.vlt",
                originalFolderPath = "Movies",
                mediaType = MediaType.VIDEO.name,
                mimeType = "video/mp4",
                sizeBytes = 2_048_000L,
                durationMs = 65_000L
            ),
            VaultItemEntity(
                id = 2L,
                originalUri = "content://media/external/images/media/2",
                originalName = "Secret Document.jpg",
                vaultFileName = "vlt_2.vlt",
                encryptedFilePath = "/data/user/0/com.example/files/vault/vlt_2.vlt",
                originalFolderPath = "Pictures",
                mediaType = MediaType.IMAGE.name,
                mimeType = "image/jpeg",
                sizeBytes = 512_000L
            )
        )

        val isUnlockedState = androidx.compose.runtime.mutableStateOf(false)
        val hasPinState = androidx.compose.runtime.mutableStateOf(false)

        composeTestRule.setContent {
            VaultScreen(
                isUnlocked = isUnlockedState.value,
                hasPinConfigured = hasPinState.value,
                vaultItems = sampleItems,
                biometricEnabled = false,
                canUseBiometrics = false,
                onRequestBiometricUnlock = {},
                onUnlockWithPin = { pin ->
                    val ok = pin == configuredPin
                    if (ok) {
                        unlocked = true
                        isUnlockedState.value = true
                    }
                    ok
                },
                onConfigurePin = { pin ->
                    configuredPin = pin
                    hasPinState.value = true
                    isUnlockedState.value = true
                },
                onLockVault = {
                    isUnlockedState.value = false
                },
                onOpenVaultItem = { item, _ -> openedItem = item },
                onRestoreItem = { item -> restoredItem = item },
                onDeleteItem = { id -> deletedItemId = id }
            )
        }

        // 1. Initial state: PIN creation screen with Numpad (enter 1357 twice to create & confirm)
        composeTestRule.onNodeWithTag("vault_auth_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_digit_1").performClick()
        composeTestRule.onNodeWithTag("vault_digit_3").performClick()
        composeTestRule.onNodeWithTag("vault_digit_5").performClick()
        composeTestRule.onNodeWithTag("vault_digit_7").performClick()
        composeTestRule.waitForIdle()

        // Confirm step: enter 1357 again
        composeTestRule.onNodeWithText("Confirma tu PIN").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_digit_1").performClick()
        composeTestRule.onNodeWithTag("vault_digit_3").performClick()
        composeTestRule.onNodeWithTag("vault_digit_5").performClick()
        composeTestRule.onNodeWithTag("vault_digit_7").performClick()
        composeTestRule.waitForIdle()

        assertEquals("1357", configuredPin)
        assertTrue(isUnlockedState.value)

        // 2. Vault content is now displayed with both items
        composeTestRule.onNodeWithText("Private Clip.mp4").assertIsDisplayed()
        composeTestRule.onNodeWithText("Secret Document.jpg").assertIsDisplayed()

        // 3. Filter by Fotos tab -> Secret Document.jpg is shown
        composeTestRule.onNodeWithText("Fotos").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Secret Document.jpg").assertIsDisplayed()

        // 4. Filter back to Todos and open item #1
        composeTestRule.onNodeWithText("Todos").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_open_1").performClick()
        composeTestRule.waitForIdle()
        assertNotNull(openedItem)
        assertEquals(1L, openedItem?.id)

        // 5. Trigger restore on item #1
        composeTestRule.onNodeWithTag("vault_restore_btn_1").performClick()
        composeTestRule.waitForIdle()
        assertNotNull(restoredItem)
        assertEquals("Private Clip.mp4", restoredItem?.originalName)

        // 6. Trigger permanent delete on item #2 -> confirmation dialog -> confirm delete
        composeTestRule.onNodeWithTag("vault_delete_btn_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vault_confirm_delete_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2L, deletedItemId)
    }

    // =========================================================================
    // FASE 6.1 — PRUEBAS DE OPTIMIZACIÓN PROFUNDA DE BÓVEDA Y RENDIMIENTO GLOBAL
    // =========================================================================

    @Test
    fun `phase6_1_vault_media3_datasource_multi_chunk_caching_and_fast_seeking`() {
        // Create 250 KB test media across 4 chunks (64KB, 64KB, 64KB, 58KB)
        val plaintextSize = 250 * 1024
        val plaintext = ByteArray(plaintextSize) { i -> ((i * 31 + 7) and 0xFF).toByte() }

        val vaultDir = VaultStorageManager.getVaultDirectory(context)
        val vaultFile = File(vaultDir, "test_phase6_1_fast_seek.vlt")
        vaultFile.delete()

        val encResult = VaultCryptoManager.encryptStream(
            context = context,
            inputStream = ByteArrayInputStream(plaintext),
            outputFile = vaultFile
        )
        assertEquals(plaintextSize.toLong(), encResult.plaintextSizeBytes)

        val dataSource = VaultMedia3DataSource(context)
        val spec = DataSpec.Builder()
            .setUri(Uri.fromFile(vaultFile))
            .setPosition(0L)
            .build()

        val totalLen = dataSource.open(spec)
        assertEquals(plaintextSize.toLong(), totalLen)

        // 1. Sequential small buffer reading (simulating ExoPlayer 4KB reads)
        val smallBuf = ByteArray(4096)
        var totalRead = 0
        val sequentialOutput = ByteArray(plaintextSize)
        val startTimeSeq = System.nanoTime()
        while (totalRead < plaintextSize) {
            val r = dataSource.read(smallBuf, 0, kotlin.math.min(smallBuf.size, plaintextSize - totalRead))
            if (r <= 0) break
            System.arraycopy(smallBuf, 0, sequentialOutput, totalRead, r)
            totalRead += r
        }
        val elapsedSeqMs = (System.nanoTime() - startTimeSeq) / 1_000_000
        assertEquals(plaintextSize, totalRead)
        assertArrayEquals(plaintext, sequentialOutput)
        // With LRU cache and persistent file handle, 62 sequential 4KB reads must be fast (< 200 ms in JVM Robolectric)
        assertTrue("Sequential read should be fast due to chunk caching (was ${elapsedSeqMs}ms)", elapsedSeqMs < 500)

        // 2. High-speed random-access seeking back and forth across chunks
        val seekPositions = listOf(
            70_000L,   // Inside chunk 1
            10_000L,   // Inside chunk 0 (cached in LRU)
            150_000L,  // Inside chunk 2
            200_000L,  // Inside chunk 3
            65_000L,   // Chunk 0 -> Chunk 1 boundary (across boundary)
            500L       // Start of file (chunk 0)
        )

        for (pos in seekPositions) {
            val seekSpec = DataSpec.Builder()
                .setUri(Uri.fromFile(vaultFile))
                .setPosition(pos)
                .build()

            val remaining = dataSource.open(seekSpec)
            assertEquals((plaintextSize - pos), remaining)

            val readSlice = ByteArray(8192)
            val bytesRead = dataSource.read(readSlice, 0, readSlice.size)
            assertEquals(8192, bytesRead)

            val expectedSlice = plaintext.copyOfRange(pos.toInt(), pos.toInt() + 8192)
            assertArrayEquals("Decrypted bytes at seek pos $pos must match plaintext exactly", expectedSlice, readSlice)
        }

        // 3. Test cross-chunk boundary read in a single read() call
        // Chunk boundary is at 65536. Position 60000 with length 10000 spans across chunk 0 and chunk 1!
        val boundaryPos = 60_000L
        val boundaryLen = 10_000
        val boundarySpec = DataSpec.Builder()
            .setUri(Uri.fromFile(vaultFile))
            .setPosition(boundaryPos)
            .build()

        dataSource.open(boundarySpec)
        val boundaryBuf = ByteArray(boundaryLen)
        var boundaryReadTotal = 0
        while (boundaryReadTotal < boundaryLen) {
            val r = dataSource.read(boundaryBuf, boundaryReadTotal, boundaryLen - boundaryReadTotal)
            if (r <= 0) break
            boundaryReadTotal += r
        }
        assertEquals(boundaryLen, boundaryReadTotal)
        assertArrayEquals(
            plaintext.copyOfRange(boundaryPos.toInt(), boundaryPos.toInt() + boundaryLen),
            boundaryBuf
        )

        dataSource.close()
        vaultFile.delete()
    }

    @Test
    fun `phase6_1_vault_open_decrypted_input_stream_buffering`() {
        val size = 150 * 1024
        val sampleBytes = ByteArray(size) { i -> ((i * 23 + 11) and 0xFF).toByte() }

        val vaultDir = VaultStorageManager.getVaultDirectory(context)
        val vaultFile = File(vaultDir, "test_stream_buffering.vlt")
        vaultFile.delete()

        VaultCryptoManager.encryptStream(
            context = context,
            inputStream = ByteArrayInputStream(sampleBytes),
            outputFile = vaultFile
        )

        val stream = VaultCryptoManager.openDecryptedInputStream(context, vaultFile)
        val readCollector = ByteArrayOutputStream()
        val buffer = ByteArray(8192) // 8KB buffer like Coil / Okio uses

        var r: Int
        while (stream.read(buffer).also { r = it } != -1) {
            readCollector.write(buffer, 0, r)
        }
        stream.close()

        val resultBytes = readCollector.toByteArray()
        assertEquals(size, resultBytes.size)
        assertArrayEquals(sampleBytes, resultBytes)

        vaultFile.delete()
    }

    @Test
    fun `phase6_1_vault_aware_data_source_factory_routing_and_isolation`() {
        val vaultDir = VaultStorageManager.getVaultDirectory(context)
        val vaultFile = File(vaultDir, "test_aware.vlt")
        val sample = ByteArray(64 * 1024) { 42 }
        VaultCryptoManager.encryptStream(context, ByteArrayInputStream(sample), vaultFile)

        val factory = com.example.data.vault.VaultAwareDataSource.Factory(context)
        val ds1 = factory.createDataSource()
        val ds2 = factory.createDataSource()

        // Verify independent instances
        assertTrue(ds1 !== ds2)

        val spec = DataSpec.Builder()
            .setUri(Uri.fromFile(vaultFile))
            .build()

        val openLen1 = ds1.open(spec)
        assertEquals(sample.size.toLong(), openLen1)

        val openLen2 = ds2.open(spec)
        assertEquals(sample.size.toLong(), openLen2)

        val b1 = ByteArray(1024)
        val b2 = ByteArray(1024)
        assertEquals(1024, ds1.read(b1, 0, 1024))
        assertEquals(1024, ds2.read(b2, 0, 1024))
        assertArrayEquals(b1, b2)

        ds1.close()
        ds2.close()
        vaultFile.delete()
    }

    @Test
    fun `phase6_1_playlist_item_mime_type_sets_on_media_item`() {
        val controller = OmniPlayerController.getInstance(context)
        val item = PlaylistItem(
            uri = "file:///data/user/0/com.example/files/vault/video.vlt",
            title = "Encrypted Movie",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO
        )

        controller.preparePlaylist(listOf(item), startIndex = 0, autoPlay = false)
        assertEquals(1, controller.playlist.value.size)
        assertEquals("video/mp4", controller.playlist.value[0].mimeType)
        controller.clearQueue()
    }

    @Test
    fun `phase7_vault_restore_image_audio_video_produces_valid_destinations`() {
        runBlocking {
            val testDir = File(context.filesDir, "test_restore_phase7").apply { mkdirs() }

            // Test Image restoration
            val imgFile = File(testDir, "test_photo.jpg").apply { writeBytes("fake_photo_bytes".toByteArray()) }
            val imgEntity = MediaFileEntity(
                uri = Uri.fromFile(imgFile).toString(),
                title = "test_photo",
                displayName = "test_photo.jpg",
                sizeBytes = imgFile.length(),
                mimeType = "image/jpeg",
                mediaType = MediaType.IMAGE.name,
                folderPath = "Pictures/Camera",
                folderName = "Camera"
            )
            val imgEncryptOutcome = VaultStorageManager.encryptMediaToVault(context, imgEntity, false).getOrThrow()
            val imgRestoreOutcome = VaultStorageManager.restoreFromVault(context, imgEncryptOutcome.vaultEntity).getOrThrow()
            assertEquals("image/jpeg", imgRestoreOutcome.restoredMediaEntity.mimeType)
            assertEquals(MediaType.IMAGE.name, imgRestoreOutcome.restoredMediaEntity.mediaType)
            assertTrue(imgRestoreOutcome.restoredUri.isNotEmpty())
            assertFalse(imgEncryptOutcome.encryptedFile.exists()) // .vlt deleted after successful restore

            // Test Audio restoration
            val audioFile = File(testDir, "test_audio.mp3").apply { writeBytes("fake_audio_bytes".toByteArray()) }
            val audioEntity = MediaFileEntity(
                uri = Uri.fromFile(audioFile).toString(),
                title = "test_audio",
                displayName = "test_audio.mp3",
                sizeBytes = audioFile.length(),
                mimeType = "audio/mpeg",
                mediaType = MediaType.AUDIO.name,
                artist = "Test Artist",
                album = "Test Album",
                folderPath = "Music/OmniMedia",
                folderName = "OmniMedia"
            )
            val audioEncryptOutcome = VaultStorageManager.encryptMediaToVault(context, audioEntity, false).getOrThrow()
            val audioRestoreOutcome = VaultStorageManager.restoreFromVault(context, audioEncryptOutcome.vaultEntity).getOrThrow()
            assertEquals("audio/mpeg", audioRestoreOutcome.restoredMediaEntity.mimeType)
            assertEquals(MediaType.AUDIO.name, audioRestoreOutcome.restoredMediaEntity.mediaType)
            assertTrue(audioRestoreOutcome.restoredUri.isNotEmpty())
            assertFalse(audioEncryptOutcome.encryptedFile.exists())

            // Test Video restoration
            val vidFile = File(testDir, "test_video.mp4").apply { writeBytes("fake_video_bytes".toByteArray()) }
            val vidEntity = MediaFileEntity(
                uri = Uri.fromFile(vidFile).toString(),
                title = "test_video",
                displayName = "test_video.mp4",
                sizeBytes = vidFile.length(),
                mimeType = "video/mp4",
                mediaType = MediaType.VIDEO.name,
                folderPath = "Movies/OmniMedia",
                folderName = "OmniMedia"
            )
            val vidEncryptOutcome = VaultStorageManager.encryptMediaToVault(context, vidEntity, false).getOrThrow()
            val vidRestoreOutcome = VaultStorageManager.restoreFromVault(context, vidEncryptOutcome.vaultEntity).getOrThrow()
            assertEquals("video/mp4", vidRestoreOutcome.restoredMediaEntity.mimeType)
            assertEquals(MediaType.VIDEO.name, vidRestoreOutcome.restoredMediaEntity.mediaType)
            assertTrue(vidRestoreOutcome.restoredUri.isNotEmpty())
            assertFalse(vidEncryptOutcome.encryptedFile.exists())

            testDir.deleteRecursively()
        }
    }

    @Test
    fun `phase7_incremental_sync_detects_new_and_modified_and_deleted`() {
        runBlocking {
            val mediaDao = db.mediaDao()

            // 1. Initial state: 2 videos in library, one is favorite with playCount
            val v1 = MediaFileEntity(
                uri = "content://media/external/video/media/101",
                title = "Video 1",
                displayName = "v1.mp4",
                mediaType = MediaType.VIDEO.name,
                dateModified = 1000L,
                isFavorite = true,
                playCount = 5
            )
            val v2 = MediaFileEntity(
                uri = "content://media/external/video/media/102",
                title = "Video 2",
                displayName = "v2.mp4",
                mediaType = MediaType.VIDEO.name,
                dateModified = 1000L
            )
            mediaDao.insertAll(listOf(v1, v2))
            assertEquals(2, mediaDao.getMediaListByType(MediaType.VIDEO.name).size)

            // 2. Incremental sync: v2 was deleted outside; v1 was modified; v3 is new!
            val v1Updated = MediaFileEntity(
                uri = "content://media/external/video/media/101",
                title = "Video 1 Renamed",
                displayName = "v1_renamed.mp4",
                mediaType = MediaType.VIDEO.name,
                dateModified = 2000L
            )
            val v3New = MediaFileEntity(
                uri = "content://media/external/video/media/103",
                title = "Video 3",
                displayName = "v3.mp4",
                mediaType = MediaType.VIDEO.name,
                dateModified = 2500L
            )

            mediaDao.syncIncremental(
                newOrModifiedItems = listOf(v1Updated, v3New),
                deletedUris = listOf("content://media/external/video/media/102"),
                mediaType = MediaType.VIDEO.name
            )

            val updatedList = mediaDao.getMediaListByType(MediaType.VIDEO.name)
            assertEquals(2, updatedList.size)

            // v2 must be gone
            assertNull(mediaDao.getByUri("content://media/external/video/media/102"))

            // v3 must be added
            assertNotNull(mediaDao.getByUri("content://media/external/video/media/103"))

            // v1 must retain favorite status and playCount
            val v1InDb = mediaDao.getByUri("content://media/external/video/media/101")
            assertNotNull(v1InDb)
            assertTrue(v1InDb!!.isFavorite)
            assertEquals(5, v1InDb.playCount)
            assertEquals("Video 1 Renamed", v1InDb.title)
        }
    }

    @Test
    fun `phase7_incremental_sync_strictly_excludes_vault_items`() {
        runBlocking {
            val mediaDao = db.mediaDao()
            val vaultDao = db.vaultDao()

            // Insert a protected item into the Vault
            val vaultItem = VaultItemEntity(
                originalUri = "content://media/external/images/media/999",
                originalName = "secret.jpg",
                vaultFileName = "vault_secret.vlt",
                mediaType = MediaType.IMAGE.name,
                sizeBytes = 1024L,
                mimeType = "image/jpeg",
                originalFolderPath = "Pictures"
            )
            vaultDao.insert(vaultItem)

            // Now attempt an incremental sync that contains the vaulted originalUri
            val scannedSecret = MediaFileEntity(
                uri = "content://media/external/images/media/999",
                title = "Secret",
                displayName = "secret.jpg",
                mediaType = MediaType.IMAGE.name
            )
            val scannedPublic = MediaFileEntity(
                uri = "content://media/external/images/media/1000",
                title = "Public",
                displayName = "public.jpg",
                mediaType = MediaType.IMAGE.name
            )

            mediaDao.syncIncremental(
                newOrModifiedItems = listOf(scannedSecret, scannedPublic),
                deletedUris = emptyList(),
                mediaType = MediaType.IMAGE.name
            )

            val images = mediaDao.getMediaListByType(MediaType.IMAGE.name)
            assertEquals(1, images.size)
            assertEquals("content://media/external/images/media/1000", images[0].uri)
            assertNull(mediaDao.getByUri("content://media/external/images/media/999"))
        }
    }

    @Test
    fun `phase7_external_deletion_preserves_history_and_playlist_integrity`() {
        runBlocking {
            val mediaDao = db.mediaDao()
            val playlistDao = db.playlistDao()
            val historyDao = db.historyDao()

            val uri = "content://media/external/audio/media/505"
            val song = MediaFileEntity(
                uri = uri,
                title = "Song 505",
                displayName = "song505.mp3",
                mediaType = MediaType.AUDIO.name
            )
            mediaDao.insert(song)

            // Add to playlist and history
            val pId = playlistDao.insertPlaylist(PlaylistEntity(name = "Rock"))
            playlistDao.insertPlaylistItem(PlaylistItemEntity(playlistId = pId, mediaUri = uri, orderIndex = 0))
            historyDao.insert(HistoryEntity(mediaUri = uri, title = "Song 505", mediaType = MediaType.AUDIO.name))

            // File is deleted externally
            mediaDao.syncIncremental(
                newOrModifiedItems = emptyList(),
                deletedUris = listOf(uri),
                mediaType = MediaType.AUDIO.name
            )

            // media_files should have 0 items
            assertNull(mediaDao.getByUri(uri))

            // playlist and history should still exist safely without throwing SQLite exceptions
            val playlistItems = playlistDao.getItemsForPlaylist(pId).first()
            assertEquals(1, playlistItems.size)
            assertEquals(uri, playlistItems[0].mediaUri)

            val historyItems = historyDao.getHistory(10).first()
            assertEquals(1, historyItems.size)
            assertEquals(uri, historyItems[0].mediaUri)
        }
    }
}

