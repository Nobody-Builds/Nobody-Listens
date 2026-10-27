package com.nobodybuilds.listens.data.service.wear

import androidx.media3.session.MediaController
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.shared.WearBrowseRequest
import com.nobodybuilds.listens.shared.WearLibraryItem
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File

private const val MAX_QUEUE_ITEMS = 20

/** Answers the watch's library browse requests and finds the songs for "play from context". */
internal class WearLibraryBrowser(
    private val musicRepository: MusicRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val getCurrentQueueItems: suspend () -> List<WearLibraryItem>,
) {
    /**
     * Get songs for a given context type and optional context ID.
     */
    suspend fun getSongsForContext(contextType: String, contextId: String?): List<Song> {
        return when (contextType) {
            "album" -> {
                val albumId = contextId?.toLongOrNull() ?: return emptyList()
                musicRepository.getSongsForAlbum(albumId).first()
            }
            "artist" -> {
                val artistId = contextId?.toLongOrNull() ?: return emptyList()
                musicRepository.getSongsForArtist(artistId).first()
            }
            "playlist" -> {
                val playlistId = contextId ?: return emptyList()
                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                    .find { it.id == playlistId } ?: return emptyList()
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
                // Maintain playlist order
                val songsById = songs.associateBy { it.id }
                playlist.songIds.mapNotNull { id -> songsById[id] }
            }
            "favorites" -> {
                musicRepository.getFavoriteSongsPage(limit = MAX_SONGS, offset = 0)
            }
            "all_songs" -> {
                musicRepository.getSongsPage(limit = MAX_SONGS, offset = 0)
            }
            WearBrowseRequest.SEARCH -> searchSongs(contextId)
            else -> {
                Timber.tag(TAG).w("Unknown context type: $contextType")
                emptyList()
            }
        }
    }

    /**
     * Get browse items based on the browse type, following the same pattern
     * as AutoMediaBrowseTree.
     */
    suspend fun getBrowseItems(browseType: String, contextId: String?): List<WearLibraryItem> {
        return when (browseType) {
            WearBrowseRequest.ROOT -> listOf(
                WearLibraryItem("favorites", "Favorites", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("playlists", "Playlists", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("albums", "Albums", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("artists", "Artists", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("all_songs", "All Songs", "", WearLibraryItem.TYPE_CATEGORY),
            )

            WearBrowseRequest.ALBUMS -> {
                musicRepository.getAlbumsPage(limit = MAX_ALBUMS, offset = 0, minTracks = 1)
                    .map { album ->
                        WearLibraryItem(
                            id = album.id.toString(),
                            title = album.title,
                            subtitle = "${album.artist} · ${album.songCount} songs",
                            type = WearLibraryItem.TYPE_ALBUM,
                        )
                    }
            }

            WearBrowseRequest.ARTISTS -> {
                musicRepository.getArtistsPage(limit = MAX_ARTISTS, offset = 0)
                    .map { artist ->
                        WearLibraryItem(
                            id = artist.id.toString(),
                            title = artist.name,
                            subtitle = "${artist.songCount} songs",
                            type = WearLibraryItem.TYPE_ARTIST,
                        )
                    }
            }

            WearBrowseRequest.PLAYLISTS -> {
                playlistPreferencesRepository.userPlaylistsFlow.first()
                    .map { playlist ->
                        WearLibraryItem(
                            id = playlist.id,
                            title = playlist.name,
                            subtitle = "${playlist.songIds.size} songs",
                            type = WearLibraryItem.TYPE_PLAYLIST,
                        )
                    }
            }

            WearBrowseRequest.FAVORITES -> {
                musicRepository.getFavoriteSongsPage(limit = MAX_SONGS, offset = 0)
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.ALL_SONGS -> {
                musicRepository.getSongsPage(limit = MAX_SONGS, offset = 0)
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.ALBUM_SONGS -> {
                val albumId = contextId?.toLongOrNull()
                    ?: throw IllegalArgumentException("Missing albumId for ALBUM_SONGS")
                musicRepository.getSongsForAlbum(albumId).first()
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.ARTIST_SONGS -> {
                val artistId = contextId?.toLongOrNull()
                    ?: throw IllegalArgumentException("Missing artistId for ARTIST_SONGS")
                musicRepository.getSongsForArtist(artistId).first()
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.PLAYLIST_SONGS -> {
                val playlistId = contextId
                    ?: throw IllegalArgumentException("Missing playlistId for PLAYLIST_SONGS")
                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                    .find { it.id == playlistId }
                    ?: throw IllegalArgumentException("Playlist not found: $playlistId")
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
                // Maintain playlist order
                val songsById = songs.associateBy { it.id }
                playlist.songIds
                    .mapNotNull { id -> songsById[id] }
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.QUEUE -> {
                getCurrentQueueItems()
            }

            WearBrowseRequest.SEARCH -> searchSongs(contextId).map { song -> song.toWearLibraryItem() }

            else -> {
                Timber.tag(TAG).w("Unknown browse type: $browseType")
                emptyList()
            }
        }
    }

    private suspend fun searchSongs(query: String?): List<Song> {
        if (query.isNullOrBlank()) return emptyList()
        return musicRepository.searchSongs(query.trim()).first().take(MAX_SEARCH_RESULTS)
    }

    private fun Song.toWearLibraryItem(): WearLibraryItem {
        return WearLibraryItem(
            id = id,
            title = title,
            subtitle = displayArtist,
            type = WearLibraryItem.TYPE_SONG,
            // Only songs with a file on this phone (Jellyfin/Navidrome paths are server paths).
            canSaveToWatch = (path.isNotBlank() && File(path).isFile) || contentUriString.startsWith("content://"),
        )
    }

    private companion object {
        const val TAG = "WearCommandReceiver"
        const val MAX_SONGS = 500
        const val MAX_ALBUMS = 200
        const val MAX_ARTISTS = 200
        const val MAX_SEARCH_RESULTS = 50
    }
}

internal fun buildCurrentQueueItems(controller: MediaController): List<WearLibraryItem> {
    val queueSize = controller.mediaItemCount
    if (queueSize <= 0) return emptyList()

    val currentIndex = controller.currentMediaItemIndex
        .takeIf { it in 0 until queueSize }
        ?: 0

    val queueIndices = buildList(queueSize) {
        for (index in currentIndex until queueSize) {
            add(index)
        }
        for (index in 0 until currentIndex) {
            add(index)
        }
    }

    return queueIndices
        .asSequence()
        .take(MAX_QUEUE_ITEMS)
        .map { actualIndex ->
        val mediaItem = controller.getMediaItemAt(actualIndex)
        val metadata = mediaItem.mediaMetadata
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: "Track ${actualIndex + 1}"
        val artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }.orEmpty()
        val isCurrentItem = actualIndex == currentIndex
        WearLibraryItem(
            id = actualIndex.toString(),
            title = title,
            subtitle = when {
                isCurrentItem && artist.isNotBlank() -> "Playing · $artist"
                isCurrentItem -> "Playing"
                artist.isNotBlank() -> artist
                else -> ""
            },
            type = WearLibraryItem.TYPE_SONG,
        )
    }
        .toList()
}
