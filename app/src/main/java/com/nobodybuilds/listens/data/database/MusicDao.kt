package com.nobodybuilds.listens.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface MusicDao :
    MusicSongQueries,
    MusicPagedSongQueries,
    MusicAlbumQueries,
    MusicArtistQueries,
    MusicSongEditQueries {

    // --- Insert Operations ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongsIgnoreConflicts(songs: List<SongEntity>): List<Long>

    @Update
    suspend fun updateSongs(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbumsIgnoreConflicts(albums: List<AlbumEntity>): List<Long>

    @Update
    suspend fun updateAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtistsIgnoreConflicts(artists: List<ArtistEntity>): List<Long>

    @Update
    suspend fun updateArtists(artists: List<ArtistEntity>)

    @Query("SELECT * FROM artists WHERE id IN (:artistIds)")
    suspend fun getArtistsByIds(artistIds: List<Long>): List<ArtistEntity>

    @Transaction
    suspend fun insertSongs(songs: List<SongEntity>) {
        if (songs.isEmpty()) return
        val insertResults = insertSongsIgnoreConflicts(songs)
        val songsToUpdate = mutableListOf<SongEntity>()
        insertResults.forEachIndexed { index, rowId ->
            if (rowId == -1L) songsToUpdate.add(songs[index])
        }
        if (songsToUpdate.isNotEmpty()) {
            updateSongs(songsToUpdate)
        }
    }

    @Transaction
    suspend fun insertAlbums(albums: List<AlbumEntity>) {
        if (albums.isEmpty()) return
        val insertResults = insertAlbumsIgnoreConflicts(albums)
        val albumsToUpdate = mutableListOf<AlbumEntity>()
        insertResults.forEachIndexed { index, rowId ->
            if (rowId == -1L) albumsToUpdate.add(albums[index])
        }
        if (albumsToUpdate.isNotEmpty()) {
            updateAlbums(albumsToUpdate)
        }
    }

    @Transaction
    suspend fun insertArtists(artists: List<ArtistEntity>) {
        if (artists.isEmpty()) return
        val insertResults = insertArtistsIgnoreConflicts(artists)
        val artistsToUpdate = mutableListOf<ArtistEntity>()
        insertResults.forEachIndexed { index, rowId ->
            if (rowId == -1L) artistsToUpdate.add(artists[index])
        }
        if (artistsToUpdate.isNotEmpty()) {
            val existingById = getArtistsByIds(artistsToUpdate.map { it.id }).associateBy { it.id }
            val mergedArtists = artistsToUpdate.map { incoming ->
                val existing = existingById[incoming.id]
                if (existing == null) {
                    incoming
                } else {
                    incoming.copy(
                        imageUrl = incoming.imageUrl ?: existing.imageUrl,
                        customImageUri = incoming.customImageUri ?: existing.customImageUri
                    )
                }
            }
            updateArtists(mergedArtists)
        }
    }



    @Transaction
    suspend fun insertMusicData(songs: List<SongEntity>, albums: List<AlbumEntity>, artists: List<ArtistEntity>) {
        insertArtists(artists)
        insertAlbums(albums)
        insertSongs(songs)
    }

    @Transaction
    suspend fun clearAllMusicData() {
        clearAllSongs()
        clearAllAlbums()
        clearAllArtists()
    }

    // --- Clear Operations ---
    @Query("DELETE FROM songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM albums")
    suspend fun clearAllAlbums()

    @Query("DELETE FROM artists")
    suspend fun clearAllArtists()

    // --- Incremental Sync Operations ---
    @Query("SELECT id FROM songs")
    suspend fun getAllSongIds(): List<Long>

    @Query("SELECT id FROM songs WHERE source_type = 0")
    suspend fun getAllMediaStoreSongIds(): List<Long>

    @Query("DELETE FROM songs WHERE id IN (:songIds)")
    suspend fun deleteSongsByIds(songIds: List<Long>)

    @Query("DELETE FROM song_artist_cross_ref WHERE song_id IN (:songIds)")
    suspend fun deleteCrossRefsBySongIds(songIds: List<Long>)

    @Query("DELETE FROM favorites WHERE songId IN (:songIds)")
    suspend fun deleteFavoritesBySongIds(songIds: List<Long>)

    @Query("DELETE FROM lyrics WHERE songId IN (:songIds)")
    suspend fun deleteLyricsBySongIds(songIds: List<Long>)

    @Query("SELECT id FROM songs WHERE source_type = 1")
    suspend fun getAllTelegramSongIds(): List<Long>

    @Query("""
        SELECT id FROM songs
        WHERE source_type = 1
        AND (telegram_chat_id = :chatId
             OR content_uri_string LIKE 'telegram://' || :chatId || '/%')
    """)
    suspend fun getTelegramSongIdsByChatId(chatId: Long): List<Long>

    @Query("""
        SELECT s.id FROM songs s
        INNER JOIN telegram_songs ts
            ON ts.chat_id = s.telegram_chat_id
            AND ('telegram://' || ts.chat_id || '/' || ts.message_id) = s.content_uri_string
        WHERE ts.chat_id = :chatId AND ts.thread_id = :threadId
    """)
    suspend fun getTelegramSongIdsByTopicId(chatId: Long, threadId: Long): List<Long>

    @Query("SELECT id FROM songs WHERE source_type = 2")
    suspend fun getAllNeteaseSongIds(): List<Long>

    @Query("SELECT id FROM songs WHERE source_type = 3")
    suspend fun getAllGDriveSongIds(): List<Long>

    @Query("SELECT id FROM songs WHERE source_type = 4")
    suspend fun getAllQqMusicSongIds(): List<Long>

    @Query("SELECT id FROM songs WHERE source_type = 5")
    suspend fun getAllNavidromeSongIds(): List<Long>

    @Query("SELECT id FROM songs WHERE source_type = 6")
    suspend fun getAllJellyfinSongIds(): List<Long>

    @Transaction
    suspend fun deleteSongsAndRelatedData(songIds: List<Long>) {
        if (songIds.isEmpty()) return
        songIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            deleteCrossRefsBySongIds(chunk)
            deleteFavoritesBySongIds(chunk)
            deleteLyricsBySongIds(chunk)
            deleteSongsByIds(chunk)
        }
        deleteOrphanedAlbums()
        deleteOrphanedArtists()
    }

    @Transaction
    suspend fun clearAllNeteaseSongs() {
        val neteaseSongIds = getAllNeteaseSongIds()
        if (neteaseSongIds.isEmpty()) return
        deleteSongsAndRelatedData(neteaseSongIds)
    }

    @Transaction
    suspend fun clearAllGDriveSongs() {
        val gdriveSongIds = getAllGDriveSongIds()
        if (gdriveSongIds.isEmpty()) return
        deleteSongsAndRelatedData(gdriveSongIds)
    }

    @Transaction
    suspend fun clearAllQqMusicSongs() {
        val qqMusicSongIds = getAllQqMusicSongIds()
        if (qqMusicSongIds.isEmpty()) return
        deleteSongsAndRelatedData(qqMusicSongIds)
    }

    @Transaction
    suspend fun clearAllNavidromeSongs() {
        val navidromeSongIds = getAllNavidromeSongIds()
        if (navidromeSongIds.isEmpty()) return
        deleteSongsAndRelatedData(navidromeSongIds)
    }

    @Transaction
    suspend fun clearAllJellyfinSongs() {
        val jellyfinSongIds = getAllJellyfinSongIds()
        if (jellyfinSongIds.isEmpty()) return
        deleteSongsAndRelatedData(jellyfinSongIds)
    }

    @Transaction
    suspend fun clearAllTelegramSongs() {
        val telegramSongIds = getAllTelegramSongIds()
        if (telegramSongIds.isEmpty()) return
        deleteSongsAndRelatedData(telegramSongIds)
    }

    @Transaction
    suspend fun clearTelegramSongsForChat(chatId: Long) {
        val telegramSongIds = getTelegramSongIdsByChatId(chatId)
        if (telegramSongIds.isEmpty()) return
        deleteSongsAndRelatedData(telegramSongIds)
    }

    @Transaction
    suspend fun clearTelegramSongsForTopic(chatId: Long, threadId: Long) {
        val songIds = getTelegramSongIdsByTopicId(chatId, threadId)
        if (songIds.isEmpty()) return
        deleteSongsAndRelatedData(songIds)
    }

    /**
     * Incrementally sync music data: upsert new/modified songs and remove deleted ones.
     * More efficient than clear-and-replace for large libraries with few changes.
     */
    @Transaction
    suspend fun incrementalSyncMusicData(
        songs: List<SongEntity>,
        albums: List<AlbumEntity>,
        artists: List<ArtistEntity>,
        crossRefs: List<SongArtistCrossRef>,
        deletedSongIds: List<Long>
    ) {
        // Protect cloud songs from deletion during generic media scan
        // Only allow explicit deletions if the list is non-empty.
        // During general refresh, deletedSongIds strictly contains local MediaStore IDs only.
        if (deletedSongIds.isNotEmpty()) {
            deletedSongIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
                deleteCrossRefsBySongIds(chunk)
                deleteFavoritesBySongIds(chunk)
                deleteLyricsBySongIds(chunk)
                deleteSongsByIds(chunk)
            }
        }

        // Upsert artists, albums, and songs.
        insertArtists(artists)
        insertAlbums(albums)

        // Insert songs in chunks to allow concurrent reads
        songs.chunked(SONG_BATCH_SIZE).forEach { chunk ->
            insertSongs(chunk)
        }

        // Delete old cross-refs for updated songs and insert new ones
        val updatedSongIds = songs.map { it.id }
        updatedSongIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            deleteCrossRefsBySongIds(chunk)
        }
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertSongArtistCrossRefs(chunk)
        }

        // Clean up orphaned albums and artists
        deleteOrphanedAlbums()
        deleteOrphanedArtists()
    }

    // Transaction to toggle favorite status
    @Transaction
    suspend fun toggleFavoriteStatus(songId: Long): Boolean {
        val currentStatus = getFavoriteStatus(songId) ?: false // Default to false if not found (should not happen for existing song)
        val newStatus = !currentStatus
        setFavoriteStatus(songId, newStatus)
        return newStatus
    }

    @Transaction
    suspend fun updateSongMetadataAndArtistLinks(
        songId: Long,
        title: String,
        artist: String,
        artistId: Long,
        artistsJson: String?,
        album: String,
        genre: String?,
        trackNumber: Int,
        discNumber: Int?,
        artistsToEnsure: List<ArtistEntity>,
        crossRefs: List<SongArtistCrossRef>
    ) {
        if (artistsToEnsure.isNotEmpty()) {
            insertArtistsIgnoreConflicts(artistsToEnsure)
        }

        updateSongMetadata(
            songId = songId,
            title = title,
            artist = artist,
            artistId = artistId,
            artistsJson = artistsJson,
            album = album,
            genre = genre,
            trackNumber = trackNumber,
            discNumber = discNumber
        )

        deleteCrossRefsForSong(songId)
        if (crossRefs.isNotEmpty()) {
            crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
                insertSongArtistCrossRefs(chunk)
            }
        }

        deleteOrphanedArtists()
    }

    /**
     * Clear all music data including cross-references.
     */
    @Transaction
    suspend fun clearAllMusicDataWithCrossRefs() {
        clearAllSongArtistCrossRefs()
        clearAllSongs()
        clearAllAlbums()
        clearAllArtists()
    }

    /**
     * Insert music data with cross-references in a single transaction.
     * Uses chunked inserts for cross-refs to avoid SQLite variable limits.
     */
    @Transaction
    suspend fun insertMusicDataWithCrossRefs(
        songs: List<SongEntity>,
        albums: List<AlbumEntity>,
        artists: List<ArtistEntity>,
        crossRefs: List<SongArtistCrossRef>
    ) {
        insertArtists(artists)
        insertAlbums(albums)
        insertSongs(songs)
        // Insert cross-refs in chunks to avoid SQLite variable limit.
        // Each SongArtistCrossRef has 3 fields, so batch size is calculated accordingly.
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertSongArtistCrossRefs(chunk)
        }
    }

    @Transaction
    suspend fun rebuildMusicDataWithCrossRefs(
        songs: List<SongEntity>,
        albums: List<AlbumEntity>,
        artists: List<ArtistEntity>,
        crossRefs: List<SongArtistCrossRef>
    ) {
        // Save current cloud songs before clearing to prevent accidental data loss
        // Only clear if we have new songs to insert, or we are explicitly asked to REBUILD everything.
        // We handle this logic at the worker/repository level to be more precise.

        clearAllSongArtistCrossRefs()
        clearAllSongs()
        clearAllAlbums()
        clearAllArtists()

        insertArtists(artists)
        insertAlbums(albums)
        insertSongs(songs)
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertSongArtistCrossRefs(chunk)
        }
    }

    companion object {
        /**
         * SQLite has a limit on the number of variables per statement (default 999, higher in newer versions).
         * Each SongArtistCrossRef insert uses 3 variables (songId, artistId, isPrimary).
         * The batch size is calculated so that batchSize * 3 <= SQLITE_MAX_VARIABLE_NUMBER.
         */
        private const val SQLITE_MAX_VARIABLE_NUMBER = 999 // Increase if you know your SQLite version supports more
        private const val CROSS_REF_FIELDS_PER_OBJECT = 3
        val CROSS_REF_BATCH_SIZE: Int = SQLITE_MAX_VARIABLE_NUMBER / CROSS_REF_FIELDS_PER_OBJECT

        /**
         * Batch size for song inserts during incremental sync.
         * Allows database reads to interleave with writes for better UX.
         */
        const val SONG_BATCH_SIZE = 500
    }
}
