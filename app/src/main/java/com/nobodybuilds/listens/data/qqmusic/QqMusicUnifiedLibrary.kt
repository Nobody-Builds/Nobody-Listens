package com.nobodybuilds.listens.data.qqmusic

import com.nobodybuilds.listens.data.database.AlbumEntity
import com.nobodybuilds.listens.data.database.ArtistEntity
import com.nobodybuilds.listens.data.database.MusicDao
import com.nobodybuilds.listens.data.database.QqMusicDao
import com.nobodybuilds.listens.data.database.SongArtistCrossRef
import com.nobodybuilds.listens.data.database.SongEntity
import com.nobodybuilds.listens.data.database.SourceType
import com.nobodybuilds.listens.data.stream.CloudMusicUtils
import kotlin.math.absoluteValue

/** Copies the synced QQ Music songs into the unified music library: songs, albums and artists. */
internal class QqMusicUnifiedLibrary(
    private val dao: QqMusicDao,
    private val musicDao: MusicDao
) {
    private companion object {
        private const val QQ_MUSIC_SONG_ID_OFFSET = 6_000_000_000_000L
        private const val QQ_MUSIC_ALBUM_ID_OFFSET = 7_000_000_000_000L
        private const val QQ_MUSIC_ARTIST_ID_OFFSET = 8_000_000_000_000L
        private const val QQ_MUSIC_PARENT_DIRECTORY = "/Cloud/QQMusic"
        private const val QQ_MUSIC_GENRE = "QQ Music"
    }

    suspend fun syncUnifiedLibrarySongsFromQqMusic() {
        val qqMusicSongs = dao.getAllQqMusicSongsList()
        val existingUnifiedIds = musicDao.getAllQqMusicSongIds()

        if (qqMusicSongs.isEmpty()) {
            if (existingUnifiedIds.isNotEmpty()) {
                musicDao.clearAllQqMusicSongs()
            }
            return
        }

        val songs = ArrayList<SongEntity>(qqMusicSongs.size)
        val artists = LinkedHashMap<Long, ArtistEntity>()
        val albums = LinkedHashMap<Long, AlbumEntity>()
        val crossRefs = mutableListOf<SongArtistCrossRef>()

        qqMusicSongs.forEach { qqSong ->
            val songId = toUnifiedSongId(qqSong.songMid)
            val artistNames = parseArtistNames(qqSong.artist)
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

            val albumId = toUnifiedAlbumId(qqSong.albumMid ?: "", qqSong.album)
            val albumName = qqSong.album.ifBlank { "Unknown Album" }
            albums.putIfAbsent(
                albumId,
                AlbumEntity(
                    id = albumId,
                    title = albumName,
                    artistName = primaryArtistName,
                    artistId = primaryArtistId,
                    songCount = 0,
                    dateAdded = qqSong.dateAdded,
                    year = 0,
                    albumArtUriString = qqSong.albumArtUrl
                )
            )

            songs.add(
                SongEntity(
                    id = songId,
                    title = qqSong.title,
                    artistName = qqSong.artist.ifBlank { primaryArtistName },
                    artistId = primaryArtistId,
                    albumArtist = null,
                    albumName = albumName,
                    albumId = albumId,
                    contentUriString = "qqmusic://${qqSong.songMid}",
                    albumArtUriString = qqSong.albumArtUrl,
                    duration = qqSong.duration,
                    genre = QQ_MUSIC_GENRE,
                    filePath = "",
                    parentDirectoryPath = QQ_MUSIC_PARENT_DIRECTORY,
                    isFavorite = false,
                    lyrics = null,
                    trackNumber = 0,
                    year = 0,
                    dateAdded = qqSong.dateAdded.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    mimeType = qqSong.mimeType,
                    bitrate = qqSong.bitrate,
                    sampleRate = null,
                    telegramChatId = null,
                    telegramFileId = null,
                    sourceType = SourceType.QQMUSIC
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

    private fun parseArtistNames(rawArtist: String): List<String> =
        CloudMusicUtils.parseArtistNames(rawArtist)

    fun toUnifiedSongId(mid: String): Long {
        val hash = mid.hashCode().toLong().absoluteValue
        return -(QQ_MUSIC_SONG_ID_OFFSET + hash)
    }

    private fun toUnifiedAlbumId(albumMid: String, albumName: String): Long {
        val normalized = if (albumMid.isNotBlank()) albumMid.hashCode().toLong().absoluteValue 
                         else albumName.lowercase().hashCode().toLong().absoluteValue
        return -(QQ_MUSIC_ALBUM_ID_OFFSET + normalized)
    }

    private fun toUnifiedArtistId(artistName: String): Long {
        return -(QQ_MUSIC_ARTIST_ID_OFFSET + artistName.lowercase().hashCode().toLong().absoluteValue)
    }
}
