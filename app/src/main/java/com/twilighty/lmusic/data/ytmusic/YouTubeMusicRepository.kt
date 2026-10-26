package com.twilighty.lmusic.data.ytmusic

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicRepository @Inject constructor(
    @ApplicationContext context: Context,
    okHttpClient: OkHttpClient,
) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "YouTubeMusicRepository: Failed to create EncryptedSharedPreferences, falling back to plain")
        context.getSharedPreferences("${PREFS}_plain", Context.MODE_PRIVATE)
    }.also { encrypted ->
        migrateLegacySession(context, encrypted)
    }
    private val api = YouTubeMusicApi(okHttpClient)
    private val streamPlayback = ConcurrentHashMap<String, StreamPlayback>()
    private val failedClientsUntil = ConcurrentHashMap<String, Long>()
    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val inFlightStreams = ConcurrentHashMap<String, Deferred<YouTubeMusicStream>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _session = MutableStateFlow(readSession())

    val session: StateFlow<YouTubeMusicSession> = _session.asStateFlow()

    // Quality settings are not secrets, so they live outside the encrypted session store.
    private val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val _streamQuality = MutableStateFlow(readQuality(KEY_STREAM_QUALITY))
    private val _downloadQuality = MutableStateFlow(readQuality(KEY_DOWNLOAD_QUALITY))

    val streamQuality: StateFlow<YouTubeMusicAudioQuality> = _streamQuality.asStateFlow()
    val downloadQuality: StateFlow<YouTubeMusicAudioQuality> = _downloadQuality.asStateFlow()

    fun setStreamQuality(quality: YouTubeMusicAudioQuality) {
        settings.edit().putString(KEY_STREAM_QUALITY, quality.name).apply()
        _streamQuality.value = quality
    }

    fun setDownloadQuality(quality: YouTubeMusicAudioQuality) {
        settings.edit().putString(KEY_DOWNLOAD_QUALITY, quality.name).apply()
        _downloadQuality.value = quality
    }

    private val _searchSuggestionsEnabled = MutableStateFlow(settings.getBoolean(KEY_SEARCH_SUGGESTIONS, false))

    /** Whether YouTube search suggestions are fetched while typing. Off until turned on in Settings. */
    val searchSuggestionsEnabled: StateFlow<Boolean> = _searchSuggestionsEnabled.asStateFlow()

    fun setSearchSuggestionsEnabled(enabled: Boolean) {
        settings.edit().putBoolean(KEY_SEARCH_SUGGESTIONS, enabled).apply()
        _searchSuggestionsEnabled.value = enabled
    }

    private fun readQuality(key: String): YouTubeMusicAudioQuality =
        settings.getString(key, null)
            ?.let { name -> YouTubeMusicAudioQuality.entries.firstOrNull { it.name == name } }
            ?: YouTubeMusicAudioQuality.HIGH

    val isLoggedIn: Boolean
        get() = _session.value.isLoggedIn

    val accountName: String?
        get() = _session.value.accountName.takeIf { it.isNotBlank() }

    /** Bitrate of the stream picked for playback, once it has been resolved. */
    fun knownStreamBitrate(videoId: String): Int? =
        streamCache["$videoId:${streamQuality.value}:false"]?.stream?.format?.bitrate?.takeIf { it > 0 }

    /** A stream URL was refused mid-playback: rest its client so the next resolve picks another. */
    fun reportStreamRejected(videoId: String) {
        streamCache.keys.removeIf { it.startsWith("$videoId:") }
        streamPlayback.remove(videoId)?.let { markClientFailed(it.clientName) }
    }

    fun saveSession(session: YouTubeMusicSession) {
        val sanitized = session.copy(cookie = YouTubeMusicAuth.mergeCookies(session.cookie))
        if (!sanitized.isLoggedIn) {
            throw IllegalStateException("Sign in on YouTube Music first, then tap Done")
        }
        prefs.edit()
            .putString(KEY_COOKIE, sanitized.cookie)
            .putString(KEY_VISITOR, sanitized.visitorData)
            .putString(KEY_SYNC, sanitized.dataSyncId)
            .putString(KEY_PO, sanitized.poToken)
            .putString(KEY_NAME, sanitized.accountName)
            .putString(KEY_EMAIL, sanitized.accountEmail)
            .apply()
        _session.value = sanitized
    }

    fun logout() {
        prefs.edit().clear().apply()
        streamPlayback.clear()
        streamCache.clear()
        _session.value = YouTubeMusicSession()
    }

    suspend fun home(): YouTubeMusicBrowsePage =
        YouTubeMusicParsers.parseBrowse(api.browse(readySession(), HOME))

    suspend fun library(): YouTubeMusicBrowsePage =
        YouTubeMusicParsers.parseBrowse(api.browse(readySession(), LIBRARY))

    /**
     * YouTube Music results, then plain YouTube videos that the music catalog does not
     * have, so covers, live versions and uploads can be found too.
     */
    suspend fun search(query: String): YouTubeMusicBrowsePage = coroutineScope {
        val session = readySession()
        val videos = async { runCatching { YouTubeMusicParsers.parseVideoSearch(api.searchVideos(session, query)) } }
        val music = runCatching { YouTubeMusicParsers.parseBrowse(api.search(session, query)) }
        val videoTracks = videos.await().getOrDefault(emptyList())
        // Either half is enough to show results; fail only when both do.
        val page = music.getOrElse { error -> if (videoTracks.isEmpty()) throw error else YouTubeMusicBrowsePage(shelves = emptyList()) }
        val known = page.shelves.flatMap { shelf -> shelf.tracks.map { it.videoId } }.toSet()
        val extra = videoTracks.filterNot { it.videoId in known }
        if (extra.isEmpty()) page else page.copy(shelves = page.shelves + YouTubeMusicShelf(title = "YouTube videos", tracks = extra))
    }

    suspend fun playlist(playlistId: String): YouTubeMusicBrowsePage {
        val browseId = if (playlistId.startsWith("VL") || playlistId.startsWith("MP")) playlistId else "VL$playlistId"
        val tracks = linkedMapOf<String, YouTubeMusicTrack>()
        var continuation: String? = null
        var page = YouTubeMusicParsers.parseBrowse(api.browse(readySession(), browseId))
        repeat(MAX_CONTINUATIONS + 1) {
            page.shelves.flatMap { it.tracks }.forEach { track -> tracks[track.videoId] = track }
            continuation = page.continuation
            if (continuation.isNullOrBlank()) return@repeat
            page = YouTubeMusicParsers.parseBrowse(
                api.browse(readySession(), browseId = "", continuation = continuation)
            )
        }
        return YouTubeMusicBrowsePage(
            shelves = listOf(YouTubeMusicShelf(title = "", tracks = tracks.values.toList())),
        )
    }

    /** YouTube's radio for a song: about 50 similar songs and the token for more. */
    suspend fun radio(videoId: String): YouTubeMusicRadioPage =
        YouTubeMusicParsers.parseRadio(api.next(readySession(), videoId))

    suspend fun radioMore(continuation: String): YouTubeMusicRadioPage =
        YouTubeMusicParsers.parseRadio(api.next(readySession(), videoId = null, continuation = continuation))

    suspend fun searchSuggestions(input: String): List<String> =
        if (!searchSuggestionsEnabled.value) emptyList()
        else YouTubeMusicParsers.parseSuggestions(api.searchSuggestions(readySession(), input))

    /** Plain lyrics for a song, or null when YouTube has none. */
    suspend fun lyrics(videoId: String): YouTubeMusicLyrics? {
        val browseId = radio(videoId).lyricsBrowseId ?: return null
        return YouTubeMusicParsers.parseLyrics(api.browse(readySession(), browseId))
    }

    /** Artist page: top songs, albums, singles, videos, playlists and similar artists. */
    suspend fun artist(browseId: String): YouTubeMusicBrowsePage {
        val raw = api.browse(readySession(), browseId)
        return YouTubeMusicParsers.parseBrowse(raw).copy(title = YouTubeMusicParsers.parsePageTitle(raw))
    }

    /** Album tracks; album rows leave out the artist and cover, so they come from the header. */
    suspend fun album(browseId: String): YouTubeMusicBrowsePage {
        val raw = api.browse(readySession(), browseId)
        val (title, artist, cover) = YouTubeMusicParsers.parseAlbumHeader(raw)
        val tracks = YouTubeMusicParsers.parseBrowse(raw).shelves.flatMap { it.tracks }.distinctBy { it.videoId }.map { track ->
            track.copy(
                artist = track.artist.ifBlank { artist.orEmpty() },
                album = track.album.ifBlank { title.orEmpty() },
                thumbnailUrl = cover ?: track.thumbnailUrl,
                albumId = track.albumId ?: browseId,
            )
        }
        return YouTubeMusicBrowsePage(shelves = listOf(YouTubeMusicShelf(title = "", tracks = tracks)), title = title)
    }

    /** Sends a like or unlike to the signed-in account; does nothing when signed out. */
    suspend fun setLiked(videoId: String, liked: Boolean): Boolean {
        val session = readySession()
        if (!session.isLoggedIn || !YouTubeMusicFormats.isValidVideoId(videoId)) return false
        return runCatching { api.setLiked(session, videoId, liked) }.isSuccess
    }

    /** A single track for a pasted link, so any YouTube video can be played or saved as audio. */
    suspend fun trackPage(videoId: String): YouTubeMusicBrowsePage {
        val payload = YouTubeMusicParsers.parsePlayer(
            api.player(readySession(), YouTubeMusicClients.STREAM.first(), videoId)
        )
        if (payload.status != "OK") throw IllegalStateException(payload.reason ?: "This video is not available")
        val track = YouTubeMusicTrack(
            videoId = videoId,
            title = payload.title ?: videoId,
            artist = payload.author.orEmpty().removeSuffix(" - Topic"),
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            durationMs = payload.durationMs ?: 0L,
        )
        return YouTubeMusicBrowsePage(shelves = listOf(YouTubeMusicShelf(title = "", tracks = listOf(track))))
    }

    /** Adaptive HLS video (with audio) for the video player. */
    suspend fun resolveVideo(videoId: String): YouTubeMusicVideo {
        if (!YouTubeMusicFormats.isValidVideoId(videoId)) throw IllegalArgumentException("Invalid YouTube video id")
        val session = readySession()
        var lastError = "This video is not available"
        for (client in YouTubeMusicClients.STREAM) {
            val payload = runCatching { YouTubeMusicParsers.parsePlayer(api.player(session, client, videoId)) }
                .getOrElse { error ->
                    lastError = error.message ?: lastError
                    continue
                }
            if (payload.status != "OK") {
                lastError = payload.reason ?: payload.status
                continue
            }
            payload.hlsManifestUrl?.let { return YouTubeMusicVideo(url = it, userAgent = client.userAgent) }
        }
        throw IllegalStateException(lastError)
    }

    /**
     * The player, the proxy and the next-song pre-loader all ask for the same stream,
     * often at once. Share one lookup per song and reuse it until shortly before the
     * URL expires (about six hours).
     */
    suspend fun resolveStream(
        videoId: String,
        quality: YouTubeMusicAudioQuality = streamQuality.value,
        opusOnly: Boolean = false,
    ): YouTubeMusicStream {
        if (!YouTubeMusicFormats.isValidVideoId(videoId)) {
            throw IllegalArgumentException("Invalid YouTube video id")
        }
        val key = "$videoId:$quality:$opusOnly"
        streamCache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.stream }
        val lookup = inFlightStreams.computeIfAbsent(key) { scope.async { fetchStream(videoId, quality, opusOnly) } }
        try {
            val stream = lookup.await()
            val usableForMs = (stream.expiresInSeconds * 1000L - STREAM_EXPIRY_MARGIN_MS).coerceAtLeast(0L)
            streamCache[key] = CachedStream(stream, System.currentTimeMillis() + usableForMs)
            return stream
        } finally {
            inFlightStreams.remove(key, lookup)
        }
    }

    private suspend fun fetchStream(
        videoId: String,
        quality: YouTubeMusicAudioQuality,
        opusOnly: Boolean,
    ): YouTubeMusicStream {
        val session = readySession()
        var lastError = "YouTube Music did not return a playable stream"
        val now = System.currentTimeMillis()
        val clients = YouTubeMusicClients.STREAM.filter { (failedClientsUntil[it.clientName] ?: 0L) <= now }
            .ifEmpty { YouTubeMusicClients.STREAM }
        for (client in clients) {
            val payload = runCatching {
                YouTubeMusicParsers.parsePlayer(api.player(session, client, videoId))
            }.getOrElse { error ->
                lastError = error.message ?: lastError
                continue
            }
            if (payload.status != "OK") {
                lastError = payload.reason ?: payload.status
                if (isBotCheck(lastError)) markClientFailed(client.clientName)
                continue
            }
            val formats = YouTubeMusicFormats.ranked(payload.formats, payload.durationMs, quality.maxBitrate, opusOnly)
            for (format in formats.take(3)) {
                val url = YouTubeMusicFormats.resolveUrl(format) ?: continue
                if (client.probeBeforeUse && !api.probeStream(url, client.userAgent, format.contentLength)) {
                    // Every format of a client fails the same way, so move on to the next client.
                    lastError = "YouTube Music refused the stream"
                    markClientFailed(client.clientName)
                    break
                }
                val origin = if (client.useYoutubeHost) {
                    YouTubeMusicClients.YOUTUBE_ORIGIN
                } else {
                    YouTubeMusicClients.ORIGIN
                }
                streamPlayback[videoId] = StreamPlayback(client.clientName, client.userAgent, origin)
                Timber.d("Resolved %s with %s itag %d in %d ms", videoId, client.clientName, format.itag, System.currentTimeMillis() - now)
                return YouTubeMusicStream(
                    videoId = videoId,
                    url = url,
                    format = format,
                    expiresInSeconds = payload.expiresInSeconds ?: DEFAULT_EXPIRY_SECONDS,
                    userAgent = client.userAgent,
                    origin = origin,
                    durationMs = payload.durationMs ?: format.approxDurationMs ?: 0L,
                )
            }
        }
        throw IllegalStateException(lastError)
    }

    private suspend fun readySession(): YouTubeMusicSession {
        val current = _session.value
        if (current.visitorData.isNotBlank()) return current
        val visitor = runCatching { api.fetchVisitorData(current.cookie) }.getOrNull().orEmpty()
        if (visitor.isBlank()) return current
        val updated = current.copy(visitorData = visitor)
        prefs.edit().putString(KEY_VISITOR, visitor).apply()
        _session.value = updated
        return updated
    }

    private fun migrateLegacySession(context: Context, target: SharedPreferences) {
        if (target.getString(KEY_COOKIE, "").orEmpty().isNotBlank()) return
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val cookie = legacy.getString(KEY_COOKIE, "").orEmpty()
        if (cookie.isBlank()) return
        target.edit()
            .putString(KEY_COOKIE, cookie)
            .putString(KEY_VISITOR, legacy.getString(KEY_VISITOR, "").orEmpty())
            .putString(KEY_SYNC, legacy.getString(KEY_SYNC, "").orEmpty())
            .putString(KEY_PO, legacy.getString(KEY_PO, "").orEmpty())
            .putString(KEY_NAME, legacy.getString(KEY_NAME, "").orEmpty())
            .putString(KEY_EMAIL, legacy.getString(KEY_EMAIL, "").orEmpty())
            .apply()
        legacy.edit().clear().apply()
    }

    private fun readSession(): YouTubeMusicSession = YouTubeMusicSession(
        cookie = prefs.getString(KEY_COOKIE, "").orEmpty(),
        visitorData = prefs.getString(KEY_VISITOR, "").orEmpty(),
        dataSyncId = prefs.getString(KEY_SYNC, "").orEmpty(),
        poToken = prefs.getString(KEY_PO, "").orEmpty(),
        accountName = prefs.getString(KEY_NAME, "").orEmpty(),
        accountEmail = prefs.getString(KEY_EMAIL, "").orEmpty(),
    )

    private fun markClientFailed(clientName: String) {
        failedClientsUntil[clientName] = System.currentTimeMillis() + CLIENT_BACKOFF_MS
    }

    private fun isBotCheck(reason: String): Boolean {
        val lower = reason.lowercase()
        return "bot" in lower || "sign in" in lower
    }

    private class CachedStream(val stream: YouTubeMusicStream, val expiresAtMs: Long)

    private data class StreamPlayback(
        val clientName: String,
        val userAgent: String,
        val origin: String,
    )

    private companion object {
        const val PREFS = "ytmusic_prefs"
        const val LEGACY_PREFS = "ytmusic_session"
        const val KEY_COOKIE = "cookie"
        const val KEY_VISITOR = "visitor"
        const val KEY_SYNC = "data_sync"
        const val KEY_PO = "po_token"
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
        const val HOME = "FEmusic_home"
        const val LIBRARY = "FEmusic_liked_playlists"
        const val DEFAULT_EXPIRY_SECONDS = 6 * 60 * 60
        const val MAX_CONTINUATIONS = 20
        const val CLIENT_BACKOFF_MS = 10L * 60 * 1000
        const val SETTINGS_PREFS = "ytmusic_settings"
        const val KEY_STREAM_QUALITY = "stream_quality"
        const val KEY_DOWNLOAD_QUALITY = "download_quality"
        const val KEY_SEARCH_SUGGESTIONS = "search_suggestions"
        const val STREAM_EXPIRY_MARGIN_MS = 30L * 60 * 1000
    }
}
