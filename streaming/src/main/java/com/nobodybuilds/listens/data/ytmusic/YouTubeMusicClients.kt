package com.nobodybuilds.listens.data.ytmusic

import okhttp3.OkHttpClient

object YouTubeMusicClients {
    const val ORIGIN = "https://music.youtube.com"
    const val REFERER = "$ORIGIN/"
    const val API_URL = "$ORIGIN/youtubei/v1/"
    const val YOUTUBE_ORIGIN = "https://www.youtube.com"
    const val YOUTUBE_API_URL = "$YOUTUBE_ORIGIN/youtubei/v1/"
    const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    val WEB_REMIX = YouTubeMusicClient(
        clientName = "WEB_REMIX",
        clientVersion = "1.20261018.16.00",
        clientId = "67",
        userAgent = WEB_USER_AGENT,
        loginSupported = true,
    )

    /** youtube.com search, for videos that are not in the YouTube Music catalog. */
    val WEB = YouTubeMusicClient(
        clientName = "WEB",
        clientVersion = "2.20261019.01.00",
        clientId = "1",
        userAgent = WEB_USER_AGENT,
        useYoutubeHost = true,
    )

    /** Direct audio URLs that play to the end without a PO token or a signature solver. */
    val VISIONOS = YouTubeMusicClient(
        clientName = "VISIONOS",
        clientVersion = "1.02",
        clientId = "101",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        osName = "visionOS",
        osVersion = "26.5.23O471",
        deviceMake = "Apple",
        deviceModel = "RealityDevice17,1",
        useYoutubeHost = true,
    )

    /** Direct URLs too, but YouTube may cut them off after the first megabyte. */
    val IOS = YouTubeMusicClient(
        clientName = "IOS",
        clientVersion = "21.26.4",
        clientId = "5",
        userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
        osName = "iPhone",
        osVersion = "18.3.2.22D82",
        deviceMake = "Apple",
        deviceModel = "iPhone16,2",
        useYoutubeHost = true,
        probeBeforeUse = true,
    )

    /** Browse, search, and library. */
    val METADATA = WEB_REMIX

    /**
     * googlevideo URLs name the client that asked for them (`c=VISIONOS`) and only answer
     * that client's User-Agent, so take it from the URL rather than from shared state.
     */
    fun userAgentForStreamUrl(url: String): String {
        val client = Regex("""[?&]c=([A-Z_]+)""").find(url)?.groupValues?.get(1)
        return STREAM.firstOrNull { it.clientName == client }?.userAgent ?: VISIONOS.userAgent
    }

    /** Stream clients in the order they are tried. */
    val STREAM = listOf(VISIONOS, IOS)

    /**
     * The app-wide client rewrites User-Agent and, in debug, logs whole bodies.
     * YouTube binds stream URLs to the client's User-Agent, and body logging
     * would buffer entire songs, so YouTube traffic uses the client without them.
     */
    fun http(appClient: OkHttpClient): OkHttpClient = appClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .build()
}
