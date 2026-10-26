package com.nobodybuilds.listens.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

// Songs-table repair and schema introspection helpers used by the migrations.

internal fun ensureSongsTableHasDateAdded(db: SupportSQLiteDatabase) {
    if (!tableExists(db, "songs")) {
        recreateSongsTable(db)
        return
    }

    if ("date_added" in getTableColumns(db, "songs")) {
        return
    }

    try {
        db.execSQL("ALTER TABLE songs ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
    } catch (_: Exception) {
        // Some restored databases report the right version but still carry
        // a drifted songs table. If ALTER TABLE did not stick, rebuild it.
    }

    if ("date_added" !in getTableColumns(db, "songs")) {
        recreateSongsTable(db)
    }
}

internal fun recreateSongsTable(db: SupportSQLiteDatabase) {
    val songsTableExists = tableExists(db, "songs")
    val columns = if (songsTableExists) getTableColumns(db, "songs") else emptySet()

    db.execSQL("DROP TABLE IF EXISTS songs_new")
    db.execSQL(
        """
            CREATE TABLE songs_new (
                id INTEGER NOT NULL,
                title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                artist_id INTEGER NOT NULL,
                album_artist TEXT,
                album_name TEXT NOT NULL,
                album_id INTEGER NOT NULL,
                content_uri_string TEXT NOT NULL,
                album_art_uri_string TEXT,
                duration INTEGER NOT NULL,
                genre TEXT,
                file_path TEXT NOT NULL,
                parent_directory_path TEXT NOT NULL,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                lyrics TEXT DEFAULT null,
                track_number INTEGER NOT NULL DEFAULT 0,
                disc_number INTEGER DEFAULT null,
                year INTEGER NOT NULL DEFAULT 0,
                date_added INTEGER NOT NULL DEFAULT 0,
                mime_type TEXT,
                bitrate INTEGER,
                sample_rate INTEGER,
                telegram_chat_id INTEGER,
                telegram_file_id INTEGER,
                PRIMARY KEY(id),
                FOREIGN KEY(album_id) REFERENCES albums(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(artist_id) REFERENCES artists(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent()
    )

    val requiredColumns = setOf(
        "id",
        "title",
        "artist_name",
        "artist_id",
        "album_name",
        "album_id",
        "content_uri_string",
        "duration",
        "file_path"
    )

    // If the restored table still has the core song columns, preserve rows.
    // Otherwise prefer a clean empty table over another migration-time crash.
    if (songsTableExists && requiredColumns.all(columns::contains)) {
        val albumArtistExpr = columnExpr(columns, "album_artist", "NULL")
        val albumArtUriExpr = columnExpr(columns, "album_art_uri_string", "NULL")
        val genreExpr = columnExpr(columns, "genre", "NULL")
        val parentDirectoryPathExpr = columnExpr(columns, "parent_directory_path", "''")
        val isFavoriteExpr = columnExpr(columns, "is_favorite", "0")
        val lyricsExpr = columnExpr(columns, "lyrics", "NULL")
        val trackNumberExpr = columnExpr(columns, "track_number", "0")
        val discNumberExpr = columnExpr(columns, "disc_number", "NULL")
        val yearExpr = columnExpr(columns, "year", "0")
        val dateAddedExpr = columnExpr(columns, "date_added", "0")
        val mimeTypeExpr = columnExpr(columns, "mime_type", "NULL")
        val bitrateExpr = columnExpr(columns, "bitrate", "NULL")
        val sampleRateExpr = columnExpr(columns, "sample_rate", "NULL")
        val telegramChatIdExpr = columnExpr(columns, "telegram_chat_id", "NULL")
        val telegramFileIdExpr = columnExpr(columns, "telegram_file_id", "NULL")

        db.execSQL(
            """
                INSERT OR REPLACE INTO songs_new (
                    id,
                    title,
                    artist_name,
                    artist_id,
                    album_artist,
                    album_name,
                    album_id,
                    content_uri_string,
                    album_art_uri_string,
                    duration,
                    genre,
                    file_path,
                    parent_directory_path,
                    is_favorite,
                    lyrics,
                    track_number,
                    disc_number,
                    year,
                    date_added,
                    mime_type,
                    bitrate,
                    sample_rate,
                    telegram_chat_id,
                    telegram_file_id
                )
                SELECT
                    id,
                    title,
                    artist_name,
                    artist_id,
                    $albumArtistExpr,
                    album_name,
                    album_id,
                    content_uri_string,
                    $albumArtUriExpr,
                    duration,
                    $genreExpr,
                    file_path,
                    $parentDirectoryPathExpr,
                    $isFavoriteExpr,
                    $lyricsExpr,
                    $trackNumberExpr,
                    $discNumberExpr,
                    $yearExpr,
                    $dateAddedExpr,
                    $mimeTypeExpr,
                    $bitrateExpr,
                    $sampleRateExpr,
                    $telegramChatIdExpr,
                    $telegramFileIdExpr
                FROM songs
                WHERE id IS NOT NULL
                  AND title IS NOT NULL
                  AND artist_name IS NOT NULL
                  AND artist_id IS NOT NULL
                  AND album_name IS NOT NULL
                  AND album_id IS NOT NULL
                  AND content_uri_string IS NOT NULL
                  AND duration IS NOT NULL
                  AND file_path IS NOT NULL
            """.trimIndent()
        )
    }

    if (songsTableExists) {
        db.execSQL("DROP TABLE songs")
    }

    db.execSQL("ALTER TABLE songs_new RENAME TO songs")
    createSongsEntityIndexes(db)
}

internal fun createSongsEntityIndexes(db: SupportSQLiteDatabase) {
    val columns = getTableColumns(db, "songs")

    fun createIndexIfColumnExists(columnName: String, indexName: String) {
        if (columnName in columns) {
            db.execSQL("CREATE INDEX IF NOT EXISTS $indexName ON songs($columnName)")
        }
    }

    fun createCompositeIndexIfColumnsExist(indexName: String, vararg columnNames: String) {
        if (columnNames.all(columns::contains)) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS $indexName ON songs(${columnNames.joinToString(", ")})"
            )
        }
    }

    createIndexIfColumnExists("title", "index_songs_title")
    createIndexIfColumnExists("album_id", "index_songs_album_id")
    createIndexIfColumnExists("artist_id", "index_songs_artist_id")
    createIndexIfColumnExists("artist_name", "index_songs_artist_name")
    createIndexIfColumnExists("genre", "index_songs_genre")
    createIndexIfColumnExists("parent_directory_path", "index_songs_parent_directory_path")
    createIndexIfColumnExists("file_path", "index_songs_file_path")
    createIndexIfColumnExists("content_uri_string", "index_songs_content_uri_string")
    createIndexIfColumnExists("date_added", "index_songs_date_added")
    createIndexIfColumnExists("duration", "index_songs_duration")
    createIndexIfColumnExists("source_type", "index_songs_source_type")
    createCompositeIndexIfColumnsExist(
        "index_songs_parent_directory_path_source_type_album_id",
        "parent_directory_path",
        "source_type",
        "album_id"
    )
    createCompositeIndexIfColumnsExist(
        "index_songs_parent_directory_path_source_type_id",
        "parent_directory_path",
        "source_type",
        "id"
    )
}

internal fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
    db.query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
        arrayOf(tableName)
    ).use { cursor ->
        return cursor.moveToFirst()
    }
}

internal fun getTableColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> {
    val columns = mutableSetOf<String>()
    db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        if (nameIndex == -1) return columns
        while (cursor.moveToNext()) {
            columns += cursor.getString(nameIndex)
        }
    }
    return columns
}

internal fun getTableColumnDefaultValue(
    db: SupportSQLiteDatabase,
    tableName: String,
    columnName: String
): String? {
    db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")
        val defaultValueIndex = cursor.getColumnIndex("dflt_value")
        if (nameIndex == -1 || defaultValueIndex == -1) return null

        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == columnName) {
                return cursor.getString(defaultValueIndex)
            }
        }
    }
    return null
}

internal fun ensureSongsTableHasDiscNumber(db: SupportSQLiteDatabase) {
    if (!tableExists(db, "songs")) {
        recreateSongsTable(db)
        return
    }

    val columns = getTableColumns(db, "songs")
    if ("disc_number" !in columns) {
        try {
            db.execSQL("ALTER TABLE songs ADD COLUMN disc_number INTEGER DEFAULT null")
        } catch (_: Exception) {
            // Restored/drifted databases may already contain a partially applied column.
        }
    }

    val refreshedColumns = getTableColumns(db, "songs")
    val discNumberDefault = getTableColumnDefaultValue(db, "songs", "disc_number")

    if ("disc_number" !in refreshedColumns || !discNumberDefault.equals("null", ignoreCase = true)) {
        recreateSongsTable(db)
    }
}

internal fun columnExpr(columns: Set<String>, columnName: String, fallbackExpr: String): String {
    return if (columnName in columns) {
        "COALESCE($columnName, $fallbackExpr)"
    } else {
        fallbackExpr
    }
}
