package com.twilighty.lmusic.data.ytmusic

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.twilighty.lmusic.data.worker.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class YouTubeMusicDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: YouTubeMusicRepository,
    private val syncManager: SyncManager,
    okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Mutex()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    private val _activeVideoId = MutableStateFlow<String?>(null)
    /** The song being downloaded right now, so buttons can show progress for it. */
    val activeVideoId: StateFlow<String?> = _activeVideoId.asStateFlow()
    // Which file each downloaded song became, so it can be removed again later.
    private val savedFiles = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE)
    private val _savedVideoIds = MutableStateFlow(savedFiles.all.keys.toSet())
    /** Songs that have a downloaded file. */
    val savedVideoIds: StateFlow<Set<String>> = _savedVideoIds.asStateFlow()

    init {
        // Files deleted outside LMusic no longer count as downloaded.
        scope.launch { forgetMissingFiles() }
    }

    private val http = YouTubeMusicClients.http(okHttpClient).newBuilder()
        .callTimeout(2, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun enqueue(tracks: List<YouTubeMusicTrack>) {
        if (tracks.isEmpty()) return
        scope.launch {
            // One download at a time, so taps on several songs do not race each other.
            queue.withLock {
                var saved = 0
                tracks.forEachIndexed { index, track ->
                    val label = if (tracks.size == 1) track.title else "${track.title} (${index + 1}/${tracks.size})"
                    _status.value = "Downloading $label"
                    _activeVideoId.value = track.videoId
                    runCatching { download(track) { percent -> _status.value = "Downloading $label · $percent%" } }
                        .onSuccess { uri ->
                            saved += 1
                            savedFiles.edit().putString(track.videoId, uri).apply()
                            _savedVideoIds.value = _savedVideoIds.value + track.videoId
                        }
                        .onFailure { error ->
                            Timber.w(error, "YouTube Music download failed for ${track.videoId}")
                            _status.value = "Download failed: ${error.message ?: track.title}"
                        }
                }
                _activeVideoId.value = null
                if (saved > 0) syncManager.incrementalSync()
                _status.value = when {
                    tracks.size == 1 && saved == 1 -> "Saved ${tracks.first().title}"
                    tracks.size == 1 -> _status.value
                    else -> "Saved $saved of ${tracks.size}"
                }
            }
        }
    }

    fun clearStatus() {
        _status.value = null
    }

    /** Deletes a song's downloaded file. LMusic created it, so no extra permission is needed. */
    fun delete(videoId: String, title: String) {
        scope.launch {
            val uri = savedFiles.getString(videoId, null)
            val removed = uri == null || runCatching {
                context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
            }.onFailure { Timber.w(it, "Could not delete the download of $videoId") }.isSuccess
            if (!removed) {
                _status.value = "Could not remove $title"
                return@launch
            }
            savedFiles.edit().remove(videoId).apply()
            _savedVideoIds.value = _savedVideoIds.value - videoId
            syncManager.incrementalSync()
            _status.value = "Removed $title"
        }
    }

    private fun forgetMissingFiles() {
        val missing = savedFiles.all.filter { (_, value) ->
            val uri = (value as? String)?.let(android.net.Uri::parse) ?: return@filter true
            runCatching {
                context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)
                    ?.use { it.count == 0 } ?: true
            }.getOrDefault(false)
        }.keys
        if (missing.isEmpty()) return
        savedFiles.edit().apply { missing.forEach(::remove) }.apply()
        _savedVideoIds.value = _savedVideoIds.value - missing
    }

    /** Downloads the highest bitrate audio and saves it, tagged, under Music/Nobody Listens/YouTube Music. */
    suspend fun download(
        track: YouTubeMusicTrack,
        onProgress: (Int) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        // Opus only: every download becomes a tagged .opus file, whatever the quality.
        val stream = repository.resolveStream(track.videoId, repository.downloadQuality.value, opusOnly = true)
        val workDir = File(context.cacheDir, "ytmusic-downloads").apply { mkdirs() }
        val raw = File(workDir, "${track.videoId}.part")
        val tagged = File(workDir, "${track.videoId}.opus")
        try {
            fetch(stream, raw, onProgress)
            val isOpus = YouTubeMusicFormats.fileExtension(stream.format.mimeType) == "webm"
            val remuxed = isOpus && runCatching {
                val tags = YouTubeMusicOpusRemuxer.Tags(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    cover = fetchCover(track),
                )
                raw.inputStream().use { input ->
                    tagged.outputStream().use { output -> YouTubeMusicOpusRemuxer.remux(input, output, tags) }
                }
            }.onFailure { Timber.w(it, "Opus remux failed, keeping the WebM file") }.isSuccess
            val spec = YouTubeMusicFormats.downloadSpec(
                title = track.title,
                artist = track.artist,
                mimeType = if (remuxed) YouTubeMusicFormats.OGG_OPUS_MIME else stream.format.mimeType,
            )
            write(spec, track, if (remuxed) tagged else raw)
        } finally {
            raw.delete()
            tagged.delete()
        }
    }

    /** Fetches in ranges: googlevideo serves bounded ranges at full speed. */
    private suspend fun fetch(stream: YouTubeMusicStream, target: File, onProgress: (Int) -> Unit) {
        val total = stream.format.contentLength
        target.outputStream().use { output ->
            var position = 0L
            var lastPercent = -1
            while (total == null || position < total) {
                coroutineContext.ensureActive()
                val end = if (total == null) "" else minOf(total - 1, position + CHUNK_BYTES - 1).toString()
                val request = Request.Builder()
                    .url(stream.url)
                    .header("User-Agent", stream.userAgent)
                    .header("Range", "bytes=$position-$end")
                    .build()
                val read = http.newCall(request).execute().use { response ->
                    if (response.code != 200 && response.code != 206) {
                        if (response.code == 403) repository.reportStreamRejected(stream.videoId)
                        throw IOException("YouTube refused the download (${response.code})")
                    }
                    response.body.byteStream().use { it.copyTo(output) }
                }
                if (read == 0L) break
                position += read
                if (total != null) {
                    val percent = ((position * 100) / total).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                } else {
                    break
                }
            }
            if (total != null && position < total) throw IOException("Download ended early")
        }
    }

    /** Square cover for the file: big album art, or the HD video thumbnail cropped square. */
    private suspend fun fetchCover(track: YouTubeMusicTrack): ByteArray? {
        val isVideo = track.thumbnailUrl == null || YouTubeMusicThumbnails.isVideoThumbnail(track.thumbnailUrl)
        val url = if (isVideo) {
            YouTubeMusicThumbnails.bestVideoThumbnail(http, track.videoId) ?: track.thumbnailUrl
        } else {
            YouTubeMusicThumbnails.albumArt(track.thumbnailUrl, DOWNLOAD_COVER_SIZE)
        } ?: return null
        val bytes = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body.bytes() else null
            }
        }.getOrNull() ?: return null
        return if (isVideo) {
            runCatching { YouTubeMusicThumbnails.squareCover(bytes, YouTubeMusicThumbnails.hasLetterbox(url)) }.getOrDefault(bytes)
        } else bytes
    }

    private fun write(spec: YouTubeMusicDownloadSpec, track: YouTubeMusicTrack, file: File): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, spec.fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, spec.mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Nobody Listens/YouTube Music")
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, YouTubeMusicUris.artistName(track))
            put(MediaStore.Audio.Media.ALBUM, YouTubeMusicUris.albumName(track))
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create the download")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { it.copyTo(output) }
            } ?: throw IllegalStateException("Could not write the download")
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private companion object {
        const val CHUNK_BYTES = 4L * 1024 * 1024
        const val DOWNLOADS_PREFS = "ytmusic_downloads"
        const val DOWNLOAD_COVER_SIZE = 1200
    }
}
