package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.HistoryDao
import com.example.data.local.dao.MediaDao
import com.example.data.local.dao.PlaybackProgressDao
import com.example.data.local.dao.PlaylistDao
import com.example.data.local.dao.SettingsDao
import com.example.data.local.dao.VaultDao
import com.example.data.local.dao.VideoSpeedDao
import com.example.data.local.entity.HistoryEntity
import com.example.data.local.entity.MediaFileEntity
import com.example.data.local.entity.PlaybackProgressEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistItemEntity
import com.example.data.local.entity.UserSettingsEntity
import com.example.data.local.entity.VaultItemEntity
import com.example.data.local.entity.VideoSpeedEntity

@Database(
    entities = [
        MediaFileEntity::class,
        PlaybackProgressEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        HistoryEntity::class,
        VaultItemEntity::class,
        UserSettingsEntity::class,
        VideoSpeedEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun vaultDao(): VaultDao
    abstract fun settingsDao(): SettingsDao
    abstract fun videoSpeedDao(): VideoSpeedDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vault_items ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vault_items ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vault_items ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE vault_items ADD COLUMN artist TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vault_items ADD COLUMN album TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vault_items ADD COLUMN encryptedFilePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vault_items ADD COLUMN encryptionIv TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vault_items ADD COLUMN status TEXT NOT NULL DEFAULT 'COMPLETED'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vault_items_originalUri ON vault_items(originalUri)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_files_mediaType_inTrash_dateAdded ON media_files(mediaType, inTrash, dateAdded)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_files_isFavorite_inTrash_dateAdded ON media_files(isFavorite, inTrash, dateAdded)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_media_files_inTrash_lastPlayedTimestamp ON media_files(inTrash, lastPlayedTimestamp)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omnimedia.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
