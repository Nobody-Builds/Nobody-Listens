package com.nobodybuilds.listens.data.ytmusic

import java.util.concurrent.ConcurrentHashMap

/**
 * Finds a playable audio URL for a song. Tries each stream client in turn and rests one for
 * ten minutes when YouTube refuses it, so the next lookups go straight to a client that works.
 */
class YouTubeMusicStreamFinder(private val api: YouTubeMusicApi) {

    private val failedClientsUntil = ConcurrentHashMap<String, Long>()

    suspend fun find(
        session: YouTubeMusicSession,
        videoId: String,
        quality: YouTubeMusicAudioQuality,
        opusOnly: Boolean = false,
    ): YouTubeMusicStream {
        var lastError = "YouTube Music did not return a playable stream"
        val now = System.currentTimeMillis()
        val clients = YouTubeMusicClients.STREAM.filter { (failedClientsUntil[it.clientName] ?: 0L) <= now }
            .ifEmpty { YouTubeMusicClients.STREAM }
        for (client in clients) {
            val payload = runCatching {
                YouTubeMusicParsers.parsePlayer(api.player(session, client, videoId))
            }.getOrElse { error ->
                lastError = error.message ?: lastError
                continue
            }
            if (payload.status != "OK") {
                lastError = payload.reason ?: payload.status
                if (isBotCheck(lastError)) markClientFailed(client.clientName)
                continue
            }
            val formats = YouTubeMusicFormats.ranked(payload.formats, payload.durationMs, quality.maxBitrate, opusOnly)
            for (format in formats.take(3)) {
                val url = YouTubeMusicFormats.resolveUrl(format) ?: continue
                if (client.probeBeforeUse && !api.probeStream(url, client.userAgent, format.contentLength)) {
                    // Every format of a client fails the same way, so move on to the next client.
                    lastError = "YouTube Music refused the stream"
                    markClientFailed(client.clientName)
                    break
                }
                return YouTubeMusicStream(
                    videoId = videoId,
                    url = url,
                    format = format,
                    expiresInSeconds = payload.expiresInSeconds ?: DEFAULT_EXPIRY_SECONDS,
                    userAgent = client.userAgent,
                    origin = if (client.useYoutubeHost) YouTubeMusicClients.YOUTUBE_ORIGIN else YouTubeMusicClients.ORIGIN,
                    durationMs = payload.durationMs ?: format.approxDurationMs ?: 0L,
                    clientName = client.clientName,
                )
            }
        }
        throw IllegalStateException(lastError)
    }

    /** A client's URL was refused mid-playback: rest it so the next lookup picks another. */
    fun markClientFailed(clientName: String) {
        failedClientsUntil[clientName] = System.currentTimeMillis() + CLIENT_BACKOFF_MS
    }

    private fun isBotCheck(reason: String): Boolean {
        val lower = reason.lowercase()
        return "bot" in lower || "sign in" in lower
    }

    private companion object {
        const val DEFAULT_EXPIRY_SECONDS = 6 * 60 * 60
        const val CLIENT_BACKOFF_MS = 10L * 60 * 1000
    }
}
