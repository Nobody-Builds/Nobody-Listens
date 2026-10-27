package com.nobodybuilds.listens.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.service.http.MediaFileHttpServerService
import com.nobodybuilds.listens.data.service.player.CastPlayer
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.nobodybuilds.listens.presentation.viewmodel.CastLocalQueueRestorer.TransferSnapshot

@Singleton
class CastTransferStateHolder @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val castStateHolder: CastStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val dualPlayerEngine: DualPlayerEngine // For local player control during transfer
) {
    private val CAST_LOG_TAG = "PlayerCastTransfer"

    private var scope: CoroutineScope? = null

    // Callbacks for interacting with PlayerViewModel, shared with the collaborators below.
    // The properties delegate to it so initialize() and onCleared() can set them directly.
    private val callbacks = CastTransferCallbacks()
    private var getCurrentQueue: (() -> List<Song>)? by callbacks::getCurrentQueue
    private var updateQueue: ((List<Song>) -> Unit)? by callbacks::updateQueue
    private var getSongsByIdMap: (() -> Map<String, Song>)? by callbacks::getSongsByIdMap
    private var onTransferBackComplete: (() -> Unit)? by callbacks::onTransferBackComplete
    private var onSheetVisible: (() -> Unit)? by callbacks::onSheetVisible
    private var onDisconnect: (() -> Unit)? by callbacks::onDisconnect
    private var onCastError: ((String) -> Unit)? by callbacks::onCastError
    private var onSongChanged: ((String?) -> Unit)? by callbacks::onSongChanged

    private val remoteStatus = CastRemoteStatusTracker(castStateHolder, playbackStateHolder, callbacks)
    private val mediaServer = CastMediaServerLauncher(context) { message -> emitCastError(message) }
    private val localQueueRestorer = CastLocalQueueRestorer(dualPlayerEngine, playbackStateHolder, callbacks)

    // Session Management
    private val sessionManager: SessionManager? by lazy {
        try {
            CastContext.getSharedInstance(context).sessionManager
        } catch (e: Exception) {
            Timber.tag(CAST_LOG_TAG).e(e, "Failed to get CastContext sharedInstance")
            null
        }
    }
    
    // We retain MediaRouter reference if needed, but managing routes is usually done via callbacks
    // in PlayerViewModel. We'll assume route selection logic remains there or is migrated separately.

    // State tracking variables (owned by remoteStatus, which updates them from Cast status)
    private var lastRemoteMediaStatus: MediaStatus? by remoteStatus::lastRemoteMediaStatus
    var lastRemoteQueue: List<Song> by remoteStatus::lastRemoteQueue
        private set
    var lastRemoteSongId: String? by remoteStatus::lastRemoteSongId
        private set
    private var lastRemoteStreamPosition: Long by remoteStatus::lastRemoteStreamPosition
    private var lastRemoteRepeatMode: Int by remoteStatus::lastRemoteRepeatMode
    private var lastKnownRemoteIsPlaying: Boolean by remoteStatus::lastKnownRemoteIsPlaying

    private var pendingRemoteSongId: String? by remoteStatus::pendingRemoteSongId
    private var pendingRemoteSongMarkedAt: Long by remoteStatus::pendingRemoteSongMarkedAt
    private var pendingMismatchStatusRequestCount: Int by remoteStatus::pendingMismatchStatusRequestCount
    private var lastPendingMismatchStatusRequestAt: Long by remoteStatus::lastPendingMismatchStatusRequestAt
    private var pendingForceJumpAttempts: Int by remoteStatus::pendingForceJumpAttempts
    private var lastPendingForceJumpAt: Long by remoteStatus::lastPendingForceJumpAt
    private var skipTransferBackOnNextSessionEnd: Boolean = false

    // Listeners
    private var remoteMediaClientCallback: RemoteMediaClient.Callback? = null
    private var remoteProgressListener: RemoteMediaClient.ProgressListener? = null
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null
    private var remoteProgressObserverJob: Job? = null
    private var remoteStatusRefreshJob: Job? = null
    private var sessionSuspendedRecoveryJob: Job? = null
    private var alignToTargetJob: Job? = null

    fun initialize(
        scope: CoroutineScope,
        getCurrentQueue: () -> List<Song>,
        updateQueue: (List<Song>) -> Unit,
        getSongsByIdMap: () -> Map<String, Song>,
        onTransferBackComplete: () -> Unit,
        onSheetVisible: () -> Unit,
        onDisconnect: () -> Unit,
        onCastError: (String) -> Unit,
        onSongChanged: (String?) -> Unit
    ) {
        this.scope = scope
        this.getCurrentQueue = getCurrentQueue
        this.updateQueue = updateQueue
        this.getSongsByIdMap = getSongsByIdMap
        this.onTransferBackComplete = onTransferBackComplete
        this.onSheetVisible = onSheetVisible
        this.onDisconnect = onDisconnect
        this.onCastError = onCastError
        this.onSongChanged = onSongChanged

        setupListeners()
    }

    private fun setupListeners() {
        remoteProgressListener = RemoteMediaClient.ProgressListener { progress, _ ->
            val isSeeking = castStateHolder.isRemotelySeeking.value
            if (!isSeeking) {
                val pendingId = pendingRemoteSongId
                if (pendingId != null && SystemClock.elapsedRealtime() - pendingRemoteSongMarkedAt < 4000) {
                    val status = castStateHolder.castSession.value?.remoteMediaClient?.mediaStatus
                    val activeId = status
                        ?.getQueueItemById(status.getCurrentItemId())
                        ?.customData
                        ?.optString("songId")
                    if (activeId == null || activeId != pendingId) {
                         Timber.tag(CAST_LOG_TAG).d("Ignoring remote progress %d while pending target %s", progress, pendingId)
                        return@ProgressListener
                    }
                }
                castStateHolder.setRemotePosition(progress)
                lastRemoteStreamPosition = progress
            }
        }

        remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                remoteStatus.handleRemoteStatusUpdate()
            }

            override fun onMetadataUpdated() {
                remoteStatus.handleRemoteStatusUpdate()
            }

            override fun onQueueStatusUpdated() {
                remoteStatus.handleRemoteStatusUpdate()
            }

            override fun onPreloadStatusUpdated() {
                remoteStatus.handleRemoteStatusUpdate()
            }
        }

        castSessionManagerListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                sessionSuspendedRecoveryJob?.cancel()
                transferPlayback(session)
            }
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                sessionSuspendedRecoveryJob?.cancel()
                transferPlayback(session)
            }
            override fun onSessionEnded(session: CastSession, error: Int) {
                sessionSuspendedRecoveryJob?.cancel()
                scope?.launch { stopServerAndTransferBack() }
            }
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                Timber.tag(CAST_LOG_TAG).w("Cast session suspended (reason=%d). Waiting for recovery.", reason)
                castStateHolder.setRemotePlaybackActive(false)
                castStateHolder.setCastConnecting(true)
                scheduleSessionSuspendedRecovery(session)
            }
            override fun onSessionStarting(session: CastSession) {
                castStateHolder.setCastConnecting(true)
            }
            override fun onSessionStartFailed(session: CastSession, error: Int) {
                sessionSuspendedRecoveryJob?.cancel()
                castStateHolder.setPendingCastRouteId(null)
                castStateHolder.setCastConnecting(false)
                emitCastError("Couldn't connect to Cast device (error $error).")
            }
            override fun onSessionEnding(session: CastSession) { }
            override fun onSessionResuming(session: CastSession, sessionId: String) {
                castStateHolder.setCastConnecting(true)
            }
            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                sessionSuspendedRecoveryJob?.cancel()
                castStateHolder.setPendingCastRouteId(null)
                castStateHolder.setCastConnecting(false)
                emitCastError("Cast session resume failed (error $error).")
            }
        }
        
        sessionManager?.addSessionManagerListener(castSessionManagerListener as SessionManagerListener<CastSession>, CastSession::class.java)
        
        // Sync initial state if session exists
        val currentSession = sessionManager?.currentCastSession
        castStateHolder.setCastSession(currentSession)
        castStateHolder.setRemotePlaybackActive(currentSession != null)
        
        if (currentSession != null) {
            currentSession.remoteMediaClient?.registerCallback(remoteMediaClientCallback!!)
            currentSession.remoteMediaClient?.addProgressListener(remoteProgressListener!!, 1000)
            startRemoteProgressObserver()
            playbackStateHolder.startProgressUpdates()
            currentSession.remoteMediaClient?.requestStatus()
        }
    }

    private fun scheduleSessionSuspendedRecovery(suspendedSession: CastSession) {
        sessionSuspendedRecoveryJob?.cancel()
        sessionSuspendedRecoveryJob = scope?.launch {
            delay(12000)
            val activeSession = sessionManager?.currentCastSession
            val stillSameSession = activeSession === suspendedSession
            val hasRemoteClient = activeSession?.remoteMediaClient != null
            if (stillSameSession && !hasRemoteClient) {
                Timber.tag(CAST_LOG_TAG).w("Suspended Cast session did not recover in time. Transferring back.")
                stopServerAndTransferBack()
            }
        }
    }

    private fun transferPlayback(session: CastSession) {
        scope?.launch {
            castStateHolder.setPendingCastRouteId(null)
            castStateHolder.setCastConnecting(true)
            castStateHolder.setRemotelySeeking(false)

            val castDeviceIpHint = mediaServer.resolveCastDeviceIp(session)
            if (!ensureHttpServerRunning(castDeviceIpHint)) {
                castStateHolder.setCastConnecting(false)
                onDisconnect?.invoke()
                return@launch
            }
            
            // Ensure no local transition is messing with the player references
            dualPlayerEngine.cancelNext()

            val serverAddress = MediaFileHttpServerService.serverAddress
            val localPlayer = dualPlayerEngine.masterPlayer // Safe now as we are on Main and cancelled transitions

            val currentQueue = getCurrentQueue?.invoke() ?: emptyList()
            
            if (serverAddress == null || currentQueue.isEmpty()) {
                castStateHolder.setCastConnecting(false)
                emitCastError("Cast setup is incomplete. Try reconnecting.")
                return@launch
            }

            val wasPlaying = localPlayer.isPlaying
            val currentSongIndex = localPlayer.currentMediaItemIndex
            val safeStartIndex = currentSongIndex.takeIf { it in currentQueue.indices } ?: 0
            val currentPosition = localPlayer.currentPosition
            
             val castRepeatMode = if (localPlayer.shuffleModeEnabled) {
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
            } else {
                when (localPlayer.repeatMode) {
                    Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                    Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                    else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                }
            }

            lastRemoteMediaStatus = null

            onSheetVisible?.invoke()
            localPlayer.pause()
            
            playbackStateHolder.stopProgressUpdates()

            castStateHolder.setCastPlayer(CastPlayer(session, context.contentResolver))
            castStateHolder.setCastSession(session)
            castStateHolder.setRemotePlaybackActive(false)

            val castPlayer = castStateHolder.castPlayer
            if (castPlayer == null) {
                Timber.tag(CAST_LOG_TAG).w("Cast player unavailable during transferPlayback.")
                castStateHolder.setRemotePlaybackActive(false)
                castStateHolder.setCastConnecting(false)
                emitCastError("Cast player is unavailable. Try reconnecting.")
                sessionManager?.endCurrentSession(true)
                return@launch
            }

            val accessPolicy = MediaFileHttpServerService.configureCastSessionAccess(
                allowedSongIds = currentQueue.map(Song::id),
                castDeviceIpHint = castDeviceIpHint
            )
            val preflightSong = currentQueue.getOrNull(safeStartIndex)
            if (preflightSong != null && !mediaServer.waitForSongEndpointReady(serverAddress, preflightSong, accessPolicy.authToken)) {
                Timber.tag(CAST_LOG_TAG).w(
                    "Song endpoint preflight failed for songId=%s; continuing with queueLoad to avoid false negatives.",
                    preflightSong.id
                )
            }

            var initialLoadAttempt = 0
            fun loadInitialQueueAttempt() {
                initialLoadAttempt += 1
                castPlayer.loadQueue(
                    songs = currentQueue,
                    startIndex = safeStartIndex,
                    startPosition = currentPosition,
                    repeatMode = castRepeatMode,
                    serverAddress = serverAddress,
                    authToken = accessPolicy.authToken,
                    autoPlay = wasPlaying, // Simplification
                    onComplete = loadResult@{ success, detail ->
                        if (!success && initialLoadAttempt < 2) {
                            Timber.tag(CAST_LOG_TAG).w(
                                "Initial Cast queue load failed (attempt %d, detail=%s). Retrying once.",
                                initialLoadAttempt,
                                detail
                            )
                            session.remoteMediaClient?.requestStatus()
                            scope?.launch {
                                delay(450)
                                if (castStateHolder.castSession.value === session &&
                                    !castStateHolder.isRemotePlaybackActive.value
                                ) {
                                    loadInitialQueueAttempt()
                                }
                            }
                            return@loadResult
                        }

                        if (!success) {
                            Timber.tag(CAST_LOG_TAG).w(
                                "Initial Cast queue load failed after retry; ending session to avoid stuck route. detail=%s",
                                detail
                            )
                            castStateHolder.setRemotePlaybackActive(false)
                            castStateHolder.setCastConnecting(false)
                            val detailedMessage = detail?.takeIf { it.isNotBlank() }
                            emitCastError(
                                if (detailedMessage != null) {
                                    "Failed to load media on Cast device: $detailedMessage"
                                } else {
                                    "Failed to load media on Cast device."
                                }
                            )
                            sessionManager?.endCurrentSession(true)
                            return@loadResult
                        }

                        lastRemoteQueue = currentQueue
                        lastRemoteSongId = currentQueue.getOrNull(safeStartIndex)?.id
                        lastRemoteStreamPosition = currentPosition
                        lastRemoteRepeatMode = castRepeatMode
                        playbackStateHolder.startProgressUpdates()
                        session.remoteMediaClient?.requestStatus()
                        currentQueue.getOrNull(safeStartIndex)?.id?.let(::launchAlignToTarget)

                        castStateHolder.setRemotePlaybackActive(true)
                        castStateHolder.setCastConnecting(false)
                    }
                )
            }

            loadInitialQueueAttempt()

            session.remoteMediaClient?.registerCallback(remoteMediaClientCallback!!)
            session.remoteMediaClient?.addProgressListener(remoteProgressListener!!, 1000)
            
            startRemoteProgressObserver()
        }
    }
    
    private fun startRemoteProgressObserver() {
        remoteProgressObserverJob?.cancel()
        remoteStatusRefreshJob?.cancel()

        remoteProgressObserverJob = scope?.launch {
            castStateHolder.remotePosition.collect { position ->
                playbackStateHolder.setCurrentPosition(position)
            }
        }

        remoteStatusRefreshJob = scope?.launch {
            while (true) {
                val remoteClient = castStateHolder.castSession.value?.remoteMediaClient
                if (remoteClient == null) {
                    delay(if (castStateHolder.isCastConnecting.value) 1000 else 2500)
                    continue
                }
                runCatching { remoteClient.requestStatus() }
                    .onFailure { throwable ->
                        Timber.tag(CAST_LOG_TAG).d(throwable, "requestStatus failed during refresh loop")
                    }
                val refreshDelayMs = when {
                    remoteClient.isPlaying -> 1500L
                    castStateHolder.isRemotePlaybackActive.value -> 2500L
                    castStateHolder.isCastConnecting.value -> 1500L
                    else -> 4000L
                }
                delay(refreshDelayMs)
            }
        }
    }

    private fun emitCastError(message: String) {
        runCatching { onCastError?.invoke(message) }
            .onFailure { throwable ->
                Timber.tag(CAST_LOG_TAG).w(throwable, "Failed to emit cast error message")
            }
    }

    /**
     * One-shot guard used by mini-player dismiss: disconnect Cast without restoring
     * the remote queue back into the local player.
     */
    fun skipNextTransferBack() {
        skipTransferBackOnNextSessionEnd = true
    }
    
    suspend fun stopServerAndTransferBack() {
        sessionSuspendedRecoveryJob?.cancel()
        alignToTargetJob?.cancel()
        remoteProgressObserverJob?.cancel()
        remoteStatusRefreshJob?.cancel()
        castStateHolder.setRemotelySeeking(false)
        val shouldSkipTransferBack = skipTransferBackOnNextSessionEnd
        skipTransferBackOnNextSessionEnd = false
        val session = castStateHolder.castSession.value ?: return
        val remoteMediaClient = session.remoteMediaClient
         
        // Cleanup callbacks
        remoteProgressListener?.let { listener ->
            remoteMediaClient?.removeProgressListener(listener)
        }
        remoteMediaClientCallback?.let { callback ->
            remoteMediaClient?.unregisterCallback(callback)
        }
        
        val liveStatus = remoteMediaClient?.mediaStatus
        val lastKnownStatus = liveStatus ?: lastRemoteMediaStatus
        val lastPosition = (liveStatus?.streamPosition ?: lastKnownStatus?.streamPosition ?: lastRemoteStreamPosition)
            .takeIf { it > 0 } ?: castStateHolder.remotePosition.value
            
        val wasPlaying = (liveStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING) 
            || (lastKnownStatus?.playerState == MediaStatus.PLAYER_STATE_PLAYING)
            || lastKnownRemoteIsPlaying
            
        val lastItemId = liveStatus?.currentItemId ?: lastKnownStatus?.currentItemId
        val lastRepeatMode = liveStatus?.queueRepeatMode ?: lastKnownStatus?.queueRepeatMode ?: lastRemoteRepeatMode
        val isShuffleEnabled = lastRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
        
        val transferSnapshot = TransferSnapshot(
            lastKnownStatus = lastKnownStatus,
            lastRemoteQueue = lastRemoteQueue,
            lastRemoteSongId = lastRemoteSongId,
            lastRemoteStreamPosition = lastRemoteStreamPosition,
            lastRemoteRepeatMode = lastRemoteRepeatMode,
            wasPlaying = wasPlaying,
            lastPosition = lastPosition,
            isShuffleEnabled = isShuffleEnabled
        )

        castStateHolder.setCastPlayer(null)
        castStateHolder.setCastSession(null)
        castStateHolder.setRemotePlaybackActive(false)
        
        if (castStateHolder.pendingCastRouteId == null) {
            context.stopService(Intent(context, MediaFileHttpServerService::class.java))
            // Signal disconnect to PlayerViewModel if needed, or rely on state holder
            onDisconnect?.invoke() 
        } else {
            castStateHolder.setCastConnecting(true)
        }
        
        if (!shouldSkipTransferBack) {
            localQueueRestorer.restoreLocalQueue(transferSnapshot, lastItemId, lastRepeatMode, wasPlaying)
        } else {
            Timber.tag(CAST_LOG_TAG).i("Skipping transfer back for this Cast session end.")
        }
        
        lastRemoteMediaStatus = null
        lastRemoteQueue = emptyList()
        lastRemoteSongId = null
        lastRemoteStreamPosition = 0L
        
        onTransferBackComplete?.invoke()
    }

    fun primeHttpServerStart() {
        mediaServer.primeHttpServerStart {
            castStateHolder.castSession.value ?: sessionManager?.currentCastSession
        }
    }

    suspend fun ensureHttpServerRunning(castDeviceIpHint: String? = null): Boolean =
        mediaServer.ensureHttpServerRunning(castDeviceIpHint)

    suspend fun playRemoteQueue(
        songsToPlay: List<Song>,
        startSong: Song,
        isShuffleEnabled: Boolean
    ): Boolean {
        val castDeviceIpHint = mediaServer.resolveCastDeviceIp(castStateHolder.castSession.value)
        if (!ensureHttpServerRunning(castDeviceIpHint)) return false

        val serverAddress = MediaFileHttpServerService.serverAddress ?: return false
        val startIndex = songsToPlay.indexOfFirst { it.id == startSong.id }.coerceAtLeast(0)

        val repeatMode = playbackStateHolder.stablePlayerState.value.repeatMode
        val castRepeatMode = if (isShuffleEnabled) {
             MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
        } else {
             when (repeatMode) {
                 Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                 Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                 else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
             }
        }

        val previousStableSong = playbackStateHolder.stablePlayerState.value.currentSong
        markPendingRemoteSong(startSong)

        val castPlayer = castStateHolder.castPlayer
        if (castPlayer != null) {
            val accessPolicy = MediaFileHttpServerService.configureCastSessionAccess(
                allowedSongIds = songsToPlay.map(Song::id),
                castDeviceIpHint = castDeviceIpHint
            )
            val completionDeferred = CompletableDeferred<Boolean>()
            castPlayer.loadQueue(
                songs = songsToPlay,
                startIndex = startIndex,
                startPosition = 0L,
                repeatMode = castRepeatMode,
                serverAddress = serverAddress,
                authToken = accessPolicy.authToken,
                autoPlay = true,
                onComplete = { success, detail ->
                    if (!success) {
                        pendingRemoteSongId = null
                        pendingRemoteSongMarkedAt = 0L
                        pendingMismatchStatusRequestCount = 0
                        lastPendingMismatchStatusRequestAt = 0L
                        pendingForceJumpAttempts = 0
                        lastPendingForceJumpAt = 0L
                        val currentRemoteSongId = castStateHolder.castSession.value
                            ?.remoteMediaClient
                            ?.mediaStatus
                            ?.let { status ->
                                status.getQueueItemById(status.getCurrentItemId())
                                    ?.customData
                                    ?.optString("songId")
                                    ?.takeIf { it.isNotBlank() }
                            }
                        if (currentRemoteSongId != null) {
                            lastRemoteSongId = currentRemoteSongId
                        }
                        if (previousStableSong != null) {
                            playbackStateHolder.updateStablePlayerState { state ->
                                if (state.currentSong?.id == startSong.id) {
                                    state.copy(currentSong = previousStableSong)
                                } else {
                                    state
                                }
                            }
                            onSongChanged?.invoke(previousStableSong.albumArtUriString)
                        }
                        Timber.tag(CAST_LOG_TAG).w(
                            "Remote queue load failed for songId=%s (size=%d detail=%s). Session kept active.",
                            startSong.id,
                            songsToPlay.size,
                            detail
                        )
                        val detailedMessage = detail?.takeIf { it.isNotBlank() }
                        if (detailedMessage != null) {
                            emitCastError("Failed to load media on Cast device: $detailedMessage")
                        }
                        castStateHolder.castSession.value?.remoteMediaClient?.requestStatus()
                    } else {
                        lastRemoteQueue = songsToPlay
                        lastRemoteSongId = startSong.id
                        lastRemoteStreamPosition = 0L
                        lastRemoteRepeatMode = castRepeatMode
                        castStateHolder.setRemotePlaybackActive(true)
                        playbackStateHolder.startProgressUpdates()
                        castStateHolder.castSession.value?.remoteMediaClient?.requestStatus()
                        launchAlignToTarget(startSong.id)
                    }
                    completionDeferred.complete(success)
                }
            )
            return completionDeferred.await()
        }
        return false
    }

    fun markPendingRemoteSong(song: Song) {
        remoteStatus.markPendingRemoteSong(song)
    }

    private fun launchAlignToTarget(targetSongId: String) {
        alignToTargetJob?.cancel()
        alignToTargetJob = scope?.launch {
            remoteStatus.alignRemotePlaybackToSong(targetSongId)
        }
    }

    /**
     * Cleanup all listeners, callbacks, and jobs to prevent memory leaks.
     * Must be called when the owning ViewModel is cleared.
     */
    fun onCleared() {
        // Cancel all active jobs
        remoteProgressObserverJob?.cancel()
        remoteStatusRefreshJob?.cancel()
        sessionSuspendedRecoveryJob?.cancel()
        alignToTargetJob?.cancel()

        // Unregister Cast session manager listener
        castSessionManagerListener?.let { listener ->
            sessionManager?.removeSessionManagerListener(
                listener,
                CastSession::class.java
            )
        }

        // Unregister remote media client listeners from active session
        val remoteClient = castStateHolder.castSession.value?.remoteMediaClient
        remoteProgressListener?.let { remoteClient?.removeProgressListener(it) }
        remoteMediaClientCallback?.let { remoteClient?.unregisterCallback(it) }

        // Null out all callback references to break retain cycles
        castSessionManagerListener = null
        remoteMediaClientCallback = null
        remoteProgressListener = null
        getCurrentQueue = null
        updateQueue = null
        getSongsByIdMap = null
        onTransferBackComplete = null
        onSheetVisible = null
        onDisconnect = null
        onCastError = null
        onSongChanged = null
        scope = null
        skipTransferBackOnNextSessionEnd = false
    }
}
