package com.nobodybuilds.listens.data.ytmusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class YouTubeMusicApi(
    okHttpClient: OkHttpClient,
) {
    private val http = YouTubeMusicClients.http(okHttpClient).newBuilder()
        .callTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun browse(
        session: YouTubeMusicSession,
        browseId: String,
        continuation: String? = null,
    ): String = post(
        session,
        YouTubeMusicClients.METADATA,
        "browse",
        browseBody(session, browseId, continuation),
    )

    suspend fun search(session: YouTubeMusicSession, query: String): String =
        post(session, YouTubeMusicClients.METADATA, "search", searchBody(session, query))

    /** "Up next" for a song, as a radio (RDAMVM playlist), or the next batch of it. */
    suspend fun next(session: YouTubeMusicSession, videoId: String?, continuation: String? = null): String {
        val body = buildJsonObject {
            put("context", clientContext(session, YouTubeMusicClients.METADATA))
            if (continuation != null) {
                put("continuation", continuation)
            } else if (videoId != null) {
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("isAudioOnly", true)
                put("tunerSettingValue", "AUTOMIX_SETTING_NORMAL")
            }
        }.toString()
        return post(session, YouTubeMusicClients.METADATA, "next", body)
    }

    suspend fun searchSuggestions(session: YouTubeMusicSession, input: String): String {
        val body = buildJsonObject {
            put("context", clientContext(session, YouTubeMusicClients.METADATA))
            put("input", input)
        }.toString()
        return post(session, YouTubeMusicClients.METADATA, "music/get_search_suggestions", body)
    }

    /** Like or unlike a song on the signed-in account. */
    suspend fun setLiked(session: YouTubeMusicSession, videoId: String, liked: Boolean): String {
        val body = buildJsonObject {
            put("context", clientContext(session, YouTubeMusicClients.METADATA))
            putJsonObject("target") { put("videoId", videoId) }
        }.toString()
        return post(session, YouTubeMusicClients.METADATA, if (liked) "like/like" else "like/removelike", body)
    }

    /** Plain YouTube search, filtered to videos. */
    suspend fun searchVideos(session: YouTubeMusicSession, query: String): String {
        val body = buildJsonObject {
            put("context", clientContext(session, YouTubeMusicClients.WEB))
            put("query", query)
            put("params", VIDEOS_ONLY)
        }.toString()
        return post(session, YouTubeMusicClients.WEB, "search", body)
    }

    suspend fun player(
        session: YouTubeMusicSession,
        client: YouTubeMusicClient,
        videoId: String,
    ): String = post(session, client, "player", playerBody(session, client, videoId))

    /**
     * Without a PO token YouTube serves the first megabyte and then answers 403,
     * so probe a byte past that point when the file is large enough.
     */
    suspend fun probeStream(url: String, userAgent: String, contentLength: Long?): Boolean =
        withContext(Dispatchers.IO) {
            val offset = YouTubeMusicFormats.probeOffset(contentLength)
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=$offset-${offset + 1}")
                .get()
                .build()
            runCatching {
                http.newCall(request).execute().use { response -> response.code in 200..299 }
            }.getOrDefault(false)
        }

    suspend fun fetchVisitorData(cookie: String): String? = withContext(Dispatchers.IO) {
        listOf(YouTubeMusicClients.ORIGIN, YouTubeMusicClients.YOUTUBE_ORIGIN).firstNotNullOfOrNull { origin ->
            val request = Request.Builder()
                .url(origin)
                .header("User-Agent", YouTubeMusicClients.WEB_USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .apply {
                    if (cookie.isNotBlank()) header("Cookie", cookie)
                }
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                val html = response.body?.string().orEmpty()
                VISITOR_REGEX.find(html)?.groupValues?.getOrNull(1)
            }
        }
    }

    private suspend fun post(
        session: YouTubeMusicSession,
        client: YouTubeMusicClient,
        path: String,
        body: String,
    ): String = withContext(Dispatchers.IO) {
        val origin = if (client.useYoutubeHost) YouTubeMusicClients.YOUTUBE_ORIGIN else YouTubeMusicClients.ORIGIN
        val apiUrl = if (client.useYoutubeHost) YouTubeMusicClients.YOUTUBE_API_URL else YouTubeMusicClients.API_URL
        val request = Request.Builder()
            .url("${apiUrl}$path?prettyPrint=false")
            .header("Content-Type", "application/json")
            .header("User-Agent", client.userAgent)
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", client.clientId)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .header("X-Origin", origin)
            .header("Origin", origin)
            .header("Referer", "$origin/")
            .apply {
                session.visitorData.takeIf { it.isNotBlank() }?.let {
                    header("X-Goog-Visitor-Id", it)
                }
                // The web account cookie only goes to clients that accept cookies.
                if (session.cookie.isNotBlank() && client.loginSupported) {
                    header("Cookie", session.cookie)
                    YouTubeMusicAuth.authorizationHeader(
                        cookie = session.cookie,
                        origin = origin,
                        unixSeconds = System.currentTimeMillis() / 1000,
                    )?.let { header("Authorization", it) }
                }
            }
            .post(body.toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("YouTube Music $path failed (${response.code})")
            }
            text
        }
    }

    private fun clientContext(session: YouTubeMusicSession, client: YouTubeMusicClient) = buildJsonObject {
        val locale = Locale.getDefault()
        putJsonObject("client") {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", locale.toLanguageTag().ifBlank { "en" })
            put("gl", locale.country.ifBlank { "US" })
            client.osName?.let { put("osName", it) }
            client.osVersion?.let { put("osVersion", it) }
            client.deviceMake?.let { put("deviceMake", it) }
            client.deviceModel?.let { put("deviceModel", it) }
            client.androidSdkVersion?.let { put("androidSdkVersion", it) }
            session.visitorData.takeIf { it.isNotBlank() }?.let { put("visitorData", it) }
        }
        putJsonObject("user") {
            put("lockedSafetyMode", false)
            if (client.loginSupported && session.dataSyncId.isNotBlank()) {
                put("onBehalfOfUser", session.dataSyncId)
            }
        }
    }

    private fun browseBody(
        session: YouTubeMusicSession,
        browseId: String,
        continuation: String?,
    ) = buildJsonObject {
        put("context", clientContext(session, YouTubeMusicClients.METADATA))
        if (!continuation.isNullOrBlank()) {
            put("continuation", continuation)
        } else {
            put("browseId", browseId)
        }
    }.toString()

    private fun searchBody(session: YouTubeMusicSession, query: String) = buildJsonObject {
        put("context", clientContext(session, YouTubeMusicClients.METADATA))
        put("query", query)
    }.toString()

    private fun playerBody(
        session: YouTubeMusicSession,
        client: YouTubeMusicClient,
        videoId: String,
    ) = buildJsonObject {
        put("context", clientContext(session, client))
        put("videoId", videoId)
        put("contentCheckOk", true)
        put("racyCheckOk", true)
        session.poToken.takeIf { it.isNotBlank() && client.loginSupported }?.let { token ->
            putJsonObject("serviceIntegrityDimensions") {
                put("poToken", token)
            }
        }
    }.toString()

    private companion object {
        val JSON = "application/json".toMediaType()
        val VISITOR_REGEX = Regex(""""VISITOR_DATA":"([^"]+)"""")
        const val VIDEOS_ONLY = "EgIQAQ%3D%3D"
    }
}
