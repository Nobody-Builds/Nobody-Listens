package com.nobodybuilds.listens.data.stream

import android.net.Uri
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract base class for local HTTP proxy servers that stream cloud music audio.
 *
 * Subclasses define the route, ID type, validation, allowed hosts, and URL resolution.
 * The base class handles the full Ktor CIO server lifecycle, URL caching, and OkHttp
 * proxying with security checks via [CloudStreamSecurity].
 *
 * @param K The song identifier type (e.g. [String] for QQ Music songMid, [Long] for Netease songId)
 */
abstract class CloudStreamProxy<K : Any>(
    private val okHttpClient: OkHttpClient
) {
    // ─── Subclass Configuration ────────────────────────────────────────

    protected abstract val allowedHostSuffixes: Set<String>
    protected abstract val cacheExpirationMs: Long
    protected abstract val proxyTag: String

    /** Route path registered with Ktor, e.g. "/qqmusic/{songMid}" */
    protected abstract val routePath: String
    /** The parameter name inside the route path, e.g. "songMid" */
    protected abstract val routeParamName: String
    /** URI scheme this proxy handles, e.g. "qqmusic" or "netease" */
    protected abstract val uriScheme: String
    /** URL path prefix for proxy URLs, e.g. "/qqmusic" or "/netease" */
    protected abstract val routePrefix: String

    /** Parse the raw route parameter string into the typed ID, or null if invalid */
    protected abstract fun parseRouteParam(value: String): K?
    /** Validate whether the given ID is acceptable */
    protected abstract fun validateId(id: K): Boolean
    /** Convert the ID to a string for use in URLs */
    protected abstract fun formatIdForUrl(id: K): String
    /** Resolve the actual streaming URL for the given song ID */
    protected abstract suspend fun resolveStreamUrl(id: K): String?

    // ─── Server State ──────────────────────────────────────────────────

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var actualPort: Int = 0
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null

    private val urlCache = ConcurrentHashMap<K, CachedUrl>()

    private data class CachedUrl(val url: String, val timestamp: Long, val expirationMs: Long) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > expirationMs
    }

    // ─── Public API ────────────────────────────────────────────────────

    fun isReady(): Boolean = actualPort > 0

    fun startIfNeeded() {
        if (isReady() || startJob?.isActive == true) return
        start()
    }

    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean {
        if (isReady()) return true
        val stepMs = 50L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            if (isReady()) return true
            delay(stepMs)
            elapsed += stepMs
        }
        return false
    }

    suspend fun ensureReady(timeoutMs: Long = 10_000L): Boolean {
        startIfNeeded()
        return awaitReady(timeoutMs)
    }

    fun getProxyUrl(id: K): String {
        if (actualPort == 0) return ""
        if (!validateId(id)) return ""
        return "http://127.0.0.1:$actualPort$routePrefix/${formatIdForUrl(id)}"
    }

    /**
     * Parse a cloud URI (e.g. "qqmusic://xxxx" or "netease://12345") and return
     * the local proxy URL. Returns null if the URI doesn't match this proxy's scheme.
     */
    fun resolveUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != uriScheme) return null
        val rawId = extractIdFromUri(uri) ?: return null
        val id = parseRouteParam(rawId) ?: return null
        if (!validateId(id)) return null
        return getProxyUrl(id)
    }

    fun start() {
        startJob?.cancel()
        startJob = proxyScope.launch {
            try {
                val freePort = ServerSocket(0).use { it.localPort }
                val createdServer = createServer(freePort)
                createdServer.start(wait = false)
                server = createdServer
                actualPort = freePort
                Timber.d("$proxyTag started on port $actualPort")
            } catch (_: CancellationException) {
                Timber.d("$proxyTag start cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start $proxyTag")
            }
        }
    }

    fun stop() {
        startJob?.cancel()
        startJob = null
        proxyScope.coroutineContext.cancelChildren()
        server?.stop(1000, 2000)
        server = null
        actualPort = 0
        urlCache.clear()
        Timber.d("$proxyTag stopped")
    }

    // ─── Overridable Hooks ─────────────────────────────────────────────

    /** Extract the raw ID string from a parsed URI. Override for custom URI layouts. */
    protected open fun extractIdFromUri(uri: Uri): String? = uri.host

    /** Add headers the upstream host requires, such as a client User-Agent. */
    protected open fun decorateUpstreamRequest(id: K, url: String, builder: Request.Builder) = Unit

    /**
     * Called when the upstream refuses a cached URL (403, 410 or 429).
     * Return true to drop the URL and retry once with a freshly resolved one.
     */
    protected open fun onUpstreamRejected(id: K, code: Int): Boolean = false

    /**
     * Some hosts stall requests without a Range header. When true, a request that
     * arrives without one is sent upstream as "bytes=0-" and answered as a plain 200.
     */
    protected open val alwaysRequestRange: Boolean = false

    // ─── Internal ──────────────────────────────────────────────────────

    protected suspend fun getOrFetchStreamUrl(id: K): String? {
        urlCache[id]?.let { cached ->
            if (!cached.isExpired()) return cached.url
        }
        return resolveStreamUrl(id)?.also { url ->
            urlCache[id] = CachedUrl(url, System.currentTimeMillis(), cacheExpirationMs)
        }
    }

    private fun createServer(port: Int): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        return embeddedServer(CIO, port = port, host = "127.0.0.1") {
            routing {
                get(routePath) {
                    val rawParam = call.parameters[routeParamName]
                    val id = rawParam?.let { parseRouteParam(it) }
                    if (id == null || !validateId(id)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                        return@get
                    }

                    try {
                        val rangeValidation = CloudStreamSecurity.validateRangeHeader(
                            call.request.headers["Range"]
                        )
                        if (!rangeValidation.isValid) {
                            call.respond(
                                HttpStatusCode(416, "Range Not Satisfiable"),
                                "Invalid range header"
                            )
                            return@get
                        }

                        val startedAt = System.currentTimeMillis()
                        val forcedRange = alwaysRequestRange && rangeValidation.normalizedHeader == null
                        var response: okhttp3.Response? = null
                        for (attempt in 0..1) {
                            val streamUrl = getOrFetchStreamUrl(id)
                            if (streamUrl.isNullOrBlank()) {
                                call.respond(HttpStatusCode.NotFound, "No stream URL available")
                                return@get
                            }
                            if (!CloudStreamSecurity.isSafeRemoteStreamUrl(
                                    url = streamUrl,
                                    allowedHostSuffixes = allowedHostSuffixes,
                                    allowHttpForAllowedHosts = true
                                )
                            ) {
                                call.respond(HttpStatusCode.BadGateway, "Rejected upstream stream URL")
                                return@get
                            }

                            val requestBuilder = Request.Builder().url(streamUrl)
                            (rangeValidation.normalizedHeader ?: "bytes=0-".takeIf { forcedRange })?.let {
                                requestBuilder.header("Range", it)
                            }
                            decorateUpstreamRequest(id, streamUrl, requestBuilder)

                            val current = withContext(Dispatchers.IO) {
                                okHttpClient.newCall(requestBuilder.build()).execute()
                            }
                            val rejected = current.code == 403 || current.code == 410 || current.code == 429
                            if (attempt == 0 && rejected && onUpstreamRejected(id, current.code)) {
                                current.close()
                                urlCache.remove(id)
                                continue
                            }
                            response = current
                            break
                        }

                        Timber.d(
                            "%s range=%s -> %d in %d ms",
                            proxyTag,
                            rangeValidation.normalizedHeader ?: "none",
                            response?.code ?: -1,
                            System.currentTimeMillis() - startedAt,
                        )
                        checkNotNull(response).use { upstream ->
                            if (upstream.code != 200 && upstream.code != 206) {
                                call.respond(
                                    CloudStreamSecurity.mapUpstreamStatusToProxyStatus(upstream.code),
                                    "Upstream stream request failed"
                                )
                                return@get
                            }

                            val body = upstream.body
                            val contentTypeHeader = upstream.header("Content-Type")

                            if (!CloudStreamSecurity.isSupportedAudioContentType(contentTypeHeader)) {
                                call.respond(
                                    HttpStatusCode.BadGateway,
                                    "Unsupported stream content type"
                                )
                                return@get
                            }

                            val contentLength = upstream.header("Content-Length")
                            if (!CloudStreamSecurity.isAcceptableContentLength(contentLength)) {
                                call.respond(
                                    HttpStatusCode(413, "Payload Too Large"),
                                    "Stream content too large"
                                )
                                return@get
                            }

                            val contentRange = upstream.header("Content-Range")
                            val acceptRanges = upstream.header("Accept-Ranges")
                            val responseContentType = contentTypeHeader
                                ?.substringBefore(';')
                                ?.trim()
                                ?.let { raw ->
                                    runCatching { ContentType.parse(raw) }.getOrNull()
                                }
                                ?: ContentType.Audio.Any

                            // A range we added ourselves covers the whole file, so the client gets a plain 200.
                            val partial = upstream.code == 206 && !forcedRange
                            if (partial) {
                                call.response.status(HttpStatusCode.PartialContent)
                            } else {
                                call.response.status(HttpStatusCode.OK)
                            }
                            call.response.header("Accept-Ranges", acceptRanges ?: "bytes")
                            contentLength?.let { call.response.header("Content-Length", it) }
                            if (partial) contentRange?.let { call.response.header("Content-Range", it) }

                            call.respondBytesWriter(contentType = responseContentType) {
                                withContext(Dispatchers.IO) {
                                    body.byteStream().use { input ->
                                        val buffer = ByteArray(64 * 1024)
                                        var bytesRead: Int
                                        while (input.read(buffer)
                                                .also { bytesRead = it } != -1
                                        ) {
                                            writeFully(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val msg = e.toString()
                        if (msg.contains("ChannelWriteException") ||
                            msg.contains("ClosedChannelException") ||
                            msg.contains("Broken pipe") ||
                            msg.contains("JobCancellationException")
                        ) {
                            // Client disconnected, normal behavior
                        } else {
                            Timber.w(e, "$proxyTag stream failed")
                        }
                    }
                }
            }
        }
    }
}
