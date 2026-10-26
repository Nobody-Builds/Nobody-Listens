package com.nobodybuilds.listens.data.service.player

import android.net.Uri
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.nobodybuilds.listens.data.gdrive.GDriveStreamProxy
import com.nobodybuilds.listens.data.jellyfin.JellyfinStreamProxy
import com.nobodybuilds.listens.data.navidrome.NavidromeStreamProxy
import com.nobodybuilds.listens.data.netease.NeteaseStreamProxy
import com.nobodybuilds.listens.data.qqmusic.QqMusicStreamProxy
import com.nobodybuilds.listens.data.telegram.TelegramRepository
import com.nobodybuilds.listens.data.telegram.TelegramStreamProxy
import com.nobodybuilds.listens.data.ytmusic.YouTubeMusicStreamProxy
import com.nobodybuilds.listens.presentation.viewmodel.ConnectivityStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Turns cloud URIs (telegram://, netease://, ytmusic://, …) into playable local-proxy or file
 * URIs for [DualPlayerEngine], and keeps the last 100 results so ExoPlayer can reuse them.
 */
@OptIn(UnstableApi::class)
internal class CloudUriResolver(
    private val telegramRepository: TelegramRepository,
    private val telegramStreamProxy: TelegramStreamProxy,
    private val neteaseStreamProxy: NeteaseStreamProxy,
    private val qqMusicStreamProxy: QqMusicStreamProxy,
    private val navidromeStreamProxy: NavidromeStreamProxy,
    private val jellyfinStreamProxy: JellyfinStreamProxy,
    private val youTubeMusicStreamProxy: YouTubeMusicStreamProxy,
    private val gdriveStreamProxy: GDriveStreamProxy,
    private val connectivityStateHolder: ConnectivityStateHolder
) {
    private val resolvedUriCache = LruCache<String, Uri>(100)

    val dataSpecResolver = object : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val uri = dataSpec.uri
            youTubeMusicStreamProxy.rebindStaleProxyUri(uri)?.let { current ->
                return dataSpec.buildUpon().setUri(current).build()
            }
            val scheme = uri.scheme
            if (scheme in REMOTE_MEDIA_SCHEMES) {
                val originalUri = uri.toString()
                val resolved = resolvedUriCache.get(originalUri)
                if (resolved != null) {
                    return dataSpec.buildUpon().setUri(resolved).build()
                }

                Timber.tag("DualPlayerEngine").d("resolveDataSpec: Cache MISS for %s - attempting to use original URI", scheme)
            }
            return dataSpec
        }
    }

    /** Resolves the songs either side of the current one, so skipping does not wait for the network. */
    suspend fun preResolveNeighbours(playerA: Player) {
        try {
            val currentIndex = playerA.currentMediaItemIndex
            if (currentIndex != C.INDEX_UNSET) {
                val itemsToPreResolve = mutableListOf<Uri>()

                if (currentIndex + 1 < playerA.mediaItemCount) {
                    playerA.getMediaItemAt(currentIndex + 1).localConfiguration?.uri?.let {
                        itemsToPreResolve.add(it)
                    }
                }
                if (currentIndex - 1 >= 0) {
                    playerA.getMediaItemAt(currentIndex - 1).localConfiguration?.uri?.let {
                        itemsToPreResolve.add(it)
                    }
                }

                for (uriToResolve in itemsToPreResolve) {
                    val scheme = uriToResolve.scheme
                    if (scheme == "telegram" || scheme == "netease" || scheme == "qqmusic" || scheme == "navidrome" || scheme == "jellyfin" || scheme == "gdrive" || scheme == "ytmusic") {
                        resolveCloudUri(uriToResolve)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("DualPlayerEngine").w(e, "Error during pre-resolution in onMediaItemTransition")
        }
    }

    suspend fun resolveCloudUri(uri: Uri): Uri = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        resolvedUriCache.get(uriString)?.let { return@withContext it }

        val resolved: Uri? = when (uri.scheme) {
            "telegram" -> resolveTelegramUriAsync(uri, uriString)
            "netease" -> resolveNeteaseUriAsync(uriString)
            "qqmusic" -> resolveQqMusicUriAsync(uriString)
            "navidrome" -> resolveNavidromeUriAsync(uriString)
            "jellyfin" -> resolveJellyfinUriAsync(uriString)
            "ytmusic" -> resolveYouTubeMusicUriAsync(uriString)
            "gdrive" -> resolveGDriveUriAsync(uriString)
            else -> null
        }

        if (resolved != null) {
            resolvedUriCache.put(uriString, resolved)
            return@withContext resolved
        }
        uri
    }

    private suspend fun resolveTelegramUriAsync(uri: Uri, uriString: String): Uri? = withContext(Dispatchers.IO) {
        val pathSegments = uri.pathSegments
        val fileId = if (pathSegments.isNotEmpty()) {
            telegramRepository.resolveTelegramUri(uriString)?.first
        } else {
            uri.host?.toIntOrNull()
        } ?: return@withContext null

        val fileInfo = telegramRepository.getFile(fileId)
        if (fileInfo?.local?.isDownloadingCompleted == true && fileInfo.local.path.isNotEmpty()) {
            return@withContext Uri.fromFile(File(fileInfo.local.path))
        }

        if (!connectivityStateHolder.isOnline.value) {
            connectivityStateHolder.triggerOfflineBlockedEvent()
            return@withContext null
        }

        if (!telegramStreamProxy.ensureReady(5_000L)) return@withContext null
        val proxyUrl = telegramStreamProxy.getProxyUrl(fileId, 0L)
        if (proxyUrl.isNotEmpty()) Uri.parse(proxyUrl) else null
    }

    private suspend fun resolveNeteaseUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!neteaseStreamProxy.ensureReady(5_000L)) return@withContext null
        neteaseStreamProxy.resolveNeteaseUri(uriString)?.let { Uri.parse(it) }
    }

    private suspend fun resolveQqMusicUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!qqMusicStreamProxy.ensureReady(5_000L)) return@withContext null
        qqMusicStreamProxy.warmUpStreamUrl(uriString)
        qqMusicStreamProxy.resolveQqMusicUri(uriString)?.let { Uri.parse(it) }
    }

    private suspend fun resolveNavidromeUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!navidromeStreamProxy.ensureReady(5_000L)) return@withContext null
        navidromeStreamProxy.warmUpStreamUrl(uriString)
        navidromeStreamProxy.resolveNavidromeUri(uriString)?.let { Uri.parse(it) }
    }

    private suspend fun resolveJellyfinUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!jellyfinStreamProxy.ensureReady(5_000L)) return@withContext null
        jellyfinStreamProxy.warmUpStreamUrl(uriString)
        jellyfinStreamProxy.resolveJellyfinUri(uriString)?.let { Uri.parse(it) }
    }

    private suspend fun resolveYouTubeMusicUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!youTubeMusicStreamProxy.ensureReady(5_000L)) return@withContext null
        youTubeMusicStreamProxy.warmUpStreamUrl(uriString)
        youTubeMusicStreamProxy.resolveYouTubeMusicUri(uriString)?.let { Uri.parse(it) }
    }

    private suspend fun resolveGDriveUriAsync(uriString: String): Uri? = withContext(Dispatchers.IO) {
        if (!connectivityStateHolder.isOnline.value) {
            connectivityStateHolder.triggerOfflineBlockedEvent()
            return@withContext null
        }
        if (!gdriveStreamProxy.ensureReady(5_000L)) return@withContext null
        gdriveStreamProxy.resolveGDriveUri(uriString)?.let { Uri.parse(it) }
    }
}
