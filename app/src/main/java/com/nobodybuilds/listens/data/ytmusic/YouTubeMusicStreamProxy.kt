package com.nobodybuilds.listens.data.ytmusic

import android.net.Uri
import com.nobodybuilds.listens.data.stream.CloudStreamProxy
import com.nobodybuilds.listens.data.stream.CloudStreamSecurity
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicStreamProxy @Inject constructor(
    private val repository: YouTubeMusicRepository,
    private val library: YouTubeMusicLibrary,
    okHttpClient: OkHttpClient,
) : CloudStreamProxy<String>(YouTubeMusicClients.http(okHttpClient)) {

    override val allowedHostSuffixes = setOf(
        "googlevideo.com",
        "googleusercontent.com",
        "youtube.com",
        "ytimg.com",
    )
    override val cacheExpirationMs = 5L * 60 * 1000
    // googlevideo stalls whole-file requests that carry no Range header.
    override val alwaysRequestRange = true
    override val proxyTag = "YouTubeMusicStreamProxy"
    override val routePath = "/ytmusic/{videoId}"
    override val routeParamName = "videoId"
    override val uriScheme = YouTubeMusicUris.SCHEME
    override val routePrefix = "/ytmusic"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { CloudStreamSecurity.validateYouTubeVideoId(it) }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateYouTubeVideoId(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? =
        runCatching { repository.resolveStream(id).url }.getOrNull()

    override fun extractIdFromUri(uri: Uri): String? =
        YouTubeMusicUris.videoIdFrom(uri.toString())

    // googlevideo checks the User-Agent of the client that asked for the URL.
    override fun decorateUpstreamRequest(id: String, url: String, builder: Request.Builder) {
        builder.header("User-Agent", YouTubeMusicClients.userAgentForStreamUrl(url))
    }

    override fun onUpstreamRejected(id: String, code: Int): Boolean {
        repository.reportStreamRejected(id)
        return true
    }

    /**
     * A queue restored after the app restarts still holds proxy URLs with the old,
     * dead port. Point them at the proxy that is running now. Blocks briefly, so call
     * it from a loader thread only.
     */
    fun rebindStaleProxyUri(uri: Uri): Uri? {
        if (uri.scheme != "http" || uri.host != "127.0.0.1") return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != routePrefix.removePrefix("/")) return null
        val videoId = segments[1].takeIf { CloudStreamSecurity.validateYouTubeVideoId(it) } ?: return null
        if (!isReady()) runBlocking { ensureReady(5_000L) }
        val current = getProxyUrl(videoId).takeIf { it.isNotEmpty() } ?: return null
        return if (current == uri.toString()) null else Uri.parse(current)
    }

    /**
     * The player cannot read a bitrate from YouTube WebM streams, and without one it
     * re-reads the stream to probe it. Hand it the bitrate the resolver already knows.
     */
    fun knownBitrate(uri: Uri): Int? {
        val videoId = YouTubeMusicUris.videoIdFromPlaybackUri(uri) ?: return null
        return repository.knownStreamBitrate(videoId)
    }

    fun resolveYouTubeMusicUri(uriString: String): String? = resolveUri(uriString)

    suspend fun warmUpStreamUrl(uriString: String) {
        val videoId = YouTubeMusicUris.videoIdFrom(uriString) ?: return
        library.saveIfQueued(videoId)
        runCatching { getOrFetchStreamUrl(videoId) }
        runCatching { library.fillDuration(videoId, repository.resolveStream(videoId).durationMs) }
    }
}
