package com.nobodybuilds.listens.presentation.viewmodel

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.data.media.MediaMapper
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.utils.MediaItemBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val SONG_ID_QUERY_CHUNK_SIZE = 900

/**
 * Turns song ids and media items into [Song]s for PlayerViewModel: the in-memory library
 * first, then the repository in chunks.
 */
@UnstableApi
internal class PlayerSongResolver(
    private val shared: PlayerSharedState,
    private val musicRepository: MusicRepository,
    private val libraryStateHolder: LibraryStateHolder,
    private val mediaMapperProvider: () -> MediaMapper
) {
    private val _playerUiState get() = shared.playerUiState
    private val mediaMapper: MediaMapper get() = mediaMapperProvider()

    /** Library source filter (phone / cloud / all) every shuffle path must respect. */
    suspend fun currentLibrarySourceFilter(): com.nobodybuilds.listens.data.model.StorageFilter =
        libraryStateHolder.effectiveStorageFilterNow()

    suspend fun resolvePlaybackQueueFromSortedIds(sortedIds: List<Long>): List<Song> {
        if (sortedIds.isEmpty()) return emptyList()

        val orderedIds = sortedIds.map(Long::toString)
        val cachedSongsById = libraryStateHolder.allSongsById.value
        val missingIds = ArrayList<String>()
        val cachedQueue = ArrayList<Song>(orderedIds.size)

        withContext(Dispatchers.Default) {
            orderedIds.forEach { songId ->
                val cachedSong = cachedSongsById[songId]
                if (cachedSong != null) {
                    cachedQueue.add(cachedSong)
                } else {
                    missingIds.add(songId)
                }
            }
        }

        if (missingIds.isEmpty()) {
            return cachedQueue
        }

        val missingSongsById = getSongsByIdsChunked(missingIds).associateBy { it.id }
        return withContext(Dispatchers.Default) {
            val finalQueue = ArrayList<Song>(orderedIds.size)
            orderedIds.forEach { songId ->
                val resolvedSong = cachedSongsById[songId] ?: missingSongsById[songId]
                if (resolvedSong != null) {
                    finalQueue.add(resolvedSong)
                }
            }
            finalQueue
        }
    }

    private suspend fun getSongsByIdsChunked(songIds: List<String>): List<Song> {
        if (songIds.isEmpty()) return emptyList()
        if (songIds.size <= SONG_ID_QUERY_CHUNK_SIZE) {
            return musicRepository.getSongsByIds(songIds).first()
        }

        return withContext(Dispatchers.IO) {
            buildList(songIds.size) {
                songIds.chunked(SONG_ID_QUERY_CHUNK_SIZE).forEach { chunk ->
                    addAll(musicRepository.getSongsByIds(chunk).first())
                }
            }
        }
    }

    suspend fun hydrateSongsIfNeeded(songs: List<Song>): List<Song> {
        if (songs.isEmpty() || songs.none { it.requiresHydration() }) return songs
        val hydratedSongs = getSongsByIdsChunked(songs.map { it.id })
        if (hydratedSongs.isEmpty()) return songs
        val hydratedById = hydratedSongs.associateBy { it.id }
        return songs.mapNotNull { original ->
            hydratedById[original.id] ?: original.takeIf { !original.requiresHydration() }
        }
    }

    fun resolveSongFromMediaItem(
        mediaItem: MediaItem,
        allSongsById: Map<String, Song>? = null
    ): Song? {
        val resolvedSong =
            allSongsById?.get(mediaItem.mediaId)
                ?: libraryStateHolder.allSongsById.value[mediaItem.mediaId]
                ?: _playerUiState.value.currentPlaybackQueue.find { it.id == mediaItem.mediaId }
                ?: mediaMapper.resolveSongFromMediaItem(mediaItem)

        return resolvedSong?.let { normalizeArtworkForResolvedSong(it, mediaItem) }
    }

    private fun normalizeArtworkForResolvedSong(song: Song, mediaItem: MediaItem): Song {
        val metadataArtwork =
            mediaItem.mediaMetadata.artworkUri?.toString()?.takeIf { it.isNotBlank() }
                ?: mediaItem.mediaMetadata.extras
                    ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART)
                    ?.takeIf { it.isNotBlank() }

        return when {
            metadataArtwork == null && song.albumArtUriString != null -> song.copy(albumArtUriString = null)
            metadataArtwork != null && song.albumArtUriString != metadataArtwork ->
                song.copy(albumArtUriString = metadataArtwork)
            else -> song
        }
    }
}
