package com.nobodybuilds.listens.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A phone playlist saved to the watch, so it plays without the phone. The id is the phone's playlist id. */
@Entity(tableName = "local_playlists")
data class LocalPlaylistEntity(
    @PrimaryKey val playlistId: String,
    val name: String,
    val importedAt: Long,
)

/**
 * One entry of a saved playlist, in order. Title and artist are kept so the list shows
 * before the song's file has arrived.
 */
@Entity(
    tableName = "local_playlist_songs",
    primaryKeys = ["playlistId", "position"],
    indices = [Index("songId")],
)
data class LocalPlaylistSongEntity(
    val playlistId: String,
    val position: Int,
    val songId: String,
    val title: String,
    val artist: String,
)
