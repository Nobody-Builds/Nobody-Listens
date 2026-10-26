package com.nobodybuilds.listens.data

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import kotlinx.coroutines.runBlocking

/**
 * Data source for the watch player: local files as before, and `ytmusic://` / `jiosaavn://` songs resolved to
 * their stream URL as they load (on the loader thread, so blocking is fine).
 *
 * googlevideo only answers the User-Agent of the client that asked for the URL, and stalls on a request without
 * a Range header, which ExoPlayer leaves out when reading from the start. JioSaavn's file server wants
 * jiosaavn.com's User-Agent, Referer and Origin.
 */
@OptIn(UnstableApi::class)
internal fun wearPlayerDataSourceFactory(
    application: Application,
    youTube: WearYouTubeRepository,
    jioSaavn: WearJioSaavnRepository,
): DataSource.Factory {
    // No factory User-Agent: it would replace the per-request one set below.
    val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
    return ResolvingDataSource.Factory(DefaultDataSource.Factory(application, http)) { dataSpec ->
        resolveYouTube(dataSpec, youTube) ?: resolveJioSaavn(dataSpec, jioSaavn) ?: dataSpec
    }
}

@OptIn(UnstableApi::class)
private fun resolveYouTube(dataSpec: DataSpec, youTube: WearYouTubeRepository): DataSpec? {
    val videoId = WearYouTubeRepository.videoIdFrom(dataSpec.uri) ?: return null
    val stream = runBlocking { youTube.resolveStream(videoId) }
    val headers = buildMap {
        putAll(dataSpec.httpRequestHeaders)
        put("User-Agent", stream.userAgent)
        if (dataSpec.position == 0L && dataSpec.length == C.LENGTH_UNSET.toLong()) put("Range", "bytes=0-")
    }
    return dataSpec.buildUpon().setUri(stream.url).setHttpRequestHeaders(headers).build()
}

@OptIn(UnstableApi::class)
private fun resolveJioSaavn(dataSpec: DataSpec, jioSaavn: WearJioSaavnRepository): DataSpec? {
    val songId = WearJioSaavnRepository.songIdFrom(dataSpec.uri) ?: return null
    val stream = runBlocking { jioSaavn.resolveStream(songId) }
    return dataSpec.buildUpon()
        .setUri(stream.url)
        .setHttpRequestHeaders(dataSpec.httpRequestHeaders + WearJioSaavnRepository.MEDIA_HEADERS)
        .build()
}
