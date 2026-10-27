package com.nobodybuilds.listens.data

import android.app.Application
import android.os.SystemClock
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.nobodybuilds.listens.data.local.LocalSongDao
import com.nobodybuilds.listens.data.local.LocalSongEntity
import com.nobodybuilds.listens.shared.WearDataPaths
import com.nobodybuilds.listens.shared.WearLibraryState
import com.nobodybuilds.listens.shared.WearTransferMetadata
import com.nobodybuilds.listens.shared.WearTransferProgress
import com.nobodybuilds.listens.shared.WearTransferRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository managing song transfers from phone to watch.
 *
 * Transfer flow:
 * 1. [requestTransfer] sends a WearTransferRequest to the phone
 * 2. Phone validates and sends WearTransferMetadata back ([onMetadataReceived])
 * 3. Phone opens a ChannelClient stream and sends audio data
 * 4. Watch receives via [onChannelOpened], writes to disk, inserts into Room
 * 5. Progress updates arrive via [onProgressReceived] during streaming
 */
@Singleton
class WearTransferRepository @Inject constructor(
    private val application: Application,
    private val localSongDao: LocalSongDao,
    private val channelClient: ChannelClient,
    private val messageClient: MessageClient,
    private val nodeClient: NodeClient,
    private val localPlayerRepository: WearLocalPlayerRepository,
    private val stateRepository: WearStateRepository,
    private val playbackController: WearPlaybackController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val tracker = WearTransferTracker(scope)
    private val receiver = WearTransferReceiver(
        application = application,
        localSongDao = localSongDao,
        channelClient = channelClient,
        localPlayerRepository = localPlayerRepository,
        stateRepository = stateRepository,
        playbackController = playbackController,
        tracker = tracker,
        scope = scope,
        json = json,
    )

    /** Observable map of active transfers: requestId -> TransferState */
    val activeTransfers: StateFlow<Map<String, TransferState>> = tracker.activeTransfers.asStateFlow()

    /**
     * All locally stored songs that still have a valid file on disk.
     * Any stale DB rows (missing/empty files) are cleaned up automatically.
     */
    val localSongs: Flow<List<LocalSongEntity>> = localSongDao.getAllSongs()
        .transform { songs ->
            val (validSongs, staleSongs) = songs.partition { it.hasPlayableLocalFile() }
            if (staleSongs.isNotEmpty()) {
                staleSongs.forEach { stale ->
                    stale.artworkPath?.let { artworkPath ->
                        runCatching { File(artworkPath).delete() }
                    }
                    localSongDao.deleteById(stale.songId)
                }
                Timber.tag(TAG).w("Removed ${staleSongs.size} stale local song entries from DB")
            }
            emit(validSongs)
        }

    /** Set of song IDs that are already downloaded and still valid on disk */
    val downloadedSongIds: Flow<Set<String>> = localSongs
        .map { songs -> songs.map { it.songId }.toSet() }

    companion object {
        private const val TAG = "WearTransferRepo"
    }

    init {
        scope.launch {
            downloadedSongIds
                .distinctUntilChanged()
                .collect { songIds ->
                    publishLibraryState(songIds = songIds)
                }
        }
    }

    /**
     * Request transfer of a song from the phone.
     * Sends a WearTransferRequest via MessageClient.
     */
    fun requestTransfer(
        songId: String,
        requestId: String = UUID.randomUUID().toString(),
        targetNodeId: String? = null,
        transferMode: String = WearTransferRequest.MODE_SAVE_TO_LIBRARY,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = false,
    ) {
        // Don't request if already transferring this song
        if (tracker.songToRequestId.containsKey(songId)) {
            Timber.tag(TAG).d("Transfer already in progress for songId=$songId")
            return
        }

        scope.launch {
            val existingSong = localSongDao.getSongById(songId)
            if (existingSong?.hasPlayableLocalFile() == true) {
                notifyPhoneTransferFailure(
                    targetNodeId = targetNodeId,
                    requestId = requestId,
                    songId = songId,
                    message = WearTransferProgress.ERROR_ALREADY_ON_WATCH,
                )
                return@launch
            }

            tracker.clearStaleTransfersForSong(songId)
            tracker.songToRequestId[songId] = requestId
            if (
                transferMode == WearTransferRequest.MODE_TEMPORARY_PLAYBACK &&
                !tracker.pendingTemporaryPlaybackRequests.containsKey(requestId)
            ) {
                tracker.pendingTemporaryPlaybackRequests[requestId] = PendingTemporaryPlaybackRequest(
                    songId = songId,
                    startPositionMs = startPositionMs,
                    autoPlay = autoPlay,
                    pausePhoneAfterStart = false,
                    requestedAtElapsedMs = SystemClock.elapsedRealtime(),
                )
            }

            tracker.activeTransfers.update { map ->
                map + (requestId to TransferState(
                    requestId = requestId,
                    songId = songId,
                    songTitle = "",
                    bytesTransferred = 0,
                    totalBytes = 0,
                    status = WearTransferProgress.STATUS_TRANSFERRING,
                ))
            }
            tracker.armTransferWatchdog(requestId, songId)

            try {
                val request = WearTransferRequest(
                    requestId = requestId,
                    songId = songId,
                    transferMode = transferMode,
                    startPositionMs = startPositionMs,
                    autoPlay = autoPlay,
                )
                val requestBytes = json.encodeToString(request).toByteArray(Charsets.UTF_8)

                if (targetNodeId != null) {
                    messageClient.sendMessage(
                        targetNodeId,
                        WearDataPaths.TRANSFER_REQUEST,
                        requestBytes,
                    ).await()
                } else {
                    val nodes = nodeClient.connectedNodes.await()
                    if (nodes.isEmpty()) {
                        tracker.handleTransferError(requestId, songId, "Phone not connected")
                        return@launch
                    }

                    nodes.forEach { node ->
                        messageClient.sendMessage(
                            node.id,
                            WearDataPaths.TRANSFER_REQUEST,
                            requestBytes,
                        ).await()
                    }
                }
                Timber.tag(TAG).d("Transfer requested: songId=$songId, requestId=$requestId")
            } catch (e: Exception) {
                tracker.handleTransferError(requestId, songId, e.message ?: "Failed to send request")
            }
        }
    }

    fun requestTemporaryPlayback(
        songId: String,
        startPositionMs: Long,
        autoPlay: Boolean,
        pausePhoneAfterStart: Boolean,
    ) {
        if (tracker.songToRequestId.containsKey(songId)) {
            Timber.tag(TAG).d("Temporary watch playback already requested for songId=%s", songId)
            return
        }
        val requestId = UUID.randomUUID().toString()
        tracker.pendingTemporaryPlaybackRequests[requestId] = PendingTemporaryPlaybackRequest(
            songId = songId,
            startPositionMs = startPositionMs,
            autoPlay = autoPlay,
            pausePhoneAfterStart = pausePhoneAfterStart,
            requestedAtElapsedMs = SystemClock.elapsedRealtime(),
        )
        requestTransfer(
            songId = songId,
            requestId = requestId,
            transferMode = WearTransferRequest.MODE_TEMPORARY_PLAYBACK,
            startPositionMs = startPositionMs,
            autoPlay = autoPlay,
        )
    }

    private suspend fun notifyPhoneTransferFailure(
        targetNodeId: String?,
        requestId: String,
        songId: String,
        message: String,
    ) {
        Timber.tag(TAG).d(
            "Rejecting transfer requestId=%s songId=%s: %s",
            requestId,
            songId,
            message,
        )
        if (targetNodeId == null) return

        runCatching {
            val progress = WearTransferProgress(
                requestId = requestId,
                songId = songId,
                bytesTransferred = 0L,
                totalBytes = 0L,
                status = WearTransferProgress.STATUS_FAILED,
                error = message,
            )
            messageClient.sendMessage(
                targetNodeId,
                WearDataPaths.TRANSFER_PROGRESS,
                json.encodeToString(progress).toByteArray(Charsets.UTF_8),
            ).await()
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to report transfer failure to phone")
        }
    }

    /**
     * Called when metadata arrives from the phone (before the audio channel opens).
     */
    suspend fun onMetadataReceived(
        metadata: WearTransferMetadata,
        sourceNodeId: String? = null,
    ) {
        if (tracker.isTransferCancelled(metadata.requestId)) {
            tracker.cleanupCancelledTransfer(metadata.requestId, metadata.songId)
            return
        }
        val errorMsg = metadata.error
        if (errorMsg != null) {
            Timber.tag(TAG).w("Transfer rejected by phone: $errorMsg")
            tracker.handleTransferError(metadata.requestId, metadata.songId, errorMsg)
            return
        }
        val existingSong = localSongDao.getSongById(metadata.songId)
        if (
            metadata.transferMode == WearTransferRequest.MODE_SAVE_TO_LIBRARY &&
            existingSong?.hasPlayableLocalFile() == true
        ) {
            notifyPhoneTransferFailure(
                targetNodeId = sourceNodeId,
                requestId = metadata.requestId,
                songId = metadata.songId,
                message = WearTransferProgress.ERROR_ALREADY_ON_WATCH,
            )
            tracker.handleTransferError(
                requestId = metadata.requestId,
                songId = metadata.songId,
                message = WearTransferProgress.ERROR_ALREADY_ON_WATCH,
            )
            return
        }

        tracker.pendingMetadata[metadata.requestId] = metadata
        tracker.armTransferWatchdog(metadata.requestId, metadata.songId)
        tracker.activeTransfers.update { map ->
            val current = map[metadata.requestId] ?: TransferState(
                requestId = metadata.requestId,
                songId = metadata.songId,
                songTitle = metadata.title,
                bytesTransferred = 0L,
                totalBytes = metadata.fileSize,
                status = WearTransferProgress.STATUS_TRANSFERRING,
            )
            map + (metadata.requestId to current.copy(
                songTitle = metadata.title,
                totalBytes = metadata.fileSize,
                status = WearTransferProgress.STATUS_TRANSFERRING,
            ))
        }
        Timber.tag(TAG).d(
            "Metadata received: ${metadata.title} (${metadata.fileSize} bytes)"
        )
    }

    /**
     * Called when progress updates arrive from the phone during streaming.
     */
    fun onProgressReceived(progress: WearTransferProgress) {
        if (progress.status == WearTransferProgress.STATUS_CANCELLED) {
            tracker.rememberCancelledRequest(progress.requestId)
            tracker.cleanupCancelledTransfer(progress.requestId, progress.songId)
            return
        }
        if (tracker.isTransferCancelled(progress.requestId)) {
            return
        }
        val normalizedStatus = if (
            progress.status == WearTransferProgress.STATUS_COMPLETED &&
            tracker.activeChannelRequestIds.contains(progress.requestId)
        ) {
            WearTransferProgress.STATUS_TRANSFERRING
        } else {
            progress.status
        }

        tracker.activeTransfers.update { map ->
            val current = map[progress.requestId] ?: TransferState(
                requestId = progress.requestId,
                songId = progress.songId,
                songTitle = tracker.pendingMetadata[progress.requestId]?.title.orEmpty(),
                bytesTransferred = 0L,
                totalBytes = 0L,
                status = normalizedStatus,
            )
            map + (progress.requestId to current.copy(
                bytesTransferred = maxOf(current.bytesTransferred, progress.bytesTransferred),
                totalBytes = maxOf(current.totalBytes, progress.totalBytes),
                status = normalizedStatus,
                error = progress.error,
            ))
        }

        if (normalizedStatus == WearTransferProgress.STATUS_FAILED) {
            tracker.handleTransferError(progress.requestId, progress.songId, progress.error ?: "Transfer failed")
        } else if (
            normalizedStatus == WearTransferProgress.STATUS_COMPLETED ||
            normalizedStatus == WearTransferProgress.STATUS_CANCELLED
        ) {
            tracker.clearTransferWatchdog(progress.requestId)
        } else {
            tracker.armTransferWatchdog(progress.requestId, progress.songId)
        }
    }

    fun receiveAudioChannel(channel: ChannelClient.Channel) {
        receiver.receiveAudioChannel(channel)
    }

    fun receiveArtworkChannel(channel: ChannelClient.Channel) {
        receiver.receiveArtworkChannel(channel)
    }

    /**
     * Delete a locally stored song (file + Room entry).
     */
    suspend fun deleteSong(songId: String): Result<Unit> {
        val song = localSongDao.getSongById(songId)
            ?: return Result.failure(IllegalArgumentException("Song not found on watch"))
        val file = File(song.localPath)
        if (file.exists() && !file.delete()) {
            return Result.failure(IllegalStateException("Couldn't remove the song file from watch"))
        }
        song.artworkPath?.let { artwork ->
            val artworkFile = File(artwork)
            if (artworkFile.exists() && !artworkFile.delete()) {
                Timber.tag(TAG).w("Artwork cleanup failed for songId=%s path=%s", songId, artwork)
            }
        }
        localSongDao.deleteById(songId)
        Timber.tag(TAG).d("Deleted local song: ${song.title}")
        return Result.success(Unit)
    }

    /**
     * Get total storage used by transferred songs.
     */
    suspend fun getStorageUsed(): Long {
        return localSongDao.getTotalStorageUsed() ?: 0L
    }

    /**
     * Cancel an in-progress transfer.
     */
    fun cancelTransfer(requestId: String, notifyPhone: Boolean = true) {
        scope.launch {
            val state = tracker.activeTransfers.value[requestId]
            val songId = state?.songId ?: tracker.pendingMetadata[requestId]?.songId
            tracker.rememberCancelledRequest(requestId)
            try {
                if (notifyPhone) {
                    val cancelRequest = WearTransferRequest(
                        requestId = requestId,
                        songId = songId.orEmpty(),
                    )
                    val cancelBytes = json.encodeToString(cancelRequest).toByteArray(Charsets.UTF_8)
                    val nodes = nodeClient.connectedNodes.await()
                    nodes.forEach { node ->
                        messageClient.sendMessage(
                            node.id,
                            WearDataPaths.TRANSFER_CANCEL,
                            cancelBytes,
                        ).await()
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to send cancel request")
            }
            tracker.cleanupCancelledTransfer(requestId, songId)
        }
    }

    suspend fun publishLibraryState(
        targetNodeId: String? = null,
        songIds: Set<String>? = null,
    ) {
        val snapshotSongIds = songIds ?: localSongDao.getAllSongIds().first().toSet()
        val payload = json.encodeToString(
            WearLibraryState(songIds = snapshotSongIds.sorted())
        ).toByteArray(Charsets.UTF_8)

        runCatching {
            if (targetNodeId != null) {
                messageClient.sendMessage(
                    targetNodeId,
                    WearDataPaths.WATCH_LIBRARY_STATE,
                    payload,
                ).await()
            } else {
                val nodes = nodeClient.connectedNodes.await()
                nodes.forEach { node ->
                    messageClient.sendMessage(
                        node.id,
                        WearDataPaths.WATCH_LIBRARY_STATE,
                        payload,
                    ).await()
                }
            }
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to publish watch library state")
        }
    }

    /**
     * Called when artwork bytes arrive over the dedicated artwork channel.
     * If song row exists, artwork is persisted immediately; otherwise cached until audio finishes.
     */
    suspend fun onArtworkReceived(requestId: String, songId: String, artworkBytes: ByteArray) {
        receiver.onArtworkReceived(requestId, songId, artworkBytes)
    }

    private fun LocalSongEntity.hasPlayableLocalFile(): Boolean {
        val file = File(localPath)
        return file.isFile && file.length() > 0L
    }
}
