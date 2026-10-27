package com.nobodybuilds.listens.data

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl

/**
 * What the watch player does differently for streams (YouTube Music, JioSaavn): holds Wi-Fi while you mean to
 * listen, buffers deeper to ride out a patchy connection, and picks up where it stopped after a dropout.
 */
internal class WearStreamingPlayback(
    private val network: WearStreamingNetwork,
    private val youTube: WearYouTubeRepository,
    private val jioSaavn: WearJioSaavnRepository,
) {
    private var retriesForItem = 0
    private val handler = Handler(Looper.getMainLooper())
    private val releaseNetwork = Runnable { network.release() }

    fun isStream(uri: Uri?): Boolean =
        uri != null && (WearYouTubeRepository.videoIdFrom(uri) != null || WearJioSaavnRepository.songIdFrom(uri) != null)

    /**
     * Keeps Wi-Fi while a stream is meant to play (also while it rebuffers); lets it go a little after a pause
     * or the end, so setting up the next queue or a quick pause doesn't drop it.
     */
    fun updateNetwork(player: Player) {
        val wantsStream = player.playWhenReady && player.playerError == null && player.playbackState != Player.STATE_ENDED &&
            isStream(player.currentMediaItem?.localConfiguration?.uri)
        handler.removeCallbacks(releaseNetwork)
        if (wantsStream) network.acquire() else handler.postDelayed(releaseNetwork, RELEASE_DELAY_MS)
    }

    fun onItemChanged() {
        retriesForItem = 0
    }

    /**
     * Retries a stream that failed on the network, from the same position, a few times per song. A refused
     * URL (403/410, usually expired) is dropped first so the retry gets a fresh one. Returns whether it retried.
     */
    @OptIn(UnstableApi::class)
    fun recover(player: Player, error: PlaybackException): Boolean {
        val uri = player.currentMediaItem?.localConfiguration?.uri
        if (!isStream(uri) || uri == null) return false
        val code = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        if (code == 403 || code == 410) {
            WearYouTubeRepository.videoIdFrom(uri)?.let(youTube::reportStreamRejected)
            WearJioSaavnRepository.songIdFrom(uri)?.let(jioSaavn::reportStreamRejected)
        } else if (error.errorCode / 1000 != IO_ERROR_GROUP) {
            return false
        }
        if (retriesForItem >= MAX_RETRIES_PER_ITEM) return false
        retriesForItem++
        player.prepare()
        player.playWhenReady = true
        return true
    }

    private companion object {
        /** PlaybackException.ERROR_CODE_IO_* are 2000–2999. */
        const val IO_ERROR_GROUP = 2
        const val MAX_RETRIES_PER_ITEM = 3
        const val RELEASE_DELAY_MS = 15_000L
    }
}

/** Up to three minutes ahead, so a Bluetooth-proxy hiccup or a Wi-Fi handover doesn't stop the song. */
@OptIn(UnstableApi::class)
internal fun wearStreamingLoadControl(): LoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs = */ 60_000,
        /* maxBufferMs = */ 180_000,
        /* bufferForPlaybackMs = */ 2_500,
        /* bufferForPlaybackAfterRebufferMs = */ 5_000,
    )
    .build()
