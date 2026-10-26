package com.nobodybuilds.listens.data.database

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nobodybuilds.listens.utils.AudioMeta
import kotlinx.coroutines.flow.Flow

/** Favourite, metadata and song-artist cross-reference queries. Part of [MusicDao]. */
interface MusicSongEditQueries {

    // --- Favorite Operations ---
    @Query("UPDATE songs SET is_favorite = :isFavorite WHERE id = :songId")
    suspend fun setFavoriteStatus(songId: Long, isFavorite: Boolean)

    @Query("SELECT is_favorite FROM songs WHERE id = :songId")
    suspend fun getFavoriteStatus(songId: Long): Boolean?

    @Query("""
        UPDATE songs
        SET title = :title,
            artist_name = :artist,
            artist_id = :artistId,
            artists_json = :artistsJson,
            album_name = :album,
            genre = :genre,
            track_number = :trackNumber,
            disc_number = :discNumber
        WHERE id = :songId
    """)
    suspend fun updateSongMetadata(
        songId: Long,
        title: String,
        artist: String,
        artistId: Long,
        artistsJson: String?,
        album: String,
        genre: String?,
        trackNumber: Int,
        discNumber: Int?
    )

    @Query("UPDATE songs SET album_art_uri_string = :albumArtUri WHERE id = :songId")
    suspend fun updateSongAlbumArt(songId: Long, albumArtUri: String?)

    @Query("UPDATE songs SET album_artist = :albumArtist, year = :year WHERE id = :songId")
    suspend fun updateSongAlbumArtistAndYear(songId: Long, albumArtist: String?, year: Int)

    @Query("UPDATE songs SET lyrics = :lyrics WHERE id = :songId")
    suspend fun updateLyrics(songId: Long, lyrics: String)

    @Query("UPDATE songs SET lyrics = NULL WHERE id = :songId")
    suspend fun resetLyrics(songId: Long)

    @Query("UPDATE songs SET lyrics = NULL")
    suspend fun resetAllLyrics()

    @Query("SELECT " + SONG_LIST_PROJECTION + " FROM songs")
    suspend fun getAllSongsList(): List<SongEntity>

    @Query("SELECT id, title, artist_name, album_name, duration FROM songs WHERE source_type = 0")
    suspend fun getAllLocalSongSummaries(): List<SongSummary>

    @Query("SELECT album_art_uri_string FROM songs WHERE id=:id")
    suspend fun getAlbumArtUriById(id: Long) : String?

    @Query("DELETE FROM songs WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("""
    SELECT mime_type AS mimeType,
           bitrate,
           sample_rate AS sampleRate
    FROM songs
    WHERE id = :id
    """)
    suspend fun getAudioMetadataById(id: Long): AudioMeta?

    // ===== Song-Artist Cross Reference (Junction Table) Operations =====

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongArtistCrossRefs(crossRefs: List<SongArtistCrossRef>)

    @Query("SELECT * FROM song_artist_cross_ref")
    fun getAllSongArtistCrossRefs(): Flow<List<SongArtistCrossRef>>

    @Query("SELECT * FROM song_artist_cross_ref")
    suspend fun getAllSongArtistCrossRefsList(): List<SongArtistCrossRef>

    @Query("DELETE FROM song_artist_cross_ref")
    suspend fun clearAllSongArtistCrossRefs()

    @Query("DELETE FROM song_artist_cross_ref WHERE song_id = :songId")
    suspend fun deleteCrossRefsForSong(songId: Long)

    @Query("DELETE FROM song_artist_cross_ref WHERE artist_id = :artistId")
    suspend fun deleteCrossRefsForArtist(artistId: Long)

    /**
     * Get all artists for a specific song using the junction table.
     */
    @Query("""
        SELECT artists.* FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        WHERE song_artist_cross_ref.song_id = :songId
        ORDER BY song_artist_cross_ref.is_primary DESC, artists.name ASC
    """)
    fun getArtistsForSong(songId: Long): Flow<List<ArtistEntity>>

    /**
     * Get all artists for a specific song (one-shot).
     */
    @Query("""
        SELECT artists.* FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        WHERE song_artist_cross_ref.song_id = :songId
        ORDER BY song_artist_cross_ref.is_primary DESC, artists.name ASC
    """)
    suspend fun getArtistsForSongList(songId: Long): List<ArtistEntity>

    /**
     * Get all songs for a specific artist using the junction table.
     */
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN song_artist_cross_ref ON songs.id = song_artist_cross_ref.song_id
        WHERE song_artist_cross_ref.artist_id = :artistId
        ORDER BY songs.title ASC
    """)
    fun getSongsForArtist(artistId: Long): Flow<List<SongEntity>>

    /**
     * Get all songs for a specific artist (one-shot).
     */
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN song_artist_cross_ref ON songs.id = song_artist_cross_ref.song_id
        WHERE song_artist_cross_ref.artist_id = :artistId
        ORDER BY songs.title ASC
    """)
    suspend fun getSongsForArtistList(artistId: Long): List<SongEntity>

    /**
     * Get the cross-references for a specific song.
     */
    @Query("SELECT * FROM song_artist_cross_ref WHERE song_id = :songId")
    suspend fun getCrossRefsForSong(songId: Long): List<SongArtistCrossRef>

    /**
     * Get the primary artist for a song.
     */
    @Query("""
        SELECT artists.id AS artist_id, artists.name FROM artists
        INNER JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        WHERE song_artist_cross_ref.song_id = :songId AND song_artist_cross_ref.is_primary = 1
        LIMIT 1
    """)
    suspend fun getPrimaryArtistForSong(songId: Long): PrimaryArtistInfo?

    /**
     * Get song count for an artist from the junction table.
     */
    @Query("SELECT COUNT(*) FROM song_artist_cross_ref WHERE artist_id = :artistId")
    suspend fun getSongCountForArtist(artistId: Long): Int

    /**
     * Get all artists with their song counts computed from the junction table.
     */
    @Query("""
        SELECT artists.id, artists.name, artists.image_url, artists.custom_image_uri,
               COUNT(DISTINCT song_artist_cross_ref.song_id) AS track_count
        FROM artists
        LEFT JOIN song_artist_cross_ref ON artists.id = song_artist_cross_ref.artist_id
        GROUP BY artists.id
        ORDER BY artists.name ASC
    """)
    fun getArtistsWithSongCounts(): Flow<List<ArtistEntity>>

    /**
     * Get all artists with song counts, filtered by allowed directories.
     */
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
            OR (:filterMode = 3 AND songs.source_type = 0 AND (songs.parent_directory_path LIKE '%/Nobody Listens/YouTube Music' OR songs.parent_directory_path LIKE '%/Nobody Listens/JioSaavn'))
        )
        GROUP BY artists.id
        ORDER BY artists.name ASC
    """)
    fun getArtistsWithSongCountsFiltered(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean,
        filterMode: Int
    ): Flow<List<ArtistEntity>>
}
