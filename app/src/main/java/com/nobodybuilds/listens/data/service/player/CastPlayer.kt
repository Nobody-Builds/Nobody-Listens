package com.nobodybuilds.listens.data.service.player

import android.content.ContentResolver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.nobodybuilds.listens.data.model.Song
import timber.log.Timber
import java.util.Locale
import java.util.ArrayDeque

class CastPlayer(
    private val castSession: CastSession,
    private val contentResolver: ContentResolver? = null
) {

    private val remoteMediaClient: RemoteMediaClient? = castSession.remoteMediaClient
    private val queueLoadTimeoutMs = 25000L
    private val commandTimeoutMs = 3500L
    private val commandRetryDelayMs = 220L
    private val minCommandSpacingMs = 120L
    private val seekDebounceMs = 140L

    private val commandHandler = Handler(Looper.getMainLooper())
    private val commandQueue = ArrayDeque<QueuedCommand>()
    private var isCommandInFlight = false
    private var lastCommandSentAtMs = 0L
    private var pendingSeekPositionMs: Long? = null
    private var seekDispatchRunnable: Runnable? = null
    private val castLogTag = "CastPlayer"
    private val mimeProbe = CastAudioMimeProbe(contentResolver)
    private val queueItemFactory = CastQueueItemFactory(contentResolver, mimeProbe)

    private data class QueuedCommand(
        val name: String,
        val retryAttempts: Int,
        val action: (RemoteMediaClient) -> PendingResult<RemoteMediaClient.MediaChannelResult>?
    )

    private fun enqueueRemoteCommand(
        name: String,
        retryAttempts: Int = 1,
        action: (RemoteMediaClient) -> PendingResult<RemoteMediaClient.MediaChannelResult>?
    ) {
        synchronized(commandQueue) {
            commandQueue.addLast(
                QueuedCommand(
                    name = name,
                    retryAttempts = retryAttempts,
                    action = action
                )
            )
            maybeDispatchNextCommandLocked()
        }
    }

    private fun maybeDispatchNextCommandLocked() {
        if (isCommandInFlight || commandQueue.isEmpty()) return
        val nextCommand = commandQueue.removeFirst()
        isCommandInFlight = true

        val now = SystemClock.elapsedRealtime()
        val delayMs = (lastCommandSentAtMs + minCommandSpacingMs - now).coerceAtLeast(0L)
        commandHandler.postDelayed({ executeQueuedCommand(nextCommand) }, delayMs)
    }

    private fun executeQueuedCommand(
        queuedCommand: QueuedCommand,
        retriesLeft: Int = queuedCommand.retryAttempts
    ) {
        val client = remoteMediaClient
        if (client == null) {
            Timber.w("Dropping cast command '%s': remote client unavailable", queuedCommand.name)
            finishQueuedCommand()
            return
        }

        lastCommandSentAtMs = SystemClock.elapsedRealtime()
        var completed = false

        fun complete(requestStatus: Boolean = true) {
            if (completed) return
            completed = true
            if (requestStatus) {
                client.requestStatus()
            }
            finishQueuedCommand()
        }

        val timeoutRunnable = Runnable {
            if (completed) return@Runnable
            Timber.w("Cast command timed out: %s", queuedCommand.name)
            if (retriesLeft > 0) {
                commandHandler.postDelayed(
                    { executeQueuedCommand(queuedCommand, retriesLeft - 1) },
                    commandRetryDelayMs
                )
                return@Runnable
            }
            complete(requestStatus = true)
        }
        commandHandler.postDelayed(timeoutRunnable, commandTimeoutMs)

        try {
            val pendingResult = queuedCommand.action(client)
            if (pendingResult == null) {
                commandHandler.removeCallbacks(timeoutRunnable)
                complete(requestStatus = true)
                return
            }

            pendingResult.setResultCallback { result ->
                if (completed) return@setResultCallback
                commandHandler.removeCallbacks(timeoutRunnable)

                if (!result.status.isSuccess && retriesLeft > 0) {
                    val isInvalidRequest = result.status.statusMessage
                        ?.contains("Invalid Request", ignoreCase = true) == true
                    if (isInvalidRequest) {
                        Log.e(
                            "PX_CAST_CMD",
                            "Invalid Request command=${queuedCommand.name} status=${result.status.statusCode} msg=${result.status.statusMessage}"
                        )
                    }
                    if (isInvalidRequest) {
                        Timber.w(
                            "Cast command invalid request: %s (%s/%d)",
                            queuedCommand.name,
                            result.status.statusMessage,
                            result.status.statusCode
                        )
                        complete(requestStatus = true)
                        return@setResultCallback
                    }
                    Timber.w(
                        "Cast command failed (%s/%d). Retrying %s",
                        result.status.statusMessage,
                        result.status.statusCode,
                        queuedCommand.name
                    )
                    commandHandler.postDelayed(
                        { executeQueuedCommand(queuedCommand, retriesLeft - 1) },
                        commandRetryDelayMs
                    )
                    return@setResultCallback
                }

                if (!result.status.isSuccess) {
                    Log.e(
                        "PX_CAST_CMD",
                        "Command failed command=${queuedCommand.name} status=${result.status.statusCode} msg=${result.status.statusMessage}"
                    )
                    Timber.w(
                        "Cast command failed: %s (%s/%d)",
                        queuedCommand.name,
                        result.status.statusMessage,
                        result.status.statusCode
                    )
                }
                complete(requestStatus = true)
            }
        } catch (e: Exception) {
            commandHandler.removeCallbacks(timeoutRunnable)
            if (retriesLeft > 0) {
                Timber.w(e, "Cast command threw; retrying %s", queuedCommand.name)
                commandHandler.postDelayed(
                    { executeQueuedCommand(queuedCommand, retriesLeft - 1) },
                    commandRetryDelayMs
                )
                return
            }
            Timber.e(e, "Cast command threw: %s", queuedCommand.name)
            complete(requestStatus = true)
        }
    }

    private fun finishQueuedCommand() {
        synchronized(commandQueue) {
            isCommandInFlight = false
            maybeDispatchNextCommandLocked()
        }
    }

    private fun clearCommandPipeline() {
        seekDispatchRunnable?.let { commandHandler.removeCallbacks(it) }
        seekDispatchRunnable = null
        pendingSeekPositionMs = null
        synchronized(commandQueue) {
            commandQueue.clear()
            isCommandInFlight = false
        }
        commandHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Load a queue of songs onto the Cast device.
     * Includes a timeout to prevent stuck "Connecting..." states.
     */
    fun loadQueue(
        songs: List<Song>,
        startIndex: Int,
        startPosition: Long,
        repeatMode: Int,
        serverAddress: String,
        authToken: String?,
        autoPlay: Boolean,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val client = remoteMediaClient
        if (client == null) {
            onComplete(false, "RemoteMediaClient is null")
            return
        }

        clearCommandPipeline()

        // Track whether callback has been fired to prevent double-calling
        var callbackFired = false
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (!callbackFired) {
                callbackFired = true
                Timber.e("Cast loadQueue timed out after %d ms", queueLoadTimeoutMs)
                onComplete(false, "Timed out after ${queueLoadTimeoutMs}ms")
            }
        }

        try {
            val safeStartIndex = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
            val startSong = songs.getOrNull(safeStartIndex)
            val queueLoadNonce = SystemClock.elapsedRealtime().toString(36)

            // Run I/O-heavy codec probing on a background thread so the UI stays responsive.
            // Cast SDK calls (queueLoad, pause, requestStatus) must happen on Main, so we
            // post back via the timeoutHandler (which targets the Main looper).
            Thread {
                Thread.currentThread().name = "cast-queue-probe"
                try {
                    val forcedMimeBySongId = mutableMapOf<String, String>()

                    // Probe every song that could be in an M4A container for ALAC codec.
                    // The server transcodes ALAC → AAC ADTS (audio/aac), so those queue items must be
                    // declared as audio/aac, not audio/mp4 (the container type).
                    // IMPORTANT: only force audio/aac when the raw codec MIME is "audio/alac".
                    // "audio/mp4a-latm" means AAC-LC inside an MP4 container; the server serves it
                    // as audio/mp4 (no transcode), so declaring audio/aac would cause a MIME mismatch
                    // and a Cast load failure (status 2103).  Let resolveCastContentType() handle those.
                    for (song in songs) {
                        val ext = song.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        val mimeL = song.mimeType?.lowercase(Locale.ROOT)
                        // We need to probe songs that the server will transcode (ALAC or FLAC → AAC).
                        val isMaybeTranscodeCandidate =
                            ext.let { it == "m4a" || it == "m4b" || it == "mp4" || it == "3gp" || it == "3gpp" || it == "flac" } ||
                            mimeL?.let { it.contains("mp4") || it.contains("m4a") || it == "audio/alac" || it == "audio/flac" || it == "audio/x-flac" } == true

                        if (!isMaybeTranscodeCandidate && song.id != startSong?.id) {
                            // Not a transcode-candidate file and not the start song — skip the probe.
                            continue
                        }

                        val rawExtractorMime = mimeProbe.detectAudioMimeTypeViaExtractor(song)

                        // Only override the MIME when the server will transcode (ALAC or FLAC → AAC ADTS).
                        // For every other codec — including audio/mp4a-latm (AAC-LC in MP4 container) —
                        // the server pipes the raw bytes as audio/mp4, which resolveCastContentType()
                        // already returns correctly from the file extension / metadata.
                        if (rawExtractorMime == "audio/alac") {
                            // Only declare audio/aac if the server can actually transcode.
                            // If findDecoderForFormat returns null (e.g. device AudioCapabilities
                            // rejects audio/alac at this sample rate), the server falls back to
                            // serving the raw M4A, so we must declare audio/mp4 instead to avoid
                            // a content-type mismatch that causes Cast to reject the item.
                            val alacDecoderAvailable = mimeProbe.isAlacTranscodeSupported()
                            val forcedMime = if (alacDecoderAvailable) "audio/aac" else "audio/mp4"
                            forcedMimeBySongId[song.id] = forcedMime
                            Log.i(
                                "PX_CAST_QLOAD",
                                "alac_probe songId=${song.id} rawCodec=audio/alac forcedMime=$forcedMime decoderAvailable=$alacDecoderAvailable nonce=$queueLoadNonce"
                            )
                            continue
                        }

                        if (rawExtractorMime == "audio/flac") {
                            // FLAC: Cast DMR can play FLAC natively but its duration estimate is
                            // wrong (VBR without seektable → Cast reports wrong total time), so any
                            // seek computes a bad byte offset, overshoots, and triggers an involuntary
                            // track skip. The server transcodes FLAC → AAC-ADTS (CBR), which gives
                            // Cast an accurate byte↔time mapping and reliable seeking.
                            val flacDecoderAvailable = mimeProbe.isFlacTranscodeSupported()
                            val forcedMime = if (flacDecoderAvailable) "audio/aac" else "audio/flac"
                            forcedMimeBySongId[song.id] = forcedMime
                            Log.i(
                                "PX_CAST_QLOAD",
                                "flac_probe songId=${song.id} rawCodec=audio/flac forcedMime=$forcedMime decoderAvailable=$flacDecoderAvailable nonce=$queueLoadNonce"
                            )
                            continue
                        }

                        if (song.id == startSong?.id) {
                            // ALAC is already caught by the rawExtractorMime == "audio/alac" branch above.
                            // Do NOT feed rawExtractorMime into toCastSupportedMimeTypeOrNull() here:
                            // MediaExtractor returns codec-level MIMEs (e.g. "audio/mp4a-latm" for
                            // AAC-LC inside an MP4 container), which normalise to "audio/aac".
                            // That would declare audio/aac while the server pipes the raw MP4 container
                            // as audio/mp4 — a mismatch the Cast receiver rejects with status 2103.
                            // MediaMetadataRetriever returns the container MIME ("audio/mp4") instead.
                            val retrieverMime = mimeProbe.detectAudioMimeTypeViaMetadataRetriever(song)?.toCastSupportedMimeTypeOrNull()
                            // Only consult signature when retriever failed; the framing scan can produce
                            // false positives on binary container data (e.g. 0xFF bytes in moov atom).
                            val signatureMime = if (retrieverMime == null) {
                                mimeProbe.detectAudioMimeTypeBySignature(song)?.toCastSupportedMimeTypeOrNull()
                            } else null
                            val forcedMime = retrieverMime ?: signatureMime
                            if (forcedMime != null) {
                                forcedMimeBySongId[song.id] = forcedMime
                            }
                            val resolverMime = contentResolver
                                ?.let { resolver -> runCatching { resolver.getType(song.contentUriString.toUri()) }.getOrNull() }
                            Log.i(
                                "PX_CAST_QLOAD",
                                "start_probe songId=${song.id} songMime=${song.mimeType} resolverMime=$resolverMime rawExtractorMime=$rawExtractorMime retrieverMime=$retrieverMime signatureMime=$signatureMime forcedMime=$forcedMime nonce=$queueLoadNonce"
                            )
                        }
                        // Non-start, non-ALAC M4A: no forced override needed. resolveCastContentType()
                        // correctly returns audio/mp4 from the file extension, matching what the server sends.
                    }

                    // Post back to Main thread — Cast SDK requires Main for queueLoad.
                    timeoutHandler.post {
                        try {
                            val mediaItems = songs.map { song ->
                                with(queueItemFactory) {
                                    song.toMediaQueueItem(
                                        serverAddress = serverAddress,
                                        authToken = authToken,
                                        forcedMimeType = forcedMimeBySongId[song.id],
                                        queueLoadNonce = queueLoadNonce
                                    )
                                }
                            }.toTypedArray()

                            Timber.tag(castLogTag).i(
                                "queueLoad start size=%d startIndex=%d startSongId=%s autoPlay=%s server=%s",
                                songs.size,
                                safeStartIndex,
                                startSong?.id,
                                autoPlay,
                                serverAddress
                            )
                            Log.i(
                                "PX_CAST_QLOAD",
                                "start size=${songs.size} startIndex=$safeStartIndex songId=${startSong?.id} autoPlay=$autoPlay nonce=$queueLoadNonce"
                            )
                            queueItemFactory.logQueueDiagnostics(
                                songs = songs,
                                startIndex = safeStartIndex,
                                serverAddress = serverAddress,
                                authToken = authToken,
                                queueLoadNonce = queueLoadNonce,
                                forcedMimeBySongId = forcedMimeBySongId
                            )

                            timeoutHandler.postDelayed(timeoutRunnable, queueLoadTimeoutMs)

                            client.queueLoad(
                                mediaItems,
                                safeStartIndex,
                                repeatMode,
                                startPosition,
                                null
                            ).setResultCallback { result ->
                                // Cancel timeout since we got a response
                                timeoutHandler.removeCallbacks(timeoutRunnable)

                                if (callbackFired) {
                                    // Timeout already fired, ignore this late callback
                                    Timber.w("Cast loadQueue result received after timeout, ignoring")
                                    return@setResultCallback
                                }
                                callbackFired = true

                                if (result.status.isSuccess) {
                                    Timber.tag(castLogTag).i(
                                        "queueLoad success statusCode=%d message=%s",
                                        result.status.statusCode,
                                        result.status.statusMessage
                                    )
                                    Log.i(
                                        "PX_CAST_QLOAD",
                                        "success status=${result.status.statusCode} msg=${result.status.statusMessage}"
                                    )
                                    if (!autoPlay) {
                                        // queueLoad typically starts playback by default; explicitly pause when caller requests no autoplay.
                                        client.pause()
                                    }
                                    // Immediately acknowledge success and request a status update to avoid UI stalls.
                                    onComplete(true, null)
                                    client.requestStatus()
                                } else {
                                    val failureDetail = "status=${result.status.statusCode} msg=${result.status.statusMessage ?: "unknown"}"
                                    Timber.tag(castLogTag).e(
                                        "queueLoad failed statusCode=%d message=%s startSongId=%s size=%d",
                                        result.status.statusCode,
                                        result.status.statusMessage,
                                        startSong?.id,
                                        songs.size
                                    )
                                    Log.e(
                                        "PX_CAST_QLOAD",
                                        "failed status=${result.status.statusCode} msg=${result.status.statusMessage} songId=${startSong?.id} size=${songs.size}"
                                    )
                                    onComplete(false, failureDetail)
                                }
                            }
                        } catch (e: Exception) {
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            Timber.tag(castLogTag).e(e, "queueLoad threw exception (size=%d startIndex=%d)", songs.size, startIndex)
                            if (!callbackFired) {
                                callbackFired = true
                                onComplete(false, "${e.javaClass.simpleName}: ${e.message ?: "Unknown"}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(castLogTag).e(e, "queueLoad probe threw exception (size=%d startIndex=%d)", songs.size, startIndex)
                    timeoutHandler.post {
                        if (!callbackFired) {
                            callbackFired = true
                            onComplete(false, "${e.javaClass.simpleName}: ${e.message ?: "Unknown"}")
                        }
                    }
                }
            }.start()
        } catch (e: Exception) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            Timber.tag(castLogTag).e(e, "queueLoad threw exception (size=%d startIndex=%d)", songs.size, startIndex)
            if (!callbackFired) {
                callbackFired = true
                onComplete(false, "${e.javaClass.simpleName}: ${e.message ?: "Unknown"}")
            }
        }
    }

    fun seek(position: Long) {
        val targetPosition = position.coerceAtLeast(0L)
        pendingSeekPositionMs = targetPosition
        seekDispatchRunnable?.let { commandHandler.removeCallbacks(it) }

        val runnable = Runnable {
            val finalPosition = pendingSeekPositionMs ?: return@Runnable
            pendingSeekPositionMs = null
            enqueueRemoteCommand(
                name = "seek($finalPosition)",
                retryAttempts = 0
            ) { client ->
                val seekOptions = MediaSeekOptions.Builder()
                    .setPosition(finalPosition)
                    .build()
                client.seek(seekOptions)
            }
        }
        seekDispatchRunnable = runnable
        commandHandler.postDelayed(runnable, seekDebounceMs)
    }

    fun play() {
        enqueueRemoteCommand(name = "play", retryAttempts = 1) { client ->
            client.play()
        }
    }

    fun pause() {
        enqueueRemoteCommand(name = "pause", retryAttempts = 1) { client ->
            client.pause()
        }
    }

    fun next() {
        enqueueRemoteCommand(name = "next", retryAttempts = 1) { client ->
            val status = client.mediaStatus
            val queueItems = status?.queueItems ?: emptyList()
            val currentItemId = status?.currentItemId
            val currentIndex = queueItems.indexOfFirst { it.itemId == currentItemId }
            val nextItem = if (currentIndex >= 0) queueItems.getOrNull(currentIndex + 1) else null
            if (nextItem != null) {
                client.queueJumpToItem(nextItem.itemId, 0L, null)
            } else {
                client.queueNext(null)
            }
        }
    }

    fun previous() {
        enqueueRemoteCommand(name = "previous", retryAttempts = 1) { client ->
            val status = client.mediaStatus
            val queueItems = status?.queueItems ?: emptyList()
            val currentItemId = status?.currentItemId
            val currentIndex = queueItems.indexOfFirst { it.itemId == currentItemId }
            val previousItem = if (currentIndex > 0) queueItems.getOrNull(currentIndex - 1) else null
            if (previousItem != null) {
                client.queueJumpToItem(previousItem.itemId, 0L, null)
            } else {
                client.queuePrev(null)
            }
        }
    }

    fun jumpToItem(itemId: Int, position: Long) {
        enqueueRemoteCommand(name = "jumpToItem($itemId)", retryAttempts = 0) { client ->
            client.queueJumpToItem(itemId, position.coerceAtLeast(0L), null)
        }
    }

    fun setRepeatMode(repeatMode: Int) {
        enqueueRemoteCommand(name = "setRepeatMode($repeatMode)", retryAttempts = 0) { client ->
            client.queueSetRepeatMode(repeatMode, null)
        }
    }

    fun release() {
        clearCommandPipeline()
    }
}
