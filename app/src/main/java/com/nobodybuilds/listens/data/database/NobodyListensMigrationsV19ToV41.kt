package com.nobodybuilds.listens.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema migrations from version 19 to 41. Exposed through [NobodyListensDatabase]'s companion. */
internal object NobodyListensMigrationsV19ToV41 {
    /**
     * Reconcile Telegram tables: drop and recreate to match current entity definitions.
     * Telegram data is re-syncable cache, so this is safe.
     */
    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Drop existing Telegram tables that may have schema drift
            db.execSQL("DROP TABLE IF EXISTS telegram_songs")
            db.execSQL("DROP TABLE IF EXISTS telegram_channels")

            // Recreate telegram_songs matching TelegramSongEntity exactly
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS telegram_songs (
                    id TEXT NOT NULL PRIMARY KEY,
                    chat_id INTEGER NOT NULL,
                    message_id INTEGER NOT NULL,
                    file_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    duration INTEGER NOT NULL,
                    file_path TEXT NOT NULL,
                    mime_type TEXT NOT NULL,
                    date_added INTEGER NOT NULL,
                    album_art_uri TEXT
                )
            """.trimIndent())

            // Recreate telegram_channels matching TelegramChannelEntity exactly
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS telegram_channels (
                    chat_id INTEGER NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    username TEXT,
                    song_count INTEGER NOT NULL DEFAULT 0,
                    last_sync_time INTEGER NOT NULL DEFAULT 0,
                    photo_path TEXT
                )
            """.trimIndent())
        }
    }

    /**
     * Add Netease Cloud Music tables.
     */
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS netease_songs (
                    id TEXT NOT NULL PRIMARY KEY,
                    netease_id INTEGER NOT NULL,
                    playlist_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT NOT NULL,
                    album_id INTEGER NOT NULL,
                    duration INTEGER NOT NULL,
                    album_art_url TEXT,
                    mime_type TEXT NOT NULL,
                    bitrate INTEGER,
                    date_added INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS netease_playlists (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    cover_url TEXT,
                    song_count INTEGER NOT NULL,
                    last_sync_time INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    /**
     * Add Google Drive tables.
     */
    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS gdrive_songs (
                    id TEXT NOT NULL PRIMARY KEY,
                    drive_file_id TEXT NOT NULL,
                    folder_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT NOT NULL,
                    album_id INTEGER NOT NULL,
                    duration INTEGER NOT NULL,
                    album_art_url TEXT,
                    mime_type TEXT NOT NULL,
                    bitrate INTEGER,
                    file_size INTEGER NOT NULL,
                    date_added INTEGER NOT NULL,
                    date_modified INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS gdrive_folders (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    song_count INTEGER NOT NULL DEFAULT 0,
                    last_sync_time INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }
    }

    /**
     * Add custom_image_uri column to artists table.
     * Allows users to associate a custom image with each artist.
     * Nullable with DEFAULT NULL so this migration is safe and additive.
     */
    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE artists ADD COLUMN custom_image_uri TEXT DEFAULT NULL")
        }
    }

    /**
     * Add missing indexes for frequently filtered and sorted queries.
     *
     * Safety: the `date_added` column may be absent on databases that were
     * created before it was part of the songs schema and later restored via
     * Android auto-backup, so we repair the table defensively before indexing.
     */
    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureSongsTableHasDateAdded(db)
            createSongsEntityIndexes(db)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_favorites_timestamp ON favorites(timestamp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_song_engagements_play_count ON song_engagements(play_count)")
        }
    }

    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Lyrics are already persisted in the dedicated lyrics table. Keeping a duplicate
            // copy in songs rows makes broad SELECTs vulnerable to CursorWindow overflows.
            db.execSQL("UPDATE songs SET lyrics = NULL WHERE lyrics IS NOT NULL AND lyrics != ''")
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Cloud/source tables: add query indexes.
            db.execSQL("CREATE INDEX IF NOT EXISTS index_telegram_songs_chat_id ON telegram_songs(chat_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_telegram_songs_message_id ON telegram_songs(message_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_telegram_songs_file_id ON telegram_songs(file_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_telegram_songs_chat_id_message_id ON telegram_songs(chat_id, message_id)")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_netease_songs_netease_id ON netease_songs(netease_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_netease_songs_playlist_id ON netease_songs(playlist_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_netease_songs_playlist_id_date_added ON netease_songs(playlist_id, date_added)")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_gdrive_songs_drive_file_id ON gdrive_songs(drive_file_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_gdrive_songs_folder_id ON gdrive_songs(folder_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_gdrive_songs_folder_id_date_added ON gdrive_songs(folder_id, date_added)")

            db.execSQL("CREATE INDEX IF NOT EXISTS index_album_art_themes_albumArtUriString_paletteStyle ON album_art_themes(albumArtUriString, paletteStyle)")

            // favorites table is the source of truth; keep songs.is_favorite mirrored by trigger.
            db.execSQL(
                """
                    UPDATE songs
                    SET is_favorite = CASE
                        WHEN id IN (SELECT songId FROM favorites WHERE isFavorite = 1) THEN 1
                        ELSE 0
                    END
                """.trimIndent()
            )
            installFavoriteSyncTriggers(db)

            recreatePlaylistsTable(db)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_last_modified ON playlists(last_modified)")

            recreatePlaylistSongsTable(db)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_playlist_id_sort_order ON playlist_songs(playlist_id, sort_order)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_song_id ON playlist_songs(song_id)")
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            recreatePlaylistsTable(db)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlists_last_modified ON playlists(last_modified)")

            recreatePlaylistSongsTable(db)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_playlist_id_sort_order ON playlist_songs(playlist_id, sort_order)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_songs_song_id ON playlist_songs(song_id)")
            installFavoriteSyncTriggers(db)
        }
    }
    
    val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS ai_cache (
                    promptHash TEXT NOT NULL PRIMARY KEY,
                    responseJson TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `jellyfin_songs` (
                    `id` TEXT NOT NULL,
                    `jellyfin_id` TEXT NOT NULL,
                    `playlist_id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `artist_id` TEXT,
                    `album` TEXT NOT NULL,
                    `album_id` TEXT,
                    `duration` INTEGER NOT NULL,
                    `track_number` INTEGER NOT NULL,
                    `disc_number` INTEGER NOT NULL,
                    `year` INTEGER NOT NULL,
                    `genre` TEXT,
                    `bitRate` INTEGER,
                    `mime_type` TEXT,
                    `path` TEXT NOT NULL,
                    `date_added` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_jellyfin_songs_jellyfin_id` ON `jellyfin_songs` (`jellyfin_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_jellyfin_songs_playlist_id` ON `jellyfin_songs` (`playlist_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_jellyfin_songs_playlist_id_date_added` ON `jellyfin_songs` (`playlist_id`, `date_added`)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `jellyfin_playlists` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `song_count` INTEGER NOT NULL,
                    `duration` INTEGER NOT NULL,
                    `last_sync_time` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
        }
    }

    val MIGRATION_37_38 = object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS ai_usage (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    provider TEXT NOT NULL,
                    model TEXT NOT NULL,
                    promptType TEXT NOT NULL,
                    promptTokens INTEGER NOT NULL,
                    outputTokens INTEGER NOT NULL,
                    thoughtTokens INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    val MIGRATION_38_39 = object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_file_path ON songs(file_path)")
        }
    }

    val MIGRATION_39_40 = object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_songs_parent_directory_path_source_type_album_id " +
                    "ON songs(parent_directory_path, source_type, album_id)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_songs_parent_directory_path_source_type_id " +
                    "ON songs(parent_directory_path, source_type, id)"
            )
        }
    }

    /** Smart features (experimental): on-device audio features per song. */
    val MIGRATION_40_41 = object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_features` (" +
                    "`song_id` TEXT NOT NULL, `model_version` INTEGER NOT NULL, " +
                    "`analyzed_at` INTEGER NOT NULL, `scores` BLOB, `energy` REAL NOT NULL, " +
                    "`energy_var` REAL NOT NULL, `tempo` REAL NOT NULL, `brightness` REAL NOT NULL, " +
                    "`mood_tags` TEXT NOT NULL, `genre_tags` TEXT NOT NULL, `status` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`song_id`))"
            )
        }
    }

    /**
     * Add QQ Music support tables.
     */
    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS qqmusic_playlists (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    cover_url TEXT,
                    song_count INTEGER NOT NULL,
                    last_sync_time INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS qqmusic_songs (
                    id TEXT NOT NULL PRIMARY KEY,
                    song_mid TEXT NOT NULL,
                    playlist_id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT NOT NULL,
                    album_mid TEXT,
                    duration INTEGER NOT NULL,
                    album_art_url TEXT,
                    mime_type TEXT NOT NULL,
                    bitrate INTEGER,
                    date_added INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    /**
     * Add Navidrome/Subsonic support tables.
     */
    val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS navidrome_playlists (
                    id TEXT NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    comment TEXT,
                    owner TEXT,
                    cover_art_id TEXT,
                    song_count INTEGER NOT NULL,
                    duration INTEGER NOT NULL,
                    public INTEGER NOT NULL,
                    last_sync_time INTEGER NOT NULL
                )
            """.trimIndent())

            recreateNavidromeSongsTable(db)
        }
    }

    /**
     * Reconcile older Navidrome caches that were created with playlist_id stored as INTEGER.
     */
    val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            recreateNavidromeSongsTable(db)
        }
    }

    /**
     * Add disc_number to songs table.
     */
    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureSongsTableHasDiscNumber(db)
        }
    }

    val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Add thread_id column to telegram_songs
            db.execSQL("ALTER TABLE telegram_songs ADD COLUMN thread_id INTEGER DEFAULT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_telegram_songs_thread_id ON telegram_songs(thread_id)")

            // 2. Create telegram_topics table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS telegram_topics (
                    id TEXT NOT NULL PRIMARY KEY,
                    chat_id INTEGER NOT NULL,
                    thread_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    song_count INTEGER NOT NULL DEFAULT 0,
                    last_sync_time INTEGER NOT NULL DEFAULT 0,
                    icon_emoji TEXT
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_telegram_topics_chat_id ON telegram_topics(chat_id)")
        }
    }

    val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if ("date_added" !in getTableColumns(db, "albums")) {
                db.execSQL("ALTER TABLE albums ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
            }

            db.execSQL(
                """
                    UPDATE albums
                    SET date_added = COALESCE(
                        (
                            SELECT MAX(songs.date_added)
                            FROM songs
                            WHERE songs.album_id = albums.id
                        ),
                        0
                    )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN artists_json TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN source_type INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_source_type ON songs(source_type)")
            // Backfill source_type from content_uri_string for existing rows
            db.execSQL("UPDATE songs SET source_type = 1 WHERE content_uri_string LIKE 'telegram://%'")
            db.execSQL("UPDATE songs SET source_type = 2 WHERE content_uri_string LIKE 'netease://%'")
            db.execSQL("UPDATE songs SET source_type = 3 WHERE content_uri_string LIKE 'gdrive://%'")
            db.execSQL("UPDATE songs SET source_type = 4 WHERE content_uri_string LIKE 'qqmusic://%'")
            db.execSQL("UPDATE songs SET source_type = 5 WHERE content_uri_string LIKE 'navidrome://%'")
        }
    }

    val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            createSongsSearchVirtualTable(db)
            installSongsSearchSyncTriggers(db)
            rebuildSongsSearchIndex(db)
        }
    }
}
