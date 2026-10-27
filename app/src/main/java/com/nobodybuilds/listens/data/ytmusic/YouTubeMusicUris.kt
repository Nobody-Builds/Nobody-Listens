package com.nobodybuilds.listens.data.ytmusic

import com.nobodybuilds.listens.data.database.AlbumEntity
import com.nobodybuilds.listens.data.database.ArtistEntity
import com.nobodybuilds.listens.data.database.SongEntity
import com.nobodybuilds.listens.data.database.SourceType
import com.nobodybuilds.listens.data.database.toSong
import com.nobodybuilds.listens.data.model.Song
import kotlin.math.absoluteValue

object YouTubeMusicUris {
    const val SCHEME = "ytmusic"
    const val PARENT_DIRECTORY = "/YouTube Music"
    private const val SONG_ID_OFFSET = 15_000_000_000_000L
    private const val ALBUM_ID_OFFSET = 16_000_000_000_000L
    private const val ARTIST_ID_OFFSET = 17_000_000_000_000L

    fun contentUri(videoId: String): String = "$SCHEME://track/$videoId"

    fun videoIdFrom(uriString: String): String? {
        if (!uriString.startsWith("$SCHEME://")) return null
        val rest = uriString.removePrefix("$SCHEME://").substringBefore("?").substringBefore("#")
        val candidate = rest.substringAfter("/", missingDelimiterValue = "")
        return candidate.takeIf { YouTubeMusicFormats.isValidVideoId(it) }
    }

    /**
     * Video id of a song the player is streaming: either its `ytmusic://` content URI or
     * the local proxy URL it resolves to (`http://127.0.0.1:<port>/ytmusic/<id>`).
     */
    fun videoIdFromPlaybackUri(uri: android.net.Uri): String? = videoIdFromPlaybackUri(uri.toString())

    fun videoIdFromPlaybackUri(uriString: String): String? =
        videoIdFrom(uriString)
            ?: PROXY_URL_REGEX.matchEntire(uriString)?.groupValues?.get(1)?.takeIf { YouTubeMusicFormats.isValidVideoId(it) }

    private val PROXY_URL_REGEX = Regex("""http://127\.0\.0\.1:\d+/ytmusic/([A-Za-z0-9_-]{11})(?:[?#].*)?""")

    /** Video id from a pasted YouTube or YouTube Music link, or null when the text is not one. */
    fun videoIdFromLink(text: String): String? {
        val link = text.trim()
        if (!link.contains("youtu")) return null
        val match = LINK_REGEX.find(link) ?: return null
        return match.groupValues[1].takeIf { YouTubeMusicFormats.isValidVideoId(it) }
    }

    private val LINK_REGEX = Regex(
        """(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|live/|embed/)|youtu\.be/)([A-Za-z0-9_-]{11})"""
    )

    /** Library ids are negative like the other cloud sources, so they never clash with MediaStore ids. */
    fun songId(videoId: String): Long = -(SONG_ID_OFFSET + videoId.hashCode().toLong().absoluteValue)

    fun albumId(album: String): Long = -(ALBUM_ID_OFFSET + album.lowercase().hashCode().toLong().absoluteValue)

    fun artistId(artist: String): Long = -(ARTIST_ID_OFFSET + artist.lowercase().hashCode().toLong().absoluteValue)

    fun artistName(track: YouTubeMusicTrack): String = track.artist.ifBlank { "YouTube Music" }

    fun albumName(track: YouTubeMusicTrack): String = track.album.ifBlank { "YouTube Music" }

    fun toEntity(track: YouTubeMusicTrack): SongEntity {
        val artist = artistName(track)
        val album = albumName(track)
        return SongEntity(
            id = songId(track.videoId),
            title = track.title,
            artistName = artist,
            artistId = artistId(artist),
            albumName = album,
            albumId = albumId(album),
            contentUriString = contentUri(track.videoId),
            albumArtUriString = YouTubeMusicFormats.largeThumbnail(track.thumbnailUrl),
            duration = track.durationMs,
            genre = null,
            filePath = "",
            parentDirectoryPath = PARENT_DIRECTORY,
            sourceType = SourceType.YTMUSIC,
        )
    }

    fun toAlbum(track: YouTubeMusicTrack): AlbumEntity {
        val artist = artistName(track)
        val album = albumName(track)
        return AlbumEntity(
            id = albumId(album),
            title = album,
            artistName = artist,
            artistId = artistId(artist),
            songCount = 0,
            dateAdded = System.currentTimeMillis(),
            year = 0,
            albumArtUriString = YouTubeMusicFormats.largeThumbnail(track.thumbnailUrl),
        )
    }

    fun toArtist(track: YouTubeMusicTrack): ArtistEntity {
        val artist = artistName(track)
        return ArtistEntity(id = artistId(artist), name = artist, trackCount = 0, imageUrl = null)
    }

    fun toSong(track: YouTubeMusicTrack): Song = toEntity(track).toSong()
}
