package com.nobodybuilds.listens.data.jiosaavn

import java.util.concurrent.ConcurrentHashMap

/**
 * Finds a playable file for a JioSaavn song: its encrypted media URL from the song's details, then a signed
 * URL from JioSaavn's own `song.generateAuthToken`, so the server decides what each account may play.
 * 320 kbps is for Pro accounts; when it is refused, the free 160 kbps is asked for instead.
 */
class JioSaavnStreamFinder(private val api: JioSaavnApi) {

    private val media = ConcurrentHashMap<String, JioSaavnMedia>()

    suspend fun find(session: JioSaavnSession, songId: String, quality: JioSaavnAudioQuality): JioSaavnStream {
        val details = media[songId] ?: JioSaavnParsers.parseMedia(api.songDetails(session, listOf(songId)))[songId]
            ?.also { media[songId] = it }
            ?: throw IllegalStateException("This song is not on JioSaavn")
        if (details.drmOnly) throw IllegalStateException("Only the JioSaavn app can play this song")
        if (details.encryptedUrl.isBlank()) throw IllegalStateException("JioSaavn did not return a playable stream")
        // Asking for 320 kbps where a song has none gets nothing, so ask for the best it has.
        val kbps = if (quality.kbps > FREE_KBPS && !details.has320) FREE_KBPS else quality.kbps
        val token: suspend (Int) -> JioSaavnStream? = { asked ->
            runCatching { JioSaavnParsers.parseAuthToken(api.authToken(session, details.encryptedUrl, asked, songId), songId) }
                .getOrNull()
        }
        return token(kbps) ?: (if (kbps > FREE_KBPS) token(FREE_KBPS) else null)
            ?: throw IllegalStateException("JioSaavn did not return a playable stream")
    }

    /** Forget a song's details, e.g. after its URL was refused. */
    fun forget(songId: String) {
        media.remove(songId)
    }

    private companion object {
        const val FREE_KBPS = 160
    }
}
