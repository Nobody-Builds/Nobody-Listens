package com.nobodybuilds.listens.data.service.wear

import android.app.Application
import androidx.core.net.toUri
import com.nobodybuilds.listens.data.gdrive.GDriveStreamProxy
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.navidrome.NavidromeStreamProxy
import com.nobodybuilds.listens.data.netease.NeteaseStreamProxy
import com.nobodybuilds.listens.data.qqmusic.QqMusicStreamProxy
import com.nobodybuilds.listens.data.telegram.TelegramRepository
import com.nobodybuilds.listens.data.telegram.TelegramStreamProxy
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.io.InputStream

internal data class OpenedSongSource(
    val inputStream: InputStream,
    val fileSize: Long,
    private val closeable: Closeable? = null,
) : Closeable {
    override fun close() {
        if (closeable != null) {
            closeable.close()
        } else {
            inputStream.close()
        }
    }
}

/**
 * Opens a song's audio for a watch transfer: local files first, then (for temporary
 * playback only) the cloud stream proxies.
 */
internal class WatchTransferSongSources(
    private val application: Application,
    private val telegramRepository: TelegramRepository,
    private val telegramStreamProxy: Lazy<TelegramStreamProxy>,
    private val neteaseStreamProxy: NeteaseStreamProxy,
    private val qqMusicStreamProxy: QqMusicStreamProxy,
    private val navidromeStreamProxy: NavidromeStreamProxy,
    private val jellyfinStreamProxy: com.nobodybuilds.listens.data.jellyfin.JellyfinStreamProxy,
    private val gDriveStreamProxy: GDriveStreamProxy,
    private val okHttpClient: OkHttpClient,
) {
    private val contentResolver by lazy { application.contentResolver }

    fun isSongTransferEligible(song: Song): Boolean {
        val contentUri = song.contentUriString
        if (
            contentUri.startsWith("telegram://") ||
            contentUri.startsWith("netease://") ||
            contentUri.startsWith("gdrive://")
        ) {
            return false
        }

        val localFile = song.path
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
        if (localFile != null && localFile.isFile && localFile.canRead() && localFile.length() > 0L) {
            return true
        }

        val uri = runCatching { song.contentUriString.toUri() }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme == "file") {
            val uriFile = uri.path?.let { File(it) }
            return uriFile != null && uriFile.isFile && uriFile.canRead() && uriFile.length() > 0L
        }
        if (scheme != "content") return false

        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length != 0L
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun openSongSource(
        song: Song,
        allowProxyStreaming: Boolean,
    ): OpenedSongSource? {
        openDirectSongSource(song)?.let { return it }
        if (!allowProxyStreaming) return null

        val streamUrl = resolveStreamUrl(song) ?: return null
        return openHttpSongSource(streamUrl)
    }

    private fun openDirectSongSource(song: Song): OpenedSongSource? {
        val directFile = song.path
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile && it.canRead() && it.length() > 0L }
        if (directFile != null) {
            return runCatching {
                OpenedSongSource(
                    inputStream = directFile.inputStream(),
                    fileSize = directFile.length(),
                )
            }.onFailure { error ->
                Timber.tag(TAG).w(error, "Failed to open direct file for songId=%s", song.id)
            }.getOrNull()
        }

        val rawUri = song.contentUriString
        if (rawUri.isBlank()) return null
        if (rawUri.startsWith("/")) {
            val rawFile = File(rawUri)
            if (rawFile.isFile && rawFile.canRead() && rawFile.length() > 0L) {
                return runCatching {
                    OpenedSongSource(
                        inputStream = rawFile.inputStream(),
                        fileSize = rawFile.length(),
                    )
                }.getOrNull()
            }
        }

        val uri = runCatching { rawUri.toUri() }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            "file" -> {
                val uriFile = uri.path?.let(::File)
                    ?.takeIf { it.isFile && it.canRead() && it.length() > 0L }
                    ?: return null
                runCatching {
                    OpenedSongSource(
                        inputStream = uriFile.inputStream(),
                        fileSize = uriFile.length(),
                    )
                }.getOrNull()
            }

            "content" -> {
                runCatching {
                    val inputStream = contentResolver.openInputStream(uri) ?: return@runCatching null
                    val size = contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        afd.length.takeIf { it > 0L } ?: afd.declaredLength.takeIf { it > 0L }
                    } ?: 0L
                    OpenedSongSource(
                        inputStream = inputStream,
                        fileSize = size.coerceAtLeast(0L),
                    )
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to open content source for songId=%s", song.id)
                }.getOrNull()
            }

            else -> null
        }
    }

    private suspend fun resolveStreamUrl(song: Song): String? {
        val rawUri = song.contentUriString
        val uri = runCatching { rawUri.toUri() }.getOrNull() ?: return null
        return when (uri.scheme?.lowercase()) {
            "http", "https" -> rawUri
            "telegram" -> resolveTelegramStreamUrl(song, uri, rawUri)
            "netease" -> {
                ensureCloudProxyReady(neteaseStreamProxy) || return null
                neteaseStreamProxy.resolveNeteaseUri(rawUri)
            }
            "qqmusic" -> {
                ensureCloudProxyReady(qqMusicStreamProxy) || return null
                qqMusicStreamProxy.warmUpStreamUrl(rawUri)
                qqMusicStreamProxy.resolveQqMusicUri(rawUri)
            }
            "navidrome" -> {
                ensureCloudProxyReady(navidromeStreamProxy) || return null
                navidromeStreamProxy.warmUpStreamUrl(rawUri)
                navidromeStreamProxy.resolveNavidromeUri(rawUri)
            }
            "jellyfin" -> {
                ensureCloudProxyReady(jellyfinStreamProxy) || return null
                jellyfinStreamProxy.warmUpStreamUrl(rawUri)
                jellyfinStreamProxy.resolveJellyfinUri(rawUri)
            }
            "gdrive" -> {
                ensureGDriveProxyReady() || return null
                gDriveStreamProxy.resolveGDriveUri(rawUri)
            }
            else -> null
        }
    }

    private suspend fun resolveTelegramStreamUrl(song: Song, uri: android.net.Uri, rawUri: String): String? {
        if (!telegramRepository.isReady()) {
            val ready = telegramRepository.awaitReady(10_000L)
            if (!ready) {
                Timber.tag(TAG).w("Telegram repository not ready for watch handoff")
                return null
            }
        }

        val resolved = telegramRepository.resolveTelegramUri(rawUri)
        val fileId = resolved?.first
            ?: song.telegramFileId
            ?: uri.host?.toIntOrNull()
            ?: uri.pathSegments.firstOrNull()?.toIntOrNull()
            ?: return null
        val knownSize = (resolved?.second ?: 0L).coerceAtLeast(0L)

        val proxy = telegramStreamProxy.get()
        val ready = proxy.ensureReady(5_000L)
        if (!ready) {
            Timber.tag(TAG).w("Telegram stream proxy not ready for watch handoff")
            return null
        }
        return proxy.getProxyUrl(fileId, knownSize)
    }

    private suspend fun ensureCloudProxyReady(proxy: Any): Boolean {
        return when (proxy) {
            is NeteaseStreamProxy -> proxy.ensureReady(5_000L)
            is QqMusicStreamProxy -> proxy.ensureReady(5_000L)
            is NavidromeStreamProxy -> proxy.ensureReady(5_000L)
            is com.nobodybuilds.listens.data.jellyfin.JellyfinStreamProxy -> proxy.ensureReady(5_000L)
            else -> false
        }
    }

    private suspend fun ensureGDriveProxyReady(): Boolean {
        if (gDriveStreamProxy.isReady()) return true
        gDriveStreamProxy.start()
        repeat(50) {
            if (gDriveStreamProxy.isReady()) return true
            delay(100L)
        }
        Timber.tag(TAG).w("GDrive stream proxy not ready for watch handoff")
        return false
    }

    private suspend fun openHttpSongSource(url: String): OpenedSongSource? {
        val response = withContext(Dispatchers.IO) {
            okHttpClient.newCall(
                Request.Builder()
                    .url(url)
                    .get()
                    .build()
            ).execute()
        }
        if (!response.isSuccessful) {
            Timber.tag(TAG).w("Watch handoff stream request failed: code=%d url=%s", response.code, url)
            response.close()
            return null
        }

        val body = response.body

        return OpenedSongSource(
            inputStream = body.byteStream(),
            fileSize = body.contentLength().coerceAtLeast(0L),
            closeable = response,
        )
    }

    private companion object {
        const val TAG = "PhoneDirectTransfer"
    }
}
