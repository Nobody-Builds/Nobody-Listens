package com.nobodybuilds.listens.data.database

import androidx.paging.PagingSource
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Album queries. Part of [MusicDao]. */
interface MusicAlbumQueries {

    // --- Album Queries ---
    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            COUNT(songs.id) AS song_count,
            albums.date_added AS date_added,
            albums.year AS year
        FROM songs
        INNER JOIN albums ON albums.id = songs.album_id
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
            OR (:filterMode = 3 AND songs.source_type = 0 AND (songs.parent_directory_path LIKE '%/Nobody Listens/YouTube Music' OR songs.parent_directory_path LIKE '%/Nobody Listens/JioSaavn'))
        )
        GROUP BY
            albums.id,
            albums.title,
            albums.artist_name,
            albums.artist_id,
            albums.album_art_uri_string,
            albums.date_added,
            albums.year
        HAVING COUNT(songs.id) >= :minTracks
        ORDER BY albums.title ASC
    """)
    fun getAlbums(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int,
        minTracks: Int
    ): Flow<List<AlbumEntity>>

    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            COUNT(songs.id) AS song_count,
            albums.date_added AS date_added,
            albums.year AS year
        FROM songs
        INNER JOIN albums ON albums.id = songs.album_id
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
            OR (:filterMode = 3 AND songs.source_type = 0 AND (songs.parent_directory_path LIKE '%/Nobody Listens/YouTube Music' OR songs.parent_directory_path LIKE '%/Nobody Listens/JioSaavn'))
        )
        GROUP BY
            albums.id,
            albums.title,
            albums.artist_name,
            albums.artist_id,
            albums.album_art_uri_string,
            albums.date_added,
            albums.year
        HAVING COUNT(songs.id) >= :minTracks
        ORDER BY
            CASE WHEN :sortOrder = 'album_title_az' THEN albums.title END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'album_title_za' THEN albums.title END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'album_artist' THEN albums.artist_name END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'album_artist_desc' THEN albums.artist_name END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'album_release_year' THEN albums.year END DESC,
            CASE WHEN :sortOrder = 'album_release_year_asc' THEN albums.year END ASC,
            CASE WHEN :sortOrder = 'album_date_added' THEN albums.date_added END DESC,
            CASE WHEN :sortOrder = 'album_size_asc' THEN song_count END ASC,
            CASE WHEN :sortOrder = 'album_size_desc' THEN song_count END DESC,
            albums.title COLLATE NOCASE ASC,
            albums.artist_name COLLATE NOCASE ASC,
            albums.id ASC
    """)
    fun getAlbumsPaginated(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int,
        sortOrder: String,
        minTracks: Int
    ): PagingSource<Int, AlbumEntity>

    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            COUNT(songs.id) AS song_count,
            albums.date_added AS date_added,
            albums.year AS year
        FROM songs
        INNER JOIN albums ON albums.id = songs.album_id
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
            OR (:filterMode = 3 AND songs.source_type = 0 AND (songs.parent_directory_path LIKE '%/Nobody Listens/YouTube Music' OR songs.parent_directory_path LIKE '%/Nobody Listens/JioSaavn'))
        )
        GROUP BY
            albums.id,
            albums.title,
            albums.artist_name,
            albums.artist_id,
            albums.album_art_uri_string,
            albums.date_added,
            albums.year
        HAVING COUNT(songs.id) >= :minTracks
        ORDER BY
            CASE WHEN :sortOrder = 'album_title_az' THEN albums.title END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'album_title_za' THEN albums.title END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'album_artist' THEN albums.artist_name END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'album_artist_desc' THEN albums.artist_name END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'album_release_year' THEN albums.year END DESC,
            CASE WHEN :sortOrder = 'album_release_year_asc' THEN albums.year END ASC,
            CASE WHEN :sortOrder = 'album_date_added' THEN albums.date_added END DESC,
            CASE WHEN :sortOrder = 'album_size_asc' THEN song_count END ASC,
            CASE WHEN :sortOrder = 'album_size_desc' THEN song_count END DESC,
            albums.title COLLATE NOCASE ASC,
            albums.artist_name COLLATE NOCASE ASC,
            albums.id ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getAlbumsPage(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int,
        sortOrder: String,
        minTracks: Int,
        limit: Int,
        offset: Int
    ): List<AlbumEntity>

    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            (
                SELECT COUNT(*)
                FROM songs
                WHERE songs.album_id = albums.id
            ) AS song_count,
            albums.date_added AS date_added,
            albums.year AS year
        FROM albums
        WHERE albums.id = :albumId
        LIMIT 1
    """)
    fun getAlbumById(albumId: Long): Flow<AlbumEntity?>

    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            (
                SELECT COUNT(*)
                FROM songs
                WHERE songs.album_id = albums.id
            ) AS song_count,
            albums.date_added AS date_added,
            albums.year AS year
        FROM albums
        WHERE albums.title LIKE '%' || :query || '%'
        AND song_count >= :minTracks
        ORDER BY albums.title ASC
    """)
    fun searchAlbums(query: String, minTracks: Int = 1): Flow<List<AlbumEntity>>

    @Query("SELECT COUNT(*) FROM albums")
    fun getAlbumCount(): Flow<Int>

    // Version of getAlbums that returns a List for one-shot reads
    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            COUNT(songs.id) AS song_count,
            albums.date_added AS date_added,
            albums.year AS year
        FROM songs
        INNER JOIN albums ON albums.id = songs.album_id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        GROUP BY
            albums.id,
            albums.title,
            albums.artist_name,
            albums.artist_id,
            albums.album_art_uri_string,
            albums.date_added,
            albums.year
        HAVING COUNT(songs.id) >= :minTracks
        ORDER BY albums.title ASC
    """)
    suspend fun getAllAlbumsList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        minTracks: Int
    ): List<AlbumEntity>

    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            COUNT(songs.id) AS song_count,
            albums.date_added AS date_added,
            albums.year AS year
        FROM albums
        LEFT JOIN songs ON albums.id = songs.album_id
        WHERE albums.artist_id = :artistId
        GROUP BY
            albums.id,
            albums.title,
            albums.artist_name,
            albums.artist_id,
            albums.album_art_uri_string,
            albums.date_added,
            albums.year
        ORDER BY albums.title ASC
    """)
    fun getAlbumsByArtistId(artistId: Long): Flow<List<AlbumEntity>>

    @Query("""
        SELECT
            albums.id AS id,
            albums.title AS title,
            albums.artist_name AS artist_name,
            albums.artist_id AS artist_id,
            albums.album_art_uri_string AS album_art_uri_string,
            COUNT(songs.id) AS song_count,
            albums.date_added AS date_added,
            albums.year AS year
        FROM songs
        INNER JOIN albums ON albums.id = songs.album_id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND (albums.title LIKE '%' || :query || '%' OR albums.artist_name LIKE '%' || :query || '%')
        GROUP BY
            albums.id,
            albums.title,
            albums.artist_name,
            albums.artist_id,
            albums.album_art_uri_string,
            albums.date_added,
            albums.year
        HAVING COUNT(songs.id) >= :minTracks
        ORDER BY albums.title ASC
    """)
    fun searchAlbums(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        minTracks: Int
    ): Flow<List<AlbumEntity>>
}
