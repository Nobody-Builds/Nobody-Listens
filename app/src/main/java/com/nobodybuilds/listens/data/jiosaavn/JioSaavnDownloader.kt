package com.nobodybuilds.listens.data.jiosaavn

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.nobodybuilds.listens.data.worker.SyncManager
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

/**
 * Saves JioSaavn songs as tagged .m4a files under Music/Nobody Listens/JioSaavn. JioSaavn
 * serves AAC in an MP4 container, so the file is kept as it comes and only tagged.
 */
@Singleton
class JioSaavnDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: JioSaavnRepository,
    private val syncManager: SyncManager,
    okHttpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Mutex()
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()
    private val _activeSongId = MutableStateFlow<String?>(null)
    /** The song being downloaded right now, so buttons can show progress for it. */
    val activeSongId: StateFlow<String?> = _activeSongId.asStateFlow()
    // Which file each downloaded song became, so it can be removed again later.
    private val savedFiles = context.getSharedPreferences(DOWNLOADS_PREFS, Context.MODE_PRIVATE)
    private val _savedSongIds = MutableStateFlow(savedFiles.all.keys.toSet())
    /** Songs that have a downloaded file. */
    val savedSongIds: StateFlow<Set<String>> = _savedSongIds.asStateFlow()

    init {
        // Files deleted outside Nobody Listens no longer count as downloaded.
        scope.launch { forgetMissingFiles() }
    }

    private val http = JioSaavnClients.http(okHttpClient).newBuilder()
        .callTimeout(5, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    fun enqueue(tracks: List<JioSaavnTrack>) {
        if (tracks.isEmpty()) return
        scope.launch {
            // One download at a time, so taps on several songs do not race each other.
            queue.withLock {
                var saved = 0
                tracks.forEachIndexed { index, track ->
                    val label = if (tracks.size == 1) track.title else "${track.title} (${index + 1}/${tracks.size})"
                    _status.value = "Downloading $label"
                    _activeSongId.value = track.id
                    runCatching { download(track) { percent -> _status.value = "Downloading $label · $percent%" } }
                        .onSuccess { uri ->
                            saved += 1
                            savedFiles.edit().putString(track.id, uri).apply()
                            _savedSongIds.value = _savedSongIds.value + track.id
                        }
                        .onFailure { error ->
                            Timber.w(error, "JioSaavn download failed for ${track.id}")
                            _status.value = "Download failed: ${error.message ?: track.title}"
                        }
                }
                _activeSongId.value = null
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

    /** Deletes a song's downloaded file. Nobody Listens created it, so no extra permission is needed. */
    fun delete(songId: String, title: String) {
        scope.launch {
            val uri = savedFiles.getString(songId, null)
            val removed = uri == null || runCatching {
                context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
            }.onFailure { Timber.w(it, "Could not delete the download of $songId") }.isSuccess
            if (!removed) {
                _status.value = "Could not remove $title"
                return@launch
            }
            savedFiles.edit().remove(songId).apply()
            _savedSongIds.value = _savedSongIds.value - songId
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
        _savedSongIds.value = _savedSongIds.value - missing
    }

    /** Downloads the song at the download quality and saves it, tagged, under Music/Nobody Listens/JioSaavn. */
    suspend fun download(
        track: JioSaavnTrack,
        onProgress: (Int) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val stream = repository.resolveStream(track.id, repository.downloadQuality.value)
        val workDir = File(context.cacheDir, "jiosaavn-downloads").apply { mkdirs() }
        val file = File(workDir, "${track.id}.m4a")
        try {
            fetch(stream, file, onProgress)
            runCatching { tag(file, track) }.onFailure { Timber.w(it, "Could not tag the JioSaavn download ${track.id}") }
            write(file, track)
        } finally {
            file.delete()
        }
    }

    private suspend fun fetch(stream: JioSaavnStream, target: File, onProgress: (Int) -> Unit) {
        val request = JioSaavnClients.decorateMediaRequest(Request.Builder().url(stream.url)).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 403) repository.reportStreamRejected(stream.id)
                throw IOException("JioSaavn refused the download (${response.code})")
            }
            val total = response.body.contentLength().takeIf { it > 0 }
            target.outputStream().use { output ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read
                        val percent = total?.let { ((written * 100) / it).toInt() }
                        if (percent != null && percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                    if (total != null && written < total) throw IOException("Download ended early")
                }
            }
        }
    }

    private fun tag(file: File, track: JioSaavnTrack) {
        val cover = fetchCover(track)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { fd ->
            val tags = hashMapOf(
                "TITLE" to arrayOf(track.title),
                "ARTIST" to arrayOf(JioSaavnUris.artistName(track)),
                "ALBUM" to arrayOf(JioSaavnUris.albumName(track)),
            )
            track.year.takeIf { it.isNotBlank() }?.let { tags["DATE"] = arrayOf(it) }
            TagLib.savePropertyMap(fd.dup().detachFd(), tags)
            if (cover != null) {
                TagLib.savePictures(
                    fd.dup().detachFd(),
                    arrayOf(Picture(data = cover, description = "Front Cover", pictureType = "Front Cover", mimeType = "image/jpeg")),
                )
            }
        }
    }

    private fun fetchCover(track: JioSaavnTrack): ByteArray? {
        val url = JioSaavnParsers.artwork(track.thumbnailUrl) ?: return null
        return runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) response.body.bytes() else null
            }
        }.getOrNull()
    }

    private fun write(file: File, track: JioSaavnTrack): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName(track))
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Nobody Listens/JioSaavn")
            put(MediaStore.Audio.Media.TITLE, track.title)
            put(MediaStore.Audio.Media.ARTIST, JioSaavnUris.artistName(track))
            put(MediaStore.Audio.Media.ALBUM, JioSaavnUris.albumName(track))
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

    private fun fileName(track: JioSaavnTrack): String {
        val title = sanitize(track.title).ifBlank { JioSaavnUris.SERVICE_NAME }
        val artist = sanitize(track.artist)
        return if (artist.isBlank()) "$title.m4a" else "$artist - $title.m4a"
    }

    private fun sanitize(value: String): String =
        value.replace('"', '\'')
            .replace(Regex("""[\\/:*?<>|]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)

    private companion object {
        const val DOWNLOADS_PREFS = "jiosaavn_downloads"
    }
}
