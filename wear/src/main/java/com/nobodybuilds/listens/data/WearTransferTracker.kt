package com.nobodybuilds.listens.data

import android.os.SystemClock
import com.nobodybuilds.listens.shared.WearTransferMetadata
import com.nobodybuilds.listens.shared.WearTransferProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared bookkeeping for [WearTransferRepository] and [WearTransferReceiver]: the active transfers, the
 * metadata and artwork waiting for their audio, cancellations and the per-transfer idle watchdogs.
 */
internal class WearTransferTracker(
    private val scope: CoroutineScope,
) {
    /** Observable map of active transfers: requestId -> TransferState */
    val activeTransfers = MutableStateFlow<Map<String, TransferState>>(emptyMap())

    /** Pending metadata awaiting channel stream: requestId -> metadata */
    val pendingMetadata = ConcurrentHashMap<String, WearTransferMetadata>()

    /** Mapping from songId -> requestId for tracking which song is being transferred */
    val songToRequestId = ConcurrentHashMap<String, String>()

    /** Artwork bytes received before/while audio transfer: requestId -> bytes */
    val pendingArtworkByRequestId = ConcurrentHashMap<String, ByteArray>()
    /** Temporary handoff requests waiting to become local watch playback. */
    val pendingTemporaryPlaybackRequests = ConcurrentHashMap<String, PendingTemporaryPlaybackRequest>()

    /** Failsafe timeout per transfer to avoid hanging states at 0%. */
    val transferWatchdogs = ConcurrentHashMap<String, Job>()
    /** Request IDs currently receiving bytes through ChannelClient. */
    val activeChannelRequestIds = ConcurrentHashMap.newKeySet<String>()
    /** Cancelled request IDs retained briefly so late metadata/progress/channel events are ignored safely. */
    val cancelledRequestIds = ConcurrentHashMap.newKeySet<String>()

    companion object {
        private const val TAG = "WearTransferRepo"
        private const val TRANSFER_IDLE_TIMEOUT_MS = 120_000L
        private const val METADATA_WAIT_TIMEOUT_MS = 8_000L
        private const val METADATA_POLL_INTERVAL_MS = 120L
        private const val CANCELLED_REQUEST_RETENTION_MS = 300_000L
    }

    suspend fun awaitMetadata(requestId: String): WearTransferMetadata? {
        pendingMetadata.remove(requestId)?.let { return it }
        val startedAt = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - startedAt < METADATA_WAIT_TIMEOUT_MS) {
            delay(METADATA_POLL_INTERVAL_MS)
            pendingMetadata.remove(requestId)?.let { return it }
        }
        return null
    }

    fun clearStaleTransfersForSong(songId: String) {
        val staleRequestIds = activeTransfers.value.values
            .filter { it.songId == songId }
            .map { it.requestId }
        if (staleRequestIds.isEmpty()) return

        activeTransfers.update { map ->
            map.filterValues { it.songId != songId }
        }
        staleRequestIds.forEach { staleRequestId ->
            pendingTemporaryPlaybackRequests.remove(staleRequestId)
            pendingMetadata.remove(staleRequestId)
            pendingArtworkByRequestId.remove(staleRequestId)
            clearTransferWatchdog(staleRequestId)
            activeChannelRequestIds.remove(staleRequestId)
        }
    }

    fun isTransferCancelled(requestId: String): Boolean {
        return cancelledRequestIds.contains(requestId)
    }

    fun rememberCancelledRequest(requestId: String) {
        cancelledRequestIds.add(requestId)
        scope.launch {
            delay(CANCELLED_REQUEST_RETENTION_MS)
            cancelledRequestIds.remove(requestId)
        }
    }

    fun cleanupCancelledTransfer(requestId: String, songId: String?) {
        activeTransfers.update { it - requestId }
        songId?.takeIf { it.isNotBlank() }?.let { safeSongId ->
            songToRequestId.remove(safeSongId)
        }
        pendingTemporaryPlaybackRequests.remove(requestId)
        pendingMetadata.remove(requestId)
        pendingArtworkByRequestId.remove(requestId)
        clearTransferWatchdog(requestId)
        activeChannelRequestIds.remove(requestId)
    }

    fun handleTransferError(requestId: String, songId: String, message: String) {
        Timber.tag(TAG).e("Transfer error: $message (requestId=$requestId, songId=$songId)")
        activeTransfers.update { map ->
            val current = map[requestId]
            if (current != null) {
                map + (requestId to current.copy(
                    status = WearTransferProgress.STATUS_FAILED,
                    error = message,
                ))
            } else {
                map
            }
        }
        songToRequestId.remove(songId)
        pendingTemporaryPlaybackRequests.remove(requestId)
        pendingMetadata.remove(requestId)
        pendingArtworkByRequestId.remove(requestId)
        clearTransferWatchdog(requestId)
        activeChannelRequestIds.remove(requestId)
    }

    fun armTransferWatchdog(requestId: String, songId: String) {
        clearTransferWatchdog(requestId)
        transferWatchdogs[requestId] = scope.launch {
            delay(TRANSFER_IDLE_TIMEOUT_MS)
            if (activeTransfers.value.containsKey(requestId)) {
                handleTransferError(requestId, songId, "Transfer timed out")
            }
        }
    }

    fun clearTransferWatchdog(requestId: String) {
        transferWatchdogs.remove(requestId)?.cancel()
    }
}
