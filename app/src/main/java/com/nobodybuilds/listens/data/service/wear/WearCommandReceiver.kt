package com.nobodybuilds.listens.data.service.wear

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.core.content.ContextCompat
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.preferences.PlaylistPreferencesRepository
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.data.service.MusicService
import com.nobodybuilds.listens.data.service.MusicNotificationProvider
import com.nobodybuilds.listens.shared.WearBrowseRequest
import com.nobodybuilds.listens.shared.WearBrowseResponse
import com.nobodybuilds.listens.shared.WearDataPaths
import com.nobodybuilds.listens.shared.WearLibraryItem
import com.nobodybuilds.listens.shared.WearPlaybackCommand
import com.nobodybuilds.listens.shared.WearTransferMetadata
import com.nobodybuilds.listens.shared.WearTransferProgress
import com.nobodybuilds.listens.shared.WearTransferRequest
import com.nobodybuilds.listens.shared.WearVolumeCommand
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * WearableListenerService that receives commands from the Wear OS watch app.
 * Handles playback commands (play, pause, next, prev, etc.), volume commands,
 * and library browse requests.
 *
 * Commands are received via the Wear Data Layer MessageClient and forwarded
 * to the MusicService via MediaController or processed directly.
 */
@AndroidEntryPoint
class WearCommandReceiver : WearableListenerService() {

    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var playlistPreferencesRepository: PlaylistPreferencesRepository
    @Inject lateinit var directTransferCoordinator: PhoneDirectWatchTransferCoordinator
    @Inject lateinit var transferCancellationStore: PhoneWatchTransferCancellationStore
    @Inject lateinit var transferStateStore: PhoneWatchTransferStateStore
    @Inject lateinit var watchHandoffQueue: WatchHandoffQueue
    @Inject lateinit var watchStartedPlayback: WatchStartedPlayback

    private val json = Json { ignoreUnknownKeys = true }
    private var mediaController: MediaController? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Set of requestIds that have been cancelled by the watch */
    private val cancelledTransfers = ConcurrentHashMap.newKeySet<String>()
    private val libraryBrowser by lazy {
        WearLibraryBrowser(musicRepository, playlistPreferencesRepository) { getCurrentQueueItems() }
    }

    companion object {
        private const val TAG = "WearCommandReceiver"
        private const val TRANSFER_CHUNK_SIZE = 8192
        private const val PROGRESS_UPDATE_INTERVAL_BYTES = 65536L
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.tag(TAG).d("Received message on path: ${messageEvent.path}")

        when (messageEvent.path) {
            WearDataPaths.PLAYBACK_COMMAND -> handlePlaybackCommand(messageEvent)
            WearDataPaths.VOLUME_COMMAND -> handleVolumeCommand(messageEvent)
            WearDataPaths.BROWSE_REQUEST -> handleBrowseRequest(messageEvent)
            WearDataPaths.TRANSFER_REQUEST -> handleTransferRequest(messageEvent)
            WearDataPaths.TRANSFER_CANCEL -> handleTransferCancel(messageEvent)
            else -> Timber.tag(TAG).w("Unknown message path: ${messageEvent.path}")
        }
    }

    private fun handlePlaybackCommand(messageEvent: MessageEvent) {
        val commandJson = String(messageEvent.data, Charsets.UTF_8)
        val command = try {
            json.decodeFromString<WearPlaybackCommand>(commandJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse playback command")
            return
        }

        Timber.tag(TAG).d("Playback command: ${command.action}")

        when (command.action) {
            WearPlaybackCommand.PLAY_FROM_CONTEXT -> {
                handlePlayFromContext(command)
            }
            WearPlaybackCommand.CONTINUE_ON_PHONE -> {
                handleContinueOnPhone(command)
            }
            else -> {
                getOrBuildMediaController { controller ->
                    when (command.action) {
                        WearPlaybackCommand.PLAY -> controller.play()
                        WearPlaybackCommand.PAUSE -> controller.pause()
                        WearPlaybackCommand.TOGGLE_PLAY_PAUSE -> {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        }
                        WearPlaybackCommand.NEXT -> controller.seekToNext()
                        WearPlaybackCommand.PREVIOUS -> controller.seekToPrevious()
                        WearPlaybackCommand.PLAY_QUEUE_INDEX -> {
                            val queueIndex = command.queueIndex
                            if (queueIndex == null) {
                                Timber.tag(TAG).w("PLAY_QUEUE_INDEX missing queueIndex")
                            } else if (queueIndex !in 0 until controller.mediaItemCount) {
                                Timber.tag(TAG).w(
                                    "PLAY_QUEUE_INDEX out of bounds: %d (size=%d)",
                                    queueIndex,
                                    controller.mediaItemCount
                                )
                            } else {
                                controller.seekToDefaultPosition(queueIndex)
                                controller.play()
                            }
                        }
                        WearPlaybackCommand.TOGGLE_SHUFFLE -> {
                            controller.sendCustomCommand(
                                SessionCommand(
                                    MusicNotificationProvider.CUSTOM_COMMAND_TOGGLE_SHUFFLE,
                                    Bundle.EMPTY
                                ),
                                Bundle.EMPTY
                            )
                        }
                        WearPlaybackCommand.CYCLE_REPEAT -> {
                            controller.sendCustomCommand(
                                SessionCommand(
                                    MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE,
                                    Bundle.EMPTY
                                ),
                                Bundle.EMPTY
                            )
                        }
                        WearPlaybackCommand.TOGGLE_FAVORITE -> {
                            val targetEnabled = command.targetEnabled
                            if (targetEnabled == null) {
                                val sessionCommand = SessionCommand(
                                    MusicNotificationProvider.CUSTOM_COMMAND_LIKE,
                                    Bundle.EMPTY
                                )
                                controller.sendCustomCommand(sessionCommand, Bundle.EMPTY)
                            } else {
                                val args = Bundle().apply {
                                    putBoolean(MusicNotificationProvider.EXTRA_FAVORITE_ENABLED, targetEnabled)
                                }
                                val sessionCommand = SessionCommand(
                                    MusicNotificationProvider.CUSTOM_COMMAND_SET_FAVORITE_STATE,
                                    Bundle.EMPTY
                                )
                                controller.sendCustomCommand(sessionCommand, args)
                            }
                        }
                        else -> Timber.tag(TAG).w("Unknown playback action: ${command.action}")
                    }
                }
            }
        }
    }

    /** Play a song within its album, artist, playlist or library queue. */
    private fun handlePlayFromContext(command: WearPlaybackCommand) {
        val songId = command.songId
        val contextType = command.contextType
        if (songId == null || contextType == null) {
            Timber.tag(TAG).w("PLAY_FROM_CONTEXT missing songId or contextType")
            return
        }
        watchStartedPlayback.play("PLAY_FROM_CONTEXT") {
            val songs = libraryBrowser.getSongsForContext(contextType, command.contextId)
            songs.takeIf { it.isNotEmpty() }?.let { it to it.indexOfFirst { song -> song.id == songId }.coerceAtLeast(0) }
        }
    }

    /** The watch switched its output to the phone: play its queue here from where it was. */
    private fun handleContinueOnPhone(command: WearPlaybackCommand) {
        watchStartedPlayback.play("CONTINUE_ON_PHONE", positionMs = command.positionMs ?: 0L) {
            watchHandoffQueue.resolve(command.handoffQueue.orEmpty(), command.queueIndex ?: 0)
        }
    }

    // ---- Browse request handling ----

    private fun handleBrowseRequest(messageEvent: MessageEvent) {
        val requestJson = String(messageEvent.data, Charsets.UTF_8)
        val request = try {
            json.decodeFromString<WearBrowseRequest>(requestJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse browse request")
            return
        }

        Timber.tag(TAG).d("Browse request: type=${request.browseType}, contextId=${request.contextId}")

        scope.launch {
            val response = try {
                val items = libraryBrowser.getBrowseItems(request.browseType, request.contextId)
                WearBrowseResponse(requestId = request.requestId, items = items)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to process browse request")
                WearBrowseResponse(
                    requestId = request.requestId,
                    error = e.message ?: "Unknown error"
                )
            }

            // Send response back to the watch
            try {
                val responseBytes = json.encodeToString(response).toByteArray(Charsets.UTF_8)
                val messageClient = Wearable.getMessageClient(this@WearCommandReceiver)
                messageClient.sendMessage(
                    messageEvent.sourceNodeId,
                    WearDataPaths.BROWSE_RESPONSE,
                    responseBytes
                ).await()
                Timber.tag(TAG).d(
                    "Sent browse response: ${response.items.size} items for ${request.browseType}"
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to send browse response")
            }
        }
    }

    private suspend fun getCurrentQueueItems(): List<WearLibraryItem> {
        val deferred = CompletableDeferred<List<WearLibraryItem>>()
        getOrBuildMediaController { controller ->
            runCatching {
                buildCurrentQueueItems(controller)
            }.onSuccess(deferred::complete)
                .onFailure(deferred::completeExceptionally)
        }
        return withTimeout(5_000L) {
            deferred.await()
        }
    }

    // ---- Volume handling ----

    private fun handleVolumeCommand(messageEvent: MessageEvent) {
        val commandJson = String(messageEvent.data, Charsets.UTF_8)
        val command = try {
            json.decodeFromString<WearVolumeCommand>(commandJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse volume command")
            return
        }

        Timber.tag(TAG).d("Volume command: direction=${command.direction}, value=${command.value}")

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val absoluteValue = command.value
        if (absoluteValue != null) {
            // Set absolute volume (scaled from 0-100 to device range)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (absoluteValue * maxVolume / 100).coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        } else {
            when (command.direction) {
                WearVolumeCommand.UP -> audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    0
                )
                WearVolumeCommand.DOWN -> audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    0
                )
            }
        }
    }

    // ---- Transfer handling ----

    private fun handleTransferRequest(messageEvent: MessageEvent) {
        val requestJson = String(messageEvent.data, Charsets.UTF_8)
        val request = try {
            json.decodeFromString<WearTransferRequest>(requestJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse transfer request")
            return
        }

        Timber.tag(TAG).d("Transfer request: songId=${request.songId}, requestId=${request.requestId}")
        directTransferCoordinator.startTransferToWatch(
            nodeId = messageEvent.sourceNodeId,
            requestId = request.requestId,
            songId = request.songId,
            transferMode = request.transferMode,
            startPositionMs = request.startPositionMs,
            autoPlay = request.autoPlay,
        )
    }

    private fun handleTransferCancel(messageEvent: MessageEvent) {
        val requestJson = String(messageEvent.data, Charsets.UTF_8)
        val request = try {
            json.decodeFromString<WearTransferRequest>(requestJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse transfer cancel")
            return
        }
        cancelledTransfers.add(request.requestId)
        transferCancellationStore.markCancelled(request.requestId)
        transferStateStore.markCancelled(request.requestId)
        Timber.tag(TAG).d("Transfer cancelled: requestId=${request.requestId}")
    }

    /**
     * Open a song's audio file, trying direct File access first,
     * then falling back to ContentResolver.
     */
    private fun openSongFile(song: Song): InputStream? {
        return try {
            val file = File(song.path)
            if (file.exists() && file.canRead()) {
                file.inputStream()
            } else {
                contentResolver.openInputStream(song.contentUriString.toUri())
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to open song file: ${song.path}")
            try {
                contentResolver.openInputStream(song.contentUriString.toUri())
            } catch (e2: Exception) {
                Timber.tag(TAG).e(e2, "ContentResolver fallback also failed")
                null
            }
        }
    }

    /**
     * Get the file size of a song, trying File.length() first,
     * then ContentResolver.
     */
    private fun getSongFileSize(song: Song): Long {
        val file = File(song.path)
        if (file.exists()) return file.length()

        // Fallback: query ContentResolver for size
        return try {
            contentResolver.openAssetFileDescriptor(song.contentUriString.toUri(), "r")?.use {
                it.length
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Stream an audio file to the watch via ChannelClient.
     * The stream format is: [4 bytes requestId length][requestId bytes][audio data]
     */
    private suspend fun streamFileToWatch(
        nodeId: String,
        requestId: String,
        songId: String,
        inputStream: InputStream,
        fileSize: Long,
    ) {
        val channelClient = Wearable.getChannelClient(this@WearCommandReceiver)
        val channel = channelClient.openChannel(nodeId, WearDataPaths.TRANSFER_CHANNEL).await()

        try {
            val outputStream = channelClient.getOutputStream(channel).await()

            // Write header: requestId length (4 bytes big-endian) + requestId bytes
            val requestIdBytes = requestId.toByteArray(Charsets.UTF_8)
            val lengthBytes = ByteBuffer.allocate(4).putInt(requestIdBytes.size).array()
            outputStream.write(lengthBytes)
            outputStream.write(requestIdBytes)

            // Stream audio data in chunks
            val buffer = ByteArray(TRANSFER_CHUNK_SIZE)
            var totalSent = 0L
            var lastProgressUpdate = 0L

            inputStream.use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    // Check for cancellation
                    if (cancelledTransfers.remove(requestId)) {
                        Timber.tag(TAG).d("Transfer cancelled during streaming: $requestId")
                        sendTransferProgress(
                            nodeId, requestId, songId, totalSent, fileSize,
                            WearTransferProgress.STATUS_CANCELLED,
                        )
                        return
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalSent += bytesRead

                    // Send progress updates periodically
                    if (totalSent - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_BYTES) {
                        sendTransferProgress(
                            nodeId, requestId, songId, totalSent, fileSize,
                            WearTransferProgress.STATUS_TRANSFERRING,
                        )
                        lastProgressUpdate = totalSent
                    }
                }
            }

            outputStream.flush()
            outputStream.close()

            // Send completion status
            sendTransferProgress(
                nodeId, requestId, songId, fileSize, fileSize,
                WearTransferProgress.STATUS_COMPLETED,
            )
            Timber.tag(TAG).d("Transfer complete: songId=$songId, $totalSent bytes sent")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to stream file to watch")
            sendTransferProgress(
                nodeId, requestId, songId, 0, fileSize,
                WearTransferProgress.STATUS_FAILED, e.message,
            )
        } finally {
            try {
                channelClient.close(channel).await()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to close channel")
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
            error = errorMessage,
        )
        try {
            val metadataBytes = json.encodeToString(metadata).toByteArray(Charsets.UTF_8)
            val msgClient = Wearable.getMessageClient(this@WearCommandReceiver)
            msgClient.sendMessage(nodeId, WearDataPaths.TRANSFER_METADATA, metadataBytes).await()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send transfer error metadata")
        }
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
        val progress = WearTransferProgress(
            requestId = requestId,
            songId = songId,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            status = status,
            error = error,
        )
        try {
            val progressBytes = json.encodeToString(progress).toByteArray(Charsets.UTF_8)
            val msgClient = Wearable.getMessageClient(this@WearCommandReceiver)
            msgClient.sendMessage(nodeId, WearDataPaths.TRANSFER_PROGRESS, progressBytes).await()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to send transfer progress")
        }
    }

    // ---- MediaController management ----

    /**
     * Get existing MediaController or build a new one, then execute the action.
     */
    private fun getOrBuildMediaController(action: (MediaController) -> Unit) {
        runOnMainThread {
            val existing = mediaController
            if (existing != null && existing.isConnected) {
                action(existing)
                return@runOnMainThread
            }

            val inFlight = mediaControllerFuture
            if (inFlight != null && !inFlight.isDone) {
                inFlight.addListener(
                    {
                        try {
                            action(inFlight.get())
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Failed to reuse pending MediaController")
                        }
                    },
                    ContextCompat.getMainExecutor(this)
                )
                return@runOnMainThread
            }

            // The application context, not this service's: the service is often destroyed before the
            // connection is up, and releasing a connection bound to it then crashes the app.
            val sessionToken = SessionToken(
                applicationContext,
                ComponentName(applicationContext, MusicService::class.java)
            )
            val future = MediaController.Builder(applicationContext, sessionToken)
                .setApplicationLooper(Looper.getMainLooper())
                .buildAsync()
            mediaControllerFuture = future
            future.addListener(
                {
                    try {
                        val controller = future.get()
                        mediaController = controller
                        action(controller)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to build MediaController")
                    }
                },
                ContextCompat.getMainExecutor(this)
            )
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun releaseController() {
        val controller = mediaController
        mediaController = null
        mediaControllerFuture = null
        if (controller != null) {
            try {
                controller.release()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to release MediaController")
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        runOnMainThread {
            // Commands still waiting for the connection run first; then it is let go.
            mediaControllerFuture?.takeIf { !it.isDone }?.let { pending ->
                pending.addListener({ MediaController.releaseFuture(pending) }, ContextCompat.getMainExecutor(applicationContext))
            }
            releaseController()
        }
        super.onDestroy()
    }
}
