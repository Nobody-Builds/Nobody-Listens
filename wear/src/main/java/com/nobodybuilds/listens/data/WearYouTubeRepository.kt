package com.nobodybuilds.listens.data

import android.app.Application
import android.content.Context
import android.net.Uri
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicApi
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicAudioQuality
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicParsers
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicSession
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicStream
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicStreamFinder
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicTrack
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music straight from the watch over its own Wi-Fi or LTE, so it works with no phone.
 * Signed out (search and play need no account); streams at Normal quality to spare the battery, or Data saver
 * over the phone's Bluetooth proxy.
 */
@Singleton
class WearYouTubeRepository @Inject constructor(
    application: Application,
    private val network: WearStreamingNetwork,
) {

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val api = YouTubeMusicApi(
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
    )
    private val streamFinder = YouTubeMusicStreamFinder(api)
    private val streams = ConcurrentHashMap<String, CachedStream>()

    @Volatile
    private var session = YouTubeMusicSession(visitorData = prefs.getString(KEY_VISITOR, "").orEmpty())

    suspend fun search(query: String): List<YouTubeMusicTrack> =
        YouTubeMusicParsers.parseBrowse(api.search(readySession(), query))
            .shelves.flatMap { it.tracks }
            .distinctBy { it.videoId }

    /** A playable URL for the song, reused until 30 minutes before it expires. */
    suspend fun resolveStream(videoId: String): YouTubeMusicStream {
        streams[videoId]?.takeIf { it.usableUntilMs > System.currentTimeMillis() }?.let { return it.stream }
        val quality = if (network.isFast) YouTubeMusicAudioQuality.NORMAL else YouTubeMusicAudioQuality.DATA_SAVER
        val stream = streamFinder.find(readySession(), videoId, quality)
        val usableForMs = (stream.expiresInSeconds * 1000L - EXPIRY_MARGIN_MS).coerceAtLeast(0L)
        streams[videoId] = CachedStream(stream, System.currentTimeMillis() + usableForMs)
        return stream
    }

    /** YouTube refused a URL mid-song: forget it and rest its client. */
    fun reportStreamRejected(videoId: String) {
        streams.remove(videoId)?.let { streamFinder.markClientFailed(it.stream.clientName) }
    }

    private suspend fun readySession(): YouTubeMusicSession {
        if (session.visitorData.isNotBlank()) return session
        val visitor = runCatching { api.fetchVisitorData(cookie = "") }.getOrNull().orEmpty()
        if (visitor.isNotBlank()) {
            prefs.edit().putString(KEY_VISITOR, visitor).apply()
            session = session.copy(visitorData = visitor)
        }
        return session
    }

    private class CachedStream(val stream: YouTubeMusicStream, val usableUntilMs: Long)

    companion object {
        private const val PREFS = "ytmusic_watch"
        private const val KEY_VISITOR = "visitor"
        private const val EXPIRY_MARGIN_MS = 30L * 60 * 1000
        private const val SCHEME = "ytmusic"

        /** Queue songs point at `ytmusic://track/<id>`; the player resolves the stream as it loads. */
        fun trackUri(videoId: String): Uri = Uri.parse("$SCHEME://track/$videoId")

        fun videoIdFrom(uri: Uri): String? =
            uri.lastPathSegment?.takeIf { uri.scheme == SCHEME && it.length == VIDEO_ID_LENGTH }

        fun songId(videoId: String): String = "yt:$videoId"

        private const val VIDEO_ID_LENGTH = 11
    }
}
