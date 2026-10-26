package com.nobodybuilds.listens.data.jiosaavn

import okhttp3.OkHttpClient
import okhttp3.Request

object JioSaavnClients {
    const val ORIGIN = "https://www.jiosaavn.com"
    const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    /**
     * The app-wide client renames the User-Agent and, in debug, logs whole bodies, which
     * would buffer entire songs. JioSaavn requests get a clean copy of it instead.
     */
    /** The headers jiosaavn.com's own player sends with every file request. */
    fun decorateMediaRequest(builder: Request.Builder): Request.Builder = builder
        .header("User-Agent", WEB_USER_AGENT)
        .header("Referer", "$ORIGIN/")
        .header("Origin", ORIGIN)

    fun http(appClient: OkHttpClient): OkHttpClient = appClient.newBuilder()
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .build()
}
