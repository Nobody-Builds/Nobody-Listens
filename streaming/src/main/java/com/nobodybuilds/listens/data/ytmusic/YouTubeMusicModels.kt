package com.nobodybuilds.listens.data.ytmusic

import com.nobodybuilds.listens.data.stream.StreamingCollection
import com.nobodybuilds.listens.data.stream.StreamingShelf
import com.nobodybuilds.listens.data.stream.StreamingTrack

/** Bitrate caps: YouTube serves Opus at about 150, 70 and 50 kbps, and AAC at 128 kbps. */
enum class YouTubeMusicAudioQuality(val maxBitrate: Int, val label: String) {
    HIGH(YouTubeMusicFormats.HIGHEST_TARGET_BITRATE, "High"),
    NORMAL(100_000, "Normal"),
    DATA_SAVER(60_000, "Data saver"),
}

data class YouTubeMusicClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val loginSupported: Boolean = false,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val useYoutubeHost: Boolean = false,
    /** Probe the URL past the first megabyte before use: set for clients YouTube may cut off. */
    val probeBeforeUse: Boolean = false,
)

data class YouTubeMusicSession(
    val cookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val poToken: String = "",
    val accountName: String = "",
    val accountEmail: String = "",
) {
    val isLoggedIn: Boolean
        get() = YouTubeMusicAuth.hasLoginCookie(cookie)
}

data class YouTubeMusicTrack(
    val videoId: String,
    override val title: String,
    override val artist: String = "",
    val album: String = "",
    override val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
    /** Browse ids for the artist (UC…) and album (MPREb_…) pages, when YouTube links them. */
    val artistId: String? = null,
    val albumId: String? = null,
) : StreamingTrack {
    override val key: String get() = videoId
}

enum class YouTubeMusicItemKind { PLAYLIST, ALBUM, ARTIST }

data class YouTubeMusicPlaylist(
    val playlistId: String,
    override val title: String,
    override val subtitle: String = "",
    override val thumbnailUrl: String? = null,
    val kind: YouTubeMusicItemKind = YouTubeMusicItemKind.PLAYLIST,
) : StreamingCollection {
    override val key: String get() = playlistId
    override val importable: Boolean get() = kind != YouTubeMusicItemKind.ARTIST
}

/** Songs YouTube picks after a seed song, the token for the next batch, and its lyrics page. */
data class YouTubeMusicRadioPage(
    val tracks: List<YouTubeMusicTrack>,
    val continuation: String? = null,
    val lyricsBrowseId: String? = null,
)

/** A song's lyrics as plain text, with YouTube's credit line ("Source: …"). */
data class YouTubeMusicLyrics(val text: String, val source: String? = null)

data class YouTubeMusicShelf(
    override val title: String,
    override val tracks: List<YouTubeMusicTrack> = emptyList(),
    override val playlists: List<YouTubeMusicPlaylist> = emptyList(),
) : StreamingShelf<YouTubeMusicTrack, YouTubeMusicPlaylist>

data class YouTubeMusicBrowsePage(
    val shelves: List<YouTubeMusicShelf>,
    val continuation: String? = null,
    /** Page heading for artist and album pages. */
    val title: String? = null,
)

data class YouTubeMusicAudioFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val sampleRate: Int? = null,
    val contentLength: Long? = null,
    val url: String? = null,
    val signatureCipher: String? = null,
    val approxDurationMs: Long? = null,
)

data class YouTubeMusicPlayerPayload(
    val status: String,
    val reason: String? = null,
    val videoId: String? = null,
    val title: String? = null,
    val author: String? = null,
    val durationMs: Long? = null,
    val expiresInSeconds: Int? = null,
    val formats: List<YouTubeMusicAudioFormat> = emptyList(),
    val hlsManifestUrl: String? = null,
)

data class YouTubeMusicStream(
    val videoId: String,
    val url: String,
    val format: YouTubeMusicAudioFormat,
    val expiresInSeconds: Int,
    val userAgent: String,
    val origin: String = YouTubeMusicClients.ORIGIN,
    /** Song length from the player response; search rows often come without one. */
    val durationMs: Long = 0L,
    /** The innertube client that returned the URL, so it can be rested if YouTube refuses it. */
    val clientName: String = "",
)

data class YouTubeMusicVideo(
    val url: String,
    val userAgent: String,
)

data class YouTubeMusicDownloadSpec(
    val fileName: String,
    val mimeType: String,
    val extension: String,
)
