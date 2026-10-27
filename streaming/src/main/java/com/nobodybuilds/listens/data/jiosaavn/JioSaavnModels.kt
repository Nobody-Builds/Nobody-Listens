package com.nobodybuilds.listens.data.jiosaavn

import com.nobodybuilds.listens.data.stream.StreamingCollection
import com.nobodybuilds.listens.data.stream.StreamingShelf
import com.nobodybuilds.listens.data.stream.StreamingTrack

/** JioSaavn's AAC bitrates. 320 kbps is a Pro feature: the server decides what an account gets. */
enum class JioSaavnAudioQuality(val kbps: Int, val label: String) {
    HIGH(320, "High"),
    NORMAL(160, "Normal"),
    DATA_SAVER(96, "Data saver"),
}

/** The jiosaavn.com cookies of a signed-in account; only saved once JioSaavn accepted them. */
data class JioSaavnSession(
    val cookie: String = "",
    val accountName: String = "",
) {
    val isLoggedIn: Boolean
        get() = cookie.isNotBlank()
}

data class JioSaavnTrack(
    val id: String,
    override val title: String,
    override val artist: String = "",
    val album: String = "",
    override val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
    val albumId: String? = null,
    val year: String = "",
) : StreamingTrack {
    override val key: String get() = id
}

enum class JioSaavnItemKind { PLAYLIST, ALBUM, ARTIST }

data class JioSaavnCollection(
    val id: String,
    override val title: String,
    override val subtitle: String = "",
    override val thumbnailUrl: String? = null,
    val kind: JioSaavnItemKind = JioSaavnItemKind.PLAYLIST,
) : StreamingCollection {
    override val key: String get() = "${kind.name}:$id"
    override val importable: Boolean get() = kind != JioSaavnItemKind.ARTIST
}

data class JioSaavnShelf(
    override val title: String,
    override val tracks: List<JioSaavnTrack> = emptyList(),
    override val playlists: List<JioSaavnCollection> = emptyList(),
) : StreamingShelf<JioSaavnTrack, JioSaavnCollection>

data class JioSaavnPage(
    val shelves: List<JioSaavnShelf>,
    /** Page heading for playlist, album and artist pages. */
    val title: String? = null,
    /** Playlists report their full length; pages are fetched until it is reached. */
    val totalSongs: Int? = null,
)

/** What a song needs to be streamed: the encrypted media URL and whether it comes in 320 kbps. */
data class JioSaavnMedia(
    val encryptedUrl: String,
    val has320: Boolean,
    /** Some songs only come DRM-protected, which only JioSaavn's own app can play. */
    val drmOnly: Boolean,
)

data class JioSaavnStream(
    val id: String,
    val url: String,
    /** Bits per second of the file JioSaavn handed out, read from its name. */
    val bitrate: Int,
)

data class JioSaavnLyrics(val text: String, val copyright: String? = null)
