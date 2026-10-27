package com.nobodybuilds.listens.data.service

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.nobodybuilds.listens.data.service.auto.AutoMediaBrowseTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * [MusicService]'s media library session callback: who may connect, the custom commands from the
 * notification, widgets and watch, and Android Auto browsing, search and queue requests.
 */
@OptIn(UnstableApi::class)
internal class MusicSessionCallback(
    private val packageName: String,
    private val serviceScope: CoroutineScope,
    private val state: MusicServiceState,
    private val autoMediaBrowseTree: AutoMediaBrowseTree,
    private val autoContextQueues: AutoContextQueueResolver,
    private val sessionActions: PlayerSessionActions,
    private val mediaButtons: MediaSessionButtons,
    private val widgetUpdates: WidgetUpdateScheduler,
    private val sleepTimer: SleepTimerScheduler,
    private val countedPlay: CountedPlayController,
    private val closeNotificationPlayer: () -> Unit
) : MediaLibrarySession.Callback {
    private val isManualShuffleEnabled by state::isManualShuffleEnabled
    private val favoriteSongIds by state::favoriteSongIds

    private fun refreshMediaSessionUi(session: MediaSession) = mediaButtons.refreshMediaSessionUi(session)

    private fun requestWidgetFullUpdate(force: Boolean) = widgetUpdates.requestWidgetFullUpdate(force)

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val controllerPackage = controller.packageName
        val hintKeys = controller.connectionHints.keySet().joinToString(",")
        Timber.tag(TAG).d(
            "onConnect from package=%s uid=%s trusted=%s version=%s hints=[%s]",
            controllerPackage,
            controller.uid,
            controller.isTrusted,
            controller.controllerVersion,
            hintKeys
        )
        if (shouldRejectWearController(controller)) {
            Timber.tag(TAG).i(
                "Rejecting Wear system controller connection from package=%s",
                controllerPackage
            )
            return MediaSession.ConnectionResult.reject()
        }

        val defaultResult = super.onConnect(session, controller)
        val customCommands = listOf(
            MusicNotificationProvider.CUSTOM_COMMAND_CLOSE_PLAYER,
            MusicNotificationProvider.CUSTOM_COMMAND_LIKE,
            MusicNotificationProvider.CUSTOM_COMMAND_SET_FAVORITE_STATE,
            MusicNotificationProvider.CUSTOM_COMMAND_TOGGLE_SHUFFLE,
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON,
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF,
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE,
            MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE,
            MusicNotificationProvider.CUSTOM_COMMAND_COUNTED_PLAY,
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_DURATION,
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_TRACK,
            MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_SLEEP_TIMER,
        ).map { SessionCommand(it, Bundle.EMPTY) }

        val sessionCommandsBuilder = SessionCommands.Builder()
            .addSessionCommands(defaultResult.availableSessionCommands.commands)
        customCommands.forEach { sessionCommandsBuilder.add(it) }
        autoContextQueues.grantArtworkUriPermissions(
            controller.packageName,
            listOfNotNull(session.player.currentMediaItem)
        )

        return MediaSession.ConnectionResult.accept(
            sessionCommandsBuilder.build(),
            defaultResult.availablePlayerCommands
        )
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        autoContextQueues.clearLastBrowsedParent(controller)
        super.onDisconnected(session, controller)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        Timber.tag("MusicService")
            .d("onCustomCommand received: ${customCommand.customAction}")
        when (customCommand.customAction) {
            MusicNotificationProvider.CUSTOM_COMMAND_CLOSE_PLAYER -> {
                closeNotificationPlayer()
            }
            MusicNotificationProvider.CUSTOM_COMMAND_COUNTED_PLAY -> {
                val count = args.getInt("count", 1)
                countedPlay.startCountedPlay(count)
            }
            MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_COUNTED_PLAY -> {
                countedPlay.stopCountedPlay()
            }
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_DURATION -> {
                val minutes = args.getInt(
                    MusicNotificationProvider.EXTRA_SLEEP_TIMER_MINUTES,
                    0
                )
                sleepTimer.setDurationSleepTimer(minutes)
            }
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_TRACK -> {
                val enabled = args.getBoolean(
                    MusicNotificationProvider.EXTRA_END_OF_TRACK_ENABLED,
                    true
                )
                sleepTimer.setEndOfTrackSleepTimer(enabled)
            }
            MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_SLEEP_TIMER -> {
                sleepTimer.cancelSleepTimers()
            }
            MusicNotificationProvider.CUSTOM_COMMAND_TOGGLE_SHUFFLE -> {
                val enabled = !isManualShuffleEnabled
                sessionActions.updateManualShuffleState(session, enabled = enabled, broadcast = true)
            }
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON -> {
                Timber.tag("MusicService")
                    .d("Executing SHUFFLE_ON. Current shuffleMode: ${session.player.shuffleModeEnabled}")
                sessionActions.updateManualShuffleState(session, enabled = true, broadcast = true)
            }
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF -> {
                Timber.tag("MusicService")
                    .d("Executing SHUFFLE_OFF. Current shuffleMode: ${session.player.shuffleModeEnabled}")
                sessionActions.updateManualShuffleState(session, enabled = false, broadcast = true)
            }
            MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE -> {
                val enabled = args.getBoolean(
                    MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED,
                    false
                )
                sessionActions.updateManualShuffleState(session, enabled = enabled, broadcast = false)
            }
            MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE -> {
                val currentMode = session.player.repeatMode
                val newMode = when (currentMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                    Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
                session.player.repeatMode = newMode
                refreshMediaSessionUi(session)
                requestWidgetFullUpdate(force = true)
            }
            MusicNotificationProvider.CUSTOM_COMMAND_LIKE -> {
                val songId = session.player.currentMediaItem?.mediaId
                    ?: return@onCustomCommand Futures.immediateFuture(
                        SessionResult(SessionError.ERROR_UNKNOWN)
                    )
                val targetFavoriteState = !favoriteSongIds.contains(songId)
                return sessionActions.setCurrentSongFavoriteState(
                    session = session,
                    targetFavoriteState = targetFavoriteState
                )
            }
            MusicNotificationProvider.CUSTOM_COMMAND_SET_FAVORITE_STATE -> {
                val enabled = args.getBoolean(
                    MusicNotificationProvider.EXTRA_FAVORITE_ENABLED,
                    false
                )
                return sessionActions.setCurrentSongFavoriteState(
                    session = session,
                    targetFavoriteState = enabled
                )
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    // --- Android Auto: Media Library Browsing ---

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val rootItem = MediaItem.Builder()
            .setMediaId(AutoMediaBrowseTree.ROOT_ID)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle("Nobody Listens")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
        return serviceScope.future {
            try {
                autoContextQueues.rememberLastBrowsedParent(browser, parentId)
                val children = autoMediaBrowseTree.getChildren(parentId, page, pageSize)
                autoContextQueues.grantArtworkUriPermissions(browser.packageName, children)
                LibraryResult.ofItemList(children, params)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "onGetChildren failed for parentId=$parentId")
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return serviceScope.future {
            try {
                val item = autoMediaBrowseTree.getItem(mediaId)
                if (item != null) {
                    autoContextQueues.grantArtworkUriPermissions(browser.packageName, listOf(item))
                    LibraryResult.ofItem(item, null)
                } else {
                    LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "onGetItem failed for mediaId=$mediaId")
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        // Signal that search is supported; results delivered via onGetSearchResult
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
        return serviceScope.future {
            try {
                val allResults = autoMediaBrowseTree.search(query)
                val effectivePage = page.coerceAtLeast(0)
                val effectivePageSize = if (pageSize > 0) pageSize else Int.MAX_VALUE
                val offset = (effectivePage.toLong() * effectivePageSize.toLong())
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                val pagedResults = allResults
                    .drop(offset)
                    .take(effectivePageSize)

                autoContextQueues.grantArtworkUriPermissions(browser.packageName, pagedResults)
                LibraryResult.ofItemList(pagedResults, params)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "onGetSearchResult failed for query=$query")
                LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
            }
        }
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        return serviceScope.future {
            if (mediaItems.size == 1) {
                autoContextQueues.resolveContextQueueForRequestedItem(mediaItems.first(), controller)?.let { queue ->
                    autoContextQueues.grantArtworkUriPermissions(controller.packageName, queue.mediaItems)
                    return@future queue.mediaItems
                }
            }
            autoContextQueues.resolveMediaItemsByIds(mediaItems).also { resolvedItems ->
                autoContextQueues.grantArtworkUriPermissions(
                    controller.packageName,
                    resolvedItems.trustedArtworkGrantItems
                )
            }.mediaItems
        }
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return serviceScope.future {
            val requestedIndex = startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
            val requestedItem = mediaItems.getOrNull(requestedIndex)

            val contextQueue = requestedItem?.let {
                autoContextQueues.resolveContextQueueForRequestedItem(it, controller)
            }
            if (contextQueue != null) {
                autoContextQueues.grantArtworkUriPermissions(controller.packageName, contextQueue.mediaItems)
                return@future MediaSession.MediaItemsWithStartPosition(
                    contextQueue.mediaItems,
                    contextQueue.startIndex,
                    startPositionMs
                )
            }

            val resolvedItems = autoContextQueues.resolveMediaItemsByIds(mediaItems)
            autoContextQueues.grantArtworkUriPermissions(
                controller.packageName,
                resolvedItems.trustedArtworkGrantItems
            )
            val safeStartIndex = requestedIndex.coerceIn(
                0,
                (resolvedItems.mediaItems.size - 1).coerceAtLeast(0)
            )
            MediaSession.MediaItemsWithStartPosition(
                resolvedItems.mediaItems,
                safeStartIndex,
                startPositionMs
            )
        }
    }

    private fun shouldRejectWearController(controller: MediaSession.ControllerInfo): Boolean {
        val controllerPackage = controller.packageName
        if (controllerPackage == packageName || controllerPackage.startsWith(APP_PACKAGE_PREFIX)) {
            return false
        }
        val blockedByPackage = BLOCKED_WEAR_CONTROLLER_PREFIXES.any { prefix ->
            controllerPackage.startsWith(prefix)
        }
        if (blockedByPackage) {
            return true
        }

        val hasWearHints = controller.connectionHints.keySet().any { key ->
            WEAR_HINT_KEY_MARKERS.any { marker ->
                key.contains(marker, ignoreCase = true)
            }
        }
        return hasWearHints
        // If hints identify a Wear/remote controller and it's not our app package,
        // reject to avoid the default Wear system media player hijacking the session.
        return true
    }

    /**
     * Bridges a suspend block into a [ListenableFuture] for Media3 callback methods.
     */
    private fun <T> CoroutineScope.future(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        launch(Dispatchers.IO) {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    private companion object {
        const val TAG = "MusicService_NobodyListens"
        const val APP_PACKAGE_PREFIX = "com.nobodybuilds.listens"
        val BLOCKED_WEAR_CONTROLLER_PREFIXES = listOf(
            "com.google.android.wearable",
            "com.google.android.clockwork",
            "com.google.android.apps.wearable",
            "com.google.android.apps.wear.companion",
            "com.samsung.android.app.watchmanager",
            "com.mobvoi.wear",
        )
        val WEAR_HINT_KEY_MARKERS = listOf(
            "wear",
            "clockwork",
            "companion",
            "node",
            "remote_device",
        )
    }
}
