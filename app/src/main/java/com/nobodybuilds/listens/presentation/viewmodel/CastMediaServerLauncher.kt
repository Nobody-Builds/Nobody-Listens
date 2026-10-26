package com.nobodybuilds.listens.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.cast.framework.CastSession
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.service.http.CastSessionSecurity
import com.nobodybuilds.listens.data.service.http.MediaFileHttpServerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL

/**
 * Starts the local media HTTP server the Cast receiver streams from and checks that it is
 * reachable from the Cast device. Owned by [CastTransferStateHolder].
 */
internal class CastMediaServerLauncher(
    private val context: Context,
    private val emitCastError: (String) -> Unit
) {
    private val CAST_LOG_TAG = "PlayerCastTransfer"
    private val httpServerStartMutex = Mutex()

    fun resolveCastDeviceIp(session: CastSession?): String? {
        val castDevice = session?.castDevice ?: return null
        return normalizeHostAddress(runCatching { castDevice.inetAddress }.getOrNull())
    }

    private fun normalizeHostAddress(rawValue: Any?): String? {
        val normalized: String? = when (rawValue) {
            null -> null
            is String -> rawValue
            is InetAddress -> rawValue.hostAddress
            else -> rawValue.toString()
        }
        val trimmed = normalized?.trim()
        return trimmed?.takeIf { value -> value.isNotEmpty() }
    }

    private fun isServerAddressCompatibleWithCastDevice(
        serverAddress: String?,
        castDeviceIpHint: String?
    ): Boolean {
        if (serverAddress.isNullOrBlank()) return false
        val castAddress = parseIpv4Address(castDeviceIpHint) ?: return true
        val serverHost = Uri.parse(serverAddress).host
        val serverHostAddress = parseIpv4Address(serverHost) ?: return true
        val prefixLength = MediaFileHttpServerService.serverPrefixLength
            .takeIf { it in 0..32 }
            ?: 24
        return isSameSubnet(serverHostAddress, castAddress, prefixLength)
    }

    private fun parseIpv4Address(rawAddress: String?): Inet4Address? {
        val normalized = rawAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = runCatching { InetAddress.getByName(normalized) }.getOrNull() ?: return null
        return parsed as? Inet4Address
    }

    private fun isSameSubnet(localAddress: Inet4Address, remoteAddress: Inet4Address, prefixLength: Int): Boolean {
        val clampedPrefix = prefixLength.coerceIn(0, 32)
        if (clampedPrefix == 0) return true
        val localInt = localAddress.toIntAddress()
        val remoteInt = remoteAddress.toIntAddress()
        val mask = if (clampedPrefix == 32) {
            -1
        } else {
            (-1 shl (32 - clampedPrefix))
        }
        return (localInt and mask) == (remoteInt and mask)
    }

    private fun Inet4Address.toIntAddress(): Int {
        val bytes = address
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    suspend fun waitForSongEndpointReady(
        serverAddress: String,
        song: Song,
        authToken: String?,
        attempts: Int = 12,
        delayMs: Long = 250L
    ): Boolean {
        repeat(attempts) { attempt ->
            if (isSongEndpointReady(serverAddress, song, authToken)) {
                return true
            }
            if (attempt < attempts - 1) {
                delay(delayMs)
            }
        }
        return false
    }

    private suspend fun isSongEndpointReady(serverAddress: String, song: Song, authToken: String?): Boolean {
        val localEndpoint = buildLoopbackEndpoint(serverAddress, song.id, authToken) ?: return false
        return isHttpEndpointReady(localEndpoint, method = "HEAD")
    }

    private suspend fun isHttpEndpointReady(
        endpoint: String,
        method: String = "GET"
    ): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        runCatching {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 150
                readTimeout = 150
                instanceFollowRedirects = false
                requestMethod = method
            }
            val code = connection?.responseCode ?: -1
            code in 200..299
        }.getOrDefault(false).also {
            connection?.disconnect()
        }
    }

    private fun buildLoopbackEndpoint(serverAddress: String, songId: String, authToken: String?): String? {
        return CastSessionSecurity.buildLoopbackSongUrl(
            serverAddress = serverAddress,
            songId = songId,
            authToken = authToken
        )
    }

    fun primeHttpServerStart(currentSession: () -> CastSession?) {
        if (MediaFileHttpServerService.isServerRunning || MediaFileHttpServerService.isServerStarting) return

        MediaFileHttpServerService.lastFailureReason = null
        MediaFileHttpServerService.lastFailureMessage = null

        val castDeviceIpHint = resolveCastDeviceIp(
            session = currentSession()
        )

        val intent = Intent(context, MediaFileHttpServerService::class.java).apply {
            action = MediaFileHttpServerService.ACTION_START_SERVER
            castDeviceIpHint?.let { putExtra(MediaFileHttpServerService.EXTRA_CAST_DEVICE_IP, it) }
        }
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure { throwable ->
            Timber.tag(CAST_LOG_TAG).e(throwable, "Failed to pre-start media server service")
            emitCastError(
                "Couldn't pre-start Cast service: ${throwable.javaClass.simpleName}: ${throwable.message ?: "Unknown"}"
            )
        }
    }

    suspend fun ensureHttpServerRunning(castDeviceIpHint: String? = null): Boolean = httpServerStartMutex.withLock {
        val runningServerAddress = MediaFileHttpServerService.serverAddress
        if (MediaFileHttpServerService.isServerRunning && runningServerAddress != null) {
            if (isServerAddressCompatibleWithCastDevice(runningServerAddress, castDeviceIpHint)) {
                return@withLock true
            }
            Timber.tag(CAST_LOG_TAG).w(
                "HTTP server host (%s) is not in Cast subnet (castDeviceIp=%s, prefix=%d). Restarting service.",
                runningServerAddress,
                castDeviceIpHint,
                MediaFileHttpServerService.serverPrefixLength
            )
            context.stopService(Intent(context, MediaFileHttpServerService::class.java))
            for (attempt in 0 until 30) {
                if (!MediaFileHttpServerService.isServerRunning && !MediaFileHttpServerService.isServerStarting) {
                    break
                }
                delay(100)
            }
        }

        // Clear stale failure state from a previous attempt before starting a new one.
        MediaFileHttpServerService.lastFailureReason = null
        MediaFileHttpServerService.lastFailureMessage = null

        val intent = Intent(context, MediaFileHttpServerService::class.java).apply {
            action = MediaFileHttpServerService.ACTION_START_SERVER
            castDeviceIpHint?.let { putExtra(MediaFileHttpServerService.EXTRA_CAST_DEVICE_IP, it) }
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Timber.tag(CAST_LOG_TAG).e(e, "Failed to start media server service")
            emitCastError("Couldn't start Cast service: ${e.javaClass.simpleName}: ${e.message ?: "Unknown"}")
            return@withLock false
        }

        // Release builds can take noticeably longer on cold start to bring up the HTTP service.
        for (i in 0..200) {
            if (MediaFileHttpServerService.isServerRunning && MediaFileHttpServerService.serverAddress != null) {
                return@withLock true
            }
            val failureReason = MediaFileHttpServerService.lastFailureReason
            if (failureReason != null) {
                val detail = MediaFileHttpServerService.lastFailureMessage
                Timber.tag(CAST_LOG_TAG).w(
                    "Media server failed to start: %s (%s)",
                    failureReason,
                    detail
                )
                when (failureReason) {
                    MediaFileHttpServerService.FailureReason.NO_NETWORK_ADDRESS -> {
                        emitCastError("Couldn't find a local Wi-Fi address for Cast.")
                    }
                    MediaFileHttpServerService.FailureReason.FOREGROUND_START_EXCEPTION -> {
                        emitCastError("Couldn't start Cast service in foreground. ${detail ?: ""}".trim())
                    }
                    MediaFileHttpServerService.FailureReason.START_EXCEPTION -> {
                        emitCastError("Cast HTTP server failed: ${detail ?: "unknown error"}")
                    }
                }
                return@withLock false
            }
            delay(100)
        }
        Timber.tag(CAST_LOG_TAG).w(
            "Timed out waiting for media server startup (reason=%s detail=%s)",
            MediaFileHttpServerService.lastFailureReason,
            MediaFileHttpServerService.lastFailureMessage
        )
        val timeoutDetail = MediaFileHttpServerService.lastFailureMessage?.takeIf { it.isNotBlank() }
        val timeoutState = "running=${MediaFileHttpServerService.isServerRunning}, " +
            "starting=${MediaFileHttpServerService.isServerStarting}, " +
            "addressSet=${MediaFileHttpServerService.serverAddress != null}"
        emitCastError(
            if (timeoutDetail != null) "Cast HTTP server timeout: $timeoutDetail"
            else "Cast HTTP server startup timed out. ($timeoutState)"
        )
        return@withLock false
    }
}
