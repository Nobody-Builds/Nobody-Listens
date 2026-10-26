package com.nobodybuilds.listens.presentation.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.snapshotFlow
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Player sheet state (expand/collapse, predictive back, queue and cast sheets) and the
 * navigation requests PlayerViewModel sends from the player to the rest of the app.
 */
@SuppressLint("LogNotTimber")
internal class PlayerSheetNavigator(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val playbackStateHolder: PlaybackStateHolder
) {
    private val scope: CoroutineScope get() = shared.scope
    private val _sheetState get() = shared.sheetState
    private val sheetState: StateFlow<PlayerSheetState> get() = shared.sheetState
    private val _isSheetVisible get() = shared.isSheetVisible
    private val playerUiState: StateFlow<PlayerUiState> get() = shared.playerUiState
    private val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState

    private val _bottomBarHeight = MutableStateFlow(0)
    val bottomBarHeight: StateFlow<Int> = _bottomBarHeight.asStateFlow()
    private val _predictiveBackCollapseFraction = MutableStateFlow(0f)
    val predictiveBackCollapseFraction: StateFlow<Float> = _predictiveBackCollapseFraction.asStateFlow()
    private val _predictiveBackSwipeEdge = MutableStateFlow<Int?>(null)
    val predictiveBackSwipeEdge: StateFlow<Int?> = _predictiveBackSwipeEdge.asStateFlow()
    private val _isQueueSheetVisible = MutableStateFlow(false)
    val isQueueSheetVisible: StateFlow<Boolean> = _isQueueSheetVisible.asStateFlow()
    private val _isCastSheetVisible = MutableStateFlow(false)
    val isCastSheetVisible: StateFlow<Boolean> = _isCastSheetVisible.asStateFlow()

    val playerContentExpansionFraction = Animatable(0f)

    private val _albumNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val albumNavigationRequests = _albumNavigationRequests.asSharedFlow()
    private val _artistNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val artistNavigationRequests = _artistNavigationRequests.asSharedFlow()
    private val _searchNavDoubleTapEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val searchNavDoubleTapEvents = _searchNavDoubleTapEvents.asSharedFlow()
    
    // New event for scrolling to a specific index in the songs list
    private val _scrollToIndexEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToIndexEvent = _scrollToIndexEvent.asSharedFlow()
    
    private var albumNavigationJob: Job? = null
    private var artistNavigationJob: Job? = null

    fun requestLocateCurrentSong() {
        val currentSong = stablePlayerState.value.currentSong ?: return

        scope.launch {
            try {
                val sortOption = playerUiState.value.currentSongSortOption
                
                // Logic must match effectiveStorageFilter in LibraryStateHolder
                val baseFilter = playerUiState.value.currentStorageFilter
                val hideLocal = playerUiState.value.hideLocalMedia
                val storageFilter = if (hideLocal) {
                    com.nobodybuilds.listens.data.model.StorageFilter.ONLINE
                } else {
                    baseFilter
                }

                val sortedIds = musicRepository.getSongIdsSorted(sortOption, storageFilter)

                val unifiedId = currentSong.id.toLongOrNull()
                    ?: currentSong.contentUriString
                        .takeIf { it.isNotBlank() }
                        ?.let { musicRepository.getSongIdByContentUri(it) }

                val index = unifiedId?.let { sortedIds.indexOf(it) } ?: -1

                if (index != -1) {
                    _scrollToIndexEvent.emit(index)
                } else {
                    shared.sendToast(context.getString(R.string.player_song_not_found_in_list))
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to locate current song")
                shared.sendToast(context.getString(R.string.player_could_not_locate_song))
            }
        }
    }

    fun onSearchNavIconDoubleTapped() {
        _searchNavDoubleTapEvents.tryEmit(Unit)
    }

    fun updatePredictiveBackCollapseFraction(fraction: Float) {
        _predictiveBackCollapseFraction.value = fraction.coerceIn(0f, 1f)
    }

    fun updatePredictiveBackSwipeEdge(edge: Int?) {
        _predictiveBackSwipeEdge.value = edge
    }

    fun resetPredictiveBackState() {
        _predictiveBackCollapseFraction.value = 0f
        _predictiveBackSwipeEdge.value = null
    }

    fun updateQueueSheetVisibility(visible: Boolean) {
        _isQueueSheetVisible.value = visible
    }

    fun updateCastSheetVisibility(visible: Boolean) {
        _isCastSheetVisible.value = visible
    }

    fun togglePlayerSheetState(resetPredictiveState: Boolean = true) {
        _sheetState.value = if (_sheetState.value == PlayerSheetState.COLLAPSED) {
            PlayerSheetState.EXPANDED
        } else {
            PlayerSheetState.COLLAPSED
        }
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    fun expandPlayerSheet(resetPredictiveState: Boolean = true) {
        _sheetState.value = PlayerSheetState.EXPANDED
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    fun collapsePlayerSheet(resetPredictiveState: Boolean = true) {
        _sheetState.value = PlayerSheetState.COLLAPSED
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    fun triggerAlbumNavigationFromPlayer(albumId: Long) {
        if (albumId == -1L) {
            Log.d("AlbumDebug", "triggerAlbumNavigationFromPlayer ignored invalid albumId=$albumId")
            return
        }

        val existingJob = albumNavigationJob
        if (existingJob != null && existingJob.isActive) {
            Log.d("AlbumDebug", "triggerAlbumNavigationFromPlayer ignored; navigation already in progress for albumId=$albumId")
            return
        }

        albumNavigationJob?.cancel()
        albumNavigationJob = scope.launch {
            val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
            Log.d(
                "AlbumDebug",
                "triggerAlbumNavigationFromPlayer: albumId=$albumId, songId=${currentSong?.id}, title=${currentSong?.title}"
            )
            collapsePlayerSheet()

            withTimeoutOrNull(900) {
                awaitSheetState(PlayerSheetState.COLLAPSED)
                awaitPlayerCollapse()
            }

            _albumNavigationRequests.emit(albumId)
        }
    }

    fun triggerArtistNavigationFromPlayer(artistId: Long) {
        if (artistId == 0L) {
            Log.d("ArtistDebug", "triggerArtistNavigationFromPlayer ignored invalid artistId=$artistId")
            return
        }

        val existingJob = artistNavigationJob
        if (existingJob != null && existingJob.isActive) {
            Log.d("ArtistDebug", "triggerArtistNavigationFromPlayer ignored; navigation already in progress for artistId=$artistId")
            return
        }

        artistNavigationJob?.cancel()
        artistNavigationJob = scope.launch {
            var resolvedId = artistId
            val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
            
            if (resolvedId == -1L && currentSong != null) {
                val idFromName = musicRepository.getArtistIdByName(currentSong.artist)
                if (idFromName != null) {
                    resolvedId = idFromName
                }
            }

            if (resolvedId == 0L || resolvedId == -1L) {
                Log.d("ArtistDebug", "triggerArtistNavigationFromPlayer: could not resolve artistId for name=${currentSong?.artist}")
                return@launch
            }

            Log.d(
                "ArtistDebug",
                "triggerArtistNavigationFromPlayer: artistId=$resolvedId, songId=${currentSong?.id}, title=${currentSong?.title}"
            )
            collapsePlayerSheet()

            withTimeoutOrNull(900) {
                awaitSheetState(PlayerSheetState.COLLAPSED)
                awaitPlayerCollapse()
            }

            _artistNavigationRequests.emit(artistId)
        }
    }

    suspend fun awaitSheetState(target: PlayerSheetState) {
        sheetState.first { it == target }
    }

    suspend fun awaitPlayerCollapse(threshold: Float = 0.1f, timeoutMillis: Long = 800L) {
        withTimeoutOrNull(timeoutMillis) {
            snapshotFlow { playerContentExpansionFraction.value }
                .first { it <= threshold }
        }
    }

    fun showPlayer() {
        if (stablePlayerState.value.currentSong != null) {
            _isSheetVisible.value = true
        }
    }
}
