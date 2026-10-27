package com.nobodybuilds.listens.data.navidrome

import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.navidrome.model.NavidromeSong

// ─── Extension Functions ────────────────────────────────────────────────────

/**
 * Convert a NavidromeSong to a Song model.
 */
fun NavidromeSong.toSong(): Song {
    return Song(
        id = "navidrome_$id",
        title = title,
        artist = artist,
        artistId = -1L,
        album = album,
        albumId = -1L,
        path = path,
        contentUriString = "navidrome://$id",
        albumArtUriString = coverArt?.let { "navidrome_cover://$it" },
        duration = duration,
        genre = genre,
        mimeType = resolvedMimeType,
        bitrate = bitRate?.let { it * 1000 },
        sampleRate = null,
        year = year,
        trackNumber = trackNumber,
        dateAdded = System.currentTimeMillis(),
        isFavorite = false,
        navidromeId = id
    )
}
