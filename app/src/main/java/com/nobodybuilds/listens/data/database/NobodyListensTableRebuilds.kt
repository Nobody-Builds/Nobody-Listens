package com.nobodybuilds.listens.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

// Rebuilds of the playlist and Navidrome tables used by the migrations.

internal fun recreatePlaylistsTable(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS playlists_new")
    db.execSQL(
        """
            CREATE TABLE playlists_new (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                last_modified INTEGER NOT NULL,
                is_ai_generated INTEGER NOT NULL,
                is_queue_generated INTEGER NOT NULL,
                cover_image_uri TEXT,
                cover_color_argb INTEGER,
                cover_icon_name TEXT,
                cover_shape_type TEXT,
                cover_shape_detail_1 REAL,
                cover_shape_detail_2 REAL,
                cover_shape_detail_3 REAL,
                cover_shape_detail_4 REAL,
                source TEXT NOT NULL
            )
        """.trimIndent()
    )

    if (tableExists(db, "playlists")) {
        val columns = getTableColumns(db, "playlists")
        if ("id" in columns && "name" in columns) {
            val nowMs = "(CAST(strftime('%s','now') AS INTEGER) * 1000)"
            val createdAtExpr = columnExpr(columns, "created_at", nowMs)
            val lastModifiedExpr = columnExpr(columns, "last_modified", createdAtExpr)
            val isAiGeneratedExpr = columnExpr(columns, "is_ai_generated", "0")
            val isQueueGeneratedExpr = columnExpr(columns, "is_queue_generated", "0")
            val coverImageUriExpr = columnExpr(columns, "cover_image_uri", "NULL")
            val coverColorArgbExpr = columnExpr(columns, "cover_color_argb", "NULL")
            val coverIconNameExpr = columnExpr(columns, "cover_icon_name", "NULL")
            val coverShapeTypeExpr = columnExpr(columns, "cover_shape_type", "NULL")
            val coverShapeDetail1Expr = columnExpr(columns, "cover_shape_detail_1", "NULL")
            val coverShapeDetail2Expr = columnExpr(columns, "cover_shape_detail_2", "NULL")
            val coverShapeDetail3Expr = columnExpr(columns, "cover_shape_detail_3", "NULL")
            val coverShapeDetail4Expr = columnExpr(columns, "cover_shape_detail_4", "NULL")
            val sourceExpr = columnExpr(columns, "source", "'LOCAL'")

            db.execSQL(
                """
                    INSERT OR REPLACE INTO playlists_new (
                        id,
                        name,
                        created_at,
                        last_modified,
                        is_ai_generated,
                        is_queue_generated,
                        cover_image_uri,
                        cover_color_argb,
                        cover_icon_name,
                        cover_shape_type,
                        cover_shape_detail_1,
                        cover_shape_detail_2,
                        cover_shape_detail_3,
                        cover_shape_detail_4,
                        source
                    )
                    SELECT
                        id,
                        name,
                        $createdAtExpr,
                        $lastModifiedExpr,
                        $isAiGeneratedExpr,
                        $isQueueGeneratedExpr,
                        $coverImageUriExpr,
                        $coverColorArgbExpr,
                        $coverIconNameExpr,
                        $coverShapeTypeExpr,
                        $coverShapeDetail1Expr,
                        $coverShapeDetail2Expr,
                        $coverShapeDetail3Expr,
                        $coverShapeDetail4Expr,
                        $sourceExpr
                    FROM playlists
                    WHERE id IS NOT NULL AND name IS NOT NULL
                """.trimIndent()
            )
        }
        db.execSQL("DROP TABLE playlists")
    }

    db.execSQL("ALTER TABLE playlists_new RENAME TO playlists")
}

internal fun recreatePlaylistSongsTable(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS playlist_songs_new")
    db.execSQL(
        """
            CREATE TABLE playlist_songs_new (
                playlist_id TEXT NOT NULL,
                song_id TEXT NOT NULL,
                sort_order INTEGER NOT NULL,
                PRIMARY KEY(playlist_id, song_id)
            )
        """.trimIndent()
    )

    if (tableExists(db, "playlist_songs")) {
        val columns = getTableColumns(db, "playlist_songs")
        if ("playlist_id" in columns && "song_id" in columns) {
            val sortOrderExpr = columnExpr(columns, "sort_order", "0")
            db.execSQL(
                """
                    INSERT OR REPLACE INTO playlist_songs_new (
                        playlist_id,
                        song_id,
                        sort_order
                    )
                    SELECT
                        playlist_id,
                        song_id,
                        $sortOrderExpr
                    FROM playlist_songs
                    WHERE playlist_id IS NOT NULL AND song_id IS NOT NULL
                """.trimIndent()
            )
        }
        db.execSQL("DROP TABLE playlist_songs")
    }

    db.execSQL("ALTER TABLE playlist_songs_new RENAME TO playlist_songs")
}

internal fun recreateNavidromeSongsTable(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TABLE IF EXISTS navidrome_songs_new")
    db.execSQL(
        """
            CREATE TABLE navidrome_songs_new (
                id TEXT NOT NULL PRIMARY KEY,
                navidrome_id TEXT NOT NULL,
                playlist_id TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                artist_id TEXT,
                album TEXT NOT NULL,
                album_id TEXT,
                cover_art_id TEXT,
                duration INTEGER NOT NULL,
                track_number INTEGER NOT NULL,
                disc_number INTEGER,
                year INTEGER NOT NULL,
                genre TEXT,
                bitRate INTEGER,
                mime_type TEXT,
                suffix TEXT,
                path TEXT NOT NULL,
                date_added INTEGER NOT NULL
            )
        """.trimIndent()
    )

    if (tableExists(db, "navidrome_songs")) {
        val columns = getTableColumns(db, "navidrome_songs")
        val requiredColumns = setOf(
            "id",
            "navidrome_id",
            "playlist_id",
            "title",
            "artist",
            "album",
            "duration",
            "track_number",
            "disc_number",
            "year",
            "path",
            "date_added"
        )

        if (requiredColumns.all(columns::contains)) {
            val artistIdExpr = columnExpr(columns, "artist_id", "NULL")
            val albumIdExpr = columnExpr(columns, "album_id", "NULL")
            val coverArtIdExpr = columnExpr(columns, "cover_art_id", "NULL")
            val genreExpr = columnExpr(columns, "genre", "NULL")
            val bitRateExpr = columnExpr(columns, "bitRate", "NULL")
            val mimeTypeExpr = columnExpr(columns, "mime_type", "NULL")
            val suffixExpr = columnExpr(columns, "suffix", "NULL")

            db.execSQL(
                """
                    INSERT OR REPLACE INTO navidrome_songs_new (
                        id,
                        navidrome_id,
                        playlist_id,
                        title,
                        artist,
                        artist_id,
                        album,
                        album_id,
                        cover_art_id,
                        duration,
                        track_number,
                        disc_number,
                        year,
                        genre,
                        bitRate,
                        mime_type,
                        suffix,
                        path,
                        date_added
                    )
                    SELECT
                        id,
                        navidrome_id,
                        CAST(playlist_id AS TEXT),
                        title,
                        artist,
                        $artistIdExpr,
                        album,
                        $albumIdExpr,
                        $coverArtIdExpr,
                        duration,
                        track_number,
                        disc_number,
                        year,
                        $genreExpr,
                        $bitRateExpr,
                        $mimeTypeExpr,
                        $suffixExpr,
                        path,
                        date_added
                    FROM navidrome_songs
                    WHERE id IS NOT NULL
                      AND navidrome_id IS NOT NULL
                      AND playlist_id IS NOT NULL
                      AND title IS NOT NULL
                      AND artist IS NOT NULL
                      AND album IS NOT NULL
                      AND path IS NOT NULL
                """.trimIndent()
            )
        }

        db.execSQL("DROP TABLE navidrome_songs")
    }

    db.execSQL("ALTER TABLE navidrome_songs_new RENAME TO navidrome_songs")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_navidrome_songs_navidrome_id ON navidrome_songs(navidrome_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_navidrome_songs_playlist_id ON navidrome_songs(playlist_id)")
    db.execSQL("CREATE INDEX IF NOT EXISTS index_navidrome_songs_playlist_id_date_added ON navidrome_songs(playlist_id, date_added)")
}
