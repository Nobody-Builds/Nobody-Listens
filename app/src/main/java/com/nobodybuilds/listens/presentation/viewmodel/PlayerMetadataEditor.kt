package com.nobodybuilds.listens.presentation.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.database.AlbumArtThemeDao
import com.nobodybuilds.listens.data.media.CoverArtUpdate
import com.nobodybuilds.listens.data.media.ImageCacheManager
import com.nobodybuilds.listens.data.model.Lyrics
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.utils.LyricsUtils
import com.nobodybuilds.listens.utils.MediaItemBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Writes song tags, cover art and .lrc lyrics for PlayerViewModel (one song or a batch genre
 * change), asking for MediaStore write access on Android 11+ first, then refreshes the UI.
 */
@UnstableApi
@SuppressLint("LogNotTimber")
internal class PlayerMetadataEditor(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val albumArtThemeDao: AlbumArtThemeDao,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val themeStateHolder: ThemeStateHolder,
    private val currentSongSync: CurrentSongStateSync,
    private val imageCacheManagerProvider: () -> ImageCacheManager
) {
    private data class PendingMetadataEdit(
        val song: Song,
        val title: String,
        val artist: String,
        val album: String,
        val genre: String,
        val lyrics: String,
        val trackNumber: Int,
        val discNumber: Int?,
        val replayGainTrackGainDb: String?,
        val replayGainAlbumGainDb: String?,
        val coverArtUpdate: CoverArtUpdate?
    )

    private data class PendingLyricsSave(
        val song: Song,
        val lyrics: Lyrics,
        val preferSynced: Boolean
    )

    private val scope: CoroutineScope get() = shared.scope
    private val _playerUiState get() = shared.playerUiState
    private val _selectedSongForInfo get() = shared.selectedSongForInfo
    private val _toastEvents get() = shared.toastEvents
    private val _writePermissionRequest get() = shared.writePermissionRequest
    private val imageCacheManager: ImageCacheManager get() = imageCacheManagerProvider()

    private var pendingMetadataEdit: PendingMetadataEdit? = null
    private var pendingLyricsSave: PendingLyricsSave? = null
    private var pendingBatchGenreEdit: Pair<List<Song>, String>? = null

    fun editSongMetadata(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        newReplayGainTrackGainDb: String? = null,
        newReplayGainAlbumGainDb: String? = null,
        coverArtUpdate: CoverArtUpdate?,
    ) {
        scope.launch {
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Starting editSongMetadata via Holder")

            // On Android 11+, request MediaStore write permission for local songs
            val songId = song.id.toLongOrNull()
            if (songId != null && songId > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val intentSender = com.nobodybuilds.listens.utils.MediaStorePermissionHelper
                    .createWriteRequestForSong(context, songId)
                if (intentSender != null) {
                    // Store pending edit and request permission from the UI
                    pendingMetadataEdit = PendingMetadataEdit(
                        song = song,
                        title = newTitle,
                        artist = newArtist,
                        album = newAlbum,
                        genre = newGenre,
                        lyrics = newLyrics,
                        trackNumber = newTrackNumber,
                        discNumber = newDiscNumber,
                        replayGainTrackGainDb = newReplayGainTrackGainDb,
                        replayGainAlbumGainDb = newReplayGainAlbumGainDb,
                        coverArtUpdate = coverArtUpdate
                    )
                    _writePermissionRequest.emit(intentSender)
                    return@launch
                }
            }

            performMetadataEdit(song, newTitle, newArtist, newAlbum, newGenre, newLyrics,
                newTrackNumber, newDiscNumber, newReplayGainTrackGainDb, newReplayGainAlbumGainDb, coverArtUpdate)
        }
    }

    /** Called from the UI after the user approves or denies the MediaStore write permission. */
    fun onWritePermissionResult(granted: Boolean) {
        // Handle batch genre edit
        val batchGenre = pendingBatchGenreEdit
        if (batchGenre != null) {
            pendingBatchGenreEdit = null
            if (!granted) {
                scope.launch {
                    _toastEvents.emit(context.getString(R.string.player_permission_denied_edit_files))
                }
                return
            }
            scope.launch { performBatchEditGenre(batchGenre.first, batchGenre.second) }
            return
        }

        // Handle lyrics save retry
        val pendingLyrics = pendingLyricsSave
        if (pendingLyrics != null) {
            pendingLyricsSave = null
            if (!granted) {
                scope.launch {
                    _toastEvents.emit(context.getString(R.string.player_permission_denied_save_lyrics))
                }
                return
            }
            performLyricsSave(pendingLyrics.song, pendingLyrics.lyrics, pendingLyrics.preferSynced)
            return
        }

        // Handle single metadata edit
        val pending = pendingMetadataEdit ?: return
        pendingMetadataEdit = null
        if (!granted) {
            com.nobodybuilds.listens.data.media.PendingExtendedTags.clear(pending.song.id)
            scope.launch {
                _toastEvents.emit(context.getString(R.string.player_permission_denied_edit_this_file))
            }
            return
        }
        scope.launch {
            performMetadataEdit(
                pending.song, pending.title, pending.artist, pending.album,
                pending.genre, pending.lyrics, pending.trackNumber, pending.discNumber,
                pending.replayGainTrackGainDb, pending.replayGainAlbumGainDb, pending.coverArtUpdate
            )
        }
    }

    fun saveLyricsToFile(song: Song, lyrics: Lyrics, preferSynced: Boolean) {
        val lrcContent = LyricsUtils.toLrcString(lyrics, preferSynced)
        if (lrcContent.isEmpty()) {
            scope.launch { _toastEvents.emit(context.getString(R.string.no_lyrics_to_save)) }
            return
        }

        val songFile = java.io.File(song.path)
        val lrcFile = java.io.File(songFile.parentFile, "${songFile.nameWithoutExtension}.lrc")

        // Android 11+ check: if file exists and we might not have permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && lrcFile.exists() && !lrcFile.canWrite()) {
            val uri = com.nobodybuilds.listens.utils.MediaStorePermissionHelper.getMediaStoreUri(context, lrcFile.absolutePath)
            if (uri != null) {
                val intentSender = com.nobodybuilds.listens.utils.MediaStorePermissionHelper.createWriteRequestIntentSender(context, listOf(uri))
                if (intentSender != null) {
                    pendingLyricsSave = PendingLyricsSave(song, lyrics, preferSynced)
                    scope.launch { _writePermissionRequest.emit(intentSender) }
                    return
                }
            }
        }

        performLyricsSave(song, lyrics, preferSynced)
    }

    private fun performLyricsSave(song: Song, lyrics: Lyrics, preferSynced: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                val songFile = java.io.File(song.path)
                val lrcFile = java.io.File(songFile.parentFile, "${songFile.nameWithoutExtension}.lrc")
                val lrcContent = LyricsUtils.toLrcString(lyrics, preferSynced)

                lrcFile.writeText(lrcContent, Charsets.UTF_8)
                _toastEvents.emit(context.getString(R.string.lyrics_saved_successfully))
                
                // If it was the current song, we might want to refresh the lyrics in state if it migrated from remote to local
                if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                    currentSongSync.loadLyricsForCurrentSong()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save lyrics to file")
                _toastEvents.emit(context.getString(R.string.lyrics_save_failed))
            }
        }
    }

    private suspend fun performMetadataEdit(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        newReplayGainTrackGainDb: String?,
        newReplayGainAlbumGainDb: String?,
        coverArtUpdate: CoverArtUpdate?,
    ) {
        val previousAlbumArt = song.albumArtUriString

        val result = metadataEditStateHolder.saveMetadata(
            extendedTags = com.nobodybuilds.listens.data.media.PendingExtendedTags.take(song.id),
            song = song,
            newTitle = newTitle,
            newArtist = newArtist,
            newAlbum = newAlbum,
            newGenre = newGenre,
            newLyrics = newLyrics,
            newTrackNumber = newTrackNumber,
            newDiscNumber = newDiscNumber,
            newReplayGainTrackGainDb = newReplayGainTrackGainDb,
            newReplayGainAlbumGainDb = newReplayGainAlbumGainDb,
            coverArtUpdate = coverArtUpdate
        )

        Log.e("PlayerViewModel", "METADATA_EDIT_VM: Result success=${result.success}")

        if (result.success && result.updatedSong != null) {
            val updatedSong = result.updatedSong
            val refreshedAlbumArtUri = result.updatedAlbumArtUri

            invalidateCoverArtCaches(previousAlbumArt, refreshedAlbumArtUri)

            _playerUiState.update { state ->
                val updatedQueue = state.currentPlaybackQueue.replaceSong(updatedSong)
                if (updatedQueue === state.currentPlaybackQueue) {
                    state
                } else {
                    state.copy(currentPlaybackQueue = updatedQueue)
                }
            }

            // libraryStateHolder.updateSong() below handles the SSOT update

            // Update the LibraryStateHolder which drives the UI
            libraryStateHolder.updateSong(updatedSong)

            if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = updatedSong,
                        lyrics = result.parsedLyrics
                    )
                }

                // Update the player's current MediaItem to refresh notification artwork
                // This is efficient: only replaces metadata, not the media stream
                val controller = playbackStateHolder.mediaController
                if (controller != null) {
                    val currentIndex = controller.currentMediaItemIndex
                    if (currentIndex >= 0 && currentIndex < controller.mediaItemCount) {
                        val currentPosition = controller.currentPosition
                        val newMediaItem = MediaItemBuilder.build(updatedSong)
                        controller.replaceMediaItem(currentIndex, newMediaItem)
                        // Restore position since replaceMediaItem may reset it
                        controller.seekTo(currentIndex, currentPosition)
                    }
                }
            }

            if (_selectedSongForInfo.value?.id == song.id) {
                _selectedSongForInfo.value = updatedSong
            }

            if (coverArtUpdate != null) {
                purgeAlbumArtThemes(previousAlbumArt, updatedSong.albumArtUriString)
                val paletteTargetUri = updatedSong.albumArtUriString
                if (paletteTargetUri != null) {
                    themeStateHolder.getAlbumColorSchemeFlow(paletteTargetUri)
                    val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                    themeStateHolder.extractAndGenerateColorScheme(paletteTargetUri.toUri(), currentUri, isPreload = false)
                } else {
                    val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                    themeStateHolder.extractAndGenerateColorScheme(null, currentUri, isPreload = false)
                }
            }

            // No need for full library sync - file, MediaStore, and local DB are already updated
            // syncManager.sync() was removed to avoid unnecessary wait time
            com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().success()
            _toastEvents.emit(context.getString(R.string.metadata_updated_successfully))
        } else {
            val errorMessage = result.getUserFriendlyErrorMessage()
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Failed - ${result.error}: $errorMessage")
            com.nobodybuilds.listens.presentation.haptics.NobodyListensHaptics.get().error()
            _toastEvents.emit(errorMessage)
        }
    }

    private fun invalidateCoverArtCaches(vararg uriStrings: String?) {
        imageCacheManager.invalidateCoverArtCaches(*uriStrings)
    }

    private suspend fun purgeAlbumArtThemes(vararg uriStrings: String?) {
        val uris = uriStrings.mapNotNull { it?.takeIf(String::isNotBlank) }.distinct()
        if (uris.isEmpty()) return

        withContext(Dispatchers.IO) {
            albumArtThemeDao.deleteThemesByUris(uris)
        }

        uris.forEach { uri ->
            // Cache invalidation delegated to ThemeStateHolder (if implemented) or relied on re-generation
            // individualAlbumColorSchemes was removed.
        }
    }

    suspend fun forceRegenerateAlbumPaletteForSong(song: Song): Boolean {
        val albumArtUri = song.albumArtUriString?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            // Full reset: clear all cached variants for this URI and recreate every style from scratch.
            themeStateHolder.forceRegenerateColorScheme(
                uriString = albumArtUri,
                regenerateAllStyles = true
            )
            true
        }.getOrDefault(false)
    }

    fun batchEditGenre(songs: List<Song>, newGenre: String) {
        if (songs.isEmpty()) return

        scope.launch {
            // On Android 11+, request write permission for all local songs upfront
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val uris = songs.mapNotNull { song ->
                    song.id.toLongOrNull()?.takeIf { it > 0 }?.let { id ->
                        com.nobodybuilds.listens.utils.MediaStorePermissionHelper.getMediaStoreUri(id)
                    }
                }
                if (uris.isNotEmpty()) {
                    val intentSender = com.nobodybuilds.listens.utils.MediaStorePermissionHelper
                        .createWriteRequestIntentSender(context, uris)
                    if (intentSender != null) {
                        pendingBatchGenreEdit = songs to newGenre
                        _writePermissionRequest.emit(intentSender)
                        return@launch
                    }
                }
            }

            performBatchEditGenre(songs, newGenre)
        }
    }

    private suspend fun performBatchEditGenre(songs: List<Song>, newGenre: String) {
            Log.d("PlayerViewModel", "Starting batch genre update for ${songs.size} songs to '$newGenre'")
            _toastEvents.emit(context.getString(R.string.player_updating_n_songs, songs.size))

            var successCount = 0
            var failCount = 0

            songs.forEach { song ->
                val sourceSong = if (song.lyrics != null) {
                    song
                } else {
                    withContext(Dispatchers.IO) {
                        musicRepository.getSong(song.id).first()
                    } ?: song
                }

                val result = metadataEditStateHolder.saveMetadata(
                    song = sourceSong,
                    newTitle = sourceSong.title,
                    newArtist = sourceSong.artist,
                    newAlbum = sourceSong.album,
                    newGenre = newGenre,
                    newLyrics = sourceSong.lyrics ?: "",
                    newTrackNumber = sourceSong.trackNumber,
                    newDiscNumber =  sourceSong.discNumber,
                    coverArtUpdate = null
                )

                if (result.success && result.updatedSong != null) {
                    successCount++
                    val updatedSong = result.updatedSong

                    // Optimistic update of UI flows
                    // libraryStateHolder.updateSong() below handles the SSOT update
                    libraryStateHolder.updateSong(updatedSong)

                    if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                        playbackStateHolder.updateStablePlayerState { it.copy(currentSong = updatedSong) }
                        val controller = playbackStateHolder.mediaController
                        if (controller != null) {
                            val idx = controller.currentMediaItemIndex
                            if (idx != C.INDEX_UNSET) {
                                controller.replaceMediaItem(idx, MediaItemBuilder.build(updatedSong))
                            }
                        }
                    }
                } else {
                    failCount++
                }
            }

            if (failCount == 0) {
                _toastEvents.emit(context.getString(R.string.player_batch_genre_updated_all, successCount))
            } else {
                _toastEvents.emit(
                    context.getString(R.string.player_batch_genre_updated_partial, successCount, failCount),
                )
            }
    }
}
