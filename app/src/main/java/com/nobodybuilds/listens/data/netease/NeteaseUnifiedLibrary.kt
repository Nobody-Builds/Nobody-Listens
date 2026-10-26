package com.nobodybuilds.listens.data.netease

import com.nobodybuilds.listens.data.database.AlbumEntity
import com.nobodybuilds.listens.data.database.ArtistEntity
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.NeteaseDao
import com.nobodybuilds.listens.data.database.SongArtistCrossRef
import com.nobodybuilds.listens.data.database.SongEntity
import com.nobodybuilds.listens.data.stream.CloudMusicUtils
import kotlin.math.absoluteValue

/** Copies the synced Netease songs into the unified music library: songs, albums and artists. */
internal class NeteaseUnifiedLibrary(
    private val dao: NeteaseDao,
    private val musicDao: MusicDao
) {
    private companion object {
        private const val NETEASE_SONG_ID_OFFSET = 3_000_000_000_000L
        private const val NETEASE_ALBUM_ID_OFFSET = 4_000_000_000_000L
        private const val NETEASE_ARTIST_ID_OFFSET = 5_000_000_000_000L
        private const val NETEASE_PARENT_DIRECTORY = "/Cloud/Netease"
        private const val NETEASE_GENRE = "Netease Cloud"
    }

    suspend fun syncUnifiedLibrarySongsFromNetease() {
        val neteaseSongs = dao.getAllNeteaseSongsList()
        val existingUnifiedNeteaseIds = musicDao.getAllNeteaseSongIds()

        if (neteaseSongs.isEmpty()) {
            if (existingUnifiedNeteaseIds.isNotEmpty()) {
                musicDao.clearAllNeteaseSongs()
            }
            return
        }

        val songs = ArrayList<SongEntity>(neteaseSongs.size)
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        neteaseSongs.forEach { neteaseSong ->
            val songId = toUnifiedSongId(neteaseSong.neteaseId)
            val artistNames = parseArtistNames(neteaseSong.artist)
            val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
            val primaryArtistId = toUnifiedArtistId(primaryArtistName)

            artistNames.forEachIndexed { index, artistName ->
                val artistId = toUnifiedArtistId(artistName)
                artists.putIfAbsent(
                    artistId,
                    ArtistEntity(
                        id = artistId,
                        name = artistName,
                        trackCount = 0,
                        imageUrl = null
                    )
                )
                crossRefs.add(
                    SongArtistCrossRef(
                        songId = songId,
                        artistId = artistId,
                        isPrimary = index == 0
                    )
                )
            }

            val albumId = toUnifiedAlbumId(neteaseSong.albumId, neteaseSong.album)
            val albumName = neteaseSong.album.ifBlank { "Unknown Album" }
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = primaryArtistName,
                    artistId = primaryArtistId,
                    songCount = 0,
                    dateAdded = neteaseSong.dateAdded,
                    year = 0,
                    albumArtUriString = neteaseSong.albumArtUrl
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = neteaseSong.title,
                    artistName = neteaseSong.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = null,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "netease://${neteaseSong.neteaseId}",
                    albumArtUriString = neteaseSong.albumArtUrl,
                    duration = neteaseSong.duration,
                    genre = NETEASE_GENRE,
                    filePath = "",
                    parentDirectoryPath = NETEASE_PARENT_DIRECTORY,
                    isFavorite = false,
                    lyrics = null,
                    trackNumber = 0,
                    year = 0,
                    dateAdded = neteaseSong.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    mimeType = neteaseSong.mimeType,
                    bitrate = neteaseSong.bitrate,
                    sampleRate = null,
                    telegramChatId = null,
                    telegramFileId = null
                )
            )
        }

        val albumCounts = songs.groupingBy { it.albumId }.eachCount()
        val finalAlbums = albums.values.map { album ->
            album.copy(songCount = albumCounts[album.id] ?: 0)
        }

        val currentUnifiedSongIds = songs.map { it.id }.toSet()
        val deletedUnifiedSongIds = existingUnifiedNeteaseIds.filter { it !in currentUnifiedSongIds }

        musicDao.incrementalSyncMusicData(
            songs = songs,
            albums = finalAlbums,
            artists = artists.values.toList(),
            crossRefs = crossRefs,
            deletedSongIds = deletedUnifiedSongIds
        )
    }

    private fun parseArtistNames(rawArtist: String): List<String> =
        CloudMusicUtils.parseArtistNames(rawArtist)

    fun toUnifiedSongId(neteaseId: Long): Long {
        return -(NETEASE_SONG_ID_OFFSET + neteaseId.absoluteValue)
    }

    private fun toUnifiedAlbumId(albumId: Long, albumName: String): Long {
        val normalized = if (albumId > 0L) albumId.absoluteValue else albumName.lowercase().hashCode().toLong().absoluteValue
        return -(NETEASE_ALBUM_ID_OFFSET + normalized)
    }

    private fun toUnifiedArtistId(artistName: String): Long {
        return -(NETEASE_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }
}
