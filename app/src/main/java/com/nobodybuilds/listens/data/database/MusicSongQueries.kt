package com.nobodybuilds.listens.data.database

import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Song list, lookup and Library-filter queries. Part of [MusicDao]. */
interface MusicSongQueries {

    // --- Directory Helper ---
    @Query("SELECT DISTINCT parent_directory_path FROM songs")
    suspend fun getDistinctParentDirectories(): List<String>

    // --- Song Queries ---
    // Updated getSongs to include Telegram songs (negative IDs) regardless of directory filter
    @Query("SELECT " + SONG_LIST_PROJECTION + """
        FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY title ASC
    """)
    fun getSongs(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("SELECT " + SONG_LIST_PROJECTION + " FROM songs WHERE id IN (:songIds)")
    suspend fun getSongsByIdsListSimple(songIds: List<Long>): List<SongEntity>

    /** Fills in a streamed song's length once it is known, without touching a real one. */
    @Query("UPDATE songs SET duration = :duration WHERE id = :id AND duration <= 0")
    suspend fun fillMissingDuration(id: Long, duration: Long)

    /** Refreshes a streamed song's display metadata, leaving likes, lyrics and dates alone. */
    @Query(
        """
        UPDATE songs SET title = :title, artist_name = :artistName, artist_id = :artistId,
            album_name = :albumName, album_id = :albumId, album_art_uri_string = :albumArtUri,
            duration = :duration
        WHERE id = :id
        """
    )
    suspend fun updateStreamedSongMetadata(
        id: Long,
        title: String,
        artistName: String,
        artistId: Long,
        albumName: String,
        albumId: Long,
        albumArtUri: String?,
        duration: Long,
    )

    /**
     * Resolves the unified-table song id for a given content URI. Used when the
     * currently-playing song was loaded from a non-unified source (e.g. raw Telegram
     * repository Songs whose ids are "chatId_messageId" strings) and we need the
     * matching negative-Long id to position the song inside the library list.
     */
    @Query("SELECT id FROM songs WHERE content_uri_string = :contentUri LIMIT 1")
    suspend fun getSongIdByContentUri(contentUri: String): Long?

    @Query(
        "SELECT " + SONG_DETAIL_PROJECTION + """
        FROM songs
        LEFT JOIN lyrics AS song_lyrics ON song_lyrics.songId = songs.id
        WHERE songs.id = :songId
        """
    )
    fun getSongById(songId: Long): Flow<SongEntity?>

    @Query(
        "SELECT " + SONG_DETAIL_PROJECTION + """
        FROM songs
        LEFT JOIN lyrics AS song_lyrics ON song_lyrics.songId = songs.id
        WHERE songs.id = :songId
        """
    )
    suspend fun getSongByIdOnce(songId: Long): SongEntity?

    @Query(
        "SELECT " + SONG_DETAIL_PROJECTION + """
        FROM songs
        LEFT JOIN lyrics AS song_lyrics ON song_lyrics.songId = songs.id
        WHERE songs.file_path = :path
        LIMIT 1
        """
    )
    suspend fun getSongByPath(path: String): SongEntity?

    @Query("SELECT " + SONG_LIST_PROJECTION + """
        FROM songs
        WHERE id IN (:songIds)
        AND (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getSongsByIds(
        songIds: List<Long>,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album_id = :albumId ORDER BY disc_number ASC, track_number ASC")
    fun getSongsByAlbumId(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist_id = :artistId ORDER BY title ASC")
    fun getSongsByArtistId(artistId: Long): Flow<List<SongEntity>>

    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN songs_fts ON songs_fts.rowid = songs.id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND songs_fts MATCH :matchQuery
        ORDER BY songs.title ASC
    """)
    fun searchSongsMatch(
        matchQuery: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (title LIKE '%' || :query || '%' OR artist_name LIKE '%' || :query || '%')
        ORDER BY title ASC
    """)
    fun searchSongsLike(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    fun searchSongs(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>> {
        val ftsFlow = searchSongsMatch(
            matchQuery = buildSongSearchMatchQuery(query),
            allowedParentDirs = allowedParentDirs,
            applyDirectoryFilter = applyDirectoryFilter
        )
        val likeFlow = searchSongsLike(
            query = query.trim(),
            allowedParentDirs = allowedParentDirs,
            applyDirectoryFilter = applyDirectoryFilter
        )
        return ftsFlow.combine(likeFlow) { ftsResults, likeResults ->
            val seen = LinkedHashMap<Long, SongEntity>(ftsResults.size + likeResults.size)
            ftsResults.forEach { seen.putIfAbsent(it.id, it) }
            likeResults.forEach { seen.putIfAbsent(it.id, it) }
            seen.values.toList()
        }
    }

    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCountOnce(): Int

    @Query("""
        SELECT
            file_path AS filePath,
            content_uri_string AS contentUriString,
            mime_type AS mimeType,
            duration,
            bitrate,
            sample_rate AS sampleRate,
            source_type AS sourceType
        FROM songs
    """)
    suspend fun getDeviceCapabilitySongRows(): List<DeviceCapabilitySongRow>

    /**
     * Returns random songs for efficient shuffle without loading all songs into memory.
     * Uses SQLite RANDOM() for true randomness.
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getRandomSongs(
        limit: Int,
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): List<SongEntity>

    /**
     * Random songs restricted to the Library source filter. Same predicate as the Library song
     * list ([getSongsPaginated] / [getSongIdsSorted]): filterMode 0 = all, 1 = local
     * (`source_type = 0`), 2 = cloud (`source_type != 0`).
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (:filterMode = 1 AND source_type = 0)
            OR (:filterMode = 2 AND source_type != 0)
            OR (:filterMode = 3 AND source_type = 0 AND (parent_directory_path LIKE '%/Nobody Listens/YouTube Music' OR parent_directory_path LIKE '%/Nobody Listens/JioSaavn'))
        )
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getRandomSongsFiltered(
        limit: Int,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int
    ): List<SongEntity>

    /** Every song id matching the Library source filter, in random order (ids only, cheap for 10k+ rows). */
    @Query("""
        SELECT id FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (:filterMode = 1 AND source_type = 0)
            OR (:filterMode = 2 AND source_type != 0)
            OR (:filterMode = 3 AND source_type = 0 AND (parent_directory_path LIKE '%/Nobody Listens/YouTube Music' OR parent_directory_path LIKE '%/Nobody Listens/JioSaavn'))
        )
        ORDER BY RANDOM()
    """)
    suspend fun getShuffledSongIdsFiltered(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int
    ): List<Long>

    @Query("""
        SELECT """ + SONG_LIST_PROJECTION + """
        FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY title COLLATE NOCASE ASC, artist_name COLLATE NOCASE ASC, id ASC
        LIMIT 1
    """)
    suspend fun getFirstPlayableSong(
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): SongEntity?

    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getAllSongs(
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): Flow<List<SongEntity>>

    @Query("""
        SELECT """ + SONG_LIST_PROJECTION + """
        FROM songs
        WHERE id IN (
            SELECT MIN(id)
            FROM songs
            WHERE album_art_uri_string IS NOT NULL
            AND album_art_uri_string != ''
            AND (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
            GROUP BY album_art_uri_string
        )
        ORDER BY title COLLATE NOCASE ASC, artist_name COLLATE NOCASE ASC, id ASC
    """)
    fun getDistinctAlbumArtSongs(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("""
        SELECT """ + SONG_LIST_PROJECTION + """
        FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY date_added DESC, id DESC
        LIMIT :limit
    """)
    fun getHomeMixPreviewSongs(
        limit: Int,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("""
        SELECT id, parent_directory_path, title, album_art_uri_string FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (
                :filterMode = 1
                AND source_type = 0
            )
            OR (
                :filterMode = 2
                AND source_type != 0
            )
            OR (:filterMode = 3 AND source_type = 0 AND (parent_directory_path LIKE '%/Nobody Listens/YouTube Music' OR parent_directory_path LIKE '%/Nobody Listens/JioSaavn'))
        )
        ORDER BY parent_directory_path ASC, title ASC
    """)
    fun getFolderSongs(
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false,
        filterMode: Int
    ): Flow<List<FolderSongRow>>

    @Query("""
        SELECT id FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (
                :filterMode = 1
                AND source_type = 0
            )
            OR (
                :filterMode = 2
                AND source_type != 0
            )
            OR (:filterMode = 3 AND source_type = 0 AND (parent_directory_path LIKE '%/Nobody Listens/YouTube Music' OR parent_directory_path LIKE '%/Nobody Listens/JioSaavn'))
        )
        ORDER BY
            CASE WHEN :sortOrder = 'song_default_order' THEN track_number END ASC,
            CASE WHEN :sortOrder = 'song_title_az' THEN title END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'song_title_za' THEN title END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'song_artist' THEN artist_name END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'song_artist_desc' THEN artist_name END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'song_album' THEN album_name END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'song_album_desc' THEN album_name END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'song_date_added' THEN date_added END DESC,
            CASE WHEN :sortOrder = 'song_date_added_asc' THEN date_added END ASC,
            CASE WHEN :sortOrder = 'song_duration' THEN duration END DESC,
            CASE WHEN :sortOrder = 'song_duration_asc' THEN duration END ASC,
            title COLLATE NOCASE ASC,
            id ASC
    """)
    suspend fun getSongIdsSorted(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        sortOrder: String,
        filterMode: Int
    ): List<Long>

    @Query("""
        SELECT songs.id FROM songs
        INNER JOIN favorites ON songs.id = favorites.songId AND favorites.isFavorite = 1
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND (
            :filterMode = 0
            OR (
                :filterMode = 1
                AND songs.source_type = 0
            )
            OR (
                :filterMode = 2
                AND songs.source_type != 0
            )
            OR (:filterMode = 3 AND songs.source_type = 0 AND (songs.parent_directory_path LIKE '%/Nobody Listens/YouTube Music' OR songs.parent_directory_path LIKE '%/Nobody Listens/JioSaavn'))
        )
        ORDER BY
            CASE WHEN :sortOrder = 'liked_title_az' THEN songs.title END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'liked_title_za' THEN songs.title END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'liked_artist' THEN songs.artist_name END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'liked_artist_desc' THEN songs.artist_name END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'liked_album' THEN songs.album_name END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'liked_album_desc' THEN songs.album_name END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'liked_date_liked' THEN favorites.timestamp END DESC,
            CASE WHEN :sortOrder = 'liked_date_liked_asc' THEN favorites.timestamp END ASC,
            songs.title COLLATE NOCASE ASC,
            songs.id ASC
    """)
    suspend fun getFavoriteSongIdsSorted(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        sortOrder: String,
        filterMode: Int
    ): List<Long>
}
