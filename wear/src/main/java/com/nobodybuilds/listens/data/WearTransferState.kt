package com.nobodybuilds.listens.data

/**
 * Represents the current state of an active transfer.
 */
data class TransferState(
    val requestId: String,
    val songId: String,
    val songTitle: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val status: String,
    val error: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f
}

/** A temporary handoff request waiting to become local watch playback. */
internal data class PendingTemporaryPlaybackRequest(
    val songId: String,
    val startPositionMs: Long,
    val autoPlay: Boolean,
    val pausePhoneAfterStart: Boolean,
    val requestedAtElapsedMs: Long,
)
