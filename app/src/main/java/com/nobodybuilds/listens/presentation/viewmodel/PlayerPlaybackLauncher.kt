package com.nobodybuilds.listens.presentation.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.utils.AppShortcutManager
import com.nobodybuilds.listens.utils.MediaItemBuilder
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val CAST_LOG_TAG = "PlayerCastTransfer"

/**
 * Hands a prepared queue to the player engine (or the Cast receiver) for PlayerViewModel,
 * and builds the media items for it.
 */
@UnstableApi
internal class PlayerPlaybackLauncher(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val castStateHolder: CastStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val dualPlayerEngine: DualPlayerEngine,
    private val appShortcutManager: AppShortcutManager,
    private val themeStateHolder: ThemeStateHolder,
    private val externalMediaStateHolder: ExternalMediaStateHolder,
    private val currentSongSync: CurrentSongStateSync,
    private val sheetNavigator: PlayerSheetNavigator
) {
    private data class PreparedPlaybackQueue(
        val mediaItems: List<MediaItem>,
        val startIndex: Int
    )

    private val scope: CoroutineScope get() = shared.scope
    private val _playerUiState get() = shared.playerUiState
    private val _sheetState get() = shared.sheetState
    private val _isSheetVisible get() = shared.isSheetVisible
    private val mediaController: MediaController? get() = shared.mediaController
    private var pendingPlaybackAction: (() -> Unit)?
        get() = shared.pendingPlaybackAction
        set(value) { shared.pendingPlaybackAction = value }
    private var transitionSchedulerJob: Job?
        get() = shared.transitionSchedulerJob
        set(value) { shared.transitionSchedulerJob = value }

    fun playExternalUri(uri: Uri) {
        scope.launch {
            val externalResult = externalMediaStateHolder.buildExternalSongFromUri(uri)
            if (externalResult == null) {
                shared.sendToast(context.getString(R.string.external_playback_error))
                return@launch
            }

            transitionSchedulerJob?.cancel()

            val queueSongs = externalMediaStateHolder.buildExternalQueue(externalResult, uri)
            val immutableQueue = queueSongs.toPlaybackQueue()

            _playerUiState.update { state ->
                state.copy(
                    currentPlaybackQueue = immutableQueue,
                    currentQueueSourceName = context.getString(R.string.external_queue_label),
                    showDismissUndoBar = false,
                    dismissedSong = null,
                    dismissedQueue = persistentListOf(),
                    dismissedQueueName = "",
                    dismissedPosition = 0L
                )
            }
            playbackStateHolder.setCurrentPosition(0L)

            playbackStateHolder.updateStablePlayerState { state ->
                state.copy(
                    currentSong = externalResult.song,
                    isPlaying = true,
                    playWhenReady = true,
                    totalDuration = externalResult.song.duration,
                    lyrics = null,
                    isLoadingLyrics = false
                )
            }

            _sheetState.value = PlayerSheetState.COLLAPSED
            _isSheetVisible.value = true

            internalPlaySongs(queueSongs, externalResult.song, context.getString(R.string.external_queue_label), null)
            sheetNavigator.showPlayer()
        }
    }

    private fun setPreparingSong(songId: String?) {
        _playerUiState.update { state ->
            if (state.preparingSongId == songId) state else state.copy(preparingSongId = songId)
        }
    }

    fun beginPreparingSong(song: Song) {
        setPreparingSong(song.id)
        scope.launch(Dispatchers.IO) {
            val albumArtUri = song.albumArtUriString
            if (albumArtUri.isNullOrBlank()) {
                themeStateHolder.extractAndGenerateColorScheme(
                    albumArtUriAsUri = null,
                    currentSongUriString = null,
                    isPreload = false
                )
            } else {
                themeStateHolder.extractAndGenerateColorScheme(
                    albumArtUriAsUri = albumArtUri.toUri(),
                    currentSongUriString = albumArtUri,
                    isPreload = false
                )
            }
        }
    }

    fun clearPreparingSongIfMatching(mediaId: String? = null) {
        val preparingSongId = _playerUiState.value.preparingSongId ?: return
        if (mediaId == null || preparingSongId == mediaId) {
            setPreparingSong(null)
        }
    }

    private suspend fun preparePlaybackQueue(
        songsToPlay: List<Song>,
        startSongId: String,
        playlistId: String?
    ): PreparedPlaybackQueue = withContext(Dispatchers.Default) {
        val mediaItems = ArrayList<MediaItem>(songsToPlay.size)
        var startIndex = 0
        var foundStartIndex = false

        songsToPlay.forEachIndexed { index, song ->
            if (!foundStartIndex && song.id == startSongId) {
                startIndex = index
                foundStartIndex = true
            }

            mediaItems += buildPlaybackMediaItem(song, playlistId)
        }

        PreparedPlaybackQueue(
            mediaItems = mediaItems,
            startIndex = startIndex
        )
    }



    suspend fun internalPlaySongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        if (songsToPlay.isEmpty()) {
            clearPreparingSongIfMatching()
            return
        }
        val effectiveStartSong = songsToPlay.firstOrNull { it.id == startSong.id } ?: songsToPlay.first()

        // Update dynamic shortcut for last played playlist
        if (playlistId != null && queueName != "None") {
            appShortcutManager.updateLastPlaylistShortcut(playlistId, queueName)
        }

        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            clearPreparingSongIfMatching()
            val remoteLoaded = castTransferStateHolder.playRemoteQueue(
                songsToPlay = songsToPlay,
                startSong = effectiveStartSong,
                isShuffleEnabled = playbackStateHolder.stablePlayerState.value.isShuffleEnabled
            )

            if (!remoteLoaded) {
                Timber.tag(CAST_LOG_TAG).w(
                    "Remote queue load failed in internalPlaySongs (songId=%s queueSize=%d).",
                    effectiveStartSong.id,
                    songsToPlay.size
                )
                castSession.remoteMediaClient?.requestStatus()
                return
            }

            _playerUiState.update { it.copy(currentPlaybackQueue = songsToPlay.toPlaybackQueue(), currentQueueSourceName = queueName) }
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = effectiveStartSong,
                    isPlaying = true,
                    playWhenReady = true,
                    totalDuration = effectiveStartSong.duration.coerceAtLeast(0L)
                )
            }
        } else {
            beginPreparingSong(effectiveStartSong)
            _playerUiState.update {
                it.copy(
                    currentPlaybackQueue = songsToPlay.toPlaybackQueue(),
                    currentQueueSourceName = queueName
                )
            }
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = effectiveStartSong,
                    isPlaying = true,
                    playWhenReady = true,
                    totalDuration = effectiveStartSong.duration.coerceAtLeast(0L)
                )
            }
            _isSheetVisible.value = true

            // Pre-resolve the starting song's cloud URI before ExoPlayer touches it.
            // This populates the resolvedUriCache so resolveDataSpec finds it instantly.
            val startingUri = MediaItemBuilder.playbackUri(effectiveStartSong)
            if (
                startingUri.scheme == "telegram" ||
                startingUri.scheme == "netease" ||
                startingUri.scheme == "qqmusic" ||
                startingUri.scheme == "navidrome" ||
                startingUri.scheme == "jellyfin" ||
                startingUri.scheme == "ytmusic" ||
                startingUri.scheme == "jiosaavn"
            ) {
                if (startingUri.scheme == "telegram") {
                    currentSongSync.ensureTelegramPlaybackObserversStarted()
                }
                dualPlayerEngine.resolveCloudUri(startingUri)
            }

            val preparedPlaybackQueue = preparePlaybackQueue(
                songsToPlay = songsToPlay,
                startSongId = effectiveStartSong.id,
                playlistId = playlistId
            )

            val playSongsAction = {
                // Use Direct Engine Access to avoid TransactionTooLargeException on Binder
                val enginePlayer = dualPlayerEngine.masterPlayer

                if (preparedPlaybackQueue.mediaItems.isNotEmpty()) {
                    // Direct access: No IPC limit involved
                    enginePlayer.setMediaItems(
                        preparedPlaybackQueue.mediaItems,
                        preparedPlaybackQueue.startIndex,
                        0L
                    )
                    enginePlayer.prepare()
                    enginePlayer.play()
                } else {
                    clearPreparingSongIfMatching(effectiveStartSong.id)
                }
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
            }

            // We still check for mediaController to ensure the Service is bound and active
            // even though we aren't using it for the heavy lifting anymore.
            if (mediaController == null) {
                Timber.w("MediaController not available. Queuing playback action.")
                pendingPlaybackAction = playSongsAction
            } else {
                playSongsAction()
            }
        }
    }

    suspend fun buildResolvedPlaybackMediaItem(song: Song): MediaItem {
        val mediaItem = MediaItemBuilder.build(song)
        val originalUri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val scheme = originalUri.scheme
        if (
            scheme != "telegram" &&
            scheme != "netease" &&
            scheme != "qqmusic" &&
            scheme != "navidrome" &&
            scheme != "jellyfin"
        ) {
            return mediaItem
        }

        if (scheme == "telegram") {
            currentSongSync.ensureTelegramPlaybackObserversStarted()
        }

        val resolvedUri = dualPlayerEngine.resolveCloudUri(originalUri)
        return if (resolvedUri == originalUri) {
            mediaItem
        } else {
            mediaItem.buildUpon().setUri(resolvedUri).build()
        }
    }

    fun buildPlaybackMediaItem(song: Song, playlistId: String? = null): MediaItem {
        val baseItem = MediaItemBuilder.build(song)
        if (playlistId == null) {
            return baseItem
        }

        val mergedExtras = Bundle(baseItem.mediaMetadata.extras ?: Bundle()).apply {
            putString("playlistId", playlistId)
        }

        return baseItem.buildUpon()
            .setMediaMetadata(
                baseItem.mediaMetadata.buildUpon()
                    .setExtras(mergedExtras)
                    .build()
            )
            .build()
    }

    fun playSong(song: Song) {
        scope.launch {
            val controller = mediaController ?: return@launch
            val mediaItem = buildResolvedPlaybackMediaItem(song)

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()

            _isSheetVisible.value = true
            _sheetState.value = PlayerSheetState.EXPANDED
        }
    }
}
