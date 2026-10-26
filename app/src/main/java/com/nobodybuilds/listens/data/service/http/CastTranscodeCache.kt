package com.nobodybuilds.listens.data.service.http

import android.net.Uri
import com.nobodybuilds.listens.data.model.Song
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// -------------------------------------------------------------------------
// Transcode temp-file cache
//
// Transcoded (ALAC→AAC, FLAC→AAC) streams are written to a per-song temp
// file so that Cast can issue byte-range requests (seeks) against a file
// with a known Content-Length.  The state machine per song id:
//
//   null                  → not yet started, first request initiates transcode
//   TranscodeEntry(done=false, latch) → transcode in-progress, subsequent
//                           requestors wait on latch before reading
//   TranscodeEntry(done=true)         → temp file complete, serve directly
// -------------------------------------------------------------------------
internal data class TranscodeEntry(
    val tempFile: File,
    /** True once the transcode goroutine has written the last byte and closed the file. */
    @Volatile var done: Boolean = false,
    /** True when transcode failed (temp file may be partial). */
    @Volatile var failed: Boolean = false,
    /** Other requests wait on this until done|failed is set. */
    val latch: CountDownLatch = CountDownLatch(1)
)

internal class CastTranscodeCache(
    private val cacheDir: File,
    private val serviceScope: CoroutineScope,
    private val transcoder: CastAacTranscoder
) {
    private val transcodeCache = ConcurrentHashMap<String, TranscodeEntry>()

    operator fun get(songId: String): TranscodeEntry? = transcodeCache[songId]

    suspend fun respondTranscoded(
        call: ApplicationCall,
        song: Song,
        codecInfo: AudioCodecInfo,
        uri: Uri,
        rangeHeader: String?
    ) = call.respondTranscodedWithCache(song, codecInfo, uri, rangeHeader)

    // -------------------------------------------------------------------------
    // Transcode cache helpers
    // -------------------------------------------------------------------------

    /**
     * Responds to a GET request for a transcoded song (ALAC→AAC or FLAC→AAC).
     *
     * Strategy:
     *  - If a completed temp file exists in [transcodeCache], serve it immediately
     *    with full Range/206/Content-Length/Accept-Ranges support (seek-safe).
     *  - If a transcode is already in-progress, wait for it then serve the file
     *    (or fall back to streaming if it failed).
     *  - If no entry exists yet, create one and start a background transcode:
     *    • First request: transcode while simultaneously piping data to the
     *      HTTP response ("tee" pattern so playback starts immediately).
     *    • Data is written to the temp file; once finished the entry is marked done.
     *    • Future seek requests (Range: bytes=X-) read directly from the temp file.
     */
    private suspend fun ApplicationCall.respondTranscodedWithCache(
        song: Song,
        codecInfo: AudioCodecInfo,
        uri: Uri,
        rangeHeader: String?
    ) {
        val songId = song.id
        val aacContentType = ContentType.parse("audio/aac")

        // Fast path: temp file already complete → serve with Range support.
        val existing = transcodeCache[songId]
        if (existing != null && existing.done && !existing.failed && existing.tempFile.exists()) {
            Timber.tag(CAST_HTTP_LOG_TAG).d(
                "transcode-cache HIT songId=%s size=%d range=%s",
                songId, existing.tempFile.length(), rangeHeader
            )
            respondWithAudioStream(
                contentType = aacContentType,
                fileSize = existing.tempFile.length(),
                rangeHeader = rangeHeader
            ) { FileInputStream(existing.tempFile) }
            return
        }

        // If a transcode is already running, wait for it.
        if (existing != null && !existing.done) {
            Timber.tag(CAST_HTTP_LOG_TAG).d(
                "transcode-cache WAIT songId=%s range=%s", songId, rangeHeader
            )
            // Wait with a generous timeout (10 min for very long songs).
            withContext(Dispatchers.IO) {
                existing.latch.await(10, TimeUnit.MINUTES)
            }
            if (existing.done && !existing.failed && existing.tempFile.exists()) {
                respondWithAudioStream(
                    contentType = aacContentType,
                    fileSize = existing.tempFile.length(),
                    rangeHeader = rangeHeader
                ) { FileInputStream(existing.tempFile) }
                return
            }
            // Transcode failed: fall back to raw on-the-fly streaming (no seek support).
            Timber.tag(CAST_HTTP_LOG_TAG).w(
                "transcode-cache FAILED-WAIT songId=%s, falling back to on-the-fly", songId
            )
            respondOutputStream(aacContentType) {
                transcoder.transcodeToAacAdts(codecInfo, song, uri, this)
            }
            return
        }

        // No entry yet — we are the first request. Create the entry and start transcoding.
        val tempFile = File(cacheDir, "cast_transcode_${songId}.aac")
        // Remove stale failed temp file if present.
        runCatching { if (tempFile.exists()) tempFile.delete() }

        val entry = TranscodeEntry(tempFile = tempFile)
        transcodeCache[songId] = entry

        Timber.tag(CAST_HTTP_LOG_TAG).d(
            "transcode-cache MISS songId=%s, starting tee transcode", songId
        )
        Timber.tag("PX_CAST_HTTP").i("transcode_cache_start songId=$songId codec=${codecInfo.codecMime}")

        // If a Range header was sent on the very first request (unlikely but defensive),
        // we can't tee and serve from offset 0. Wait is the cleanest option — start
        // transcode in a background job and serve the whole file once done.
        if (rangeHeader != null && !rangeHeader.startsWith("bytes=0-")) {
            // Launch background transcode, wait until done then serve.
            serviceScope.launch {
                runCatching {
                    FileOutputStream(tempFile).use { fos ->
                        transcoder.transcodeToAacAdts(codecInfo, song, uri, fos)
                    }
                    entry.done = true
                }.onFailure { t ->
                    entry.failed = true
                    Timber.tag(CAST_HTTP_LOG_TAG).e(t, "transcode-cache bg transcode failed songId=%s", songId)
                    runCatching { tempFile.delete() }
                }.also {
                    entry.latch.countDown()
                }
            }
            // Wait for completion.
            withContext(Dispatchers.IO) {
                entry.latch.await(10, TimeUnit.MINUTES)
            }
            if (entry.done && tempFile.exists()) {
                respondWithAudioStream(
                    contentType = aacContentType,
                    fileSize = tempFile.length(),
                    rangeHeader = rangeHeader
                ) { FileInputStream(tempFile) }
            } else {
                respond(HttpStatusCode.InternalServerError, "Transcode failed")
            }
            return
        }

        // Normal first request (no Range / Range:bytes=0-): tee transcode output to both
        // the HTTP response stream and the temp file simultaneously.
        // respondOutputStream uses an extension-function receiver: `this` IS the OutputStream.
        respondOutputStream(aacContentType) {
            val responseStream: OutputStream = this
            val transcodeError = runCatching {
                FileOutputStream(tempFile).use { fileOut ->
                    val tee = TeeOutputStream(responseStream, fileOut)
                    transcoder.transcodeToAacAdts(codecInfo, song, uri, tee)
                }
                entry.done = true
                Timber.tag("PX_CAST_HTTP").i("transcode_cache_done songId=$songId size=${tempFile.length()}")
            }.exceptionOrNull()

            if (transcodeError != null) {
                entry.failed = true
                runCatching { tempFile.delete() }
                if (!transcodeError.isClientAbortDuringResponse()) {
                    Timber.tag(CAST_HTTP_LOG_TAG).e(transcodeError, "transcode-cache tee failed songId=%s", songId)
                    Timber.tag("PX_CAST_HTTP")
                        .e(transcodeError, "transcode_cache_error songId=$songId")
                }
            }
            entry.latch.countDown()
        }
    }

    /** Cleans up all transcode temp files (called on service destroy). */
    fun cleanupAllTranscodeTempFiles() {
        transcodeCache.values.forEach { entry ->
            runCatching { entry.tempFile.delete() }
        }
        transcodeCache.clear()
        Timber.tag("PX_CAST_HTTP").i("transcode_cache_cleanup done")
    }

    /**
     * Removes the cached transcode for [songId] and deletes the temp file.
     * Call this when the Cast session changes (song skip, session end).
     */
    fun evictTranscodeCache(songId: String) {
        transcodeCache.remove(songId)?.let { entry ->
            runCatching { entry.tempFile.delete() }
            Timber.tag(CAST_HTTP_LOG_TAG).d("transcode-cache evicted songId=%s", songId)
        }
    }
}
