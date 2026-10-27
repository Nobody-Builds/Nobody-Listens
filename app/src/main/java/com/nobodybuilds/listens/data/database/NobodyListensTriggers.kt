package com.nobodybuilds.listens.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

// Favourite-sync triggers and the songs_fts search table.

internal fun installFavoriteSyncTriggers(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TRIGGER IF EXISTS trg_favorites_insert_sync_song")
    db.execSQL("DROP TRIGGER IF EXISTS trg_favorites_update_sync_song")
    db.execSQL("DROP TRIGGER IF EXISTS trg_favorites_delete_sync_song")

    db.execSQL(
        """
            CREATE TRIGGER IF NOT EXISTS trg_favorites_insert_sync_song
            AFTER INSERT ON favorites
            BEGIN
                UPDATE songs SET is_favorite = NEW.isFavorite WHERE id = NEW.songId;
            END
        """.trimIndent()
    )

    db.execSQL(
        """
            CREATE TRIGGER IF NOT EXISTS trg_favorites_update_sync_song
            AFTER UPDATE ON favorites
            BEGIN
                UPDATE songs SET is_favorite = NEW.isFavorite WHERE id = NEW.songId;
            END
        """.trimIndent()
    )

    db.execSQL(
        """
            CREATE TRIGGER IF NOT EXISTS trg_favorites_delete_sync_song
            AFTER DELETE ON favorites
            BEGIN
                UPDATE songs SET is_favorite = 0 WHERE id = OLD.songId;
            END
        """.trimIndent()
    )
}

internal fun createSongsSearchVirtualTable(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
            CREATE VIRTUAL TABLE IF NOT EXISTS songs_fts
            USING fts4(
                title,
                artist_name,
                tokenize=unicode61
            )
        """.trimIndent()
    )
}

internal fun installSongsSearchSyncTriggers(db: SupportSQLiteDatabase) {
    createSongsSearchVirtualTable(db)

    db.execSQL("DROP TRIGGER IF EXISTS trg_songs_fts_insert")
    db.execSQL("DROP TRIGGER IF EXISTS trg_songs_fts_update")
    db.execSQL("DROP TRIGGER IF EXISTS trg_songs_fts_delete")

    db.execSQL(
        """
            CREATE TRIGGER IF NOT EXISTS trg_songs_fts_insert
            AFTER INSERT ON songs
            BEGIN
                INSERT INTO songs_fts(rowid, title, artist_name)
                VALUES (NEW.id, NEW.title, NEW.artist_name);
            END
        """.trimIndent()
    )

    db.execSQL(
        """
            CREATE TRIGGER IF NOT EXISTS trg_songs_fts_update
            AFTER UPDATE ON songs
            BEGIN
                DELETE FROM songs_fts WHERE rowid = OLD.id;
                INSERT INTO songs_fts(rowid, title, artist_name)
                VALUES (NEW.id, NEW.title, NEW.artist_name);
            END
        """.trimIndent()
    )

    db.execSQL(
        """
            CREATE TRIGGER IF NOT EXISTS trg_songs_fts_delete
            AFTER DELETE ON songs
            BEGIN
                DELETE FROM songs_fts WHERE rowid = OLD.id;
            END
        """.trimIndent()
    )
}

internal fun rebuildSongsSearchIndex(db: SupportSQLiteDatabase) {
    db.execSQL("DELETE FROM songs_fts")
    db.execSQL(
        """
            INSERT INTO songs_fts(rowid, title, artist_name)
            SELECT id, title, artist_name
            FROM songs
        """.trimIndent()
    )
}
