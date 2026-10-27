package com.nobodybuilds.listens.data.service.http

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.repository.MusicRepository
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class MediaFileHttpServerService : Service() {

    @Inject
    lateinit var musicRepository: MusicRepository

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    @Volatile
    private var startInProgress = false
    private val serverStartLock = Any()
    @Volatile
    private var castDeviceIpHint: String? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val mediaSources by lazy { CastMediaSourceResolver(this, musicRepository) }
    private val transcodeCache by lazy {
        CastTranscodeCache(cacheDir, serviceScope, CastAacTranscoder(contentResolver))
    }
    private val songRoutes by lazy {
        CastSongRoutes(
            mediaSources = mediaSources,
            mimeResolver = CastAudioMimeResolver(contentResolver),
            codecSupport = CastCodecSupport(contentResolver),
            transcodeCache = transcodeCache
        )
    }
    private val artRoutes by lazy { CastArtRoutes(mediaSources) }

    companion object {
        const val ACTION_START_SERVER = "ACTION_START_SERVER"
        const val ACTION_STOP_SERVER = "ACTION_STOP_SERVER"
        const val EXTRA_CAST_DEVICE_IP = "EXTRA_CAST_DEVICE_IP"
        @Volatile
        var isServerRunning = false
        @Volatile
        var isServerStarting = false
        @Volatile
        var serverAddress: String? = null
        @Volatile
        var serverHostAddress: String? = null
        @Volatile
        var serverPrefixLength: Int = -1
        @Volatile
        var lastFailureReason: FailureReason? = null
        @Volatile
        var lastFailureMessage: String? = null
        @Volatile
        private var castAccessPolicy: CastAccessPolicy = CastAccessPolicy.EMPTY
        private const val SERVER_START_PORT_RETRY_LIMIT = 3

        internal fun configureCastSessionAccess(
            allowedSongIds: Collection<String>,
            castDeviceIpHint: String?
        ): CastAccessPolicy {
            val updatedPolicy = CastSessionSecurity.buildAccessPolicy(
                existingToken = castAccessPolicy.authToken,
                allowedSongIds = allowedSongIds,
                castDeviceIpHint = castDeviceIpHint,
                // Always whitelist the server's own LAN IP so that on-device services
                // (widget updates, notification art) that connect to http://serverIp:PORT/...
                // via the LAN interface are authorized, even when the allowlist is enforced.
                serverOwnIp = serverHostAddress
            )
            castAccessPolicy = updatedPolicy
            return updatedPolicy
        }

        internal fun currentCastAccessPolicy(): CastAccessPolicy = castAccessPolicy

        internal fun clearCastSessionAccess() {
            castAccessPolicy = CastAccessPolicy.EMPTY
        }
    }

    enum class FailureReason {
        NO_NETWORK_ADDRESS,
        FOREGROUND_START_EXCEPTION,
        START_EXCEPTION
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVER -> {
                castDeviceIpHint = null
                stopSelf()
            }
            ACTION_START_SERVER, null -> {
                castDeviceIpHint = intent
                    ?.getStringExtra(EXTRA_CAST_DEVICE_IP)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                // Ensure we are in foreground immediately if started this way.
                startForegroundService()
                startServer()
            }
            else -> {
                Timber.w("Ignoring unknown media server action: %s", intent.action)
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        runCatching {
            val channelId = "lmusic_cast_server"
            val channelName = getString(R.string.cast_server_channel_name)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    channelName,
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.createNotificationChannel(channel)
            }

            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.cast_server_notification_title))
                .setContentText(getString(R.string.cast_server_notification_text))
                .setSmallIcon(com.nobodybuilds.listens.shared.R.drawable.nl_mark_mono)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .build()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    1002, 
                    notification, 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(1002, notification)
            }
        }.onFailure { throwable ->
            lastFailureReason = FailureReason.FOREGROUND_START_EXCEPTION
            lastFailureMessage = getString(
                R.string.cast_server_foreground_error,
                throwable.javaClass.simpleName,
                throwable.message ?: getString(R.string.error_unknown),
            )
            Timber.e(throwable, "Failed to enter foreground mode for cast HTTP server")
            stopSelf()
        }
    }

    private fun startServer(retryAttempt: Int = 0) {
        synchronized(serverStartLock) {
            if (server?.application?.isActive == true || startInProgress) {
                return
            }
            startInProgress = true
            isServerStarting = true
        }

        serviceScope.launch {
            var shouldRetryAfterBindFailure = false
            try {
                lastFailureReason = null
                lastFailureMessage = null
                isServerRunning = false

                val addressSelection = selectIpAddress(
                    context = applicationContext,
                    castDeviceIpHint = castDeviceIpHint
                )
                if (addressSelection == null) {
                    Timber.w("No suitable IP address found; cannot start HTTP server")
                    lastFailureReason = FailureReason.NO_NETWORK_ADDRESS
                    lastFailureMessage = buildString {
                        append("No LAN IPv4 address available")
                        castDeviceIpHint?.let { append(" (castDeviceIp=$it)") }
                    }
                    stopSelf()
                    return@launch
                }
                val serverPort = resolveServerPort(preferredPort = 8080)
                if (serverPort != 8080) {
                    Timber.tag(CAST_HTTP_LOG_TAG).w(
                        "Port 8080 is already in use. Falling back to port %d for Cast HTTP server.",
                        serverPort
                    )
                }
                serverHostAddress = addressSelection.hostAddress
                serverPrefixLength = addressSelection.prefixLength
                serverAddress = "http://${addressSelection.hostAddress}:$serverPort"
                Timber.tag(CAST_HTTP_LOG_TAG).i(
                    "Selected cast server host=%s prefix=%d castHint=%s matchedCastSubnet=%s",
                    addressSelection.hostAddress,
                    addressSelection.prefixLength,
                    castDeviceIpHint,
                    addressSelection.matchedCastSubnet
                )
                lastFailureReason = null
                lastFailureMessage = null

                server = embeddedServer(CIO, port = serverPort, host = "0.0.0.0") {
                    routing {
                            get("/health") {
                                if (!call.ensureLoopbackHealthRequest()) return@get
                                call.respond(HttpStatusCode.OK, "ok")
                            }
                            head("/health") {
                                if (!call.ensureLoopbackHealthRequest()) return@head
                                call.respond(HttpStatusCode.OK)
                            }
                            songRoutes.register(this)
                            artRoutes.register(this)
                        }
                }.start(wait = false)
                isServerRunning = true
            } catch (e: Exception) {
                isServerRunning = false
                serverAddress = null
                serverHostAddress = null
                serverPrefixLength = -1
                if (e.isAddressAlreadyInUse() && retryAttempt < SERVER_START_PORT_RETRY_LIMIT) {
                    shouldRetryAfterBindFailure = true
                    Timber.tag(CAST_HTTP_LOG_TAG).w(
                        e,
                        "Cast HTTP server port bind failed; retrying startup (%d/%d).",
                        retryAttempt + 1,
                        SERVER_START_PORT_RETRY_LIMIT
                    )
                } else {
                    Timber.e(e, "Failed to start HTTP cast server")
                    lastFailureReason = FailureReason.START_EXCEPTION
                    lastFailureMessage = "${e.javaClass.simpleName}: ${e.message ?: "Unknown"}"
                    stopSelf()
                }
            } finally {
                synchronized(serverStartLock) {
                    startInProgress = false
                    isServerStarting = false
                }
            }

            if (shouldRetryAfterBindFailure) {
                startServer(retryAttempt + 1)
            }
        }
    }

    /**
     * Removes the cached transcode for [songId] and deletes the temp file.
     * Call this when the Cast session changes (song skip, session end).
     */
    fun evictTranscodeCache(songId: String) {
        transcodeCache.evictTranscodeCache(songId)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServerRunning = false
        isServerStarting = false
        serverAddress = null
        serverHostAddress = null
        serverPrefixLength = -1
        castDeviceIpHint = null
        clearCastSessionAccess()
        synchronized(serverStartLock) {
            startInProgress = false
        }

        // Cleanup all transcode temp files.
        transcodeCache.cleanupAllTranscodeTempFiles()

        // P0-1: Cancel serviceScope to avoid coroutine leaks after service is destroyed.
        serviceJob.cancel()

        val serverInstance = server
        server = null

        // Stop server in a background thread to avoid blocking the Main Thread
        Thread {
            try {
                // Grace period 100ms, timeout 2000ms
                serverInstance?.stop(100, 2000)
                Timber.d("MediaFileHttpServerService: Ktor server stopped")
            } catch (e: Exception) {
                Timber.e(e, "MediaFileHttpServerService: Error stopping Ktor server")
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
