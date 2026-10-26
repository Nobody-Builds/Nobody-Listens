package com.nobodybuilds.listens.data.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.nobodybuilds.listens.data.repository.MusicRepository
import com.nobodybuilds.listens.utils.AlbumArtUtils
import com.nobodybuilds.listens.utils.ArtworkTransportSanitizer
import com.nobodybuilds.listens.utils.MediaItemBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads the album art the widgets and the watch show for the current song, sanitized for
 * transport, and remembers the last result (and recent failures) so updates stay cheap.
 */
internal class WidgetArtworkLoader(
    private val applicationContext: Context,
    private val musicRepository: MusicRepository
) {
    private var cachedWidgetArtSourceKey: String? = null
    private var cachedWidgetArtResolvedUri: String? = null
    private var cachedWidgetArtBytes: ByteArray? = null
    private var cachedWidgetArtLoadFailureKey: String? = null
    private var cachedWidgetArtLoadFailureAtMs: Long = 0L

    fun invalidateCachedWidgetArtwork() {
        cachedWidgetArtSourceKey = null
        cachedWidgetArtResolvedUri = null
        cachedWidgetArtBytes = null
        cachedWidgetArtLoadFailureKey = null
        cachedWidgetArtLoadFailureAtMs = 0L
    }

    suspend fun getAlbumArtForWidget(
        mediaId: String?,
        embeddedArt: ByteArray?,
        artUris: List<Uri>,
    ): Pair<ByteArray?, String?> = withContext(Dispatchers.IO) {
        // Try embedded art first — but fall through to URI loading if sanitization fails
        val sanitizedFromEmbedded = embeddedArt?.takeIf { it.isNotEmpty() }?.let { bytes ->
            runCatching {
                ArtworkTransportSanitizer.sanitizeEncodedBytes(
                    data = bytes,
                    config = ArtworkTransportSanitizer.WIDGET_CONFIG,
                )
            }.getOrNull()
        }
        val candidateUriStrings = LinkedHashSet<String>().apply {
            artUris.forEach { candidate ->
                candidate.toString()
                    .takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
        }.toList()
        val preferredUriString = candidateUriStrings.firstOrNull()
        val sourceKey = buildWidgetArtworkSourceKey(
            mediaId = mediaId,
            candidateUriStrings = candidateUriStrings,
        )

        if (sanitizedFromEmbedded != null) {
            cachedWidgetArtSourceKey = sourceKey
            cachedWidgetArtResolvedUri = preferredUriString
            cachedWidgetArtBytes = sanitizedFromEmbedded
            cachedWidgetArtLoadFailureKey = null
            cachedWidgetArtLoadFailureAtMs = 0L
            return@withContext sanitizedFromEmbedded to preferredUriString
        }

        if (sourceKey != null && sourceKey == cachedWidgetArtSourceKey && cachedWidgetArtBytes != null) {
            return@withContext cachedWidgetArtBytes to (cachedWidgetArtResolvedUri ?: preferredUriString)
        }
        if (sourceKey != null && sourceKey == cachedWidgetArtLoadFailureKey) {
            val failureAgeMs = SystemClock.elapsedRealtime() - cachedWidgetArtLoadFailureAtMs
            if (failureAgeMs < WIDGET_ART_FAILURE_RETRY_MS) {
                return@withContext null to preferredUriString
            }
        }

        val repositoryArtUriString = if (mediaId.isNullOrBlank()) {
            null
        } else {
            resolveRepositoryArtworkUri(mediaId)?.toString()
        }
        val resolvedUriStrings = LinkedHashSet<String>().apply {
            addAll(candidateUriStrings)
            repositoryArtUriString
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
        }

        for (candidateUriString in resolvedUriStrings) {
            val candidateUri = parseArtworkUriString(candidateUriString) ?: continue
            val loadedBytes = loadArtworkBytesForWidget(candidateUri)
            if (loadedBytes != null) {
                cachedWidgetArtSourceKey = sourceKey
                cachedWidgetArtResolvedUri = candidateUriString
                cachedWidgetArtBytes = loadedBytes
                cachedWidgetArtLoadFailureKey = null
                cachedWidgetArtLoadFailureAtMs = 0L
                return@withContext loadedBytes to candidateUriString
            }
        }

        cachedWidgetArtLoadFailureKey = sourceKey
        cachedWidgetArtLoadFailureAtMs = SystemClock.elapsedRealtime()
        return@withContext null to (repositoryArtUriString ?: preferredUriString)
    }

    fun resolveWidgetArtworkUriCandidates(
        metadata: MediaMetadata?,
        preferredArtworkUri: Uri? = null,
    ): List<Uri> {
        val candidates = LinkedHashSet<String>()
        preferredArtworkUri
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let(candidates::add)
        resolveStoredArtworkUriString(metadata)?.let(candidates::add)
        metadata?.artworkUri
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let(candidates::add)
        return candidates.mapNotNull(::parseArtworkUriString)
    }

    private fun parseArtworkUriString(rawArtworkUri: String?): Uri? {
        if (rawArtworkUri.isNullOrBlank()) {
            return null
        }

        return MediaItemBuilder.artworkUri(rawArtworkUri)
            ?: if (rawArtworkUri.startsWith("/")) {
                Uri.fromFile(java.io.File(rawArtworkUri))
            } else {
                runCatching { rawArtworkUri.toUri() }.getOrNull()
            }
    }

    private fun buildWidgetArtworkSourceKey(
        mediaId: String?,
        candidateUriStrings: List<String>,
    ): String? {
        val normalizedMediaId = mediaId?.takeIf { it.isNotBlank() }
        if (normalizedMediaId == null && candidateUriStrings.isEmpty()) {
            return null
        }
        return buildString {
            normalizedMediaId?.let {
                append("mediaId=")
                append(it)
            }
            if (candidateUriStrings.isNotEmpty()) {
                if (isNotEmpty()) append('|')
                append(candidateUriStrings.joinToString(separator = ","))
            }
        }
    }

    fun resolveArtworkUri(metadata: MediaMetadata?): Uri? {
        metadata ?: return null
        metadata.artworkUri?.let { return it }
        val extrasUri = metadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return parseArtworkUriString(extrasUri)
    }

    suspend fun resolveRepositoryArtworkUri(mediaId: String?): Uri? {
        val songId = mediaId?.takeIf { it.isNotBlank() } ?: return null
        val song = withContext(Dispatchers.IO) {
            musicRepository.getSong(songId).first()
        } ?: return null

        return MediaItemBuilder.artworkUri(song.albumArtUriString)
            ?: song.albumArtUriString
                ?.takeIf { it.isNotBlank() }
                ?.let { raw ->
                    if (raw.startsWith("/")) Uri.fromFile(java.io.File(raw))
                    else runCatching { Uri.parse(raw) }.getOrNull()
                }
    }

    private suspend fun loadArtworkBytesForWidget(uri: Uri): ByteArray? {
        val uriString = uri.toString()
        val scheme = uri.scheme?.lowercase()
        val isLocalArtworkUri = com.nobodybuilds.listens.utils.LocalArtworkUri.isLocalArtworkUri(uriString)
        return when {
            isLocalArtworkUri || scheme == "content" || scheme == "file" || scheme == "android.resource" -> {
                runCatching {
                    AlbumArtUtils.openArtworkInputStream(applicationContext, uri)?.use { input ->
                        readBytesCapped(input, ArtworkTransportSanitizer.WIDGET_CONFIG.sourceBytesLimit)
                            ?.let { bytes ->
                                ArtworkTransportSanitizer.sanitizeEncodedBytes(
                                    data = bytes,
                                    config = ArtworkTransportSanitizer.WIDGET_CONFIG,
                                )
                            }
                    }
                }.getOrElse { error ->
                    Timber.tag(TAG).w(error, "Widget artwork read failed for local uri=%s", uri)
                    null
                }
            }
            scheme == "http" || scheme == "https" -> {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(uriString).openConnection() as? HttpURLConnection)
                        ?: return null
                    connection.connectTimeout = 4_000
                    connection.readTimeout = 6_000
                    connection.instanceFollowRedirects = true
                    connection.doInput = true
                    connection.inputStream.use { input ->
                        readBytesCapped(input, ArtworkTransportSanitizer.WIDGET_CONFIG.sourceBytesLimit)
                            ?.let { bytes ->
                                ArtworkTransportSanitizer.sanitizeEncodedBytes(
                                    data = bytes,
                                    config = ArtworkTransportSanitizer.WIDGET_CONFIG,
                                )
                            }
                    }
                } catch (error: Exception) {
                    Timber.tag(TAG).w(error, "Widget artwork read failed for remote uri=%s", uri)
                    null
                } finally {
                    connection?.disconnect()
                }
            }
            else -> loadArtworkBytesViaCoil(uri)
        }
    }

    private suspend fun loadArtworkBytesViaCoil(uri: Uri): ByteArray? {
        val request = ImageRequest.Builder(applicationContext)
            .data(uri)
            .size(
                ArtworkTransportSanitizer.WIDGET_CONFIG.maxDimensionPx,
                ArtworkTransportSanitizer.WIDGET_CONFIG.maxDimensionPx,
            )
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()

        return runCatching {
            val drawable = applicationContext.imageLoader.execute(request).drawable ?: return@runCatching null
            val fallbackSizePx = ArtworkTransportSanitizer.WIDGET_CONFIG.maxDimensionPx
            val bitmap = drawable.toBitmap(
                width = drawable.intrinsicWidth.takeIf { it > 0 } ?: fallbackSizePx,
                height = drawable.intrinsicHeight.takeIf { it > 0 } ?: fallbackSizePx,
                config = Bitmap.Config.ARGB_8888,
            )
            val encodedBytes = ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
                output.toByteArray()
            }
            ArtworkTransportSanitizer.sanitizeEncodedBytes(
                data = encodedBytes,
                config = ArtworkTransportSanitizer.WIDGET_CONFIG,
            )
        }.getOrElse { error ->
            Timber.tag(TAG).w(error, "Widget artwork read failed via Coil for uri=%s", uri)
            null
        }
    }

    private fun readBytesCapped(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_STREAM_BUFFER_SIZE)
        var totalRead = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            totalRead += read
            if (totalRead > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val TAG = "MusicService_NobodyListens"
        const val DEFAULT_STREAM_BUFFER_SIZE = 8 * 1024
        const val WIDGET_ART_FAILURE_RETRY_MS = 30_000L
    }
}

/** The artwork URI stored on a queue item: the album-art extra first, then the metadata URI. */
internal fun resolveStoredArtworkUriString(metadata: MediaMetadata?): String? {
    metadata ?: return null
    return metadata.extras
        ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART)
        ?.takeIf { it.isNotBlank() }
        ?: metadata.artworkUri
            ?.toString()
            ?.takeIf { it.isNotBlank() }
}
