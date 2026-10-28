package com.nobodybuilds.listens.data

/** Monotonic playback anchor, retained even while UI progress updates are suspended. */
internal data class PlaybackPosition(
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val anchoredAtMs: Long,
) {
    fun positionAt(nowMs: Long): Long {
        val duration = durationMs.coerceAtLeast(0L)
        val position = positionMs.coerceIn(0L, duration)
        if (!isPlaying) return position
        val elapsed = (nowMs - anchoredAtMs).coerceAtLeast(0L)
        return position + elapsed.coerceAtMost(duration - position)
    }
}
