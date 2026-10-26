package com.nobodybuilds.listens.data.service.player

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.common.images.WebImage
import com.nobodybuilds.listens.data.model.Song
import com.nobodybuilds.listens.data.service.http.CastSessionSecurity
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale

/**
 * Turns songs into Cast [MediaQueueItem]s for [CastPlayer]: the stream and art URLs on the
 * local HTTP server, the content type the receiver is told, and queue diagnostics for the log.
 */
internal class CastQueueItemFactory(
    private val contentResolver: ContentResolver?,
    private val mimeProbe: CastAudioMimeProbe
) {
    private val castLogTag = "CastPlayer"

    fun Song.toMediaQueueItem(
        serverAddress: String,
        authToken: String?,
        forcedMimeType: String? = null,
        queueLoadNonce: String? = null
    ): MediaQueueItem {
        val contentType = forcedMimeType ?: resolveCastContentType()
        val durationHintMs = this.duration.coerceAtLeast(0L)
        // Some library entries report inaccurate duration metadata. Let Cast infer duration
        // from the stream to avoid false "track ended" auto-skips on problematic files.
        val streamDuration = MediaInfo.UNKNOWN_DURATION
        val streamRevision = buildCastStreamRevision(contentType, queueLoadNonce)

        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, this.title)
        mediaMetadata.putString(MediaMetadata.KEY_ARTIST, this.displayArtist)
        val artUrl = CastSessionSecurity.buildArtUrl(
            serverAddress = serverAddress,
            songId = this.id,
            streamRevision = streamRevision,
            authToken = authToken
        )
        mediaMetadata.addImage(WebImage(Uri.parse(artUrl)))

        val mediaUrl = CastSessionSecurity.buildSongUrl(
            serverAddress = serverAddress,
            songId = this.id,
            streamRevision = streamRevision,
            authToken = authToken
        )
        val mediaInfo = MediaInfo.Builder(mediaUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setStreamDuration(streamDuration)
            .setMetadata(mediaMetadata)
            .build()

        return MediaQueueItem.Builder(mediaInfo)
            .setCustomData(
                JSONObject()
                    .put("songId", this.id)
                    .put("mimeType", contentType)
                    .put("durationHintMs", durationHintMs)
                    .put("streamDurationSentMs", streamDuration)
                    .put("mimeForced", forcedMimeType != null)
                    .put("streamRevision", streamRevision)
            )
            .build()
    }

    private fun Song.buildCastStreamRevision(contentType: String, queueLoadNonce: String?): String {
        val stableToken = listOf(
            id,
            contentType.lowercase(Locale.ROOT),
            mimeType?.trim().orEmpty().lowercase(Locale.ROOT),
            duration.coerceAtLeast(0L).toString(),
            dateModified.coerceAtLeast(0L).toString(),
            dateAdded.coerceAtLeast(0L).toString(),
            path.substringAfterLast('/').lowercase(Locale.ROOT),
            queueLoadNonce.orEmpty()
        ).joinToString("|")
        return stableToken.hashCode().toUInt().toString(16)
    }

    private fun Song.resolveCastContentType(): String {
        val metadataMimeType = mimeType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ROOT)

        val resolverMimeType = contentResolver
            ?.let { resolver ->
                runCatching { resolver.getType(contentUriString.toUri()) }.getOrNull()
            }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ROOT)

        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val extensionMimeType = when (extension) {
            "mp3", "mpeg" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "m4a", "m4b", "m4p", "mp4", "3gp", "3gpp", "3ga" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/ogg"
            "weba", "webm" -> "audio/webm"
            "amr" -> "audio/amr"
            else -> null
        }

        val normalizedCandidate = listOfNotNull(metadataMimeType, resolverMimeType, extensionMimeType)
            .firstNotNullOfOrNull { candidate -> candidate.toCastSupportedMimeTypeOrNull() }
            ?: "audio/mpeg"

        // Container formats from metadata are reliable. Signature detection (framed sync-word scan)
        // can produce false positives on container binary data (e.g. 0xFF bytes inside MP4 moov atom).
        // Only use signature to disambiguate truly ambiguous metadata (audio/mpeg or audio/aac).
        val isContainerFormat = normalizedCandidate != "audio/mpeg" && normalizedCandidate != "audio/aac"
        if (isContainerFormat) {
            return normalizedCandidate
        }

        // Metadata is ambiguous — consult signature detection to resolve.
        val signatureMimeType = mimeProbe.detectAudioMimeTypeBySignature(this)
        if (signatureMimeType != null && signatureMimeType != normalizedCandidate) {
            Timber.tag(castLogTag).w(
                "MIME mismatch for songId=%s title=%s meta=%s resolver=%s ext=%s signature=%s -> using signature",
                id,
                title,
                metadataMimeType,
                resolverMimeType,
                extensionMimeType,
                signatureMimeType
            )
            return signatureMimeType
        }

        return normalizedCandidate
    }

    fun logQueueDiagnostics(
        songs: List<Song>,
        startIndex: Int,
        serverAddress: String,
        authToken: String?,
        queueLoadNonce: String,
        forcedMimeBySongId: Map<String, String> = emptyMap()
    ) {
        if (songs.isEmpty()) return

        val interestingIndexes = linkedSetOf<Int>().apply {
            add(0)
            add(startIndex)
            add((startIndex - 1).coerceAtLeast(0))
            add((startIndex + 1).coerceAtMost(songs.lastIndex))
            add(songs.lastIndex)
        }

        interestingIndexes
            .filter { it in songs.indices }
            .sorted()
            .forEach { index ->
                val song = songs[index]
                // Use the forced MIME that was actually sent to the Cast queue; fall back to
                // the container-level resolution only for diagnostic display.
                val sentMime = forcedMimeBySongId[song.id] ?: song.resolveCastContentType()
                val streamRevision = song.buildCastStreamRevision(sentMime, queueLoadNonce)
                val likelySupported = sentMime in setOf(
                    "audio/mpeg",
                    "audio/aac",
                    "audio/mp4",
                    "audio/flac",
                    "audio/wav",
                    "audio/ogg",
                    "audio/webm",
                    "audio/amr"
                )
                val mediaUrl = CastSessionSecurity.redactAuthToken(
                    CastSessionSecurity.buildSongUrl(
                        serverAddress = serverAddress,
                        songId = song.id,
                        streamRevision = streamRevision,
                        authToken = authToken
                    )
                )
                val artUrl = CastSessionSecurity.redactAuthToken(
                    CastSessionSecurity.buildArtUrl(
                        serverAddress = serverAddress,
                        songId = song.id,
                        streamRevision = streamRevision,
                        authToken = authToken
                    )
                )
                Timber.tag(castLogTag).d(
                    "queueItem[%d] id=%s mimeRaw=%s mimeSent=%s mimeForced=%s supported=%s durationHintMs=%d streamDurationSentMs=%d mediaUrl=%s artUrl=%s",
                    index,
                    song.id,
                    song.mimeType,
                    sentMime,
                    forcedMimeBySongId.containsKey(song.id),
                    likelySupported,
                    song.duration,
                    MediaInfo.UNKNOWN_DURATION,
                    mediaUrl,
                    artUrl
                )
                Log.i(
                    "PX_CAST_QLOAD",
                    "item index=$index songId=${song.id} mimeRaw=${song.mimeType} mimeSent=$sentMime mimeForced=${forcedMimeBySongId.containsKey(song.id)} durationHintMs=${song.duration.coerceAtLeast(0L)} streamDurationSentMs=${MediaInfo.UNKNOWN_DURATION}"
                )
            }
    }
}
