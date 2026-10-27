package com.nobodybuilds.listens.data

import android.app.Application
import android.os.SystemClock
import android.webkit.MimeTypeMap
import com.google.android.gms.wearable.ChannelClient
import com.nobodybuilds.listens.data.local.LocalSongDao
import com.nobodybuilds.listens.data.local.LocalSongEntity
import com.nobodybuilds.listens.shared.WearTransferMetadata
import com.nobodybuilds.listens.shared.WearTransferProgress
import com.nobodybuilds.listens.shared.WearTransferRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.InputStream

/**
 * Receiving side of [WearTransferRepository]: reads the audio and artwork channels the phone opens, writes
 * the song to disk and either saves it to the watch library or hands it to local playback.
 */
internal class WearTransferReceiver(
    private val application: Application,
    private val localSongDao: LocalSongDao,
    private val channelClient: ChannelClient,
    private val localPlayerRepository: WearLocalPlayerRepository,
    private val stateRepository: WearStateRepository,
    private val playbackController: WearPlaybackController,
    private val tracker: WearTransferTracker,
    private val scope: CoroutineScope,
    private val json: Json,
) {
    companion object {
        private const val TAG = "WearTransferRepo"
        private const val ARTWORK_FILE_EXTENSION = "jpg"
        private const val WATCHDOG_TOUCH_INTERVAL_MS = 1_500L
        private const val LOCAL_PROGRESS_UPDATE_INTERVAL_BYTES = 65_536L
    }

    fun receiveAudioChannel(channel: ChannelClient.Channel) {
        scope.launch {
            runCatching {
                channelClient.getInputStream(channel).await().use { inputStream ->
                    val requestId = readLengthPrefixedString(inputStream, "requestId")
                    Timber.tag(TAG).d("Audio transfer channel: requestId=%s", requestId)
                    onAudioChannelOpened(requestId, inputStream)
                }
            }.onFailure { error ->
                Timber.tag(TAG).e(error, "Failed to receive audio transfer channel")
            }
            runCatching { channelClient.close(channel).await() }
                .onFailure { error -> Timber.tag(TAG).w(error, "Failed to close audio transfer channel") }
        }
    }

    fun receiveArtworkChannel(channel: ChannelClient.Channel) {
        scope.launch {
            runCatching {
                channelClient.getInputStream(channel).await().use { stream ->
                    val requestId = readLengthPrefixedString(stream, "requestId")
                    val songId = readLengthPrefixedString(stream, "songId")
                    val artworkBytes = stream.readBytesSafely()
                    if (artworkBytes.isNotEmpty()) {
                        onArtworkReceived(
                            requestId = requestId,
                            songId = songId,
                            artworkBytes = artworkBytes,
                        )
                        Timber.tag(TAG).d(
                            "Artwork received for requestId=%s, bytes=%d",
                            requestId,
                            artworkBytes.size,
                        )
                    } else {
                        Timber.tag(TAG).d("Artwork stream empty for requestId=%s", requestId)
                    }
                }
            }.onFailure { error ->
                Timber.tag(TAG).e(error, "Failed to receive artwork transfer channel")
            }
            runCatching { channelClient.close(channel).await() }
                .onFailure { error -> Timber.tag(TAG).w(error, "Failed to close artwork transfer channel") }
        }
    }

    /**
     * Called when a ChannelClient channel is opened by the phone.
     * Reads the audio stream, writes it to local storage, and inserts into Room.
     */
    private suspend fun onAudioChannelOpened(requestId: String, inputStream: InputStream) {
        tracker.activeChannelRequestIds.add(requestId)
        if (tracker.isTransferCancelled(requestId)) {
            inputStream.close()
            tracker.cleanupCancelledTransfer(
                requestId = requestId,
                songId = tracker.activeTransfers.value[requestId]?.songId,
            )
            tracker.activeChannelRequestIds.remove(requestId)
            return
        }

        val musicDir = File(application.filesDir, "music")
        if (!musicDir.exists()) musicDir.mkdirs()
        val tempFile = File(musicDir, "$requestId.part")
        var metadata: WearTransferMetadata? = tracker.pendingMetadata[requestId]

        try {
            if (tracker.isTransferCancelled(requestId)) {
                inputStream.close()
                tracker.cleanupCancelledTransfer(
                    requestId = requestId,
                    songId = metadata?.songId ?: tracker.activeTransfers.value[requestId]?.songId,
                )
                return
            }

            metadata?.let { availableMetadata ->
                tracker.activeTransfers.update { map ->
                    val current = map[requestId] ?: TransferState(
                        requestId = requestId,
                        songId = availableMetadata.songId,
                        songTitle = availableMetadata.title,
                        bytesTransferred = 0L,
                        totalBytes = availableMetadata.fileSize,
                        status = WearTransferProgress.STATUS_TRANSFERRING,
                    )
                    map + (requestId to current.copy(
                        songId = availableMetadata.songId,
                        songTitle = availableMetadata.title,
                        totalBytes = maxOf(current.totalBytes, availableMetadata.fileSize),
                        status = WearTransferProgress.STATUS_TRANSFERRING,
                    ))
                }
            }

            var totalReceived = 0L
            var lastProgressUpdateAtBytes = 0L
            var lastWatchdogTouchAt = SystemClock.elapsedRealtime()
            var cancelledDuringStream = false
            tracker.armTransferWatchdog(
                requestId = requestId,
                songId = metadata?.songId ?: tracker.activeTransfers.value[requestId]?.songId.orEmpty(),
            )

            tempFile.outputStream().use { fileOut ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (tracker.isTransferCancelled(requestId)) {
                        cancelledDuringStream = true
                        break
                    }
                    fileOut.write(buffer, 0, bytesRead)
                    totalReceived += bytesRead

                    if (metadata == null) {
                        tracker.pendingMetadata[requestId]?.let { availableMetadata ->
                            metadata = availableMetadata
                            tracker.activeTransfers.update { map ->
                                val current = map[requestId] ?: return@update map
                                map + (requestId to current.copy(
                                    songId = availableMetadata.songId,
                                    songTitle = availableMetadata.title,
                                    totalBytes = maxOf(current.totalBytes, availableMetadata.fileSize),
                                    status = WearTransferProgress.STATUS_TRANSFERRING,
                                ))
                            }
                        }
                    }

                    if (totalReceived - lastProgressUpdateAtBytes >= LOCAL_PROGRESS_UPDATE_INTERVAL_BYTES) {
                        tracker.activeTransfers.update { map ->
                            val current = map[requestId] ?: return@update map
                            map + (requestId to current.copy(
                                bytesTransferred = maxOf(current.bytesTransferred, totalReceived),
                                totalBytes = maxOf(current.totalBytes, metadata?.fileSize ?: current.totalBytes),
                                status = WearTransferProgress.STATUS_TRANSFERRING,
                            ))
                        }
                        lastProgressUpdateAtBytes = totalReceived
                    }

                    val now = SystemClock.elapsedRealtime()
                    if (now - lastWatchdogTouchAt >= WATCHDOG_TOUCH_INTERVAL_MS) {
                        tracker.armTransferWatchdog(
                            requestId = requestId,
                            songId = metadata?.songId ?: tracker.activeTransfers.value[requestId]?.songId.orEmpty(),
                        )
                        lastWatchdogTouchAt = now
                    }
                }
            }
            inputStream.close()

            if (cancelledDuringStream || tracker.isTransferCancelled(requestId)) {
                if (tempFile.exists() && !tempFile.delete()) {
                    Timber.tag(TAG).w(
                        "Failed to delete partial cancelled transfer for requestId=%s",
                        requestId,
                    )
                }
                tracker.cleanupCancelledTransfer(
                    requestId = requestId,
                    songId = metadata?.songId ?: tracker.activeTransfers.value[requestId]?.songId,
                )
                return
            }

            val actualSize = tempFile.length()
            if (actualSize == 0L) {
                tempFile.delete()
                tracker.handleTransferError(
                    requestId = requestId,
                    songId = metadata?.songId ?: tracker.activeTransfers.value[requestId]?.songId.orEmpty(),
                    message = "Empty file received",
                )
                return
            }

            val resolvedMetadata = metadata?.also { tracker.pendingMetadata.remove(requestId) } ?: tracker.awaitMetadata(requestId)
            if (resolvedMetadata == null) {
                tempFile.delete()
                val songId = tracker.activeTransfers.value[requestId]?.songId
                if (tracker.isTransferCancelled(requestId)) {
                    tracker.cleanupCancelledTransfer(requestId, songId)
                } else if (!songId.isNullOrBlank()) {
                    tracker.handleTransferError(requestId, songId, "Transfer metadata missing")
                } else {
                    Timber.tag(TAG).w("No pending metadata for requestId=%s after draining audio", requestId)
                    tracker.activeTransfers.update { it - requestId }
                    tracker.clearTransferWatchdog(requestId)
                }
                return
            }

            val extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(resolvedMetadata.mimeType) ?: "mp3"
            if (resolvedMetadata.transferMode == WearTransferRequest.MODE_TEMPORARY_PLAYBACK) {
                val playbackDir = File(application.cacheDir, "temporary_playback")
                if (!playbackDir.exists()) playbackDir.mkdirs()
                val playbackFile = File(playbackDir, "$requestId.$extension")
                if (playbackFile.exists() && !playbackFile.delete()) {
                    tempFile.delete()
                    tracker.handleTransferError(requestId, resolvedMetadata.songId, "Couldn't replace temporary playback file")
                    return
                }

                if (playbackFile.absolutePath != tempFile.absolutePath) {
                    val renamed = tempFile.renameTo(playbackFile)
                    if (!renamed) {
                        runCatching {
                            tempFile.inputStream().use { input ->
                                playbackFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }.onFailure { error ->
                            tempFile.delete()
                            playbackFile.delete()
                            throw error
                        }
                        if (!tempFile.delete()) {
                            Timber.tag(TAG).w("Failed to delete temp playback staging file for requestId=%s", requestId)
                        }
                    }
                }

                val artworkPath = consumeAndPersistPendingArtwork(
                    requestId = requestId,
                    artworkKey = "temp_$requestId",
                )
                val pendingPlayback = tracker.pendingTemporaryPlaybackRequests.remove(requestId)
                val resolvedStartPositionMs = resolveTemporaryPlaybackStartPosition(
                    requestId = requestId,
                    metadata = resolvedMetadata,
                    pendingPlayback = pendingPlayback,
                )
                localPlayerRepository.playTemporarySong(
                    song = LocalSongEntity(
                        songId = resolvedMetadata.songId,
                        title = resolvedMetadata.title,
                        artist = resolvedMetadata.artist,
                        album = resolvedMetadata.album,
                        albumId = resolvedMetadata.albumId,
                        duration = resolvedMetadata.duration,
                        mimeType = resolvedMetadata.mimeType,
                        fileSize = actualSize,
                        bitrate = resolvedMetadata.bitrate,
                        sampleRate = resolvedMetadata.sampleRate,
                        isFavorite = resolvedMetadata.isFavorite,
                        favoriteSyncPending = false,
                        paletteSeedArgb = resolvedMetadata.paletteSeedArgb,
                        themePaletteJson = resolvedMetadata.themePalette?.let { json.encodeToString(it) },
                        artworkPath = artworkPath,
                        localPath = playbackFile.absolutePath,
                        transferredAt = System.currentTimeMillis(),
                    ),
                    startPositionMs = resolvedStartPositionMs,
                    autoPlay = pendingPlayback?.autoPlay ?: resolvedMetadata.autoPlay,
                )
                stateRepository.setOutputTarget(WearOutputTarget.WATCH)
                if (pendingPlayback?.pausePhoneAfterStart == true) {
                    playbackController.pause()
                }
                tracker.activeTransfers.update { it - requestId }
                tracker.songToRequestId.remove(resolvedMetadata.songId)
                tracker.clearTransferWatchdog(requestId)
                Timber.tag(TAG).d(
                    "Temporary playback ready: %s (%d bytes) → %s",
                    resolvedMetadata.title,
                    actualSize,
                    playbackFile.absolutePath,
                )
                return
            }

            val localFile = File(musicDir, "${resolvedMetadata.songId}.$extension")
            val previousSong = localSongDao.getSongById(resolvedMetadata.songId)

            if (localFile.exists() && localFile.absolutePath != tempFile.absolutePath && !localFile.delete()) {
                tempFile.delete()
                tracker.handleTransferError(requestId, resolvedMetadata.songId, "Couldn't replace existing local file")
                return
            }

            if (localFile.absolutePath != tempFile.absolutePath) {
                val renamed = tempFile.renameTo(localFile)
                if (!renamed) {
                    runCatching {
                        tempFile.inputStream().use { input ->
                            localFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }.onFailure { error ->
                        tempFile.delete()
                        localFile.delete()
                        throw error
                    }
                    if (!tempFile.delete()) {
                        Timber.tag(TAG).w("Failed to delete temp transfer file for requestId=%s", requestId)
                    }
                }
            }

            val artworkPath = consumeAndPersistPendingArtwork(
                requestId = requestId,
                artworkKey = resolvedMetadata.songId,
            )

            localSongDao.insert(
                LocalSongEntity(
                    songId = resolvedMetadata.songId,
                    title = resolvedMetadata.title,
                    artist = resolvedMetadata.artist,
                    album = resolvedMetadata.album,
                    albumId = resolvedMetadata.albumId,
                    duration = resolvedMetadata.duration,
                    mimeType = resolvedMetadata.mimeType,
                    fileSize = actualSize,
                    bitrate = resolvedMetadata.bitrate,
                    sampleRate = resolvedMetadata.sampleRate,
                    isFavorite = resolvedMetadata.isFavorite,
                    favoriteSyncPending = false,
                    paletteSeedArgb = resolvedMetadata.paletteSeedArgb,
                    themePaletteJson = resolvedMetadata.themePalette?.let { json.encodeToString(it) },
                    artworkPath = artworkPath,
                    localPath = localFile.absolutePath,
                    transferredAt = System.currentTimeMillis(),
                )
            )

            cleanupReplacedSongFiles(
                previousSong = previousSong,
                currentAudioPath = localFile.absolutePath,
                currentArtworkPath = artworkPath,
            )

            tracker.activeTransfers.update { it - requestId }
            tracker.songToRequestId.remove(resolvedMetadata.songId)
            tracker.clearTransferWatchdog(requestId)

            Timber.tag(TAG).d(
                "Transfer complete: ${resolvedMetadata.title} ($actualSize bytes) → ${localFile.absolutePath}"
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to write transferred file")
            tempFile.delete()
            tracker.handleTransferError(
                requestId = requestId,
                songId = metadata?.songId ?: tracker.activeTransfers.value[requestId]?.songId.orEmpty(),
                message = e.message ?: "Write failed",
            )
        } finally {
            tracker.activeChannelRequestIds.remove(requestId)
        }
    }

    /**
     * Called when artwork bytes arrive over the dedicated artwork channel.
     * If song row exists, artwork is persisted immediately; otherwise cached until audio finishes.
     */
    suspend fun onArtworkReceived(requestId: String, songId: String, artworkBytes: ByteArray) {
        if (artworkBytes.isEmpty()) return
        tracker.armTransferWatchdog(requestId, songId)

        val existing = localSongDao.getSongById(songId)
        if (existing != null) {
            val artworkPath = persistArtwork(songId, artworkBytes)
            if (artworkPath != null) {
                if (existing.artworkPath != null && existing.artworkPath != artworkPath) {
                    runCatching { File(existing.artworkPath).delete() }
                }
                localSongDao.updateArtworkPath(songId, artworkPath)
                Timber.tag(TAG).d("Artwork updated for existing local song: songId=$songId")
            }
            return
        }

        tracker.pendingArtworkByRequestId[requestId] = artworkBytes
        Timber.tag(TAG).d("Artwork cached for pending transfer: requestId=$requestId")
    }

    private fun resolveTemporaryPlaybackStartPosition(
        requestId: String,
        metadata: WearTransferMetadata,
        pendingPlayback: PendingTemporaryPlaybackRequest?,
    ): Long {
        val initialPositionMs = pendingPlayback?.startPositionMs ?: metadata.startPositionMs
        if (!(pendingPlayback?.autoPlay ?: metadata.autoPlay)) {
            return initialPositionMs.coerceAtLeast(0L)
        }

        val requestedAtElapsedMs = pendingPlayback?.requestedAtElapsedMs
            ?: return initialPositionMs.coerceAtLeast(0L)
        val elapsedMs = (SystemClock.elapsedRealtime() - requestedAtElapsedMs).coerceAtLeast(0L)
        val adjustedPositionMs = initialPositionMs.coerceAtLeast(0L) + elapsedMs
        val clampedPositionMs = if (metadata.duration > 0L) {
            adjustedPositionMs.coerceAtMost(metadata.duration)
        } else {
            adjustedPositionMs
        }
        Timber.tag(TAG).d(
            "Resolved temporary playback start for requestId=%s: base=%d elapsed=%d final=%d",
            requestId,
            initialPositionMs,
            elapsedMs,
            clampedPositionMs,
        )
        return clampedPositionMs
    }

    private fun consumeAndPersistPendingArtwork(requestId: String, artworkKey: String): String? {
        val artworkBytes = tracker.pendingArtworkByRequestId.remove(requestId) ?: return null
        return persistArtwork(artworkKey, artworkBytes)
    }

    private fun persistArtwork(songId: String, artworkBytes: ByteArray): String? {
        return try {
            val artworkDir = File(application.filesDir, "artwork")
            if (!artworkDir.exists()) artworkDir.mkdirs()
            val artworkFile = File(artworkDir, "$songId.$ARTWORK_FILE_EXTENSION")
            artworkFile.outputStream().use { output ->
                output.write(artworkBytes)
                output.flush()
            }
            if (artworkFile.length() <= 0L) {
                artworkFile.delete()
                null
            } else {
                artworkFile.absolutePath
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to persist artwork for songId=$songId")
            null
        }
    }
}
