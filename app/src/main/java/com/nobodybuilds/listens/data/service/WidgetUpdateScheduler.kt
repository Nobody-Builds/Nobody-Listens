package com.nobodybuilds.listens.data.service

import android.content.Context
import com.nobodybuilds.listens.data.jiosaavn.JioSaavnUris
import com.nobodybuilds.listens.data.model.PlayerInfo
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.data.service.wear.WearStatePublisher
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicUris
import com.nobodybuilds.listens.shared.WearHandoffSong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debounced widget and watch updates for [MusicService]: rebuilds the [PlayerInfo] and pushes it
 * only when something the widgets or the watch show has changed.
 */
internal class WidgetUpdateScheduler(
    private val applicationContext: Context,
    private val serviceScope: CoroutineScope,
    private val engine: DualPlayerEngine,
    private val castRemoteSync: CastRemoteSync,
    private val playerInfoBuilder: WidgetPlayerInfoBuilder,
    private val wearStatePublisher: WearStatePublisher
) {
    private var debouncedWidgetUpdateJob: Job? = null
    private var followUpWidgetUpdateJob: Job? = null
    private val widgetStateDebounceMs = 300L
    var lastWidgetPlayerInfo: PlayerInfo? = null

    fun requestWidgetFullUpdate(force: Boolean = false) {
        debouncedWidgetUpdateJob?.cancel()
        debouncedWidgetUpdateJob = serviceScope.launch {
            val debounceMs = if (force) {
                FORCED_WIDGET_STATE_DEBOUNCE_MS
            } else {
                widgetStateDebounceMs
            }
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            processWidgetUpdateInternal()
        }
    }

    fun requestWidgetAndWearRefreshWithFollowUp() {
        requestWidgetFullUpdate(force = true)
        followUpWidgetUpdateJob?.cancel()
        followUpWidgetUpdateJob = serviceScope.launch {
            delay(250L)
            requestWidgetFullUpdate(force = true)
        }
    }

    fun cancelPendingUpdates() {
        followUpWidgetUpdateJob?.cancel()
        debouncedWidgetUpdateJob?.cancel()
    }

    private suspend fun resolveCurrentMediaIdForWear(): String? {
        val remoteSongId = castRemoteSync.resolveCastRemoteSnapshot()?.songId
        if (!remoteSongId.isNullOrBlank()) {
            return remoteSongId
        }
        val player = engine.masterPlayer
        return withContext(Dispatchers.Main) { player.currentMediaItem?.mediaId }
    }

    /** The current song's stream id when it is a YouTube Music or JioSaavn stream, so the watch can stream it too. */
    private suspend fun currentStreamIdForWear(): String? {
        if (castRemoteSync.resolveCastRemoteSnapshot() != null) return null
        val player = engine.masterPlayer
        val uri = withContext(Dispatchers.Main) { player.currentMediaItem?.localConfiguration?.uri?.toString() }
            ?: return null
        YouTubeMusicUris.videoIdFromPlaybackUri(uri)?.let { return WearHandoffSong.YOUTUBE_PREFIX + it }
        return JioSaavnUris.songIdFromPlaybackUri(uri)?.let { WearHandoffSong.JIOSAAVN_PREFIX + it }
    }

    private fun shouldUpdateWidget(old: PlayerInfo, new: PlayerInfo): Boolean {
        if (old.songTitle != new.songTitle) return true
        if (old.artistName != new.artistName) return true
        if (old.isPlaying != new.isPlaying) return true
        if (old.albumArtUri != new.albumArtUri) return true
        // Detect when artwork bytes arrive (null → non-null) or are cleared
        if ((old.albumArtBitmapData == null) != (new.albumArtBitmapData == null)) return true
        if (old.isFavorite != new.isFavorite) return true
        if (old.queue != new.queue) return true
        if (old.themeColors != new.themeColors) return true
        if (old.isShuffleEnabled != new.isShuffleEnabled) return true
        if (old.repeatMode != new.repeatMode) return true
        if (old.totalDurationMs != new.totalDurationMs) return true
        if (old.wearThemePalette != new.wearThemePalette) return true

        val drift = kotlin.math.abs(old.currentPositionMs - new.currentPositionMs)
        return drift > 3000L
    }

    private fun shouldPublishWearState(old: PlayerInfo, new: PlayerInfo): Boolean {
        return shouldUpdateWidget(old, new) || old.wearQueueRevision != new.wearQueueRevision
    }

    private suspend fun processWidgetUpdateInternal() {
        val playerInfo = playerInfoBuilder.buildPlayerInfo()
        val oldInfo = lastWidgetPlayerInfo

        val shouldUpdateWidgets = oldInfo == null || shouldUpdateWidget(oldInfo, playerInfo)
        val shouldPublishWear = oldInfo == null || shouldPublishWearState(oldInfo, playerInfo)

        if (shouldUpdateWidgets || shouldPublishWear) {
            lastWidgetPlayerInfo = playerInfo
        }

        if (shouldUpdateWidgets) {
            updateGlanceWidgets(playerInfo)
        }

        if (shouldPublishWear) {
            val currentMediaId = resolveCurrentMediaIdForWear()
            // Publish state to Wear OS watch
            wearStatePublisher.publishState(currentMediaId, playerInfo, currentStreamIdForWear())
        }
    }

    private fun updateGlanceWidgets(playerInfo: PlayerInfo) {
        // One shared state write for all player widgets, throttled to <= 1 push per second.
        com.nobodybuilds.listens.ui.glancewidget.PlayerWidgetUpdater.submit(
            applicationContext,
            playerInfo.toWidgetTransportState()
        )
    }

    private fun PlayerInfo.toWidgetTransportState(): PlayerInfo {
        return copy(
            lyrics = null,
            isLoadingLyrics = false,
            queue = queue.take(WIDGET_QUEUE_PREVIEW_LIMIT),
            wearThemePalette = null,
            wearQueueRevision = "",
        )
    }

    private companion object {
        const val FORCED_WIDGET_STATE_DEBOUNCE_MS = 250L
        const val WIDGET_QUEUE_PREVIEW_LIMIT = 4
    }
}
