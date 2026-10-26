package com.nobodybuilds.listens.data.jiosaavn

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
import kotlinx.coroutines.awaitAll
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
class JioSaavnRepository @Inject constructor(
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
        Timber.e(e, "JioSaavnRepository: Failed to create EncryptedSharedPreferences, falling back to plain")
        context.getSharedPreferences("${PREFS}_plain", Context.MODE_PRIVATE)
    }
    private val api = JioSaavnApi(okHttpClient)
    private val media = ConcurrentHashMap<String, JioSaavnMedia>()
    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val inFlightStreams = ConcurrentHashMap<String, Deferred<JioSaavnStream>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _session = MutableStateFlow(readSession())

    val session: StateFlow<JioSaavnSession> = _session.asStateFlow()

    // Quality settings are not secrets, so they live outside the encrypted session store.
    private val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val _streamQuality = MutableStateFlow(readQuality(KEY_STREAM_QUALITY))
    private val _downloadQuality = MutableStateFlow(readQuality(KEY_DOWNLOAD_QUALITY))

    val streamQuality: StateFlow<JioSaavnAudioQuality> = _streamQuality.asStateFlow()
    val downloadQuality: StateFlow<JioSaavnAudioQuality> = _downloadQuality.asStateFlow()

    fun setStreamQuality(quality: JioSaavnAudioQuality) {
        settings.edit().putString(KEY_STREAM_QUALITY, quality.name).apply()
        _streamQuality.value = quality
    }

    fun setDownloadQuality(quality: JioSaavnAudioQuality) {
        settings.edit().putString(KEY_DOWNLOAD_QUALITY, quality.name).apply()
        _downloadQuality.value = quality
    }

    private fun readQuality(key: String): JioSaavnAudioQuality =
        settings.getString(key, null)
            ?.let { name -> JioSaavnAudioQuality.entries.firstOrNull { it.name == name } }
            ?: JioSaavnAudioQuality.HIGH

    val isLoggedIn: Boolean
        get() = _session.value.isLoggedIn

    val accountName: String?
        get() = _session.value.accountName.takeIf { it.isNotBlank() }

    /** Bitrate of the stream picked for playback, once it has been resolved. */
    fun knownStreamBitrate(songId: String): Int? =
        streamCache["$songId:${streamQuality.value}"]?.stream?.bitrate?.takeIf { it > 0 }

    /** A signed stream URL was refused (usually expired): drop it so the next resolve signs a new one. */
    fun reportStreamRejected(songId: String) {
        streamCache.keys.removeIf { it.startsWith("$songId:") }
    }

    /**
     * Keeps the jiosaavn.com cookies once JioSaavn accepts them for the account library,
     * so a half-finished sign-in is never saved.
     */
    suspend fun signIn(cookie: String) {
        val candidate = JioSaavnSession(cookie = cookie)
        try {
            api.library(candidate)
        } catch (_: JioSaavnLoginRequired) {
            throw IllegalStateException("Sign in on JioSaavn first, then tap Done")
        }
        val name = runCatching { JioSaavnParsers.parseAccountName(api.userDetails(candidate)) }.getOrNull().orEmpty()
        prefs.edit()
            .putString(KEY_COOKIE, cookie)
            .putString(KEY_NAME, name)
            .apply()
        streamCache.clear()
        _session.value = JioSaavnSession(cookie = cookie, accountName = name)
    }

    fun logout() {
        prefs.edit().clear().apply()
        streamCache.clear()
        _session.value = JioSaavnSession()
    }

    suspend fun home(): List<JioSaavnShelf> = JioSaavnParsers.parseLaunch(api.launchData(session.value))

    /** Songs, albums, playlists and artists; fails only when every part does. */
    suspend fun search(query: String): List<JioSaavnShelf> = coroutineScope {
        val current = session.value
        val songs = async { runCatching { JioSaavnParsers.parseSearchSongs(api.searchSongs(current, query)) } }
        val albums = async { runCatching { JioSaavnParsers.parseSearchCollections(api.searchAlbums(current, query), JioSaavnItemKind.ALBUM) } }
        val playlists = async { runCatching { JioSaavnParsers.parseSearchCollections(api.searchPlaylists(current, query), JioSaavnItemKind.PLAYLIST) } }
        val artists = async { runCatching { JioSaavnParsers.parseSearchCollections(api.searchArtists(current, query), JioSaavnItemKind.ARTIST) } }
        val results = listOf(songs, albums, playlists, artists).awaitAll()
        if (results.all { it.isFailure }) results.first().getOrThrow()
        listOfNotNull(
            songs.await().getOrNull()?.takeIf { it.isNotEmpty() }?.let { JioSaavnShelf(title = "Songs", tracks = it) },
            artists.await().getOrNull()?.takeIf { it.isNotEmpty() }?.let { JioSaavnShelf(title = "Artists", playlists = it) },
            albums.await().getOrNull()?.takeIf { it.isNotEmpty() }?.let { JioSaavnShelf(title = "Albums", playlists = it) },
            playlists.await().getOrNull()?.takeIf { it.isNotEmpty() }?.let { JioSaavnShelf(title = "Playlists", playlists = it) },
        )
    }

    /** A pasted jiosaavn.com song, album or playlist link, as a page. */
    suspend fun linkPage(type: String, token: String): JioSaavnPage {
        val raw = api.byLinkToken(session.value, token, type)
        return when (type) {
            "song" -> JioSaavnPage(shelves = listOf(JioSaavnShelf(title = "", tracks = JioSaavnParsers.parseSongs(raw))))
            "album" -> JioSaavnParsers.parseAlbum(raw)
            else -> JioSaavnParsers.parsePlaylist(raw)
        }
    }

    suspend fun searchSuggestions(input: String): List<String> =
        JioSaavnParsers.parseSuggestions(api.autocomplete(session.value, input))

    suspend fun album(albumId: String): JioSaavnPage = JioSaavnParsers.parseAlbum(api.album(session.value, albumId))

    suspend fun artist(artistId: String): JioSaavnPage = JioSaavnParsers.parseArtist(api.artist(session.value, artistId))

    /** Every song of a playlist; JioSaavn hands them out a page at a time. */
    suspend fun playlist(playlistId: String): JioSaavnPage {
        if (playlistId == LIKED_SONGS_ID) return likedSongs()
        val tracks = linkedMapOf<String, JioSaavnTrack>()
        var first: JioSaavnPage? = null
        for (page in 1..MAX_PLAYLIST_PAGES) {
            val result = JioSaavnParsers.parsePlaylist(api.playlist(session.value, playlistId, page))
            if (first == null) first = result
            val fresh = result.shelves.flatMap { it.tracks }.filterNot { it.id in tracks }
            fresh.forEach { tracks[it.id] = it }
            val total = first.totalSongs ?: break
            if (fresh.isEmpty() || tracks.size >= total) break
        }
        return JioSaavnPage(shelves = listOf(JioSaavnShelf(title = "", tracks = tracks.values.toList())), title = first?.title)
    }

    /** The signed-in account's liked songs and saved playlists and albums. */
    suspend fun library(): List<JioSaavnShelf> {
        val current = session.value
        val ids = JioSaavnParsers.parseLibraryIds(api.library(current))
        val playlists = ids["playlist"].orEmpty().takeIf { it.isNotEmpty() }?.let { list ->
            runCatching {
                JioSaavnParsers.parseAnyCollections(api.libraryDetails(current, list, "playlist"), JioSaavnItemKind.PLAYLIST)
            }.getOrDefault(emptyList())
        }.orEmpty()
        val albums = ids["album"].orEmpty().takeIf { it.isNotEmpty() }?.let { list ->
            runCatching {
                JioSaavnParsers.parseAnyCollections(api.libraryDetails(current, list, "album"), JioSaavnItemKind.ALBUM)
            }.getOrDefault(emptyList())
        }.orEmpty()
        val likedCount = ids["song"].orEmpty().size
        val liked = JioSaavnCollection(
            id = LIKED_SONGS_ID,
            title = "Liked songs",
            subtitle = "$likedCount songs",
            kind = JioSaavnItemKind.PLAYLIST,
        ).takeIf { likedCount > 0 }
        return listOfNotNull(
            JioSaavnShelf(title = "Playlists", playlists = listOfNotNull(liked) + playlists).takeIf { it.playlists.isNotEmpty() },
            JioSaavnShelf(title = "Albums", playlists = albums).takeIf { albums.isNotEmpty() },
        )
    }

    private suspend fun likedSongs(): JioSaavnPage {
        val ids = JioSaavnParsers.parseLibraryIds(api.library(session.value))["song"].orEmpty()
        val tracks = ids.chunked(SONG_DETAILS_BATCH).flatMap { batch ->
            JioSaavnParsers.parseSongs(api.songDetails(session.value, batch))
        }
        return JioSaavnPage(shelves = listOf(JioSaavnShelf(title = "", tracks = tracks)), title = "Liked songs")
    }

    /** Starts a radio station from a song; returns its id and the first songs. */
    suspend fun startRadio(songId: String): Pair<String, List<JioSaavnTrack>> {
        val stationId = JioSaavnParsers.parseStationId(api.createStation(session.value, songId))
            ?: throw IllegalStateException("JioSaavn has no radio for this song")
        return stationId to radioMore(stationId)
    }

    suspend fun radioMore(stationId: String): List<JioSaavnTrack> =
        JioSaavnParsers.parseStationSongs(api.stationSongs(session.value, stationId, RADIO_BATCH))

    /** Plain lyrics for a song, or null when JioSaavn has none. */
    suspend fun lyrics(songId: String): JioSaavnLyrics? =
        runCatching { JioSaavnParsers.parseLyrics(api.lyrics(session.value, songId)) }.getOrNull()

    /** Sends a like or unlike to the signed-in account; does nothing when signed out. */
    suspend fun setLiked(songId: String, liked: Boolean): Boolean {
        val current = session.value
        if (!current.isLoggedIn) return false
        return runCatching { api.setLiked(current, songId, liked) }.isSuccess
    }

    /**
     * The player, the proxy and the next-song pre-loader all ask for the same stream,
     * often at once. Share one lookup per song and reuse it for a while.
     */
    suspend fun resolveStream(songId: String, quality: JioSaavnAudioQuality = streamQuality.value): JioSaavnStream {
        val key = "$songId:$quality"
        streamCache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return it.stream }
        val lookup = inFlightStreams.computeIfAbsent(key) { scope.async { fetchStream(songId, quality) } }
        try {
            val stream = lookup.await()
            streamCache[key] = CachedStream(stream, System.currentTimeMillis() + STREAM_TTL_MS)
            return stream
        } finally {
            inFlightStreams.remove(key, lookup)
        }
    }

    private suspend fun fetchStream(songId: String, quality: JioSaavnAudioQuality): JioSaavnStream {
        val started = System.currentTimeMillis()
        val details = media[songId] ?: JioSaavnParsers.parseMedia(api.songDetails(session.value, listOf(songId)))[songId]
            ?.also { media[songId] = it }
            ?: throw IllegalStateException("This song is not on JioSaavn")
        if (details.drmOnly) throw IllegalStateException("Only the JioSaavn app can play this song")
        if (details.encryptedUrl.isBlank()) throw IllegalStateException("JioSaavn did not return a playable stream")
        // Asking for 320 kbps where a song has none gets nothing, so ask for the best it has.
        val kbps = if (quality.kbps > FREE_KBPS && !details.has320) FREE_KBPS else quality.kbps
        val token: suspend (Int) -> JioSaavnStream? = { asked ->
            runCatching { JioSaavnParsers.parseAuthToken(api.authToken(session.value, details.encryptedUrl, asked, songId), songId) }
                .onFailure { Timber.w(it, "JioSaavn refused %d kbps for %s", asked, songId) }
                .getOrNull()
        }
        // 320 kbps is for Pro accounts; anyone else gets the best JioSaavn allows them.
        val stream = token(kbps) ?: (if (kbps > FREE_KBPS) token(FREE_KBPS) else null)
            ?: throw IllegalStateException("JioSaavn did not return a playable stream")
        Timber.d("Resolved JioSaavn %s at %d kbps (asked %d) in %d ms", songId, stream.bitrate / 1000, kbps, System.currentTimeMillis() - started)
        return stream
    }

    private fun readSession(): JioSaavnSession = JioSaavnSession(
        cookie = prefs.getString(KEY_COOKIE, "").orEmpty(),
        accountName = prefs.getString(KEY_NAME, "").orEmpty(),
    )

    private class CachedStream(val stream: JioSaavnStream, val expiresAtMs: Long)

    companion object {
        /** The account's liked songs, shown as a playlist in the library. */
        const val LIKED_SONGS_ID = "liked"
        private const val PREFS = "jiosaavn_prefs"
        private const val KEY_COOKIE = "cookie"
        private const val KEY_NAME = "name"
        private const val SETTINGS_PREFS = "jiosaavn_settings"
        private const val KEY_STREAM_QUALITY = "stream_quality"
        private const val KEY_DOWNLOAD_QUALITY = "download_quality"
        private const val MAX_PLAYLIST_PAGES = 20
        private const val SONG_DETAILS_BATCH = 50
        private const val RADIO_BATCH = 20
        private const val FREE_KBPS = 160
        // Signed stream URLs are short-lived; a refused one is fetched again by the proxy.
        private const val STREAM_TTL_MS = 20L * 60 * 1000
    }
}
