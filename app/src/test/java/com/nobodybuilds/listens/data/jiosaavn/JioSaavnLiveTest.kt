package com.nobodybuilds.listens.data.jiosaavn

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/** Talks to JioSaavn itself, so it only runs with JIOSAAVN_LIVE=1. */
class JioSaavnLiveTest {

    @Test
    fun `search, signed-out stream and the start of the file`() = runBlocking {
        assumeTrue(System.getenv("JIOSAAVN_LIVE") == "1")
        val http = OkHttpClient()
        val api = JioSaavnApi(http)
        val signedOut = JioSaavnSession()

        val track = JioSaavnParsers.parseSearchSongs(api.searchSongs(signedOut, "kesariya")).first()
        val media = JioSaavnParsers.parseMedia(api.songDetails(signedOut, listOf(track.id))).getValue(track.id)
        assertTrue(media.encryptedUrl.isNotBlank(), "no plain media URL for ${track.id}")

        val stream = listOf(320, 160).firstNotNullOfOrNull { kbps ->
            runCatching { JioSaavnParsers.parseAuthToken(api.authToken(signedOut, media.encryptedUrl, kbps, track.id), track.id) }.getOrNull()
        }
        checkNotNull(stream) { "JioSaavn gave no stream for ${track.id}" }
        println("JioSaavn granted ${stream.bitrate / 1000} kbps signed out for ${track.title}")

        // The same headers the app and the watch send; the file server answers 403 without the Referer/Origin.
        val request = JioSaavnClients.decorateMediaRequest(Request.Builder().url(stream.url))
            .header("Range", "bytes=0-1023")
            .build()
        http.newCall(request).execute().use { response ->
            assertEquals(206, response.code)
            assertEquals(1024, response.body.bytes().size)
        }
    }
}
