package com.nobodybuilds.listens.data.jiosaavn

import android.net.Uri
import com.nobodybuilds.listens.data.stream.CloudStreamProxy
import com.nobodybuilds.listens.data.stream.CloudStreamSecurity
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serves JioSaavn songs at `http://127.0.0.1:<port>/jiosaavn/<id>`, so the player keeps
 * a stable URL while the signed stream URL behind it expires and is fetched again.
 */
@Singleton
class JioSaavnStreamProxy @Inject constructor(
    private val repository: JioSaavnRepository,
    private val library: JioSaavnLibrary,
    okHttpClient: OkHttpClient,
) : CloudStreamProxy<String>(JioSaavnClients.http(okHttpClient)) {

    override val allowedHostSuffixes = setOf("saavncdn.com", "jiosaavn.com")
    override val cacheExpirationMs = 5L * 60 * 1000
    override val proxyTag = "JioSaavnStreamProxy"
    override val routePath = "/jiosaavn/{songId}"
    override val routeParamName = "songId"
    override val uriScheme = JioSaavnUris.SCHEME
    override val routePrefix = "/jiosaavn"

    override fun parseRouteParam(value: String): String? =
        value.takeIf { CloudStreamSecurity.validateJioSaavnSongId(it) }

    override fun validateId(id: String): Boolean =
        CloudStreamSecurity.validateJioSaavnSongId(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? =
        runCatching { repository.resolveStream(id).url }.getOrNull()

    override fun extractIdFromUri(uri: Uri): String? =
        JioSaavnUris.songIdFrom(uri.toString())

    override fun decorateUpstreamRequest(id: String, url: String, builder: Request.Builder) {
        JioSaavnClients.decorateMediaRequest(builder)
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
        val songId = segments[1].takeIf { CloudStreamSecurity.validateJioSaavnSongId(it) } ?: return null
        if (!isReady()) runBlocking { ensureReady(5_000L) }
        val current = getProxyUrl(songId).takeIf { it.isNotEmpty() } ?: return null
        return if (current == uri.toString()) null else Uri.parse(current)
    }

    /** The bitrate JioSaavn granted, which the player shows instead of probing the stream. */
    fun knownBitrate(uri: Uri): Int? {
        val songId = JioSaavnUris.songIdFromPlaybackUri(uri) ?: return null
        return repository.knownStreamBitrate(songId)
    }

    fun resolveJioSaavnUri(uriString: String): String? = resolveUri(uriString)

    suspend fun warmUpStreamUrl(uriString: String) {
        val songId = JioSaavnUris.songIdFrom(uriString) ?: return
        library.saveIfQueued(songId)
        runCatching { getOrFetchStreamUrl(songId) }
    }
}
