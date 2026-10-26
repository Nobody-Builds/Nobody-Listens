package com.nobodybuilds.listens.data.qqmusic

import android.util.Base64
import com.nobodybuilds.listens.data.database.QqMusicSongEntity
import org.json.JSONObject

internal fun parseTrackToEntity(track: JSONObject, playlistId: Long): QqMusicSongEntity {
    // Handle both older and newer field names
    val mid = track.optString("songmid", track.optString("mid", ""))
    val title = decodeBase64IfNeeded(track.optString("songname", track.optString("title", "Unknown")))
        
    val singerArray = track.optJSONArray("singer")
    val artist = if (singerArray != null && singerArray.length() > 0) {
        val names = mutableListOf<String>()
        for (i in 0 until singerArray.length()) {
            names.add(decodeBase64IfNeeded(singerArray.optJSONObject(i)?.optString("name", "") ?: ""))
        }
        names.filter { it.isNotBlank() }.joinToString(", ")
    } else {
        "Unknown Artist"
    }

    val album = decodeBase64IfNeeded(track.optString("albumname", track.optJSONObject("album")?.optString("name", "") ?: ""))
    val albumMid = track.optString("albummid", track.optJSONObject("album")?.optString("mid", "") ?: "")
    val duration = track.optLong("interval", 0) * 1000L  // Convert to milliseconds

    return QqMusicSongEntity(
        id = "${playlistId}_$mid",
        songMid = mid,
        playlistId = playlistId,
        title = title,
        artist = artist,
        album = album,
        albumMid = albumMid,
        duration = duration,
        albumArtUrl = if (albumMid.isNotBlank()) {
            "https://y.qq.com/music/photo_new/T002R300x300M000$albumMid.jpg"
        } else null,
        mimeType = "audio/mpeg",
        bitrate = null,
        dateAdded = System.currentTimeMillis()
    )
}

/**
 * Decode Base64-encoded string if it looks like Base64.
 * QQ Music FCG endpoints return some fields as Base64 after Zlib decompression.
 */
internal fun decodeBase64IfNeeded(input: String): String {
    if (input.isBlank()) return input
    // Check if it looks like Base64: only contains A-Za-z0-9+/= and no Chinese/special chars
    val base64Pattern = Regex("^[A-Za-z0-9+/=]+$")
    if (!base64Pattern.matches(input)) return input
    // Must be at least 4 chars and valid length for Base64
    if (input.length < 4) return input
    return try {
        val decoded = Base64.decode(input, Base64.DEFAULT)
        val result = String(decoded, Charsets.UTF_8)
        // Verify the decoded result contains actual readable text
        if (result.isNotBlank() && !result.contains('\u0000')) result else input
    } catch (_: Exception) {
        input
    }
}
