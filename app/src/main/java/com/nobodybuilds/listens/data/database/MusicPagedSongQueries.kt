package com.nobodybuilds.listens.data.database

import androidx.paging.PagingSource
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Paged song, favourite, search and genre queries. Part of [MusicDao]. */
interface MusicPagedSongQueries {

    // --- Paginated Queries for Large Libraries ---
    /**
     * Returns a PagingSource for songs, enabling efficient pagination for large libraries.
     * Room auto-generates the PagingSource implementation.
     */
    @Query("""
        SELECT * FROM songs
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
            OR (:filterMode = 3 AND source_type = 0 AND parent_directory_path LIKE '%/Nobody Listens/YouTube Music')
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

            -- Secondary sort falls back to title for consistency (case-insensitive)
            title COLLATE NOCASE ASC,
            id ASC
    """)
    fun getSongsPaginated(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        sortOrder: String,
        filterMode: Int
    ): PagingSource<Int, SongEntity>

    @Query("""
        SELECT """ + SONG_LIST_PROJECTION + """
        FROM songs
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
            OR (:filterMode = 3 AND source_type = 0 AND parent_directory_path LIKE '%/Nobody Listens/YouTube Music')
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
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getSongsPage(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        sortOrder: String,
        filterMode: Int,
        limit: Int,
        offset: Int
    ): List<SongEntity>

    // --- Paginated Favorites Queries ---
    /**
     * Returns a PagingSource for favorite songs, enabling efficient pagination.
     * Joins songs with favorites table and supports multi-sort.
     */
    @Query("""
        SELECT songs.* FROM songs
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
            OR (:filterMode = 3 AND songs.source_type = 0 AND songs.parent_directory_path LIKE '%/Nobody Listens/YouTube Music')
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
    fun getFavoriteSongsPaginated(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        sortOrder: String,
        filterMode: Int
    ): PagingSource<Int, SongEntity>

    /**
     * Returns all favorite songs as a list (for playback queue when shuffling).
     */
    @Query("""
        SELECT songs.* FROM songs
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
            OR (:filterMode = 3 AND songs.source_type = 0 AND songs.parent_directory_path LIKE '%/Nobody Listens/YouTube Music')
        )
        ORDER BY songs.title COLLATE NOCASE ASC
    """)
    suspend fun getFavoriteSongsList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int
    ): List<SongEntity>

    @Query("""
        SELECT """ + SONG_LIST_PROJECTION + """
        FROM songs
        INNER JOIN favorites ON songs.id = favorites.songId AND favorites.isFavorite = 1
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
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
            OR (:filterMode = 3 AND songs.source_type = 0 AND songs.parent_directory_path LIKE '%/Nobody Listens/YouTube Music')
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
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getFavoriteSongsPage(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        sortOrder: String,
        filterMode: Int,
        limit: Int,
        offset: Int
    ): List<SongEntity>

    /**
     * Returns the count of favorite songs (reactive).
     */
    @Query("""
        SELECT COUNT(*) FROM songs
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
            OR (:filterMode = 3 AND songs.source_type = 0 AND songs.parent_directory_path LIKE '%/Nobody Listens/YouTube Music')
        )
    """)
    fun getFavoriteSongCount(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int
    ): Flow<Int>

    // --- Paginated Search Query ---
    /**
     * Returns a PagingSource for search results, enabling efficient pagination for large result sets.
     */
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN songs_fts ON songs_fts.rowid = songs.id
        WHERE (:applyDirectoryFilter = 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND songs_fts MATCH :matchQuery
        ORDER BY songs.title ASC
    """)
    fun searchSongsPaginatedMatch(
        matchQuery: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): PagingSource<Int, SongEntity>

    fun searchSongsPaginated(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): PagingSource<Int, SongEntity> = searchSongsPaginatedMatch(
        matchQuery = buildSongSearchMatchQuery(query),
        allowedParentDirs = allowedParentDirs,
        applyDirectoryFilter = applyDirectoryFilter
    )

    /**
     * Search songs with a result limit for non-paginated contexts (FTS).
     */
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN songs_fts ON songs_fts.rowid = songs.id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND songs_fts MATCH :matchQuery
        ORDER BY songs.title ASC
        LIMIT :limit
    """)
    fun searchSongsLimitedMatch(
        matchQuery: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        limit: Int
    ): Flow<List<SongEntity>>

    /**
     * LIKE-based search focusing only on titles.
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        AND title LIKE '%' || :query || '%'
        ORDER BY title ASC
        LIMIT :limit
    """)
    fun searchSongsLimitedByTitleLike(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        limit: Int
    ): Flow<List<SongEntity>>

    /**
     * LIKE-based fallback search for songs that FTS tokenization may miss.
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (title LIKE '%' || :query || '%' OR artist_name LIKE '%' || :query || '%')
        ORDER BY title ASC
        LIMIT :limit
    """)
    fun searchSongsLimitedLike(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        limit: Int
    ): Flow<List<SongEntity>>

    fun searchSongsLimited(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        limit: Int,
        titleOnly: Boolean = false
    ): Flow<List<SongEntity>> {
        val ftsFlow = searchSongsLimitedMatch(
            matchQuery = if (titleOnly) buildSongTitleSearchMatchQuery(query) else buildSongSearchMatchQuery(query),
            allowedParentDirs = allowedParentDirs,
            applyDirectoryFilter = applyDirectoryFilter,
            limit = limit
        )
        val likeFlow = if (titleOnly) {
            searchSongsLimitedByTitleLike(
                query = query.trim(),
                allowedParentDirs = allowedParentDirs,
                applyDirectoryFilter = applyDirectoryFilter,
                limit = limit
            )
        } else {
            searchSongsLimitedLike(
                query = query.trim(),
                allowedParentDirs = allowedParentDirs,
                applyDirectoryFilter = applyDirectoryFilter,
                limit = limit
            )
        }
        return ftsFlow.combine(likeFlow) { ftsResults, likeResults ->
            val seen = LinkedHashMap<Long, SongEntity>(ftsResults.size + likeResults.size)
            ftsResults.forEach { seen.putIfAbsent(it.id, it) }
            likeResults.forEach { seen.putIfAbsent(it.id, it) }
            seen.values.toList().take(limit)
        }
    }

    // --- Paginated Genre Query ---
    /**
     * Returns a PagingSource for songs in a specific genre.
     */
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND genre LIKE :genreName
        ORDER BY title ASC
    """)
    fun getSongsByGenrePaginated(
        genreName: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): PagingSource<Int, SongEntity>
}
