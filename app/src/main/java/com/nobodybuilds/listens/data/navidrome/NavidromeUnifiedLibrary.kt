package com.nobodybuilds.listens.data.navidrome

import com.nobodybuilds.listens.data.database.AlbumEntity
import com.nobodybuilds.listens.data.database.ArtistEntity
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.NavidromeDao
import com.nobodybuilds.listens.data.database.SongArtistCrossRef
import com.nobodybuilds.listens.data.database.SongEntity
import com.nobodybuilds.listens.data.database.SourceType
import com.nobodybuilds.listens.data.stream.CloudMusicUtils
import kotlin.math.absoluteValue

/** Copies the synced Navidrome songs into the unified music library: songs, albums and artists. */
internal class NavidromeUnifiedLibrary(
    private val dao: NavidromeDao,
    private val musicDao: MusicDao
) {
    private companion object {
        // ID offsets for unified library (following Netease: 3-5, QQ: 6-8)
        // Using negative offsets to prevent collisions with MediaStore IDs
        private const val NAVIDROME_SONG_ID_OFFSET = 9_000_000_000_000L
        private const val NAVIDROME_ALBUM_ID_OFFSET = 10_000_000_000_000L
        private const val NAVIDROME_ARTIST_ID_OFFSET = 11_000_000_000_000L
        private const val NAVIDROME_PARENT_DIRECTORY = "/Cloud/Navidrome"
        private const val NAVIDROME_GENRE = "Navidrome"
    }

    suspend fun syncUnifiedLibrarySongsFromNavidrome() {
        val navidromeSongs = dao.getAllNavidromeSongsList()
        val existingUnifiedIds = musicDao.getAllNavidromeSongIds()

        if (navidromeSongs.isEmpty()) {
            if (existingUnifiedIds.isNotEmpty()) {
                musicDao.clearAllNavidromeSongs()
            }
            return
        }

        val songs = ArrayList<SongEntity>(navidromeSongs.size)
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        navidromeSongs.forEach { navidromeSong ->
            val songId = toUnifiedSongId(navidromeSong.navidromeId)
            val artistNames = parseArtistNames(navidromeSong.artist)
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

            val albumId = toUnifiedAlbumId(navidromeSong.albumId, navidromeSong.album)
            val albumName = navidromeSong.album.ifBlank { "Unknown Album" }
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = primaryArtistName,
                    artistId = primaryArtistId,
                    songCount = 0,
                    dateAdded = navidromeSong.dateAdded,
                    year = navidromeSong.year,
                    albumArtUriString = navidromeSong.coverArtId?.takeIf { it.isNotBlank() }
                        ?.let { "navidrome_cover://$it" }
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = navidromeSong.title,
                    artistName = navidromeSong.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = null,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "navidrome://${navidromeSong.navidromeId}",
                    albumArtUriString = navidromeSong.coverArtId?.takeIf { it.isNotBlank() }
                        ?.let { "navidrome_cover://$it" },
                    duration = navidromeSong.duration,
                    genre = navidromeSong.genre ?: NAVIDROME_GENRE,
                    filePath = navidromeSong.path,
                    parentDirectoryPath = NAVIDROME_PARENT_DIRECTORY,
                    isFavorite = false,
                    lyrics = null,
                    trackNumber = navidromeSong.trackNumber,
                    year = navidromeSong.year,
                    dateAdded = navidromeSong.dateAdded.takeIf { it > 0 }
                        ?: System.currentTimeMillis(),
                    mimeType = navidromeSong.mimeType,
                    bitrate = navidromeSong.bitRate?.let { it * 1000 },
                    sampleRate = null,
                    telegramChatId = null,
                    telegramFileId = null,
                    sourceType = SourceType.NAVIDROME
                )
            )
        }

        val albumCounts = songs.groupingBy { it.albumId }.eachCount()
        val finalAlbums = albums.values.map { album ->
            album.copy(songCount = albumCounts[album.id] ?: 0)
        }

        val currentUnifiedIds = songs.map { it.id }.toSet()
        val deletedUnifiedIds = existingUnifiedIds.filter { it !in currentUnifiedIds }

        musicDao.incrementalSyncMusicData(
            songs = songs,
            albums = finalAlbums,
            artists = artists.values.toList(),
            crossRefs = crossRefs,
            deletedSongIds = deletedUnifiedIds
        )
    }

    // ─── Utility Methods ───────────────────────────────────────────────────

    private fun parseArtistNames(rawArtist: String): List<String> =
        CloudMusicUtils.parseArtistNames(rawArtist)

    fun toUnifiedSongId(navidromeId: String): Long {
        return -(NAVIDROME_SONG_ID_OFFSET + navidromeId.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedAlbumId(albumId: String?, albumName: String): Long {
        val normalized = if (!albumId.isNullOrBlank()) {
            albumId.hashCode().toLong().absoluteValue
        } else {
            albumName.lowercase().hashCode().toLong().absoluteValue
        }
        return -(NAVIDROME_ALBUM_ID_OFFSET + normalized)
    }

    private fun toUnifiedArtistId(artistName: String): Long {
        return -(NAVIDROME_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }
}
