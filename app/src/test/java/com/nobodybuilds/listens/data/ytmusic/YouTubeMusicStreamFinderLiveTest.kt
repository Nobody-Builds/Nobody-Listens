package com.nobodybuilds.listens.data.ytmusic

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/** The watch's path to a song: the shared finder, then a read from byte 0 with only the client's User-Agent. */
class YouTubeMusicStreamFinderLiveTest {

    @Test
    fun `live - the finder returns a URL that serves the watch's first request`() = runBlocking {
        assumeTrue(System.getenv("YTMUSIC_LIVE") == "1", "set YTMUSIC_LIVE=1 to hit YouTube")
        val http = OkHttpClient()
        val api = YouTubeMusicApi(http)
        val session = YouTubeMusicSession(visitorData = requireNotNull(api.fetchVisitorData("")))

        val stream = YouTubeMusicStreamFinder(api).find(session, "kJQP7kiw5Fk", YouTubeMusicAudioQuality.NORMAL)
        assertTrue(stream.clientName.isNotBlank())
        assertTrue(stream.format.bitrate <= YouTubeMusicAudioQuality.NORMAL.maxBitrate, "bitrate ${stream.format.bitrate}")

        val request = Request.Builder().url(stream.url)
            .header("User-Agent", stream.userAgent)
            .header("Range", "bytes=0-")
            .build()
        http.newCall(request).execute().use { response ->
            assertEquals(206, response.code)
            val firstMegabyte = ByteArray(1024 * 1024)
            val read = response.body.byteStream().readNBytes(firstMegabyte, 0, firstMegabyte.size)
            assertEquals(firstMegabyte.size, read)
        }
        println("Live OK: ${stream.clientName} itag ${stream.format.itag} at ${stream.format.bitrate / 1000} kbps")
    }
}
