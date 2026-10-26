package com.nobodybuilds.listens.data.database

import androidx.paging.PagingSource
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Artist, artist image, genre and orphan clean-up queries. Part of [MusicDao]. */
interface MusicArtistQueries {

    // --- Artist Queries ---
    @Query("""
        SELECT DISTINCT artists.* FROM artists
        INNER JOIN songs ON artists.id = songs.artist_id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        ORDER BY artists.name ASC
    """)
    fun getArtists(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<ArtistEntity>>

    @Query("""
        SELECT artists.id, artists.name, artists.image_url, artists.custom_image_uri,
               COUNT(DISTINCT songs.id) AS track_count
        FROM songs
        INNER JOIN song_artist_cross_ref ON song_artist_cross_ref.song_id = songs.id
        INNER JOIN artists ON artists.id = song_artist_cross_ref.artist_id
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
        GROUP BY artists.id
        ORDER BY
            CASE WHEN :sortOrder = 'artist_name_az' THEN artists.name END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'artist_name_za' THEN artists.name END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'artist_num_songs' THEN track_count END DESC,
            artists.name COLLATE NOCASE ASC,
            artists.id ASC
    """)
    fun getArtistsPaginated(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int,
        sortOrder: String
    ): PagingSource<Int, ArtistEntity>

    @Query("""
        SELECT artists.id, artists.name, artists.image_url, artists.custom_image_uri,
               COUNT(DISTINCT songs.id) AS track_count
        FROM songs
        INNER JOIN song_artist_cross_ref ON song_artist_cross_ref.song_id = songs.id
        INNER JOIN artists ON artists.id = song_artist_cross_ref.artist_id
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
        GROUP BY artists.id
        ORDER BY
            CASE WHEN :sortOrder = 'artist_name_az' THEN artists.name END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'artist_name_za' THEN artists.name END COLLATE NOCASE DESC,
            CASE WHEN :sortOrder = 'artist_num_songs' THEN track_count END DESC,
            artists.name COLLATE NOCASE ASC,
            artists.id ASC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getArtistsPage(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int,
        sortOrder: String,
        limit: Int,
        offset: Int
    ): List<ArtistEntity>

    /**
     * Unfiltered list of all artists (including those only reachable via cross-refs).
     */
    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtistsRaw(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :artistId")
    fun getArtistById(artistId: Long): Flow<ArtistEntity?>

    @Query("SELECT * FROM artists WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchArtists(query: String): Flow<List<ArtistEntity>>

    @Query("SELECT COUNT(*) FROM artists")
    fun getArtistCount(): Flow<Int>

    // Version of getArtists that returns a List for one-shot reads
    @Query("""
        SELECT DISTINCT artists.* FROM artists
        INNER JOIN songs ON artists.id = songs.artist_id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        ORDER BY artists.name ASC
    """)
    suspend fun getAllArtistsList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): List<ArtistEntity>

    /**
     * Unfiltered list of all artists (one-shot).
     */
    @Query("SELECT * FROM artists ORDER BY name ASC")
    suspend fun getAllArtistsListRaw(): List<ArtistEntity>

    @Query("""
        SELECT artists.id, artists.name, artists.image_url, artists.custom_image_uri,
               COUNT(DISTINCT songs.id) AS track_count
        FROM songs
        INNER JOIN song_artist_cross_ref ON song_artist_cross_ref.song_id = songs.id
        INNER JOIN artists ON artists.id = song_artist_cross_ref.artist_id
        WHERE (:applyDirectoryFilter = 0 OR songs.id < 0 OR songs.parent_directory_path IN (:allowedParentDirs))
        AND artists.name LIKE '%' || :query || '%'
        GROUP BY artists.id
        ORDER BY artists.name ASC
    """)
    fun searchArtists(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<ArtistEntity>>

    // --- Artist Image Operations ---
    @Query("SELECT image_url FROM artists WHERE id = :artistId")
    suspend fun getArtistImageUrl(artistId: Long): String?

    @Query("SELECT image_url FROM artists WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getArtistImageUrlByNormalizedName(name: String): String?

    @Query("UPDATE artists SET image_url = :imageUrl WHERE id = :artistId")
    suspend fun updateArtistImageUrl(artistId: Long, imageUrl: String)

    @Query("SELECT id FROM artists WHERE name = :name LIMIT 1")
    suspend fun getArtistIdByName(name: String): Long?

    @Query("SELECT id FROM artists WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getArtistIdByNormalizedName(name: String): Long?

    @Query("SELECT MAX(id) FROM artists")
    suspend fun getMaxArtistId(): Long?

    // --- Artist Custom Image Operations ---
    @Query("UPDATE artists SET custom_image_uri = :uri WHERE id = :artistId")
    suspend fun updateArtistCustomImage(artistId: Long, uri: String?)

    @Query("SELECT custom_image_uri FROM artists WHERE id = :artistId")
    suspend fun getArtistCustomImage(artistId: Long): String?

    // --- Genre Queries ---
    // Example: Get all songs for a specific genre
    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        AND genre LIKE :genreName
        ORDER BY title ASC
    """)
    fun getSongsByGenre(
        genreName: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs
        WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (genre IS NULL OR genre = '')
        ORDER BY title ASC
    """)
    fun getSongsWithNullGenre(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<SongEntity>>

    // Example: Get all unique genre names
    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC")
    fun getUniqueGenres(): Flow<List<String>>

    @Query("""
        SELECT DISTINCT genre FROM songs
        WHERE genre IS NOT NULL AND genre != ''
        AND (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY genre ASC
    """)
    fun getUniqueGenres(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<String>>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM songs
            WHERE (:applyDirectoryFilter = 0 OR id < 0 OR parent_directory_path IN (:allowedParentDirs))
            AND (genre IS NULL OR genre = '')
        )
    """)
    fun hasUnknownGenre(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<Boolean>

    // --- Combined Queries (Potentially useful for more complex scenarios) ---
    // E.g., Get all album art URIs from songs (could be useful for theme preloading from SSoT)
    @Query("SELECT DISTINCT album_art_uri_string FROM songs WHERE album_art_uri_string IS NOT NULL")
    fun getAllUniqueAlbumArtUrisFromSongs(): Flow<List<String>>

    @Query("DELETE FROM albums WHERE NOT EXISTS (SELECT 1 FROM songs WHERE songs.album_id = albums.id)")
    suspend fun deleteOrphanedAlbums()

    @Query("DELETE FROM artists WHERE NOT EXISTS (SELECT 1 FROM song_artist_cross_ref WHERE song_artist_cross_ref.artist_id = artists.id)")
    suspend fun deleteOrphanedArtists()
}
