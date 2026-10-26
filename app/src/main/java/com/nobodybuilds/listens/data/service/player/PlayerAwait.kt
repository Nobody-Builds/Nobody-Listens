package com.nobodybuilds.listens.data.service.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Waits up to [timeoutMs] for [player] to leave buffering; true when it became ready. */
internal suspend fun awaitPlayerReady(player: ExoPlayer, timeoutMs: Long): Boolean {
    if (player.playbackState == Player.STATE_READY) return true
    return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_BUFFERING) {
                        player.removeListener(this)
                        if (cont.isActive) cont.resume(playbackState == Player.STATE_READY)
                    }
                }
            }
            player.addListener(listener)
            cont.invokeOnCancellation { player.removeListener(listener) }
        }
    } ?: false
}

/** Waits up to [timeoutMs] for [player] to start playing. */
internal suspend fun awaitPlayerPlaying(player: ExoPlayer, timeoutMs: Long): Boolean {
    if (player.isPlaying) return true
    return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        player.removeListener(this)
                        if (cont.isActive) cont.resume(true)
                    }
                }
            }
            player.addListener(listener)
            cont.invokeOnCancellation { player.removeListener(listener) }
        }
    } ?: false
}
