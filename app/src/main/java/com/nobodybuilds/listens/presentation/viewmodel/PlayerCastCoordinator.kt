package com.nobodybuilds.listens.presentation.viewmodel

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaControlIntent
import com.google.android.gms.cast.framework.SessionManager
import com.nobodybuilds.listens.data.service.http.MediaFileHttpServerService
import com.nobodybuilds.listens.data.service.player.CastPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Cast wiring of PlayerViewModel: resumes an existing Cast session, runs the local media
 * HTTP server while a Cast route is selected, and connects CastTransferStateHolder.
 */
@UnstableApi
internal class PlayerCastCoordinator(
    private val shared: PlayerSharedState,
    private val context: Context,
    private val castStateHolder: CastStateHolder,
    private val castRouteStateHolder: CastRouteStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
    private val themeStateHolder: ThemeStateHolder,
    private val currentSongSync: CurrentSongStateSync
) {
    private val scope: CoroutineScope get() = shared.scope
    private val _playerUiState get() = shared.playerUiState
    private val _isSheetVisible get() = shared.isSheetVisible
    private val _toastEvents get() = shared.toastEvents
    private val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState

    // Cast state is now managed by CastStateHolder
    private val sessionManager: SessionManager? get() = castStateHolder.sessionManager

    /** Pre-mapped so UI composables don't create a new Flow on every recomposition. */
    val selectedRouteName: StateFlow<String?> = castStateHolder.selectedRoute
        .map { it?.name }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    private var castSongUiSyncJob: Job? = null
    private var lastCastSongUiSyncedId: String? = null

    fun restoreExistingSession() {
        // Cast initialization if already connected
        val currentSession = sessionManager?.currentCastSession
        if (currentSession != null) {
            castStateHolder.setCastPlayer(CastPlayer(currentSession, context.contentResolver))
            castStateHolder.setRemotePlaybackActive(true)
        }
    }

    fun observeRouteForHttpServer() {
        // Observe selection for HTTP server management
        scope.launch {
            castStateHolder.selectedRoute.collect { route ->
                if (route != null && !route.isDefault && route.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)) {
                    castTransferStateHolder.primeHttpServerStart()
                } else if (route?.isDefault == true) {
                    val hasActiveRemoteSession = castStateHolder.castSession.value?.remoteMediaClient != null ||
                            castStateHolder.isRemotePlaybackActive.value ||
                            castStateHolder.isCastConnecting.value
                    if (hasActiveRemoteSession) {
                        return@collect
                    }
                    context.stopService(Intent(context, MediaFileHttpServerService::class.java))
                }
            }
        }
    }

    fun initializeTransfer() {
        castTransferStateHolder.initialize(
            scope = scope,
            getCurrentQueue = { _playerUiState.value.currentPlaybackQueue },
            updateQueue = { newQueue ->
                _playerUiState.update {
                    it.copy(currentPlaybackQueue = newQueue.toPlaybackQueue())
                }
            },
            getSongsByIdMap = { libraryStateHolder.allSongsById.value },
            onTransferBackComplete = { playbackStateHolder.startProgressUpdates() },
            onSheetVisible = { _isSheetVisible.value = true },
            onDisconnect = { castRouteStateHolder.disconnect() },
            onCastError = { message ->
                scope.launch { _toastEvents.emit(message) }
            },
            onSongChanged = { uriString ->
                castSongUiSyncJob?.cancel()
                castSongUiSyncJob = scope.launch {
                    delay(220)
                    val currentSongId = stablePlayerState.value.currentSong?.id
                    if (currentSongId != null && currentSongId == lastCastSongUiSyncedId) {
                        return@launch
                    }
                    currentSongSync.loadLyricsForCurrentSong()
                    uriString?.toUri()?.let { uri ->
                        themeStateHolder.extractAndGenerateColorScheme(uri, uriString)
                    }
                    if (currentSongId != null) {
                        lastCastSongUiSyncedId = currentSongId
                    }
                }
            }
        )
    }

    fun onCleared() {
        castSongUiSyncJob?.cancel()
    }
}
