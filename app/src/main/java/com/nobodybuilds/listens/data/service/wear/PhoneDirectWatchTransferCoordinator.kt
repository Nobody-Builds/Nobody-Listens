package com.nobodybuilds.listens.data.service.wear

import android.app.Application
import com.nobodybuilds.listens.data.gdrive.GDriveStreamProxy
import com.google.android.gms.wearable.Wearable
import com.nobodybuilds.listens.data.navidrome.NavidromeStreamProxy
import com.nobodybuilds.listens.data.netease.NeteaseStreamProxy
import com.nobodybuilds.listens.data.preferences.ThemePreferencesRepository
import com.nobodybuilds.listens.data.qqmusic.QqMusicStreamProxy
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.telegram.TelegramRepository
import com.nobodybuilds.listens.data.telegram.TelegramStreamProxy
import com.nobodybuilds.listens.presentation.viewmodel.ColorSchemeProcessor
import com.nobodybuilds.listens.shared.WearDataPaths
import com.nobodybuilds.listens.shared.WearTransferMetadata
import com.nobodybuilds.listens.shared.WearTransferProgress
import com.nobodybuilds.listens.shared.WearTransferRequest
import javax.inject.Singleton
import java.io.InputStream
import java.nio.ByteBuffer
import javax.inject.Inject
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import timber.log.Timber

@Singleton
class PhoneDirectWatchTransferCoordinator @Inject constructor(
    private val application: Application,
    private val musicRepository: MusicRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val colorSchemeProcessor: ColorSchemeProcessor,
    private val transferStateStore: PhoneWatchTransferStateStore,
    private val transferCancellationStore: PhoneWatchTransferCancellationStore,
    private val telegramRepository: TelegramRepository,
    private val telegramStreamProxy: Lazy<TelegramStreamProxy>,
    private val neteaseStreamProxy: NeteaseStreamProxy,
    private val qqMusicStreamProxy: QqMusicStreamProxy,
    private val navidromeStreamProxy: NavidromeStreamProxy,
    private val jellyfinStreamProxy: com.nobodybuilds.listens.data.jellyfin.JellyfinStreamProxy,
    private val gDriveStreamProxy: GDriveStreamProxy,
    private val okHttpClient: OkHttpClient,
) {
    private val messageClient by lazy { Wearable.getMessageClient(application) }
    private val channelClient by lazy { Wearable.getChannelClient(application) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val songSources = WatchTransferSongSources(
        application = application,
        telegramRepository = telegramRepository,
        telegramStreamProxy = telegramStreamProxy,
        neteaseStreamProxy = neteaseStreamProxy,
        qqMusicStreamProxy = qqMusicStreamProxy,
        navidromeStreamProxy = navidromeStreamProxy,
        jellyfinStreamProxy = jellyfinStreamProxy,
        gDriveStreamProxy = gDriveStreamProxy,
        okHttpClient = okHttpClient,
    )
    private val artwork = WatchTransferArtwork(
        application = application,
        themePreferencesRepository = themePreferencesRepository,
        colorSchemeProcessor = colorSchemeProcessor,
    )

    fun startTransferToWatch(
        nodeId: String,
        requestId: String,
        songId: String,
        transferMode: String = WearTransferRequest.MODE_SAVE_TO_LIBRARY,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = false,
    ) {
        transferStateStore.markRequested(
            requestId = requestId,
            songId = songId,
        )
        WatchTransferForegroundService.start(application)
        scope.launch {
            performTransfer(
                nodeId = nodeId,
                requestId = requestId,
                songId = songId,
                transferMode = transferMode,
                startPositionMs = startPositionMs,
                autoPlay = autoPlay,
            )
        }
    }

    private suspend fun performTransfer(
        nodeId: String,
        requestId: String,
        songId: String,
        transferMode: String,
        startPositionMs: Long,
        autoPlay: Boolean,
    ) {
        var openedSongSource: OpenedSongSource? = null
        try {
            val song = musicRepository.getSongsByIds(listOf(songId)).first().firstOrNull()
            if (song == null) {
                sendTransferMetadataError(
                    nodeId = nodeId,
                    requestId = requestId,
                    songId = songId,
                    errorMessage = "Song not found",
                )
                return
            }

            if (
                transferMode == WearTransferRequest.MODE_SAVE_TO_LIBRARY &&
                !songSources.isSongTransferEligible(song)
            ) {
                sendTransferMetadataError(
                    nodeId = nodeId,
                    requestId = requestId,
                    songId = song.id,
                    errorMessage = "Song must be downloaded locally on phone before saving to watch",
                )
                return
            }

            val songSource = songSources.openSongSource(
                song = song,
                allowProxyStreaming = transferMode == WearTransferRequest.MODE_TEMPORARY_PLAYBACK,
            )
            if (songSource == null) {
                sendTransferMetadataError(
                    nodeId = nodeId,
                    requestId = requestId,
                    songId = song.id,
                    errorMessage = if (transferMode == WearTransferRequest.MODE_TEMPORARY_PLAYBACK) {
                        "Cannot stream audio source to watch"
                    } else {
                        "Cannot read audio file"
                    },
                )
                return
            }
            openedSongSource = songSource

            val fileSize = songSource.fileSize
            val paletteSeedArgb = artwork.resolvePaletteSeedArgb(song)
            val transferThemePalette = artwork.resolveTransferThemePalette(song)
            val transferArtworkBytes = artwork.resolveTransferArtworkBytes(song)

            val metadata = WearTransferMetadata(
                requestId = requestId,
                songId = song.id,
                title = song.title,
                artist = song.displayArtist,
                album = song.album,
                albumId = song.albumId,
                duration = song.duration,
                mimeType = song.mimeType ?: "audio/mpeg",
                fileSize = fileSize,
                bitrate = song.bitrate ?: 0,
                sampleRate = song.sampleRate ?: 0,
                isFavorite = song.isFavorite,
                paletteSeedArgb = paletteSeedArgb,
                themePalette = transferThemePalette,
                transferMode = transferMode,
                startPositionMs = startPositionMs,
                autoPlay = autoPlay,
            )
            transferStateStore.markMetadata(
                requestId = requestId,
                songId = song.id,
                songTitle = song.title,
                totalBytes = fileSize,
            )
            if (transferCancellationStore.consumeCancellation(requestId)) {
                sendTransferProgress(
                    nodeId = nodeId,
                    requestId = requestId,
                    songId = song.id,
                    bytesTransferred = 0L,
                    totalBytes = fileSize,
                    status = WearTransferProgress.STATUS_CANCELLED,
                )
                return
            }

            messageClient.sendMessage(
                nodeId,
                WearDataPaths.TRANSFER_METADATA,
                json.encodeToString(metadata).toByteArray(Charsets.UTF_8),
            ).await()

            // Give the watch a brief window to reject duplicates before the audio stream starts.
            delay(METADATA_GUARD_DELAY_MS)
            val duplicateRejected = transferStateStore.transfers.value[requestId]?.let { transfer ->
                transfer.status == WearTransferProgress.STATUS_FAILED &&
                    transfer.error == WearTransferProgress.ERROR_ALREADY_ON_WATCH
            } == true
            if (duplicateRejected) {
                runCatching { openedSongSource?.close() }
                openedSongSource = null
                return
            }

            if (transferArtworkBytes != null) {
                runCatching {
                    streamArtworkToWatch(
                        nodeId = nodeId,
                        requestId = requestId,
                        songId = song.id,
                        artworkBytes = transferArtworkBytes,
                    )
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Artwork transfer failed for songId=%s", song.id)
                }
            }

            if (transferCancellationStore.consumeCancellation(requestId)) {
                sendTransferProgress(
                    nodeId = nodeId,
                    requestId = requestId,
                    songId = song.id,
                    bytesTransferred = 0L,
                    totalBytes = fileSize,
                    status = WearTransferProgress.STATUS_CANCELLED,
                )
                runCatching { openedSongSource?.close() }
                openedSongSource = null
                return
            }

            streamFileToWatch(
                nodeId = nodeId,
                requestId = requestId,
                songId = song.id,
                inputStream = songSource.inputStream,
                fileSize = fileSize,
            )
            runCatching { songSource.close() }
            openedSongSource = null
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Direct transfer failed for songId=%s", songId)
            sendTransferProgress(
                nodeId = nodeId,
                requestId = requestId,
                songId = songId,
                bytesTransferred = 0L,
                totalBytes = 0L,
                status = WearTransferProgress.STATUS_FAILED,
                error = error.message,
            )
            runCatching { openedSongSource?.close() }
        }
    }

    private suspend fun streamFileToWatch(
        nodeId: String,
        requestId: String,
        songId: String,
        inputStream: InputStream,
        fileSize: Long,
    ) {
        if (transferCancellationStore.consumeCancellation(requestId)) {
            sendTransferProgress(
                nodeId = nodeId,
                requestId = requestId,
                songId = songId,
                bytesTransferred = 0L,
                totalBytes = fileSize,
                status = WearTransferProgress.STATUS_CANCELLED,
            )
            return
        }
        val channel = channelClient.openChannel(nodeId, WearDataPaths.TRANSFER_CHANNEL).await()
        var shouldForceCloseChannel = true

        try {
            var totalSent = 0L
            var lastProgressUpdate = 0L
            var cancelled = false

            withContext(Dispatchers.IO) {
                channelClient.getOutputStream(channel).await().use { outputStream ->
                    val requestIdBytes = requestId.toByteArray(Charsets.UTF_8)
                    outputStream.write(ByteBuffer.allocate(4).putInt(requestIdBytes.size).array())
                    outputStream.write(requestIdBytes)

                    val buffer = ByteArray(TRANSFER_CHUNK_SIZE)
                    inputStream.use { input ->
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (transferCancellationStore.consumeCancellation(requestId)) {
                                cancelled = true
                                break
                            }

                            outputStream.write(buffer, 0, bytesRead)
                            totalSent += bytesRead

                            if (totalSent - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_BYTES) {
                                sendTransferProgress(
                                    nodeId = nodeId,
                                    requestId = requestId,
                                    songId = songId,
                                    bytesTransferred = totalSent,
                                    totalBytes = fileSize,
                                    status = WearTransferProgress.STATUS_TRANSFERRING,
                                )
                                lastProgressUpdate = totalSent
                            }
                        }
                    }

                    if (!cancelled) {
                        outputStream.flush()
                    }
                }
            }

            if (cancelled) {
                sendTransferProgress(
                    nodeId = nodeId,
                    requestId = requestId,
                    songId = songId,
                    bytesTransferred = totalSent,
                    totalBytes = fileSize,
                    status = WearTransferProgress.STATUS_CANCELLED,
                )
                return
            }

            shouldForceCloseChannel = false

            sendTransferProgress(
                nodeId = nodeId,
                requestId = requestId,
                songId = songId,
                bytesTransferred = fileSize,
                totalBytes = fileSize,
                status = WearTransferProgress.STATUS_COMPLETED,
            )
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Failed to stream file to watch")
            sendTransferProgress(
                nodeId = nodeId,
                requestId = requestId,
                songId = songId,
                bytesTransferred = 0L,
                totalBytes = fileSize,
                status = WearTransferProgress.STATUS_FAILED,
                error = error.message,
            )
        } finally {
            if (shouldForceCloseChannel) {
                runCatching { channelClient.close(channel).await() }
            }
            transferCancellationStore.clear(requestId)
        }
    }

    private suspend fun streamArtworkToWatch(
        nodeId: String,
        requestId: String,
        songId: String,
        artworkBytes: ByteArray,
    ) {
        if (artworkBytes.isEmpty()) return
        val channel = channelClient.openChannel(nodeId, WearDataPaths.TRANSFER_ARTWORK_CHANNEL).await()
        var shouldForceCloseChannel = true
        try {
            withContext(Dispatchers.IO) {
                channelClient.getOutputStream(channel).await().use { outputStream ->
                    val requestIdBytes = requestId.toByteArray(Charsets.UTF_8)
                    val songIdBytes = songId.toByteArray(Charsets.UTF_8)

                    outputStream.write(ByteBuffer.allocate(4).putInt(requestIdBytes.size).array())
                    outputStream.write(requestIdBytes)
                    outputStream.write(ByteBuffer.allocate(4).putInt(songIdBytes.size).array())
                    outputStream.write(songIdBytes)
                    outputStream.write(artworkBytes)
                    outputStream.flush()
                }
            }
            shouldForceCloseChannel = false
        } finally {
            if (shouldForceCloseChannel) {
                runCatching { channelClient.close(channel).await() }
            }
        }
    }

    private suspend fun sendTransferMetadataError(
        nodeId: String,
        requestId: String,
        songId: String,
        errorMessage: String,
    ) {
        val metadata = WearTransferMetadata(
            requestId = requestId,
            songId = songId,
            title = "",
            artist = "",
            album = "",
            albumId = 0L,
            duration = 0L,
            mimeType = "",
            fileSize = 0L,
            bitrate = 0,
            sampleRate = 0,
            isFavorite = false,
            error = errorMessage,
        )
        runCatching {
            messageClient.sendMessage(
                nodeId,
                WearDataPaths.TRANSFER_METADATA,
                json.encodeToString(metadata).toByteArray(Charsets.UTF_8),
            ).await()
        }.onFailure { error ->
            Timber.tag(TAG).e(error, "Failed to send transfer error metadata")
        }

        sendTransferProgress(
            nodeId = nodeId,
            requestId = requestId,
            songId = songId,
            bytesTransferred = 0L,
            totalBytes = 0L,
            status = WearTransferProgress.STATUS_FAILED,
            error = errorMessage,
        )
    }

    private suspend fun sendTransferProgress(
        nodeId: String,
        requestId: String,
        songId: String,
        bytesTransferred: Long,
        totalBytes: Long,
        status: String,
        error: String? = null,
    ) {
        transferStateStore.markProgress(
            requestId = requestId,
            songId = songId,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            status = status,
            error = error,
        )
        if (status == WearTransferProgress.STATUS_COMPLETED) {
            transferStateStore.markSongPresentOnWatch(
                nodeId = nodeId,
                songId = songId,
            )
        }
        val progress = WearTransferProgress(
            requestId = requestId,
            songId = songId,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            status = status,
            error = error,
        )
        runCatching {
            messageClient.sendMessage(
                nodeId,
                WearDataPaths.TRANSFER_PROGRESS,
                json.encodeToString(progress).toByteArray(Charsets.UTF_8),
            ).await()
        }.onFailure { errorMsg ->
            Timber.tag(TAG).w(errorMsg, "Failed to send transfer progress")
        }
    }

    private companion object {
        const val TAG = "PhoneDirectTransfer"
        const val TRANSFER_CHUNK_SIZE = 8192
        const val PROGRESS_UPDATE_INTERVAL_BYTES = 65536L
        const val METADATA_GUARD_DELAY_MS = 250L
    }
}
