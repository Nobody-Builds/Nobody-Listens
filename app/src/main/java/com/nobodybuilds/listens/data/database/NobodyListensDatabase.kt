package com.nobodybuilds.listens.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AlbumArtThemeEntity::class,
        SearchHistoryEntity::class,
        SongEntity::class,
        SongSearchFtsEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        TransitionRuleEntity::class,
        SongArtistCrossRef::class,
        TelegramSongEntity::class,
        TelegramChannelEntity::class,
        SongEngagementEntity::class,
        FavoritesEntity::class,
        LyricsEntity::class,
        NeteaseSongEntity::class,
        NeteasePlaylistEntity::class,
        GDriveSongEntity::class,
        GDriveFolderEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        QqMusicSongEntity::class,
        QqMusicPlaylistEntity::class,
        NavidromeSongEntity::class,
        NavidromePlaylistEntity::class,
        TelegramTopicEntity::class,
        JellyfinSongEntity::class,
        JellyfinPlaylistEntity::class,
        AiCacheEntity::class,
        AiUsageEntity::class,
        SongFeaturesEntity::class
    ],
    version = 41,
    exportSchema = true
)
abstract class NobodyListensDatabase : RoomDatabase() {
    abstract fun albumArtThemeDao(): AlbumArtThemeDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun musicDao(): MusicDao
    abstract fun transitionDao(): TransitionDao
    abstract fun telegramDao(): TelegramDao
    abstract fun engagementDao(): EngagementDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun neteaseDao(): NeteaseDao
    abstract fun gdriveDao(): GDriveDao
    abstract fun localPlaylistDao(): LocalPlaylistDao
    abstract fun qqmusicDao(): QqMusicDao
    abstract fun navidromeDao(): NavidromeDao
    abstract fun jellyfinDao(): JellyfinDao
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun aiUsageDao(): AiUsageDao
    abstract fun songFeaturesDao(): SongFeaturesDao

    companion object {
        // Migrations live in NobodyListensMigrationsV3ToV19 / NobodyListensMigrationsV19ToV41;
        // these names are what the database builder and the migration tests use.
        val MIGRATION_3_4 = NobodyListensMigrationsV3ToV19.MIGRATION_3_4
        val MIGRATION_4_5 = NobodyListensMigrationsV3ToV19.MIGRATION_4_5
        val MIGRATION_5_6 = NobodyListensMigrationsV3ToV19.MIGRATION_5_6
        val MIGRATION_6_7 = NobodyListensMigrationsV3ToV19.MIGRATION_6_7
        val MIGRATION_7_8 = NobodyListensMigrationsV3ToV19.MIGRATION_7_8
        val MIGRATION_8_9 = NobodyListensMigrationsV3ToV19.MIGRATION_8_9
        val MIGRATION_9_10 = NobodyListensMigrationsV3ToV19.MIGRATION_9_10
        val MIGRATION_10_11 = NobodyListensMigrationsV3ToV19.MIGRATION_10_11
        val MIGRATION_11_12 = NobodyListensMigrationsV3ToV19.MIGRATION_11_12
        val MIGRATION_12_13 = NobodyListensMigrationsV3ToV19.MIGRATION_12_13
        val MIGRATION_13_14 = NobodyListensMigrationsV3ToV19.MIGRATION_13_14
        val MIGRATION_14_15 = NobodyListensMigrationsV3ToV19.MIGRATION_14_15
        val MIGRATION_15_16 = NobodyListensMigrationsV3ToV19.MIGRATION_15_16
        val MIGRATION_16_17 = NobodyListensMigrationsV3ToV19.MIGRATION_16_17
        val MIGRATION_17_18 = NobodyListensMigrationsV3ToV19.MIGRATION_17_18
        val MIGRATION_18_19 = NobodyListensMigrationsV3ToV19.MIGRATION_18_19
        val MIGRATION_19_20 = NobodyListensMigrationsV19ToV41.MIGRATION_19_20
        val MIGRATION_20_21 = NobodyListensMigrationsV19ToV41.MIGRATION_20_21
        val MIGRATION_21_22 = NobodyListensMigrationsV19ToV41.MIGRATION_21_22
        val MIGRATION_22_23 = NobodyListensMigrationsV19ToV41.MIGRATION_22_23
        val MIGRATION_23_24 = NobodyListensMigrationsV19ToV41.MIGRATION_23_24
        val MIGRATION_24_25 = NobodyListensMigrationsV19ToV41.MIGRATION_24_25
        val MIGRATION_25_26 = NobodyListensMigrationsV19ToV41.MIGRATION_25_26
        val MIGRATION_26_27 = NobodyListensMigrationsV19ToV41.MIGRATION_26_27
        val MIGRATION_27_28 = NobodyListensMigrationsV19ToV41.MIGRATION_27_28
        val MIGRATION_28_29 = NobodyListensMigrationsV19ToV41.MIGRATION_28_29
        val MIGRATION_29_30 = NobodyListensMigrationsV19ToV41.MIGRATION_29_30
        val MIGRATION_30_31 = NobodyListensMigrationsV19ToV41.MIGRATION_30_31
        val MIGRATION_31_32 = NobodyListensMigrationsV19ToV41.MIGRATION_31_32
        val MIGRATION_32_33 = NobodyListensMigrationsV19ToV41.MIGRATION_32_33
        val MIGRATION_33_34 = NobodyListensMigrationsV19ToV41.MIGRATION_33_34
        val MIGRATION_34_35 = NobodyListensMigrationsV19ToV41.MIGRATION_34_35
        val MIGRATION_35_36 = NobodyListensMigrationsV19ToV41.MIGRATION_35_36
        val MIGRATION_36_37 = NobodyListensMigrationsV19ToV41.MIGRATION_36_37
        val MIGRATION_37_38 = NobodyListensMigrationsV19ToV41.MIGRATION_37_38
        val MIGRATION_38_39 = NobodyListensMigrationsV19ToV41.MIGRATION_38_39
        val MIGRATION_39_40 = NobodyListensMigrationsV19ToV41.MIGRATION_39_40
        val MIGRATION_40_41 = NobodyListensMigrationsV19ToV41.MIGRATION_40_41

        fun createRuntimeArtifactsCallback(): RoomDatabase.Callback {
            return object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    installFavoriteSyncTriggers(db)
                    installSongsSearchSyncTriggers(db)
                }
            }
        }
    }
}
