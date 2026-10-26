package com.nobodybuilds.listens.data.service

import androidx.media3.common.MediaItem
import com.nobodybuilds.listens.data.navidrome.NavidromeRepository
import com.nobodybuilds.listens.data.service.player.DualPlayerEngine
import com.nobodybuilds.listens.utils.MediaItemBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Reports Navidrome playback (now playing, position every 30 s, stopped) to the server. */
internal class NavidromePlaybackReporter(
    private val engine: DualPlayerEngine,
    private val navidromeRepository: NavidromeRepository,
    private val appScope: CoroutineScope,
    private val serviceScope: CoroutineScope
) {
    fun getNavidromeId(mediaItem: MediaItem?): String? {
        if (mediaItem == null) return null
        return mediaItem.mediaMetadata.extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_NAVIDROME_ID)
            ?: mediaItem.mediaId.let { if (it.startsWith("navidrome_")) it.substringAfter("navidrome_") else null }
            ?: mediaItem.mediaMetadata.extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)?.let {
                if (it.startsWith("navidrome://")) it.substringAfter("navidrome://") else null
            }
    }

    fun isNavidromeMediaItem(mediaItem: MediaItem?): Boolean {
        return getNavidromeId(mediaItem) != null
    }

    fun reportNavidromePlayback(state: String, mediaItem: MediaItem? = engine.masterPlayer.currentMediaItem) {
        val player = engine.masterPlayer
        // Ensure we capture player state on main thread to avoid IllegalStateException
        val targetItem = mediaItem ?: return
        val navidromeId = getNavidromeId(targetItem) ?: return

        // If reporting for current item, use player position.
        // If reporting "stopped" for a transition, use the item's duration as final position.
        val positionMs = if (targetItem === player.currentMediaItem) {
            player.currentPosition
        } else {
            targetItem.mediaMetadata.extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION) ?: 0L
        }
        val playbackRate = player.playbackParameters.speed

        // Use appScope for the network call so it survives if serviceScope is cancelled
        appScope.launch(Dispatchers.IO) {
            navidromeRepository.reportPlayback(
                navidromeId = navidromeId,
                positionMs = positionMs,
                state = state,
                playbackRate = playbackRate
            )
        }
    }

    private var navidromePlaybackReportJob: Job? = null

    fun startNavidromePlaybackReporting() {
        navidromePlaybackReportJob?.cancel()
        navidromePlaybackReportJob = serviceScope.launch {
            while (true) {
                delay(30_000) // Report every 30 seconds
                val player = engine.masterPlayer
                if (player.isPlaying && isNavidromeMediaItem(player.currentMediaItem)) {
                    reportNavidromePlayback("playing")
                }
            }
        }
    }

    fun stopNavidromePlaybackReporting() {
        navidromePlaybackReportJob?.cancel()
        navidromePlaybackReportJob = null
    }
}
