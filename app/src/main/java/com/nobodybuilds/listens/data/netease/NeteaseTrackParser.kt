package com.nobodybuilds.listens.data.netease

import com.nobodybuilds.listens.data.database.NeteaseSongEntity
import com.nobodybuilds.listens.data.model.Song
import org.json.JSONObject

// ─── JSON Parsing Helpers ──────────────────────────────────────────

internal fun parseTrackToEntity(track: JSONObject, playlistId: Long): NeteaseSongEntity {
    val artists = track.optJSONArray("ar")
    val artistName = buildString {
        if (artists != null) {
            for (j in 0 until artists.length()) {
                if (j > 0) append(", ")
                append(artists.optJSONObject(j)?.optString("name", "") ?: "")
            }
        } else {
            append("Unknown Artist")
        }
    }
    val album = track.optJSONObject("al")

    return NeteaseSongEntity(
        id = "${playlistId}_${track.optLong("id")}",
        neteaseId = track.optLong("id"),
        playlistId = playlistId,
        title = track.optString("name", ""),
        artist = artistName,
        album = album?.optString("name", "Unknown Album") ?: "Unknown Album",
        albumId = album?.optLong("id") ?: -1L,
        duration = track.optLong("dt"),
        albumArtUrl = album?.optString("picUrl"),
        mimeType = "audio/mpeg",
        bitrate = null,
        dateAdded = track.optLong("publishTime", System.currentTimeMillis())
    )
}

internal fun parseTrackToSong(track: JSONObject): Song {
    val artists = track.optJSONArray("ar")
    val artistName = buildString {
        if (artists != null) {
            for (j in 0 until artists.length()) {
                if (j > 0) append(", ")
                append(artists.optJSONObject(j)?.optString("name", "") ?: "")
            }
        } else {
            append("Unknown Artist")
        }
    }
    val album = track.optJSONObject("al")
    val neteaseId = track.optLong("id")

    return Song(
        id = "netease_$neteaseId",
        title = track.optString("name", ""),
        artist = artistName,
        artistId = artists?.optJSONObject(0)?.optLong("id") ?: -1L,
        album = album?.optString("name", "Unknown Album") ?: "Unknown Album",
        albumId = album?.optLong("id") ?: -1L,
        path = "",
        contentUriString = "netease://$neteaseId",
        albumArtUriString = album?.optString("picUrl"),
        duration = track.optLong("dt"),
        mimeType = "audio/mpeg",
        bitrate = null,
        sampleRate = null,
        year = 0,
        trackNumber = 0,
        dateAdded = track.optLong("publishTime", System.currentTimeMillis()),
        isFavorite = false,
        neteaseId = neteaseId
    )
}
