package com.nobodybuilds.listens.data.service

import android.app.BackgroundServiceStartNotAllowedException
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.nobodybuilds.listens.data.equalizer.EqualizerManager
import com.nobodybuilds.listens.data.navidrome.NavidromeRepository
import com.nobodybuilds.listens.data.preferences.EqualizerPreferencesRepository
import com.nobodybuilds.listens.data.preferences.ThemePreferencesRepository
import com.nobodybuilds.listens.data.preferences.UserPreferencesRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.service.auto.AutoMediaBrowseTree
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.data.service.player.TransitionController
import com.nobodybuilds.listens.data.service.wear.WearStatePublisher
import com.nobodybuilds.listens.di.AppScope
import com.nobodybuilds.listens.presentation.viewmodel.ColorSchemeProcessor
import com.nobodybuilds.listens.presentation.viewmodel.ListeningStatsTracker
import com.nobodybuilds.listens.ui.glancewidget.PlayerActions
import com.nobodybuilds.listens.utils.MediaItemBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * The playback service: owns the media session over [DualPlayerEngine] and wires together the
 * helpers for widgets, the watch, Cast, Android Auto, ReplayGain, timers and queue restore.
 */
@UnstableApi
@AndroidEntryPoint
class MusicService : MediaLibraryService() {

    @Inject
    lateinit var engine: DualPlayerEngine
    @Inject
    lateinit var controller: TransitionController
    @Inject
    lateinit var musicRepository: MusicRepository
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject
    lateinit var equalizerPreferencesRepository: EqualizerPreferencesRepository
    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository
    @Inject
    lateinit var equalizerManager: EqualizerManager
    @Inject
    lateinit var colorSchemeProcessor: ColorSchemeProcessor
    @Inject
    lateinit var autoMediaBrowseTree: AutoMediaBrowseTree
    @Inject
    lateinit var wearStatePublisher: WearStatePublisher
    @Inject
    lateinit var replayGainManager: com.nobodybuilds.listens.data.media.ReplayGainManager
    @Inject
    lateinit var navidromeRepository: NavidromeRepository
    @Inject
    lateinit var listeningStatsTracker: ListeningStatsTracker
    @Inject
    lateinit var gestureController: com.nobodybuilds.listens.data.gestures.GestureController
    @Inject
    @AppScope
    lateinit var appScope: CoroutineScope

    private val state = MusicServiceState()
    private var favoriteSongIds by state::favoriteSongIds
    private var keepPlayingInBackground by state::keepPlayingInBackground
    private var isPlaybackUnloadInProgress by state::isPlaybackUnloadInProgress
    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // Holds the previous main-thread UncaughtExceptionHandler so we can restore it in onDestroy.
    private var previousMainThreadExceptionHandler: Thread.UncaughtExceptionHandler? = null
    /** The cold-start queue restore; widget commands that arrive meanwhile wait for it. */
    private var playbackRestoreJob: Job? = null
    private var temporaryForegroundStartedInOnCreate = false
    private val sessionProvider: () -> MediaSession? = { mediaSession }

    // Helpers need the injected fields, so each is created on first use (after super.onCreate()).
    private val artworkLoader by lazy { WidgetArtworkLoader(applicationContext, musicRepository) }
    private val castRemoteSync: CastRemoteSync by lazy {
        CastRemoteSync(this, listeningStatsTracker) { force -> requestWidgetFullUpdate(force) }
    }
    private val widgetUpdates: WidgetUpdateScheduler by lazy {
        val playerInfoBuilder = WidgetPlayerInfoBuilder(
            applicationContext, engine, themePreferencesRepository, colorSchemeProcessor,
            castRemoteSync, artworkLoader, state
        )
        WidgetUpdateScheduler(applicationContext, serviceScope, engine, castRemoteSync, playerInfoBuilder, wearStatePublisher)
    }
    private val mediaButtons by lazy { MediaSessionButtons(this, serviceScope, state, sessionProvider) }
    private val playbackVolume by lazy { PlaybackVolumeController(engine, replayGainManager, serviceScope, sessionProvider) }
    private val snapshotStore by lazy { PlaybackSnapshotStore(this, engine, userPreferencesRepository, serviceScope, state) }
    private val sleepTimer by lazy { SleepTimerScheduler(this, sessionProvider) }
    private val countedPlay by lazy { CountedPlayController(engine) }
    private val headset by lazy { HeadsetReconnectResumer(this, engine) }
    private val navidromeReporter by lazy { NavidromePlaybackReporter(engine, navidromeRepository, appScope, serviceScope) }
    private val sessionActions by lazy {
        PlayerSessionActions(serviceScope, state, userPreferencesRepository, musicRepository, mediaButtons, widgetUpdates)
    }
    private val playerListener by lazy {
        MusicServicePlayerListener(
            engine, sessionProvider, listeningStatsTracker, gestureController, navidromeRepository, appScope,
            navidromeReporter, playbackVolume, headset, sleepTimer, snapshotStore, mediaButtons, widgetUpdates
        )
    }
    private val widgetCommands by lazy {
        WidgetCommandHandler(
            engine, musicRepository, serviceScope, state, sessionProvider, sessionActions,
            mediaButtons, widgetUpdates, sleepTimer, playbackVolume
        )
    }

    companion object {
        private const val TAG = "MusicService_NobodyListens"
        const val NOTIFICATION_ID = 101
        const val ACTION_SLEEP_TIMER_EXPIRED = "com.nobodybuilds.listens.ACTION_SLEEP_TIMER_EXPIRED"
        /** Sleep space: lower the volume from [EXTRA_SLEEP_FADE_START_AT] to [EXTRA_SLEEP_FADE_END_AT] (wall clock). */
        const val ACTION_SLEEP_FADE = "com.nobodybuilds.listens.ACTION_SLEEP_FADE"
        const val ACTION_SLEEP_FADE_CANCEL = "com.nobodybuilds.listens.ACTION_SLEEP_FADE_CANCEL"
        const val EXTRA_SLEEP_FADE_START_AT = "com.nobodybuilds.listens.extra.SLEEP_FADE_START_AT"
        const val EXTRA_SLEEP_FADE_END_AT = "com.nobodybuilds.listens.extra.SLEEP_FADE_END_AT"
        const val EXTRA_FORCE_FOREGROUND_ON_START =
            "com.nobodybuilds.listens.extra.FORCE_FOREGROUND_ON_START"
        private val pendingMediaButtonForegroundStarts = AtomicInteger(0)
        private const val WIDGET_COMMAND_FOREGROUND_WAIT_MS = 8_000L

        fun markPendingMediaButtonForegroundStart() {
            pendingMediaButtonForegroundStarts.incrementAndGet()
        }

        fun unmarkPendingMediaButtonForegroundStart() {
            while (true) {
                val currentCount = pendingMediaButtonForegroundStarts.get()
                if (currentCount <= 0) return
                if (pendingMediaButtonForegroundStarts.compareAndSet(currentCount, currentCount - 1)) {
                    return
                }
            }
        }

        private fun consumePendingMediaButtonForegroundStart(): Boolean {
            while (true) {
                val currentCount = pendingMediaButtonForegroundStarts.get()
                if (currentCount <= 0) return false
                if (pendingMediaButtonForegroundStarts.compareAndSet(currentCount, currentCount - 1)) {
                    return true
                }
            }
        }
    }

    private val playerSwapListener: (Player) -> Unit = { newPlayer ->
        serviceScope.launch(Dispatchers.Main) {
            publishMediaSessionPlayer(newPlayer, "Swapped MediaSession player to new instance.")
            playbackVolume.prepareReplayGainForTransitionPlayer(newPlayer)
        }
    }

    private val transitionDisplayPlayerListener: (Player) -> Unit = { displayPlayer ->
        serviceScope.launch(Dispatchers.Main) {
            publishMediaSessionPlayer(
                displayPlayer,
                "Published incoming crossfade player to MediaSession."
            )
            playbackVolume.prepareReplayGainForTransitionPlayer(displayPlayer)
        }
    }

    private val transitionFinishedListener: () -> Unit = {
        // Dispatch to Main so it runs after any pending playerSwapListener coroutine
        // has completed — otherwise onTransitionFinished() may see stale state.
        serviceScope.launch(Dispatchers.Main) {
            playbackVolume.onTransitionFinished()
        }
    }

    private fun publishMediaSessionPlayer(player: Player, logMessage: String) {
        val session = mediaSession ?: return
        val oldPlayer = session.player
        if (oldPlayer !== player) {
            oldPlayer.removeListener(playerListener)
            session.player = player
            player.addListener(playerListener)
        }

        Timber.tag("MusicService").d(logMessage)
        syncLocalListeningStatsFromPlayer(player)
        requestWidgetFullUpdate(force = true)
        refreshMediaSessionUi(session)
    }

    private fun syncLocalListeningStatsFromPlayer(player: Player, forceNewSession: Boolean = false) =
        syncListeningStatsFromPlayer(listeningStatsTracker, player, forceNewSession)

    private fun requestWidgetFullUpdate(force: Boolean = false) = widgetUpdates.requestWidgetFullUpdate(force)

    private fun refreshMediaSessionUi(session: MediaSession) = mediaButtons.refreshMediaSessionUi(session)

    private fun getOpenAppPendingIntent(): PendingIntent = openPlayerPendingIntent()

    override fun onCreate() {
        // Media3's Cast SDK callback path (MediaSessionImpl$$ExternalSyntheticLambda →
        // Util.postOrRun → MediaNotificationManager.updateNotificationInternal) calls
        // Service.startForeground() directly, bypassing onUpdateNotification() entirely.
        // Since startForeground() is final we cannot override it. Instead we intercept
        // ForegroundServiceStartNotAllowedException on the main thread before it reaches
        // ActivityThread and crashes the process.
        val existingHandler = Thread.currentThread().uncaughtExceptionHandler
        previousMainThreadExceptionHandler = existingHandler
        Thread.currentThread().setUncaughtExceptionHandler { thread, throwable ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                throwable is ForegroundServiceStartNotAllowedException
            ) {
                Timber.tag(TAG).w(throwable, "Suppressed ForegroundServiceStartNotAllowedException from Media3/Cast internal path")
            } else {
                existingHandler?.uncaughtException(thread, throwable)
            }
        }

        // A MEDIA_BUTTON broadcast starts the 5-second foreground-service timeout
        // BEFORE MusicService is created. Promote to foreground before super.onCreate()
        // — Hilt injection plus MediaLibraryService.onCreate() can otherwise consume
        // most of the budget on a slow cold-start. The temporary notification only uses
        // Context/resources, no injected fields, so it is safe before super.onCreate().
        temporaryForegroundStartedInOnCreate = consumePendingMediaButtonForegroundStart()
        if (temporaryForegroundStartedInOnCreate) {
            startTemporaryForegroundForCommand()
        }

        super.onCreate()
        com.nobodybuilds.listens.ui.glancewidget.WidgetPlaybackLiveness.serviceAlive = true
        listeningStatsTracker.initialize(appScope)
        
        // Ensure engine is ready (re-initialize if service was restarted)
        engine.initialize()
        playbackVolume.userSelectedVolume = engine.masterPlayer.volume.coerceIn(0f, 1f)
        syncLocalListeningStatsFromPlayer(engine.masterPlayer)

        engine.masterPlayer.addListener(playerListener)

        // Handle player swaps (crossfade) to keep MediaSession in sync
        engine.addPlayerSwapListener(playerSwapListener)
        engine.addTransitionDisplayPlayerListener(transitionDisplayPlayerListener)
        engine.addTransitionFinishedListener(transitionFinishedListener)

        controller.initialize()
        castRemoteSync.initializeCastWearSync()
        headset.registerHeadsetReconnectMonitor()
        // Shake to shuffle / face-down to pause. Sensors only run while enabled + playing (screen on or off).
        runCatching {
            gestureController.attach(object : com.nobodybuilds.listens.data.gestures.GestureController.Host {
                override fun currentPlayer(): Player = mediaSession?.player ?: engine.masterPlayer
                override fun setShuffleEnabled(enabled: Boolean) {
                    mediaSession?.let { sessionActions.updateManualShuffleState(it, enabled = enabled, broadcast = true) }
                }
            })
        }.onFailure { Timber.tag(TAG).w(it, "Gesture controller attach failed") }

        serviceScope.launch {
            musicRepository.telegramRepository.downloadCompleted.collect {
                if (isCurrentWidgetArtworkBackedByTelegram()) {
                    artworkLoader.invalidateCachedWidgetArtwork()
                    widgetUpdates.requestWidgetAndWearRefreshWithFollowUp()
                }
            }
        }

        MusicServicePreferenceSync(
            serviceScope, userPreferencesRepository, equalizerPreferencesRepository, equalizerManager,
            engine, state, headset, playbackVolume, mediaButtons, sessionProvider
        ).start()

        val callback = MusicSessionCallback(
            packageName = packageName,
            serviceScope = serviceScope,
            state = state,
            autoMediaBrowseTree = autoMediaBrowseTree,
            autoContextQueues = AutoContextQueueResolver(this, autoMediaBrowseTree, musicRepository, artworkLoader),
            sessionActions = sessionActions,
            mediaButtons = mediaButtons,
            widgetUpdates = widgetUpdates,
            sleepTimer = sleepTimer,
            countedPlay = countedPlay,
            closeNotificationPlayer = ::closeNotificationPlayer
        )

        mediaSession = MediaLibrarySession.Builder(this, engine.masterPlayer, callback)
            .setSessionActivity(getOpenAppPendingIntent())
            .setBitmapLoader(CoilBitmapLoader(this, serviceScope))
            .build()

        val localOnlyProvider = LocalOnlyMediaNotificationProvider(this).also {
            it.setSmallIcon(com.nobodybuilds.listens.shared.R.drawable.nl_mark_mono)
        }
        setMediaNotificationProvider(localOnlyProvider)
        playbackRestoreJob = serviceScope.launch {
            snapshotStore.restorePlaybackQueueSnapshotIfNeeded()
            mediaSession?.let { refreshMediaSessionUi(it) }
            requestWidgetFullUpdate(force = true)
        }

        serviceScope.launch {
            musicRepository.getFavoriteSongIdsFlow().collect { ids ->
                Timber.tag("MusicService")
                    .d("favoriteSongIdsFlow(Room) collected. New ids size: ${ids.size}")
                val oldIds = favoriteSongIds
                favoriteSongIds = ids
                val currentSongId = mediaSession?.player?.currentMediaItem?.mediaId
                if (currentSongId != null) {
                    val wasFavorite = oldIds.contains(currentSongId)
                    val isFavorite = ids.contains(currentSongId)
                    if (wasFavorite != isFavorite) {
                        Timber.tag("MusicService")
                            .d("Favorite status changed for current song. Updating notification.")
                        mediaSession?.let { refreshMediaSessionUi(it) }
                        requestWidgetFullUpdate(force = true)
                    }
                }
            }
        }
    }

    /** The real foreground state (Media3 keeps the service foreground for a while after a pause). */
    private fun isServiceAlreadyForeground(): Boolean = foregroundServiceType != 0

    /** Widget commands still running; the service never stops while one is in flight. */
    private var widgetCommandsInFlight = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startedTemporaryForegroundInOnCreate = temporaryForegroundStartedInOnCreate
        temporaryForegroundStartedInOnCreate = false
        val pendingMediaButtonForegroundStart = consumePendingMediaButtonForegroundStart()
        val forcedForegroundStart =
            intent?.getBooleanExtra(EXTRA_FORCE_FOREGROUND_ON_START, false) == true
        val isMediaButtonIntent = intent?.action == Intent.ACTION_MEDIA_BUTTON
        // A widget tap is a foreground-service start; when Media3 already holds the service in
        // the foreground there is nothing to satisfy, and a second "processing" notification
        // would replace the media one.
        val needsTemporaryForeground = (forcedForegroundStart && !isServiceAlreadyForeground()) ||
            pendingMediaButtonForegroundStart ||
            (isMediaButtonIntent &&
                !startedTemporaryForegroundInOnCreate &&
                !isServiceAlreadyForeground())
        if (needsTemporaryForeground && !startedTemporaryForegroundInOnCreate) {
            startTemporaryForegroundForCommand()
        }

        val action = intent?.action
        var widgetCommand = false
        if (action != null) {
            val player = mediaSession?.player
            if (player != null) {
                if (action in PlayerActions.ALL) {
                    widgetCommand = true
                    widgetCommandsInFlight++
                    val commandIntent = Intent(intent)
                    val startedTemp = needsTemporaryForeground
                    serviceScope.launch {
                        try {
                            val cold = player.mediaItemCount == 0 || playbackRestoreJob?.isActive == true
                            if (cold) {
                                // Cold start from a widget (app was killed): wait for the queue
                                // restore, then act — "Play" must resume the last queue.
                                runCatching { playbackRestoreJob?.join() }
                                runCatching { snapshotStore.restorePlaybackQueueSnapshotIfNeeded() }
                            }
                            val current = mediaSession?.player
                            if (current != null) {
                                runCatching { widgetCommands.handleStartCommandAction(current, action, commandIntent, coldStart = cold) }
                                    .onFailure { Timber.tag(TAG).w(it, "Widget command %s failed", action) }
                            }
                            if (startedTemp) settleTemporaryForeground()
                        } finally {
                            widgetCommandsInFlight--
                            // Only an empty player (nothing to resume) lets the service go; a paused
                            // queue stays loaded so the widgets and controls keep working.
                            if (widgetCommandsInFlight == 0 && (mediaSession?.player?.mediaItemCount ?: 0) == 0) {
                                stopSelfResult(startId)
                            }
                        }
                    }
                } else {
                    serviceScope.launch { widgetCommands.handleStartCommandAction(player, action, intent, coldStart = false) }
                }
            }
        }
        val startCommandResult = super.onStartCommand(intent, flags, startId)
        if (needsTemporaryForeground && !widgetCommand) {
            // Media-button / internal starts: same as before.
            if (!isPlayerActive(mediaSession?.player)) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
        return startCommandResult
    }

    /**
     * After a widget command that needed the temporary "processing" foreground: once playback is
     * running Media3 takes the foreground with its own notification (which replaces ours); if
     * nothing plays, only our notification is dropped — the service stays for the paused queue.
     */
    private suspend fun settleTemporaryForeground() {
        if (isPlayerActive(mediaSession?.player)) {
            kotlinx.coroutines.withTimeoutOrNull(WIDGET_COMMAND_FOREGROUND_WAIT_MS) {
                while (isTemporaryForegroundNotificationShowing() && isPlayerActive(mediaSession?.player)) {
                    delay(250L)
                }
            }
        }
        if (!isTemporaryForegroundNotificationShowing()) return
        if (isPlayerActive(mediaSession?.player)) {
            // Media3 hasn't re-posted yet: ask it to, which swaps the foreground notification.
            mediaSession?.let { runCatching { onUpdateNotification(it, true) } }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        val allowBackground = keepPlayingInBackground

        if (!allowBackground) {
            stopPlaybackAndUnload(
                reason = "task_removed_background_disabled"
            )
            return
        }

        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopPlaybackAndUnload(
                reason = "task_removed_not_playing"
            )
            return
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onDestroy() {
        listeningStatsTracker.finalizeCurrentSession(forceSynchronousPersistence = true)
        navidromeReporter.reportNavidromePlayback("stopped")
        navidromeReporter.stopNavidromePlaybackReporting()
        snapshotStore.cancelPendingPersist()
        mediaButtons.cancelPendingRefreshes()
        widgetUpdates.cancelPendingUpdates()
        castRemoteSync.stopCastWearSync()
        headset.unregisterHeadsetReconnectMonitor()
        runCatching { gestureController.detach() }
        wearStatePublisher.clearState()
        // Widgets keep their last state on disk; mark it "not playing" so they don't show a
        // playing song after the service (or later the process) is gone.
        com.nobodybuilds.listens.ui.glancewidget.WidgetPlaybackLiveness.serviceAlive = false
        com.nobodybuilds.listens.ui.glancewidget.PlayerWidgetUpdater.markStopped(applicationContext)
        playbackVolume.replayGainJob?.cancel()

        engine.removePlayerSwapListener(playerSwapListener)
        engine.removeTransitionDisplayPlayerListener(transitionDisplayPlayerListener)
        engine.removeTransitionFinishedListener(transitionFinishedListener)
        mediaSession?.player?.removeListener(playerListener)
        engine.masterPlayer.removeListener(playerListener)

        mediaSession?.run {
            release()
            mediaSession = null
        }
        engine.release()
        controller.release()
        serviceScope.cancel()
        Thread.currentThread().setUncaughtExceptionHandler(previousMainThreadExceptionHandler)
        previousMainThreadExceptionHandler = null
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Release in-process bitmap caches when the system signals memory pressure.
        // MusicService holds up to ~220 KB in cachedWidgetArtBytes and another copy
        // inside lastWidgetPlayerInfo.albumArtBitmapData. Under moderate-to-critical
        // pressure these are safe to drop — the next widget update cycle will reload them.
        //
        // Raw int value used instead of deprecated ComponentCallbacks2 constant:
        //   TRIM_MEMORY_RUNNING_LOW = 10
        // This single threshold covers all higher-severity levels too
        // (TRIM_MEMORY_BACKGROUND = 40, TRIM_MEMORY_COMPLETE = 80, etc.).
        if (level >= 10 /* TRIM_MEMORY_RUNNING_LOW */) {
            Timber.tag(TAG).d("onTrimMemory(level=%d): releasing widget bitmap caches", level)
            artworkLoader.invalidateCachedWidgetArtwork()
            // Drop the stale PlayerInfo copy so its embedded ByteArray is GC-eligible.
            // The next processWidgetUpdateInternal() call will rebuild it from scratch.
            widgetUpdates.lastWidgetPlayerInfo = null
        }
    }

    private fun isCurrentWidgetArtworkBackedByTelegram(): Boolean {
        val currentItem = engine.masterPlayer.currentMediaItem ?: return false
        val metadata = currentItem.mediaMetadata
        val contentUriString = currentItem.localConfiguration?.uri?.toString()
            ?: metadata.extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)
        val artworkUriString = resolveStoredArtworkUriString(metadata)
        return contentUriString?.startsWith("telegram://") == true ||
            artworkUriString?.startsWith("telegram_art://") == true
    }

    fun isSongFavorite(songId: String?): Boolean {
        return state.isSongFavorite(songId)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val playWhenReady = session.player.playWhenReady
        val playbackState = session.player.playbackState

        // Android 12+ (API 31+): Only request foreground when actively playing.
        // This prevents requesting foreground start when player is idle/ended.
        val shouldStartInForeground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startInForegroundRequired && playWhenReady
                    && playbackState != Player.STATE_IDLE
                    && playbackState != Player.STATE_ENDED
        } else {
            startInForegroundRequired
        }

        try {
            super.onUpdateNotification(session, shouldStartInForeground)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "onUpdateNotification suppressed: ${e.message}")
        }
    }

    override fun startForegroundService(serviceIntent: Intent?): ComponentName? {
        // Android 12+ (API 31+): Media3 calls startForegroundService asynchronously
        // (e.g. after bitmap loading or Cast SDK callbacks). By that time the app may
        // already be in the background, causing ForegroundServiceStartNotAllowedException.
        // Do not fall back to startService(): on Android 12+ that turns the original
        // foreground-service exception into BackgroundServiceStartNotAllowedException,
        // which Media3 does not handle and crashes the process. If the service is
        // already foreground, Media3's subsequent startForeground() call will simply
        // update the notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return try {
                super.startForegroundService(serviceIntent)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Timber.tag(TAG).w(
                    e,
                    "startForegroundService not allowed; ignoring redundant self-start request"
                )
                serviceIntent?.component ?: ComponentName(this, javaClass)
            } catch (e: BackgroundServiceStartNotAllowedException) {
                // Thrown when startForegroundService() itself is called while the app is in a
                // background-cached state (distinct from ForegroundServiceStartNotAllowedException).
                // Safe to swallow: the service is either already running or Media3 will retry.
                Timber.tag(TAG).w(
                    e,
                    "startForegroundService blocked (app in background); ignoring self-start request"
                )
                serviceIntent?.component ?: ComponentName(this, javaClass)
            }
        }
        return super.startForegroundService(serviceIntent)
    }

    private fun closeNotificationPlayer() {
        stopPlaybackAndUnload(
            reason = "notification_close_button",
            preservePlaybackSnapshot = false
        )
    }

    private fun stopPlaybackAndUnload(
        reason: String,
        preservePlaybackSnapshot: Boolean = true,
    ) {
        Timber.tag(TAG).d(
            "Stopping playback and unloading service. reason=%s",
            reason
        )
        isPlaybackUnloadInProgress = true
        mediaButtons.cancelPendingRefreshes()
        widgetUpdates.cancelPendingUpdates()
        snapshotStore.cancelPendingPersist()

        val sessionToRelease = mediaSession
        val player = sessionToRelease?.player ?: engine.masterPlayer

        headset.clearHeadsetReconnectResume()
        sleepTimer.cancelDurationSleepTimerInternal()
        sleepTimer.endOfTrackTimerSongId = null

        if (preservePlaybackSnapshot) {
            snapshotStore.persistPlaybackSnapshotBlocking()
        } else {
            snapshotStore.clearPlaybackSnapshotBlocking()
        }

        listeningStatsTracker.finalizeCurrentSession(forceSynchronousPersistence = true)

        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()

        requestWidgetFullUpdate(force = true)
        stopForeground(STOP_FOREGROUND_REMOVE)

        stopSelf()
    }

    // ------------------------
    // Counted Play Controls
    // ------------------------
    fun startCountedPlay(count: Int) = countedPlay.startCountedPlay(count)

    fun stopCountedPlay(restoreRepeatMode: Boolean = true) = countedPlay.stopCountedPlay(restoreRepeatMode)
}
