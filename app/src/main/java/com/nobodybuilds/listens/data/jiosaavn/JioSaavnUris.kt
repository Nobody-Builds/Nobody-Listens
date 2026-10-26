package com.nobodybuilds.listens.data.jiosaavn

import com.nobodybuilds.listens.data.database.AlbumEntity
import com.nobodybuilds.listens.data.database.ArtistEntity
import com.nobodybuilds.listens.data.database.SongEntity
import com.nobodybuilds.listens.data.database.SourceType
import com.nobodybuilds.listens.data.database.toSong
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.stream.CloudStreamSecurity
import kotlin.math.absoluteValue

object JioSaavnUris {
    const val SCHEME = "jiosaavn"
    const val PARENT_DIRECTORY = "/JioSaavn"
    const val SERVICE_NAME = "JioSaavn"
    private const val SONG_ID_OFFSET = 18_000_000_000_000L
    private const val ALBUM_ID_OFFSET = 19_000_000_000_000L
    private const val ARTIST_ID_OFFSET = 20_000_000_000_000L

    fun contentUri(songId: String): String = "$SCHEME://track/$songId"

    fun songIdFrom(uriString: String): String? {
        if (!uriString.startsWith("$SCHEME://")) return null
        val rest = uriString.removePrefix("$SCHEME://").substringBefore("?").substringBefore("#")
        return rest.substringAfter("/", missingDelimiterValue = "").takeIf(CloudStreamSecurity::validateJioSaavnSongId)
    }

    /**
     * Song id of a song the player is streaming: either its `jiosaavn://` content URI or
     * the local proxy URL it resolves to (`http://127.0.0.1:<port>/jiosaavn/<id>`).
     */
    fun songIdFromPlaybackUri(uri: android.net.Uri): String? = songIdFromPlaybackUri(uri.toString())

    fun songIdFromPlaybackUri(uriString: String): String? =
        songIdFrom(uriString)
            ?: PROXY_URL_REGEX.matchEntire(uriString)?.groupValues?.get(1)?.takeIf(CloudStreamSecurity::validateJioSaavnSongId)

    private val PROXY_URL_REGEX = Regex("""http://127\.0\.0\.1:\d+/jiosaavn/([A-Za-z0-9_-]{6,20})(?:[?#].*)?""")

    /** The kind and token of a pasted jiosaavn.com song, album or playlist link. */
    fun linkFrom(text: String): Pair<String, String>? {
        val match = LINK_REGEX.find(text.trim()) ?: return null
        val type = when (match.groupValues[1]) {
            "song" -> "song"
            "album" -> "album"
            else -> "playlist"
        }
        return type to match.groupValues[2]
    }

    private val LINK_REGEX = Regex(
        """jiosaavn\.com/(?:s/)?(song|album|featured|playlist)/(?:[^/\s]+/)*?([A-Za-z0-9_,-]+?)(?:[?#\s]|$)"""
    )

    /** Library ids are negative like the other cloud sources, so they never clash with MediaStore ids. */
    fun songId(id: String): Long = -(SONG_ID_OFFSET + id.hashCode().toLong().absoluteValue)

    fun albumId(album: String): Long = -(ALBUM_ID_OFFSET + album.lowercase().hashCode().toLong().absoluteValue)

    fun artistId(artist: String): Long = -(ARTIST_ID_OFFSET + artist.lowercase().hashCode().toLong().absoluteValue)

    fun artistName(track: JioSaavnTrack): String = track.artist.ifBlank { SERVICE_NAME }

    fun albumName(track: JioSaavnTrack): String = track.album.ifBlank { SERVICE_NAME }

    fun toEntity(track: JioSaavnTrack): SongEntity {
        val artist = artistName(track)
        val album = albumName(track)
        return SongEntity(
            id = songId(track.id),
            title = track.title,
            artistName = artist,
            artistId = artistId(artist),
            albumName = album,
            albumId = albumId(album),
            contentUriString = contentUri(track.id),
            albumArtUriString = track.thumbnailUrl,
            duration = track.durationMs,
            genre = null,
            filePath = "",
            parentDirectoryPath = PARENT_DIRECTORY,
            year = track.year.toIntOrNull() ?: 0,
            sourceType = SourceType.JIOSAAVN,
        )
    }

    fun toAlbum(track: JioSaavnTrack): AlbumEntity {
        val artist = artistName(track)
        val album = albumName(track)
        return AlbumEntity(
            id = albumId(album),
            title = album,
            artistName = artist,
            artistId = artistId(artist),
            songCount = 0,
            dateAdded = System.currentTimeMillis(),
            year = track.year.toIntOrNull() ?: 0,
            albumArtUriString = track.thumbnailUrl,
        )
    }

    fun toArtist(track: JioSaavnTrack): ArtistEntity {
        val artist = artistName(track)
        return ArtistEntity(id = artistId(artist), name = artist, trackCount = 0, imageUrl = null)
    }

    fun toSong(track: JioSaavnTrack): Song = toEntity(track).toSong()

    /** The track behind a library song, for actions that start from the song (radio, download). */
    fun toTrack(song: Song, id: String): JioSaavnTrack = JioSaavnTrack(
        id = id,
        title = song.title,
        artist = song.displayArtist.takeIf { it != SERVICE_NAME }.orEmpty(),
        album = song.album.takeIf { it != SERVICE_NAME }.orEmpty(),
        thumbnailUrl = song.albumArtUriString,
        durationMs = song.duration,
    )
}
