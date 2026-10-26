package com.nobodybuilds.listens.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalPlaylistDao {

    @Query("SELECT * FROM local_playlists ORDER BY name COLLATE NOCASE ASC")
    fun getPlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM local_playlists WHERE playlistId = :playlistId")
    fun getPlaylist(playlistId: String): Flow<LocalPlaylistEntity?>

    @Query("SELECT * FROM local_playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getSongs(playlistId: String): Flow<List<LocalPlaylistSongEntity>>

    @Query("SELECT * FROM local_playlist_songs")
    fun getAllSongs(): Flow<List<LocalPlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: LocalPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<LocalPlaylistSongEntity>)

    @Query("DELETE FROM local_playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteSongs(playlistId: String)

    @Query("DELETE FROM local_playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylistRow(playlistId: String)

    /** Saves the playlist, replacing an earlier save of it (the phone's order may have changed). */
    @Transaction
    suspend fun replacePlaylist(playlist: LocalPlaylistEntity, songs: List<LocalPlaylistSongEntity>) {
        deleteSongs(playlist.playlistId)
        insertPlaylist(playlist)
        insertSongs(songs)
    }

    @Transaction
    suspend fun deletePlaylist(playlistId: String) {
        deleteSongs(playlistId)
        deletePlaylistRow(playlistId)
    }
}
