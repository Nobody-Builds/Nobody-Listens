package com.nobodybuilds.listens.data.jiosaavn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** JioSaavn's web API (`api.php?__call=…`), the same calls jiosaavn.com makes. Returns raw JSON. */
class JioSaavnApi(okHttpClient: OkHttpClient) {
    private val http = JioSaavnClients.http(okHttpClient).newBuilder()
        .callTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun launchData(session: JioSaavnSession): String = call(session, "webapi.getLaunchData")

    suspend fun searchSongs(session: JioSaavnSession, query: String): String =
        call(session, "search.getResults", "q" to query, "p" to "1", "n" to "20")

    suspend fun searchAlbums(session: JioSaavnSession, query: String): String =
        call(session, "search.getAlbumResults", "q" to query, "p" to "1", "n" to "10")

    suspend fun searchPlaylists(session: JioSaavnSession, query: String): String =
        call(session, "search.getPlaylistResults", "q" to query, "p" to "1", "n" to "10")

    suspend fun searchArtists(session: JioSaavnSession, query: String): String =
        call(session, "search.getArtistResults", "q" to query, "p" to "1", "n" to "5")

    suspend fun autocomplete(session: JioSaavnSession, query: String): String =
        call(session, "autocomplete.get", "query" to query)

    suspend fun songDetails(session: JioSaavnSession, ids: List<String>): String =
        call(session, "song.getDetails", "pids" to ids.joinToString(","))

    suspend fun album(session: JioSaavnSession, albumId: String): String =
        call(session, "content.getAlbumDetails", "albumid" to albumId)

    suspend fun playlist(session: JioSaavnSession, playlistId: String, page: Int): String =
        call(session, "playlist.getDetails", "listid" to playlistId, "n" to PAGE_SIZE.toString(), "p" to page.toString())

    suspend fun artist(session: JioSaavnSession, artistId: String): String =
        call(session, "artist.getArtistPageDetails", "artistId" to artistId, "n_song" to "20", "n_album" to "20")

    /** A song, album or playlist from the token at the end of a jiosaavn.com link. */
    suspend fun byLinkToken(session: JioSaavnSession, token: String, type: String): String =
        call(session, "webapi.get", "token" to token, "type" to type, "n" to PAGE_SIZE.toString(), "p" to "1")

    /** Radio stations exist only for the app context. */
    suspend fun createStation(session: JioSaavnSession, songId: String): String =
        call(session, "webradio.createEntityStation", "entity_id" to "[\"$songId\"]", "entity_type" to "queue", ctx = APP_CTX)

    suspend fun stationSongs(session: JioSaavnSession, stationId: String, count: Int): String =
        call(session, "webradio.getSong", "stationid" to stationId, "k" to count.toString(), "next" to "1", ctx = APP_CTX)

    suspend fun lyrics(session: JioSaavnSession, songId: String): String =
        call(session, "lyrics.getLyrics", "lyrics_id" to songId)

    /** A signed, expiring URL for [encryptedUrl] at the asked bitrate, or the best the account may play. */
    suspend fun authToken(session: JioSaavnSession, encryptedUrl: String, kbps: Int, songId: String): String =
        call(session, "song.generateAuthToken", "url" to encryptedUrl, "bitrate" to kbps.toString(), "pid" to songId)

    suspend fun userDetails(session: JioSaavnSession): String = call(session, "user.getDetails")

    suspend fun library(session: JioSaavnSession): String = call(session, "library.getAll")

    /** Titles and covers of saved playlists or albums, by id. */
    suspend fun libraryDetails(session: JioSaavnSession, ids: List<String>, type: String): String =
        call(session, "library.getDetails", "entity_ids" to ids.joinToString(","), "entity_type" to type, "n" to ids.size.toString())

    /** Likes (adds to library) or unlikes a song on the signed-in account. */
    suspend fun setLiked(session: JioSaavnSession, songId: String, liked: Boolean): String =
        call(session, if (liked) "library.add" else "library.delete", "entity_ids" to songId, "entity_type" to "song")

    private suspend fun call(
        session: JioSaavnSession,
        name: String,
        vararg params: Pair<String, String>,
        ctx: String = WEB_CTX,
    ): String = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl().newBuilder()
            .addQueryParameter("__call", name)
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .addQueryParameter("_format", "json")
            .addQueryParameter("_marker", "0")
            .addQueryParameter("api_version", "4")
            .addQueryParameter("ctx", ctx)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", JioSaavnClients.WEB_USER_AGENT)
            .header("Accept", "application/json")
            .header("Referer", "${JioSaavnClients.ORIGIN}/")
            .apply { if (session.cookie.isNotBlank()) header("Cookie", session.cookie) }
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw IOException("JioSaavn $name failed (${response.code})")
            // Error replies are a few dozen bytes; skip parsing big replies twice.
            if (text.length < MAX_ERROR_LENGTH) JioSaavnParsers.errorOf(text)?.let { (code, message) ->
                throw if (code == "LOGIN_REQUIRED") JioSaavnLoginRequired() else IOException(message ?: "JioSaavn $name failed")
            }
            text
        }
    }

    private companion object {
        const val API_URL = "https://www.jiosaavn.com/api.php"
        const val WEB_CTX = "web6dot0"
        const val APP_CTX = "android"
        const val PAGE_SIZE = 100
        const val MAX_ERROR_LENGTH = 1024
    }
}

/** The call needs a signed-in account, and the session is missing or has expired. */
class JioSaavnLoginRequired : IOException("Sign in to JioSaavn first")
